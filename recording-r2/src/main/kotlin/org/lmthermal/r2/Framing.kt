package org.lmthermal.r2

import org.lmthermal.r2.R2Json as LmtxJson
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

enum class RecordType(val id: Int) { HEADER(1), CHUNK(2), INDEX_PAGE(3), CHECKPOINT(4), END(5) }

/** References are backwards, file-local, checked signed-Long values, never external paths. */
data class Reference(val offset: Long, val length: Long, val ordinal: Long, val hash: String,
    val first: Long = 0, val last: Long = 0) {
    fun json() = mapOf("offset" to offset.toString(), "length" to length.toString(),
        "ordinal" to ordinal.toString(), "hash" to hash, "first" to first.toString(), "last" to last.toString())
}

/** Test sinks can refuse writes or kill a separate process at declared boundaries. */
interface AppendSink : Closeable {
    fun write(bytes: ByteArray)
    fun sync()
}
class FileSink(file: File) : AppendSink {
    private val stream = FileOutputStream(file)
    override fun write(bytes: ByteArray) { stream.write(bytes) }
    override fun sync() { stream.flush(); stream.fd.sync() }
    override fun close() { stream.close() }
}

/** Experimental framing only: 40-byte header, bounded body, 72-byte independently committed footer.
 * Body-sync then footer-sync precede durable counters. No seeking or source-file rewrite.
 */
class RecordWriter(private val sink: AppendSink, private val boundary: (String, Long) -> Unit = { _, _ -> }) : Closeable {
    companion object {
        val MAGIC = "R2RECORD".toByteArray(Charsets.US_ASCII)
        val COMMIT = "R2CMIT!!".toByteArray(Charsets.US_ASCII)
        const val HEADER_BYTES = 40
        const val FOOTER_BYTES = 72
        const val NONE = Long.MAX_VALUE
    }
    var position = 0L; private set
    var ordinal = 0L; private set
    private var previous = NONE
    var syncs = 0L; private set
    /** Parts are immutable owned bytes; digest/write each independently to avoid a joined-body copy. */
    fun append(type: RecordType, parts: List<ByteArray>, first: Long = 0, last: Long = first): Reference {
        val bodyLength = parts.fold(0L) { size, bytes -> Bounds.add(size, bytes.size.toLong()) }
        val length = Bounds.add(bodyLength, (HEADER_BYTES + FOOTER_BYTES).toLong())
        require(length <= Bounds.RECORD && first >= 0 && last >= first)
        Bounds.add(position, length)
        val header = little(HEADER_BYTES).put(MAGIC).putShort(1).putShort(type.id.toShort()).putInt(0)
            .putLong(ordinal).putLong(bodyLength).putLong(previous).array()
        val digest = MessageDigest.getInstance("SHA-256")
        StageCosts.timed("file_write") { sink.write(header) }; boundary("header", ordinal)
        parts.forEachIndexed { index, bytes -> StageCosts.timed("file_write") { sink.write(bytes) }; StageCosts.timed("framing_hash") { digest.update(bytes) }; boundary("body_$index", ordinal) }
        StageCosts.timed("sync_commit") { sink.sync() }; syncs++; boundary("body_synced", ordinal)
        val bodyHash = digest.digest()
        val prefix = little(64).put(COMMIT).putLong(length).putLong(ordinal).putLong(previous).put(bodyHash).array()
        val crc = CRC32().apply { update(header); update(prefix) }.value.toInt()
        val footer = little(FOOTER_BYTES).put(prefix).putInt(crc).putInt(0).array()
        StageCosts.timed("file_write") { sink.write(footer) }; boundary("footer_written", ordinal)
        StageCosts.timed("sync_commit") { sink.sync() }; syncs++; boundary("footer_synced", ordinal)
        val ref = Reference(position, length, ordinal, hex(sha(header + bodyHash)), first, last)
        previous = position; position += length; ordinal++
        return ref
    }
    fun json(type: RecordType, metadata: Map<String, Any?>): Reference = append(type, listOf(LmtxJson.encode(metadata)))
    override fun close() { sink.close() }
}

