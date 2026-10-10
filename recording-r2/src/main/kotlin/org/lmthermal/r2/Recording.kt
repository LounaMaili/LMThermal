package org.lmthermal.r2

import org.lmthermal.r2.R2Json as LmtxJson
import java.io.Closeable
import java.io.File

/** O(depth*fanout) rolling index. Snapshot pages are immutable and appended, never patched.
 * Partial snapshot nodes are not inserted into rolling buffers, preventing duplicate references.
 */
class PageIndex(private val writer: RecordWriter) {
    private val levels = List(Bounds.DEPTH) { mutableListOf<Reference>() }
    private fun page(depth: Int, references: List<Reference>): Reference {
        require(depth in 0 until Bounds.DEPTH && references.size in 1..Bounds.FANOUT)
        require(references.zipWithNext().all { (a, b) -> a.last < b.first })
        val bytes = LmtxJson.encode(mapOf("depth" to depth, "children" to references.map { it.json() }))
        require(bytes.size <= Bounds.PAGE)
        return writer.append(RecordType.INDEX_PAGE, listOf(bytes), references.first().first, references.last().last)
    }
    fun add(reference: Reference, depth: Int = 0) {
        require(depth < Bounds.DEPTH)
        levels[depth] += reference
        if (levels[depth].size == Bounds.FANOUT) { val parent = page(depth, levels[depth]); levels[depth].clear(); add(parent, depth + 1) }
    }
    fun snapshot(): Reference? {
        var carry: Reference? = null
        levels.forEachIndexed { depth, refs ->
            val children = refs.toList() + listOfNotNull(carry)
            carry = if (children.isEmpty()) null else page(depth, children)
        }
        return carry
    }
}

/** Disposable forward-only source recorder. Close/Stop seals a short chunk and END only on success.
 * Counter advancement is after commit sync; a refusing sink leaves pending data uncounted.
 */
