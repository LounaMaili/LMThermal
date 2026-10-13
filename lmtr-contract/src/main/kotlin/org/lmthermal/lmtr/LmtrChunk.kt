package org.lmthermal.lmtr

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** A verified selected chunk owns its decoded logical bytes, never a live camera buffer. */
data class ChunkData(val metadata: Obj, val payloads: Map<String, ByteArray>, val logicalHashes: Map<String, String>)

/** Complete payload integrity and numerical checks, independently of JSON shape validation. */
object LmtrChunk {
    val roles = listOf("temperature", "validity", "native_samples", "acquisition", "preview", "visible", "module_extension")
    private val widths = mapOf("u8" to 1, "i8" to 1, "u16" to 2, "i16" to 2, "u32" to 4, "i32" to 4, "f32" to 4, "u64" to 8, "i64" to 8, "f64" to 8)

    /** SHA-256 binds exact binary bytes, separately from canonical semantic context IDs. */
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Checked nonnegative addition reports a format bound rather than integer wraparound. */
    fun add(a: Long, b: Long): Long {
        demand(a >= 0 && b >= 0, "invalid_container")
        return try { Math.addExact(a, b) } catch (_: ArithmeticException) { throw LmtrFailure("resource_limit") }
    }

    /** Split bounded metadata before interpreting stored blocks or allocating decoded buffers. */
    fun metadata(body: ByteArray): Pair<Obj, Int> {
        demand(body.size >= 4, "invalid_container")
        val length = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        demand(length in 1..1048576L, "resource_limit")
        demand(length <= body.size - 4L, "invalid_container")
        return LmtrJson.parse(body.copyOfRange(4, 4 + length.toInt())) to (4 + length.toInt())
    }

    /** Verify decoded block, logical/view hashes, full grids/masks and current caches. */
    fun decode(body: ByteArray, header: Obj, workingBudget: Long = 134217728L): ChunkData {
        val (meta, start) = metadata(body)
        for(block in meta["blocks"].arr().map { it.obj() }) demand(block["codec"].i() in 0..1,"unsupported_codec")
        LmtrMetadata.chunk(meta, header)
        val blocks = meta["blocks"].arr().map { it.obj() }
        var decodedBytes = 0L; var logicalBytes = 0L
        for (block in blocks) decodedBytes = add(decodedBytes, block["decoded"].d())
        for (entry in meta["entries"].arr().map { it.obj() }) {
            if (entry["kind"] == "observation") for (p in entry["payloads"].arr()) logicalBytes = add(logicalBytes, p.obj()["length"].d())
        }
        demand(add(decodedBytes, start.toLong()) <= 67108864L, "resource_limit")
        // Charge compressed body, decoded groups, logical slices and conservative
        // metadata overhead. Hard file ceilings are not a universal heap promise.
        val storedScratch = blocks.maxOfOrNull { it["stored"].d() } ?: 0L
        demand(add(add(body.size.toLong(), decodedBytes), add(add(logicalBytes, start * 8L), storedScratch)) <= workingBudget, "local_resource_limit")
        val decoded = linkedMapOf<String, ByteArray>(); var cursor = 0L
        val codecs = header["codecs"].arr().map { it.i() }
        for (block in blocks) {
            demand(block["offset"].d() == cursor, "invalid_payload")
            val size = block["stored"].d(); val end = add(cursor, size)
            demand(end <= body.size - start.toLong(), "invalid_container")
            val codec = block["codec"].i(); demand(codec in codecs, "unsupported_codec")
            val bytes = LmtrCodec.decode(body.copyOfRange(start + cursor.toInt(), start + end.toInt()), codec, block["decoded"].d(), workingBudget)
            demand(sha(bytes) == block["hash"].text(), "committed_payload_integrity")
            decoded[block["id"].text()] = bytes; cursor = end
        }
        demand(cursor == body.size - start.toLong(), "invalid_container")
        val payloads = linkedMapOf<String, ByteArray>(); val hashes = linkedMapOf<String, String>()
        val covered = blocks.associate { it["id"].text() to 0L }.toMutableMap()
        for (entry in meta["entries"].arr().map { it.obj() }) {
            if (entry["kind"] != "observation") continue
            val descriptors = entry["payloads"].arr().map { it.obj() }; val id = entry["entry_id"].text()
            for (p in descriptors.filter { it["kind"] == "materialized" }) {
                val blockId = p["block_id"].text(); val raw = decoded[blockId] ?: throw LmtrFailure("invalid_payload")
                val offset = p["offset"].d(); val end = add(offset, p["length"].d())
                demand(covered[blockId] == offset && end <= raw.size, "invalid_payload")
                covered[blockId] = end
                val bytes = raw.copyOfRange(offset.toInt(), end.toInt())
                demand(sha(bytes) == p["hash"].text(), "committed_payload_integrity")
                payloads["$id:${p["id"].text()}"] = bytes
            }
            for (p in descriptors.filter { it["kind"] == "view" }) {
                val parent = payloads["$id:${p["parent_payload_id"].text()}"] ?: throw LmtrFailure("invalid_payload")
                val offset = p["offset"].d(); val end = add(offset, p["length"].d())
                val alignment = widths[p["dtype"].text()] ?: 1
                demand(offset % alignment == 0L && end <= parent.size, "invalid_payload")
                val bytes = parent.copyOfRange(offset.toInt(), end.toInt())
                demand(sha(bytes) == p["hash"].text(), "committed_payload_integrity")
                payloads["$id:${p["id"].text()}"] = bytes
            }
            for (p in descriptors) {
                val key = "$id:${p["id"].text()}"; val bytes = payloads.getValue(key)
                if (p["encoding_id"] == "org.lmthermal.ht301.raw14") {
                    demand(p["shape"].arr().map { it.i() } == listOf(288, 384) && p["dtype"] == "u16" && p["byte_order"] == "little", "invalid_payload")
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    while (buffer.hasRemaining()) demand(buffer.short.toInt() and 65535 < 16384, "invalid_payload")
                }
                hashes[key] = sha(bytes)
            }
            measurement(entry, meta, payloads)
        }
        for ((id, bytes) in decoded) demand(covered[id] == bytes.size.toLong(), "invalid_payload")
        return ChunkData(meta, payloads, hashes)
    }

