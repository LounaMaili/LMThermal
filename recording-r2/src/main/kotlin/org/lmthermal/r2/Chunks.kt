package org.lmthermal.r2

import org.lmthermal.r2.R2Json as LmtxJson
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun Map<String, Any?>.long(key: String): Long = getValue(key).toString().toLong().also { require(it >= 0) }
internal fun Map<String, Any?>.int(key: String): Int = Math.toIntExact(long(key))
@Suppress("UNCHECKED_CAST") internal fun Any?.objectMap(): Map<String, Any?> = this as? Map<String, Any?> ?: throw IllegalArgumentException("object_required")
internal fun Map<String, Any?>.list(key: String): List<Any?> = getValue(key) as? List<Any?> ?: throw IllegalArgumentException("array_required")

/** Role-major independent blocks preserve logical per-frame hashes. HT Full stores transport once.
 * Context closure is chunk-local; no seeking reader needs an earlier frame's settings.
 */
class ChunkEncoder(private val profile: Profile, private val codec: BlockCodec) {
    data class Encoded(val parts: List<ByteArray>, val logicalBytes: Long, val physicalBytes: Long,
        val storedBytes: Long, val metadataBytes: Int, val codecNs: Long)
    fun encode(observations: List<Observation>): Encoded {
        require(observations.size in 1..Bounds.ENTRIES)
        var aggregate = 0L
        observations.forEach { frame ->
            frame.validate()
            val physical = (frame.temperature?.size?.toLong() ?: 0L) + (frame.mask?.size ?: 0) + when (profile) {
                Profile.ANALYSIS -> 0L; Profile.NATIVE -> frame.native?.size?.toLong() ?: 0L
                Profile.FULL -> frame.acquisition?.size?.toLong() ?: 0L
            }
            aggregate = Bounds.add(aggregate, physical); require(aggregate <= Bounds.CHUNK)
        }
        val contexts = mutableListOf<Map<String, Any?>>()
        val contextKeys = mutableListOf<String>()
        val roles = linkedMapOf<String, ByteArrayOutputStream>()
        var logical = 0L
        val entries = observations.map { frame ->
            // The complete owned observation was validated in the admission pass above.
            val contextBytes = LmtxJson.encode(frame.context)
            val contextHash = hex(sha(contextBytes))
            var contextIndex = contextKeys.indexOf(contextHash)
            if (contextIndex < 0) { contextIndex = contexts.size; contexts += frame.context; contextKeys += contextHash }
            val descriptors = linkedMapOf<String, Any?>()
            fun materialize(role: String, bytes: ByteArray, dtype: String, shape: List<Int>, encoding: String? = null) {
                val block = roles.getOrPut(role) { ByteArrayOutputStream() }
                val offset = block.size(); require(Bounds.add(offset.toLong(), bytes.size.toLong()) <= Bounds.CHUNK)
                block.write(bytes); logical = Bounds.add(logical, bytes.size.toLong())
                descriptors[role] = mapOf("kind" to "materialized", "block" to role, "offset" to offset,
                    "length" to bytes.size, "hash" to hex(sha(bytes)), "dtype" to dtype, "shape" to shape, "encoding" to encoding)
            }
            frame.temperature?.let { temperature ->
                val shape = listOf(frame.height, frame.width)
                materialize("temperature", temperature, "f32le", shape)
                frame.mask?.let { materialize("validity", it, "u8", shape) }
                if (profile == Profile.NATIVE) materialize("native", requireNotNull(frame.native), "u16le", shape, frame.nativeEncoding)
                if (profile == Profile.FULL) {
                    val transport = requireNotNull(frame.acquisition); val native = requireNotNull(frame.native)
                    require(transport.size >= native.size && native.indices.all { transport[it] == native[it] })
                    materialize("acquisition", transport, "u8", listOf(transport.size))
                    logical = Bounds.add(logical, native.size.toLong())
                    descriptors["native"] = mapOf("kind" to "view", "parent" to "acquisition", "offset" to 0,
                        "length" to native.size, "hash" to hex(sha(native)), "dtype" to "u16le", "shape" to shape,
                        "encoding" to frame.nativeEncoding)
                }
            }
            mapOf("sequence" to frame.sequence.toString(), "gap_end" to frame.gapEnd.toString(),
                "receipt_ns" to frame.receiptNs?.toString(), "relative_ns" to frame.relativeNs.toString(),
                "width" to frame.width, "height" to frame.height, "context" to contextIndex,
                "reason" to frame.reason, "payloads" to descriptors)
        }
        var offset = 0L; var physical = 0L; var codecNs = 0L
        val encoded = mutableListOf<ByteArray>()
        val blocks = roles.map { (role, stream) ->
            val original = stream.toByteArray(); physical = Bounds.add(physical, original.size.toLong())
            val start = System.nanoTime(); val stored = codec.encode(original); codecNs += System.nanoTime() - start
            val descriptor = mapOf("role" to role, "offset" to offset, "stored" to stored.size,
                "decoded" to original.size, "codec" to codec.id, "hash" to hex(sha(original)))
            offset = Bounds.add(offset, stored.size.toLong()); encoded += stored; descriptor
        }
        val metadata = LmtxJson.encode(mapOf("profile" to profile.name, "contexts" to contexts,
            "entries" to entries, "blocks" to blocks))
        require(metadata.size <= Bounds.META && Bounds.add(physical, metadata.size.toLong()) <= Bounds.CHUNK)
        require(Bounds.add(offset, metadata.size.toLong() + 4) <= Bounds.RECORD - 112)
        return Encoded(listOf(little(4).putInt(metadata.size).array(), metadata) + encoded,
            logical, physical, offset, metadata.size, codecNs)
    }
}

