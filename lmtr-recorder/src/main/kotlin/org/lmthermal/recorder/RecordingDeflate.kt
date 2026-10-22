package org.lmthermal.recorder

import org.lmthermal.lmtr.LmtrFailure
import java.util.zip.Deflater

/** Call-local writer workspace, with no pool or permanently retained image-sized arena.
 * Each segment has one owner until the final independent result is copied. Segment capacity
 * never exceeds the caller's negotiated stored-byte cap; native codec state always closes.
 * This bounds binary workspace, not JSON object expansion or total process live heap.
 */
object RecordingDeflate {
    private const val SEGMENT_BYTES = 65536

    /** Preserve level-1 RFC1950 bytes while avoiding ByteArrayOutputStream's geometric growth.
     * Output segmentation does not flush/reset the stream. At most E+65535 segment bytes and
     * E final bytes coexist (also <=2*maximumStoredBytes), excluding source and native zlib.
     * The allocator hook supports shared JVM/ART fault tests; it must return a new, exactly
     * requested, exclusively owned array on every call (never a recycled/source buffer).
     */
    fun encode(raw: ByteArray, maximumStoredBytes: Int,
        allocate: (Int) -> ByteArray = ::ByteArray): ByteArray {
        if (maximumStoredBytes <= 0) throw LmtrFailure("local_resource_limit")
        val segments = ArrayList<ByteArray>()
        val deflater = Deflater(1, false)
        try {
            deflater.setInput(raw)
            deflater.finish()
            var capacity = 0
            var used = 0
            var tailUsed = 0
            while (!deflater.finished()) {
                if (segments.isEmpty() || tailUsed == segments.last().size) {
                    // Reaching the local cap is a resource refusal, never truncation or fallback.
                    if (capacity == maximumStoredBytes) throw LmtrFailure("local_resource_limit")
                    val size = minOf(SEGMENT_BYTES, maximumStoredBytes - capacity)
                    segments.add(allocate(size))
                    capacity += size
                    tailUsed = 0
                }
                val tail = segments.last()
                val count = deflater.deflate(tail, tailUsed, tail.size - tailUsed)
                if (count <= 0 && !deflater.finished()) throw LmtrFailure("invalid_payload")
                tailUsed += count
                used += count
            }
            val result = allocate(used)
            var position = 0
            for (segment in segments) {
                val count = minOf(segment.size, used - position)
                segment.copyInto(result, position, 0, count)
                position += count
            }
            return result
        } finally {
            // Clear even on OOM/cancellation-like allocator failure; no shared lease can linger.
            segments.clear()
            deflater.end()
        }
    }
}