    /** Compute finite-valid row-major results without replacing any source bit pattern. */
    fun statistics(temperature: ByteArray, mask: ByteArray?, width: Int, height: Int,
                   bounds: List<Int> = listOf(0, 0, width, height)): Obj {
        demand(width in 1..16384 && height in 1..16384 && width.toLong()*height<=4194304L,"resource_limit")
        val pixels=width.toLong()*height
        demand(temperature.size.toLong()==pixels*4 && (mask==null || mask.size.toLong()==pixels),"invalid_payload")
        demand(bounds.size==4,"invalid_metadata")
        val (x1, y1, x2, y2) = bounds
        demand(x1>=0 && y1>=0 && x1<x2 && y1<y2 && x2<=width && y2<=height,"invalid_metadata")
        demand(mask==null || mask.all { it.toInt() in 0..1 },"invalid_payload")
        var count = 0; var sum = 0.0; var low = Double.POSITIVE_INFINITY; var high = Double.NEGATIVE_INFINITY
        var lowIndex = 0; var highIndex = 0
        val input = ByteBuffer.wrap(temperature).order(ByteOrder.LITTLE_ENDIAN)
        for (y in y1 until y2) for (x in x1 until x2) {
            val index = y * width + x
            if (mask != null && mask[index].toInt() == 0) continue
            val value = input.getFloat(index * 4).toDouble(); demand(value.isFinite(), "invalid_payload")
            if (value < low) { low = value; lowIndex = index }
            if (value > high) { high = value; highIndex = index }
            sum += value; count++
        }
        val result = linkedMapOf<String, Any?>("pixel_count" to (x2-x1)*(y2-y1), "valid_pixel_count" to count)
        if (count > 0) result.putAll(mapOf("min" to BigDecimal(low), "max" to BigDecimal(high), "mean" to BigDecimal(sum/count),
            "min_xy" to listOf(lowIndex%width, lowIndex/width), "max_xy" to listOf(highIndex%width, highIndex/width)))
        return result
    }

