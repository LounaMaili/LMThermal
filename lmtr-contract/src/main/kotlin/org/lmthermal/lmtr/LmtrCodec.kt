package org.lmthermal.lmtr

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Frozen baseline codecs; decompression budgets are checked before allocation. */
object LmtrCodec {
    /** Return owned STORED or RFC1950 DEFLATE bytes; no dictionaries or Zstd. */
    fun encode(raw: ByteArray, codec: Int): ByteArray {
        demand(codec in 0..1, "unsupported_codec")
        if (codec == 0) return raw.copyOf()
        val deflater = Deflater(1, false)
        try {
            deflater.setInput(raw); deflater.finish()
            val out = ByteArrayOutputStream(); val scratch = ByteArray(65536)
            while (!deflater.finished()) {
                val n = deflater.deflate(scratch)
                demand(n > 0, "invalid_payload"); out.write(scratch, 0, n)
            }
            return out.toByteArray()
        } finally { deflater.end() }
    }

    /** Require one completely consumed zlib stream and exact expected decoded size. */
    fun decode(stored: ByteArray, codec: Int, decoded: Long, budget: Long): ByteArray {
        demand(codec in 0..1, "unsupported_codec")
        demand(decoded in 1..67108864L, "resource_limit")
        demand(decoded <= budget && decoded <= Int.MAX_VALUE, "local_resource_limit")
        if (codec == 0) { demand(stored.size.toLong() == decoded, "invalid_payload"); return stored.copyOf() }
        demand(stored.size >= 6, "invalid_payload")
        val cmf = stored[0].toInt() and 255; val flg = stored[1].toInt() and 255
        demand(cmf and 15 == 8 && cmf shr 4 <= 7 && (cmf * 256 + flg) % 31 == 0 && flg and 32 == 0, "invalid_payload")
        val inflater = Inflater(false)
        try {
            inflater.setInput(stored); val result = ByteArray(decoded.toInt()); var pos = 0
            while (!inflater.finished() && pos < result.size) {
                val n = try { inflater.inflate(result, pos, result.size - pos) } catch (_: java.util.zip.DataFormatException) { throw LmtrFailure("committed_payload_integrity") }
                if (n == 0) { demand(!inflater.needsDictionary() && !inflater.needsInput(), "invalid_payload"); throw LmtrFailure("invalid_payload") }
                pos += n
            }
            // A stream exactly filling the output may consume its footer on one
            // final bounded probe. Any extra decoded byte is an expansion attack.
            if (!inflater.finished()) {
                val extra = ByteArray(1)
                val n = try { inflater.inflate(extra) } catch (_: java.util.zip.DataFormatException) { throw LmtrFailure("committed_payload_integrity") }
                demand(n == 0 && inflater.finished(), "invalid_payload")
            }
            demand(pos.toLong() == decoded && inflater.finished() && inflater.remaining == 0 && !inflater.needsDictionary(), "invalid_payload")
            return result
        } finally { inflater.end() }
    }
}