/** Bounded diagnostic reader, independent of writer internals and safe before body allocation. */
class RecordReader(file: File) : Closeable {
    private val source = RandomAccessFile(file, "r")
    val size get() = source.length()
    var bytesRead = 0L; private set
    data class Record(val ref: Reference, val type: RecordType, val previous: Long, val bodyLength: Int)
    fun bytes(offset: Long, count: Int): ByteArray {
        require(count in 0..Bounds.RECORD && Bounds.add(offset, count.toLong()) <= size)
        source.seek(offset); return ByteArray(count).also { source.readFully(it); bytesRead += count }
    }
    /** Footer CRC validates repeated framing; streaming SHA rejects body damage without allocating it. */
    fun record(offset: Long, expected: Reference? = null, verifyBody: Boolean = true): Record {
        val header = bytes(offset, RecordWriter.HEADER_BYTES)
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8); h.get(magic); require(magic.contentEquals(RecordWriter.MAGIC))
        require(h.short.toInt() == 1)
        val typeId = h.short.toInt()
        val type = RecordType.entries.singleOrNull { it.id == typeId } ?: throw IllegalArgumentException("record_type")
        require(h.int == 0); val ordinal = h.long; val body = h.long; val previous = h.long
        val length = Bounds.add(body, (RecordWriter.HEADER_BYTES + RecordWriter.FOOTER_BYTES).toLong())
        require(ordinal >= 0 && length <= Bounds.RECORD && Bounds.add(offset, length) <= size)
        if (offset == 0L) require(type == RecordType.HEADER && ordinal == 0L && previous == RecordWriter.NONE)
        else require(previous >= 0 && previous < offset && ordinal > 0 && type != RecordType.HEADER)
        val footer = bytes(offset + RecordWriter.HEADER_BYTES + body, RecordWriter.FOOTER_BYTES)
        val f = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN); f.get(magic)
        require(magic.contentEquals(RecordWriter.COMMIT) && f.long == length && f.long == ordinal && f.long == previous)
        val bodyHash = ByteArray(32); f.get(bodyHash); val crc = f.int; require(f.int == 0)
        require(CRC32().apply { update(header); update(footer, 0, 64) }.value.toInt() == crc)
        val ref = Reference(offset, length, ordinal, hex(sha(header + bodyHash)))
        if (expected != null) require(expected.offset == offset && expected.length == length &&
            expected.ordinal == ordinal && expected.hash == ref.hash)
        if (verifyBody) {
            val digest = MessageDigest.getInstance("SHA-256"); var at = offset + RecordWriter.HEADER_BYTES; var left = body
            while (left > 0) { val n = minOf(left, 65536).toInt(); digest.update(bytes(at, n)); at += n; left -= n }
            require(digest.digest().contentEquals(bodyHash)) { "integrity_mismatch" }
        }
        return Record(ref, type, previous, body.toInt())
    }
    fun json(record: Record, limit: Int = Bounds.PAGE): Map<String, Any?> {
        require(record.bodyLength <= limit)
        return LmtxJson.decode(bytes(record.ref.offset + RecordWriter.HEADER_BYTES, record.bodyLength))
    }
    /** Bounded backwards scan after a torn tail, never trust an unverified magic-byte hit. */
    fun last(): Record {
        val lower = maxOf(0L, size - Bounds.RECORD - RecordWriter.FOOTER_BYTES)
        var end = size
        while (end > lower) {
            val start = maxOf(lower, end - 1048576)
            val block = bytes(start, (end - start).toInt())
            for (i in block.size - RecordWriter.FOOTER_BYTES downTo 0) {
                if (!RecordWriter.COMMIT.indices.all { block[i + it] == RecordWriter.COMMIT[it] }) continue
                val length = ByteBuffer.wrap(block, i + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
                if (length !in 112..Bounds.RECORD.toLong()) continue
                val offset = start + i + RecordWriter.FOOTER_BYTES - length
                if (offset < 0) continue
                try { return record(offset, verifyBody = false) } catch (_: IllegalArgumentException) { /* Test another bounded candidate. */ }
            }
            if (start == lower) break
            end = start + RecordWriter.FOOTER_BYTES - 1
        }
        error("no_verified_commit_within_tail_bound")
    }
    override fun close() { source.close() }
}