    /** Verify exact mask/filler authority and optional per-observation/ROI caches. */
    private fun measurement(entry: Obj, chunk: Obj, bytes: Map<String, ByteArray>) {
        val measurement = entry["measurement"].obj()
        if (measurement["status"] != "available") return
        val id = entry["entry_id"].text(); val temperature = bytes["$id:${measurement["temperature_payload_id"].text()}"] ?: throw LmtrFailure("invalid_payload")
        val mask = measurement["mask_payload_id"]?.let { bytes["$id:${it.text()}"] ?: throw LmtrFailure("invalid_payload") }
        val segment = chunk["segments"].arr().map { it.obj() }.single { it["id"] == entry["segment_id"] }
        val stream = segment["streams"].arr().map { it.obj() }.single { it["stream_id"] == entry["stream_id"] }
        val geometry = stream["geometry"].obj(); val width = geometry["width_px"].i(); val height = geometry["height_px"].i()
        val count = width * height; demand(temperature.size == count * 4 && (mask == null || mask.size == count), "invalid_payload")
        var valid = 0; val buffer = ByteBuffer.wrap(temperature).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val bit = mask?.get(i)?.toInt() ?: 1; demand(bit in 0..1, "invalid_payload")
            val raw = buffer.getInt(i * 4)
            if (bit == 0) demand(raw == 0, "invalid_payload")
            else { demand(Float.fromBits(raw).isFinite(), "invalid_payload"); valid++ }
        }
        val state = if (valid == count) "all_valid" else if (valid == 0) "no_valid_pixels" else "partially_valid"
        demand(measurement["validity"] == state, "invalid_payload")
        if (state != "all_valid") demand(mask != null, "invalid_payload")
        measurement["statistics"]?.let { compareStatistics(it.obj(), statistics(temperature, mask, width, height)) }
        entry["analysis"]?.let { a ->
            val analysis = a.obj()
            for (p in analysis["points"].arr().map { it.obj() }) p["temperature_c"]?.let { cached ->
                val index = p["y_px"].i()*width+p["x_px"].i()
                demand(mask == null || mask[index].toInt() == 1, "invalid_payload")
                val actual = buffer.getFloat(index*4).toDouble(); compareNumber(cached, actual)
            }
            for (r in analysis["rectangles"].arr().map { it.obj() }) r["statistics"]?.let { cached ->
                val b=r["bounds"].obj();compareStatistics(cached.obj(), statistics(temperature,mask,width,height,listOf(b["x1_px"].i(),b["y1_px"].i(),b["x2_px"].i(),b["y2_px"].i())))
            }
        }
    }

    /** Cached numeric values have the accepted still-compatible tolerance, not bit authority. */
    private fun compareNumber(saved: Any?, actual: Double) {
        val value = when(saved) { is JsonNumber -> saved.decimal.toDouble(); is BigDecimal -> saved.toDouble(); is Number -> saved.toDouble(); else -> throw LmtrFailure("invalid_metadata") }
        demand(value.isFinite() && kotlin.math.abs(value-actual) <= maxOf(1e-4,1e-6*kotlin.math.abs(actual)), "invalid_metadata")
    }

    /** Counts and tie locations are exact even where cached Celsius has tolerance. */
    private fun compareStatistics(saved: Obj, actual: Obj) {
        demand(saved["pixel_count"].i() == actual["pixel_count"].i() && saved["valid_pixel_count"].i() == actual["valid_pixel_count"].i(), "invalid_metadata")
        if (actual["valid_pixel_count"].i() == 0) demand(listOf("min","max","mean","min_xy","max_xy").none { it in saved }, "invalid_metadata")
        else {
            for (field in listOf("min","max","mean")) compareNumber(saved[field], (actual[field] as BigDecimal).toDouble())
            for (field in listOf("min_xy","max_xy")) demand(saved[field].arr().map { it.i() } == actual[field].arr().map { it.i() }, "invalid_metadata")
        }
    }
}
