package org.lmthermal.lmtr

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

/** Exact envelope bytes and digest identities from accepted LMTR v1 §5; no R2 identifiers. */
object LmtrFraming {
    const val HEADER = 1
    const val CHUNK = 2
    const val INDEX = 3
    const val CHECKPOINT = 4
    const val END = 5
    const val SENTINEL = Long.MAX_VALUE
    const val HEADER_BYTES = 40
    const val FOOTER_BYTES = 72
    const val ENVELOPE_BYTES = HEADER_BYTES + FOOTER_BYTES
    const val MAX_RECORD_BYTES = 68_157_440L
    const val MAX_DECODED_CHUNK_BYTES = 67_108_864L
    const val MAX_HEADER_BODY_BYTES = 1_048_576L
    const val MAX_NAVIGATION_BODY_BYTES = 524_288L
    private val magic = "LMTRREC1".toByteArray(Charsets.US_ASCII)
    private val footerMagic = "LMTRCMT1".toByteArray(Charsets.US_ASCII)

    /**
     * Append one record with two separate file sync barriers before returning acknowledgement.
     * The caller owns serialization, directory persistence and final close/reopen verification.
     * Failure can leave a torn tail, which is never patched, truncated or counted as acknowledged.
     */
    fun append(file: RandomAccessFile, type: Int, ordinal: Long, previous: Long, body: ByteArray): Record {
        val offset = file.length()
        validateIdentity(offset, ordinal, type, previous)
        val length = checkedAdd(body.size.toLong(), ENVELOPE_BYTES.toLong())
        demand(length <= MAX_RECORD_BYTES && body.size.toLong() <= bodyLimit(type), "resource_limit")
        checkedAdd(offset, length)
        if (offset != 0L) {
            val prior = envelope(file, previous)
            demand(prior.type != END && prior.ordinal < Long.MAX_VALUE && prior.ordinal + 1 == ordinal &&
                checkedAdd(previous, prior.length) == offset, "invalid_container")
        }
        val header = buffer(HEADER_BYTES).apply {
            put(magic); putShort(1); putShort(type.toShort()); putInt(0)
            putLong(ordinal); putLong(body.size.toLong()); putLong(previous)
        }.array()
        val bodyDigest = digest(body)
        val footer = buffer(FOOTER_BYTES).apply {
            put(footerMagic); putLong(length); putLong(ordinal); putLong(previous); put(bodyDigest)
        }.array()
        val crc = CRC32().apply { update(header); update(footer, 0, 64) }.value
        ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN).putInt(64, crc.toInt()).putInt(68, 0)
        file.seek(offset)
        file.write(header); file.write(body); file.fd.sync()
        file.write(footer); file.fd.sync()
        // The serialized owner retains this body; do not create an uncharged second chunk buffer.
        return Record(offset, length, ordinal, type, previous, body, hex(bodyDigest),
            hex(digest(header + bodyDigest)))
    }

    /**
     * Inspect fixed-size framing and actual file bounds before allocating a body.
     * The allocation ceiling is a local resource policy, distinct from the wire bound.
     * Immediate predecessor framing is checked; full prefix integrity remains a reader obligation.
     */
    fun read(file: RandomAccessFile, offset: Long, maxBodyBytes: Long = MAX_DECODED_CHUNK_BYTES): Record {
        demand(maxBodyBytes >= 0, "local_resource_limit")
        val framing = checkedEnvelope(file, offset)
        demand(framing.bodyLength <= maxBodyBytes && framing.bodyLength <= Int.MAX_VALUE.toLong(), "local_resource_limit")
        val body = ByteArray(framing.bodyLength.toInt())
        file.seek(checkedAdd(offset, HEADER_BYTES.toLong()))
        file.readFully(body)
        val actual = digest(body)
        demand(MessageDigest.isEqual(actual, framing.bodyDigest), "committed_payload_integrity")
        return Record(offset, framing.length, framing.ordinal, framing.type, framing.previous, body,
            hex(actual), hex(digest(framing.header + actual)))
    }

    /**
     * Verify only fixed framing and immediate predecessor framing without reading the body.
     * Recovery can traverse trustworthy links across damaged navigation metadata, but declared
     * hashes in this result are not evidence of body integrity. Use read before exposing data.
     */
    fun inspect(file: RandomAccessFile, offset: Long): RecordEnvelope {
        val framing = checkedEnvelope(file, offset)
        return RecordEnvelope(offset, framing.length, framing.ordinal, framing.type, framing.previous,
            framing.bodyLength, hex(framing.bodyDigest), hex(digest(framing.header + framing.bodyDigest)))
    }

    /** Checked nonnegative signed-64 addition shared by file and metadata validation. */
    fun checkedAdd(left: Long, right: Long): Long {
        demand(left >= 0 && right >= 0 && left <= Long.MAX_VALUE - right, "resource_limit")
        return left + right
    }

    /** Hash exact bytes, never metadata's semantic reserialization. */
    fun sha256(bytes: ByteArray): String = hex(digest(bytes))

    private fun validateIdentity(offset: Long, ordinal: Long, type: Int, previous: Long) {
        demand(offset >= 0 && ordinal >= 0 && type in HEADER..END, "invalid_container")
        if (offset == 0L) demand(type == HEADER && ordinal == 0L && previous == SENTINEL, "invalid_container")
        else demand(type != HEADER && ordinal > 0 && previous >= 0 && previous < offset, "invalid_container")
    }

    private fun bodyLimit(type: Int): Long = when (type) {
        HEADER -> MAX_HEADER_BODY_BYTES
        CHUNK -> MAX_RECORD_BYTES - ENVELOPE_BYTES
        INDEX, CHECKPOINT, END -> MAX_NAVIGATION_BODY_BYTES
        else -> throw LmtrFailure("invalid_container")
    }

    private data class Envelope(val header: ByteArray, val bodyDigest: ByteArray, val bodyLength: Long,
                                val length: Long, val ordinal: Long, val type: Int, val previous: Long)

    private fun checkedEnvelope(file: RandomAccessFile, offset: Long): Envelope {
        val framing = envelope(file, offset)
        if (offset != 0L) {
            val prior = envelope(file, framing.previous)
            demand(prior.type != END && prior.ordinal < Long.MAX_VALUE && prior.ordinal + 1 == framing.ordinal &&
                checkedAdd(framing.previous, prior.length) == offset, "invalid_container")
        }
        return framing
    }

    private fun envelope(file: RandomAccessFile, offset: Long): Envelope {
        val fileLength = file.length()
        demand(offset >= 0 && offset <= fileLength && fileLength - offset >= ENVELOPE_BYTES, "invalid_container")
        val header = ByteArray(HEADER_BYTES)
        file.seek(offset); file.readFully(header)
        val parsed = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        demand(header.copyOfRange(0, 8).contentEquals(magic), "unsupported_format")
        demand(parsed.getShort(8).toInt() and 0xffff == 1, "unsupported_envelope")
        val type = parsed.getShort(10).toInt() and 0xffff
        demand(parsed.getInt(12) == 0, "invalid_container")
        val ordinal = parsed.getLong(16)
        val bodyLength = parsed.getLong(24)
        val previous = parsed.getLong(32)
        validateIdentity(offset, ordinal, type, previous)
        demand(bodyLength >= 0 && bodyLength <= bodyLimit(type), "resource_limit")
        val length = checkedAdd(bodyLength, ENVELOPE_BYTES.toLong())
        demand(length <= MAX_RECORD_BYTES, "resource_limit")
        val end = checkedAdd(offset, length)
        demand(end <= fileLength, "invalid_container")
        val footer = ByteArray(FOOTER_BYTES)
        file.seek(end - FOOTER_BYTES); file.readFully(footer)
        val commitment = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN)
        demand(footer.copyOfRange(0, 8).contentEquals(footerMagic), "invalid_container")
        demand(commitment.getLong(8) == length && commitment.getLong(16) == ordinal &&
            commitment.getLong(24) == previous && commitment.getInt(68) == 0, "invalid_container")
        val crc = CRC32().apply { update(header); update(footer, 0, 64) }.value
        demand(commitment.getInt(64).toLong() and 0xffffffffL == crc, "invalid_container")
        return Envelope(header, footer.copyOfRange(32, 64), bodyLength, length, ordinal, type, previous)
    }

    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Verified current record; body ownership belongs to the reading/writing caller. */
data class Record(val offset: Long, val length: Long, val ordinal: Long, val type: Int,
                  val previous: Long, val body: ByteArray, val bodyHash: String, val refHash: String)

/** CRC-verified envelope with declared, still-unverified body digest; not a verified source record. */
data class RecordEnvelope(val offset: Long, val length: Long, val ordinal: Long, val type: Int,
                          val previous: Long, val bodyLength: Long, val bodyHash: String, val refHash: String)

/** Metadata reference; entry bounds are checked by index/chunk semantics separately. */
data class RecordRef(val offset: Long, val length: Long, val ordinal: Long,
                     val first: Long?, val last: Long?, val hash: String) {
    /** Render D fields as strings and retain explicit null bounds, not default zeros. */
    fun json(): Obj = mapOf("offset" to offset.toString(), "length" to length.toString(),
        "ordinal" to ordinal.toString(), "first" to first?.toString(), "last" to last?.toString(), "hash" to hash)
}