class PrototypeRecorder(private val records: RecordWriter, val profile: Profile, private val codec: BlockCodec,
    private val chunkBoundary: (String, Long) -> Unit = { _, _ -> }) : Closeable {
    private val index = PageIndex(records)
    private val pending = mutableListOf<ProducerEntry>()
    private var pendingBytes = 0L
    private var previousSequence = -1L
    private var previousTime = -1L
    private var lastCheckpoint: Reference? = null
    private var lastChunk: Reference? = null
    var committedEntries = 0L; private set
    var committedChunks = 0L; private set
    var committedMeasurements = 0L; private set
    var committedGapSequences = 0L; private set
    @Volatile var encodingBufferBytes = 0L; private set
    @Volatile var pendingPayloadBytes = 0L; private set
    var physicalBytes = 0L; private set
    var logicalBytes = 0L; private set
    var storedBytes = 0L; private set
    var codecNs = 0L; private set
    var metadataBytes = 0L; private set
    var maxChunkBytes = 0L; private set
    var finalized = false; private set
    init {
        records.json(RecordType.HEADER, mapOf("artifact" to "noncanonical-r2", "revision" to 1,
            "profile" to profile.name, "required_features" to if (profile == Profile.FULL) listOf("contiguous-native-view") else emptyList<String>(),
            "codecs" to listOf(codec.id), "warning" to "Native-equivalent temperatures; absolute physical accuracy not yet independently validated."))
    }
    private fun physical(frame: Observation): Long = frame.bytes - if (profile == Profile.FULL) (frame.native?.size?.toLong() ?: 0L) else 0L
    fun accept(frame: Observation) = acceptEntry(ProducerEntry(frame))
    fun accept(frame: OwnedObservation) = acceptEntry(ProducerEntry(frame.frame, frame))
    internal fun acceptEntry(input: ProducerEntry) {
        val frame = input.frame
        require(!finalized)
        if (input.owned == null) StageCosts.timed("admission_validation") { frame.validate() }
        else require(input.owned.frame === frame)
        require(frame.sequence > previousSequence && frame.relativeNs >= previousTime)
        val bytes = physical(frame)
        if (pending.isNotEmpty() && (frame.relativeNs - pending.first().frame.relativeNs >= 1000000000L ||
            pending.size >= 32 || Bounds.add(pendingBytes, bytes) >= Bounds.TARGET)) seal()
        pending += input; pendingPayloadBytes += frame.payloadBytes; pendingBytes = Bounds.add(pendingBytes, bytes)
        previousSequence = frame.gapEnd; previousTime = frame.relativeNs
        if (pendingBytes >= Bounds.TARGET || pending.size >= 32) seal()
    }
    fun seal() {
        if (pending.isEmpty()) return
        val encoded = StageCosts.timed("chunk_encode_total") { ChunkEncoder(profile, codec) { encodingBufferBytes = it }.encodeEntries(pending) }
        chunkBoundary("before_chunk", committedChunks)
        val ref = records.append(RecordType.CHUNK, encoded.parts, pending.first().frame.sequence, pending.last().frame.gapEnd)
        // This boundary permits an actual child-process kill after commit and before index/checkpoint.
        chunkBoundary("after_chunk", committedChunks)
        index.add(ref); lastChunk = ref; committedChunks++; committedEntries += pending.size
        committedMeasurements += pending.count { it.frame.temperature != null }
        committedGapSequences += pending.filter { it.frame.temperature == null }.sumOf { it.frame.gapEnd - it.frame.sequence + 1 }
        physicalBytes += encoded.physicalBytes; logicalBytes += encoded.logicalBytes; storedBytes += encoded.storedBytes
        metadataBytes += encoded.metadataBytes; codecNs += encoded.codecNs
        maxChunkBytes = maxOf(maxChunkBytes, encoded.physicalBytes + encoded.metadataBytes)
        pending.clear(); pendingBytes = 0; pendingPayloadBytes = 0; encodingBufferBytes = 0
        if (committedChunks % 32 == 0L) checkpoint()
    }
    fun checkpoint() {
        val root = index.snapshot()
        lastCheckpoint = records.json(RecordType.CHECKPOINT, mapOf("root" to root?.json(),
            "chunks" to committedChunks.toString(), "entries" to committedEntries.toString(),
            "last_chunk" to lastChunk?.json(), "previous_checkpoint" to lastCheckpoint?.json()))
        chunkBoundary("after_checkpoint", committedChunks)
    }
    fun finish(reason: String = "user_stop") {
        require(!finalized); seal(); checkpoint()
        records.json(RecordType.END, mapOf("root" to index.snapshot()?.json(), "chunks" to committedChunks.toString(),
            "entries" to committedEntries.toString(), "reason" to reason, "checkpoint" to lastCheckpoint?.json()))
        finalized = true
    }
    override fun close() { records.close() }
}

internal fun reference(value: Any?): Reference {
    val m = value.objectMap()
    val ref = Reference(m.long("offset"), m.long("length"), m.long("ordinal"), m["hash"] as String, m.long("first"), m.long("last"))
    require(ref.length in 112..Bounds.RECORD.toLong() && ref.last >= ref.first && ref.hash.matches(Regex("[0-9a-f]{64}")))
    return ref
}

/** Lazy pages, backwards references and a fixed-depth walk. Recovery does not invent END/count/time.
 * At most 64 suffix chunks/1024 records cover one damaged checkpoint plus the current interval.
 * More severe damage is an explicit recovery limit, not an unbounded magic scan or trusted tail.
 */
