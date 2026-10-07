package org.lmthermal.r2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Experimental engineering bounds; none of these constants freezes the LMTR contract. */
object Bounds {
    const val MIB = 1048576
    const val CHUNK = 64 * MIB
    const val RECORD = 65 * MIB
    const val TARGET = 16 * MIB
    const val META = MIB
    const val PAGE = 512 * 1024
    const val FANOUT = 256
    const val DEPTH = 8
    const val ENTRIES = 1024
    const val QUEUE = 4 * MIB
    fun add(a: Long, b: Long): Long { require(a >= 0 && b >= 0); require(a <= Long.MAX_VALUE - b) { "integer_overflow" }; return a + b }
    fun pixels(width: Int, height: Int): Int {
        require(width in 1..16384 && height in 1..16384)
        return Math.multiplyExact(width, height).also { require(it <= 4194304) }
    }
}

/** One independent bounded block; no dictionaries, implicit codec fallback or concatenated frames. */
interface BlockCodec { val id: Int; val name: String; fun encode(input: ByteArray): ByteArray; fun decode(input: ByteArray, size: Int): ByteArray }

object Stored : BlockCodec {
    override val id = 0; override val name = "stored"
    override fun encode(input: ByteArray) = input.copyOf()
    override fun decode(input: ByteArray, size: Int): ByteArray { require(size in 0..Bounds.CHUNK && input.size == size); return input.copyOf() }
}

/** RFC1950 wrapper / RFC1951 DEFLATE level 1; exact length and trailing input checked. */
object Deflate1 : BlockCodec {
    override val id = 1; override val name = "deflate-1"
    override fun encode(input: ByteArray): ByteArray {
        require(input.size <= Bounds.CHUNK)
        val compressor = Deflater(1)
        try {
            compressor.setInput(input); compressor.finish()
            val output = ByteArrayOutputStream(); val scratch = ByteArray(65536)
            while (!compressor.finished()) {
                val count = compressor.deflate(scratch); check(count > 0)
                output.write(scratch, 0, count); require(output.size() <= Bounds.RECORD)
            }
            return output.toByteArray()
        } finally { compressor.end() }
    }
    override fun decode(input: ByteArray, size: Int): ByteArray {
        require(size in 0..Bounds.CHUNK && input.size <= Bounds.RECORD)
        val inflater = Inflater()
        try {
            inflater.setInput(input); val output = ByteArray(size); var at = 0
            while (!inflater.finished() && at < size) {
                val n = inflater.inflate(output, at, size - at); require(n > 0); at += n
            }
            // Consume an end marker even when output exactly fills its verified bound.
            val extra = ByteArray(1)
            if (!inflater.finished()) require(inflater.inflate(extra) == 0)
            require(at == size && inflater.finished() && inflater.remaining == 0)
            return output
        } finally { inflater.end() }
    }
}

/** Hashes bind logical bytes, independent of their physical block or candidate view representation. */
fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
fun hex(bytes: ByteArray): String {
    // Hash spelling is fixed ASCII, so per-byte Formatter/locale allocations add no semantics.
    val digits = "0123456789abcdef"
    return CharArray(bytes.size * 2).also { result -> bytes.forEachIndexed { i, byte ->
        val value = byte.toInt() and 255
        result[i * 2] = digits[value ushr 4]; result[i * 2 + 1] = digits[value and 15]
    } }.concatToString()
}
fun little(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

enum class Profile { ANALYSIS, NATIVE, FULL }

/** Owned observation for experiments. Null measurement is a gap, never a retained last-good plane. */
data class Observation(val sequence: Long, val receiptNs: Long?, val relativeNs: Long,
    val width: Int, val height: Int, val temperature: ByteArray?, val mask: ByteArray? = null,
    val native: ByteArray? = null, val acquisition: ByteArray? = null,
    val context: Map<String, Any?> = emptyMap(), val reason: String? = null,
    val nativeEncoding: String = "org.lmthermal.ht301.raw14",
    val gapEnd: Long = sequence) {
    // Metadata must also consume the byte budget, including observations with no plane.
    // Cache the immutable serialized closure so admission and encoding use the same values.
    val contextBytes: ByteArray by lazy { R2Json.encode(context) }
    val payloadBytes: Long get() = listOfNotNull(temperature, mask, native, acquisition).sumOf { it.size.toLong() }
    val bytes: Long get() = Bounds.add(payloadBytes, contextBytes.size.toLong())
    fun validate() {
        require(sequence >= 0 && gapEnd >= sequence && relativeNs >= 0 && (receiptNs == null || receiptNs >= 0))
        val pixels = Bounds.pixels(width, height)
        if (temperature == null) { require(reason != null && mask == null && native == null && acquisition == null); return }
        require(gapEnd == sequence && temperature.size == Math.multiplyExact(pixels, 4))
        require(mask == null || mask.size == pixels && mask.all { it == 0.toByte() || it == 1.toByte() })
        val values = ByteBuffer.wrap(temperature).order(ByteOrder.LITTLE_ENDIAN)
        repeat(pixels) { i -> val bits = values.int
            if (mask == null || mask[i] == 1.toByte()) require(Float.fromBits(bits).isFinite())
            else require(bits == 0) }
        if (native != null) require(native.size == Math.multiplyExact(pixels, 2))
        require(bytes <= Bounds.CHUNK)
    }
}