/** Validate before exposing bytes; a view can reference only its own materialized acquisition.
 * Hashes are integrity checks, not signatures or a claim of independent physical accuracy.
 */
class ChunkDecoder(private val codecs: Map<Int, BlockCodec> = mapOf(0 to Stored, 1 to Deflate1)) {
    data class Decoded(val metadata: Map<String, Any?>, val entries: List<Map<String, Any?>>, val bytes: List<Map<String, ByteArray>>)
    fun decode(reader: RecordReader, record: RecordReader.Record): Decoded {
        require(record.type == RecordType.CHUNK)
        reader.record(record.ref.offset, record.ref)
        val start = record.ref.offset + 40
        val metadataLength = ByteBuffer.wrap(reader.bytes(start, 4)).order(ByteOrder.LITTLE_ENDIAN).int
        require(metadataLength in 1..Bounds.META && metadataLength.toLong() + 4 <= record.bodyLength)
        val metadata = LmtxJson.decode(reader.bytes(start + 4, metadataLength))
        return decodeMetadata(metadata, record.bodyLength - 4 - metadataLength, metadataLength) { at, size -> reader.bytes(start + 4 + metadataLength + at, size) }
    }
    fun decodeMetadata(metadata: Map<String, Any?>, storedLength: Int, metadataLength: Int = LmtxJson.encode(metadata).size, read: (Long, Int) -> ByteArray): Decoded {
        require(metadata["profile"] in Profile.entries.map { it.name })
        val contexts = metadata.list("contexts"); require(contexts.size in 1..Bounds.ENTRIES); contexts.forEach { it.objectMap() }
        val entries = metadata.list("entries").map { it.objectMap() }; require(entries.size in 1..Bounds.ENTRIES)
        val blockDescriptions = metadata.list("blocks").map { it.objectMap() }; require(blockDescriptions.size <= 4)
        var decodedSum = 0L; var storedSum = 0L
        val blocks = linkedMapOf<String, ByteArray>()
        // Check every length and the aggregate BEFORE allocating the first decompression buffer.
        blockDescriptions.forEach { block ->
            val role = block["role"] as String; require(role in listOf("temperature", "validity", "native", "acquisition"))
            require(block.long("offset") == storedSum)
            storedSum = Bounds.add(storedSum, block.long("stored")); decodedSum = Bounds.add(decodedSum, block.long("decoded"))
            require(storedSum <= storedLength && Bounds.add(decodedSum, metadataLength.toLong()) <= Bounds.CHUNK && block.int("codec") in codecs)
        }
        require(storedSum == storedLength.toLong())
        blockDescriptions.forEach { block ->
            val role = block["role"] as String; require(role !in blocks)
            val bytes = codecs.getValue(block.int("codec")).decode(read(block.long("offset"), block.int("stored")), block.int("decoded"))
            require(hex(sha(bytes)) == block["hash"]) { "block_integrity" }; blocks[role] = bytes
        }
        var previous = -1L; var previousTime = -1L
        val used = mutableMapOf<String, Int>()
        val payloads = entries.map { entry ->
            val sequence = entry.long("sequence"); val gapEnd = entry.long("gap_end"); val time = entry.long("relative_ns")
            require(sequence > previous && gapEnd >= sequence && time >= previousTime)
            previous = gapEnd; previousTime = time
            entry["receipt_ns"]?.let { require(it.toString().toLong() >= 0) }
            require(entry.int("context") in contexts.indices)
            val pixels = Bounds.pixels(entry.int("width"), entry.int("height")); val shape = listOf(entry.int("height"), entry.int("width"))
            val descriptors = entry.getValue("payloads").objectMap(); require(descriptors.size <= 4)
            val resolved = linkedMapOf<String, ByteArray>()
            descriptors.forEach { (role, value) ->
                val descriptor = value.objectMap(); require(role in listOf("temperature", "validity", "native", "acquisition"))
                require(descriptor.keys.none { it in listOf("stride", "transform", "frame", "chunk") })
                val length = descriptor.int("length"); val offset = descriptor.long("offset")
                val view = descriptor["kind"] == "view"
                val expectedType = when (role) { "temperature" -> "f32le"; "native" -> "u16le"; else -> "u8" }
                val expectedLength = when (role) { "temperature" -> Math.multiplyExact(pixels, 4); "native" -> Math.multiplyExact(pixels, 2); "validity" -> pixels; else -> length }
                val actualShape = descriptor.list("shape").map { it.toString().toInt() }
                require(descriptor["dtype"] == expectedType && length == expectedLength &&
                    actualShape == if (role == "acquisition") listOf(length) else shape)
                val parent: ByteArray
                if (view) {
                    require(role == "native" && metadata["profile"] == "FULL" && descriptor["parent"] == "acquisition" && "block" !in descriptor)
                    val parentDescriptor = descriptors["acquisition"].objectMap(); require(parentDescriptor["kind"] == "materialized")
                    require(parentDescriptor["dtype"] == "u8" && offset == 0L)
                    parent = resolved["acquisition"] ?: run {
                        val block = blocks.getValue(parentDescriptor["block"] as String)
                        val p = parentDescriptor.int("offset"); val n = parentDescriptor.int("length")
                        require(Bounds.add(p.toLong(), n.toLong()) <= block.size)
                        block.copyOfRange(p, p + n).also { require(hex(sha(it)) == parentDescriptor["hash"]) { "parent_integrity" } }
                    }
                } else {
                    require(descriptor["kind"] == "materialized" && "parent" !in descriptor && descriptor["block"] == role)
                    parent = blocks.getValue(role); require(offset == (used[role] ?: 0).toLong())
                    used[role] = Math.toIntExact(Bounds.add(offset, length.toLong()))
                }
                require(Bounds.add(offset, length.toLong()) <= parent.size)
                val bytes = parent.copyOfRange(offset.toInt(), offset.toInt() + length)
                require(hex(sha(bytes)) == descriptor["hash"]) { "logical_integrity" }; resolved[role] = bytes
            }
            val temperature = resolved["temperature"]
            if (temperature == null) require(resolved.isEmpty() && entry["reason"] is String)
            else {
                require(gapEnd == sequence && entry["reason"] == null)
                val mask = resolved["validity"]; require(mask == null || mask.all { it == 0.toByte() || it == 1.toByte() })
                val values = ByteBuffer.wrap(temperature).order(ByteOrder.LITTLE_ENDIAN)
                repeat(pixels) { i -> val bits = values.int; if (mask == null || mask[i] == 1.toByte()) require(Float.fromBits(bits).isFinite()) else require(bits == 0) }
                if (metadata["profile"] != "ANALYSIS") require("native" in resolved)
                if (metadata["profile"] == "FULL") require("acquisition" in resolved && descriptors["native"].objectMap()["kind"] == "view")
                if (entry.int("width") == 384 && entry.int("height") == 288 && descriptors["native"]?.objectMap()?.get("encoding") == "org.lmthermal.ht301.raw14") {
                    val raw = ByteBuffer.wrap(resolved.getValue("native")).order(ByteOrder.LITTLE_ENDIAN)
                    repeat(pixels) { require((raw.short.toInt() and 65535) < 16384) }
                    if (metadata["profile"] == "FULL") require(resolved.getValue("acquisition").size == 224256)
                }
            }
            resolved
        }
        require(blocks.all { (role, bytes) -> (used[role] ?: 0) == bytes.size })
        return Decoded(metadata, entries, payloads)
    }
}