class PrototypeReader(file: File, private val codecs: Map<Int, BlockCodec> = mapOf(0 to Stored, 1 to Deflate1)) : Closeable {
    val records = RecordReader(file)
    val header: Map<String, Any?>
    var complete = false; private set
    var recoveryReason: String? = null; private set
    private var root: Reference? = null
    private val suffix = mutableListOf<Reference>()
    init {
        val first = records.record(0); header = records.json(first)
        require(header["artifact"] == "noncanonical-r2" && header.int("revision") == 1)
        require(header.list("required_features").all { it == "contiguous-native-view" }) { "unsupported_required_feature" }
        require(header.list("codecs").all { it.toString().toInt() in codecs }) { "unsupported_codec" }
        val last = records.last()
        if (last.type == RecordType.END && last.ref.offset + last.ref.length == records.size) {
            try {
                val end = records.json(records.record(last.ref.offset)); root = end["root"]?.let(::reference)
                root?.let { page(it, last.ref.offset) }; complete = true
            } catch (_: IllegalArgumentException) {
                // A damaged END root must not survive fallback when no checkpoint exists.
                root = null; recoveryReason = "damaged_final_index"; recover(last)
            }
        } else { recoveryReason = "missing_or_torn_end"; recover(last) }
    }
    private fun page(ref: Reference, owner: Long): Map<String, Any?> {
        require(Bounds.add(ref.offset, ref.length) <= owner)
        val record = records.record(ref.offset, ref); require(record.type == RecordType.INDEX_PAGE)
        val page = records.json(record); val depth = page.int("depth"); require(depth in 0 until Bounds.DEPTH)
        val children = page.list("children").map(::reference); require(children.size in 1..Bounds.FANOUT)
        require(children.first().first == ref.first && children.last().last == ref.last)
        require(children.zipWithNext().all { (a, b) -> a.last < b.first })
        children.forEach { require(Bounds.add(it.offset, it.length) <= ref.offset && it.ordinal < ref.ordinal) }
        return page
    }
    private fun recover(last: RecordReader.Record) {
        var current = last
        repeat(1024) {
            when (current.type) {
                RecordType.CHUNK -> {
                    val decoded = ChunkDecoder(codecs).decode(records, current)
                    suffix += current.ref.copy(first = decoded.entries.first().long("sequence"), last = decoded.entries.last().long("gap_end"))
                    require(suffix.size <= 64) { "recovery_suffix_bound" }
                }
                RecordType.CHECKPOINT -> try {
                    val metadata = records.json(records.record(current.ref.offset))
                    val checkpointRoot = metadata["root"]?.let(::reference)
                    checkpointRoot?.let { page(it, current.ref.offset) }; root = checkpointRoot
                    return
                } catch (_: IllegalArgumentException) { /* Index damage can fall back to a previous checkpoint. */ }
                RecordType.HEADER -> return
                else -> Unit
            }
            require(current.previous != RecordWriter.NONE)
            val prior = records.record(current.previous, verifyBody = false)
            require(prior.ref.ordinal + 1 == current.ref.ordinal && prior.ref.offset + prior.ref.length == current.ref.offset)
            current = prior
        }
        error("recovery_record_bound")
    }
    private fun visit(ref: Reference, owner: Long, expectedDepth: Int? = null, visitor: (Reference) -> Unit) {
        val p = page(ref, owner); val depth = p.int("depth"); if (expectedDepth != null) require(depth == expectedDepth)
        p.list("children").map(::reference).forEach { child ->
            if (depth == 0) { val rec = records.record(child.offset, child); require(rec.type == RecordType.CHUNK); visitor(child) }
            else visit(child, ref.offset, depth - 1, visitor)
        }
    }
    fun forEachChunk(visitor: (ChunkDecoder.Decoded) -> Unit) {
        val decode: (Reference) -> Unit = { ref ->
            val chunk = ChunkDecoder(codecs).decode(records, records.record(ref.offset, ref))
            require(chunk.entries.first().long("sequence") == ref.first && chunk.entries.last().long("gap_end") == ref.last) { "index_leaf_range" }
            visitor(chunk)
        }
        root?.let { visit(it, records.size, visitor = decode) }
        suffix.sortedBy { it.first }.forEach(decode)
    }
    fun seek(sequence: Long): ChunkDecoder.Decoded? {
        require(sequence >= 0)
        suffix.firstOrNull { sequence in it.first..it.last }?.let { return ChunkDecoder(codecs).decode(records, records.record(it.offset, it)) }
        var ref = root ?: return null; var owner = records.size; var expectedDepth: Int? = null
        repeat(Bounds.DEPTH) {
            if (sequence !in ref.first..ref.last) return null
            val p = page(ref, owner); val depth = p.int("depth"); if (expectedDepth != null) require(depth == expectedDepth)
            val child = p.list("children").map(::reference).firstOrNull { sequence in it.first..it.last } ?: return null
            if (depth == 0) { val rec = records.record(child.offset, child); require(rec.type == RecordType.CHUNK); return ChunkDecoder(codecs).decode(records, rec).also {
                require(it.entries.first().long("sequence") == child.first && it.entries.last().long("gap_end") == child.last) { "index_leaf_range" }
            } }
            owner = ref.offset; ref = child; expectedDepth = depth - 1
        }
        error("index_depth")
    }
    override fun close() { records.close() }
}
