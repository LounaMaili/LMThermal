package org.lmthermal.r2

import org.lmthermal.r2.R2Json as LmtxJson
import java.io.File

/** Synthetic, noncanonical test vectors only. Never infer physical temperatures from these values. */
object Synthetic {
    fun frame(sequence: Long, width: Int = 384, height: Int = 288, masked: Boolean = false): Observation {
        val pixels = Bounds.pixels(width, height)
        val mask = if (masked) ByteArray(pixels) { if (it % 7 == 0) 0 else 1 } else null
        val values = little(pixels * 4); val native = little(pixels * 2)
        repeat(pixels) { i -> values.putFloat(if (mask?.get(i) == 0.toByte()) 0f else 18f + ((i + sequence) % 600).toFloat() / 32f)
            native.putShort((5000 + (i + sequence) % 2000).toShort()) }
        val raw = native.array(); val transport = ByteArray(if (width == 384 && height == 288) 224256 else raw.size + 64)
        raw.copyInto(transport)
        return Observation(sequence, 9000000000L + sequence * 40000000L, sequence * 40000000L, width, height,
            values.array(), mask, raw, transport, mapOf("provenance" to "synthetic-r2-not-camera", "settings" to mapOf("emissivity" to "0.98"), "epoch" to sequence / 30),
            nativeEncoding = if (width == 384 && height == 288) "org.lmthermal.ht301.raw14" else "org.lmthermal.r2.synthetic")
    }
    fun write(file: File, profile: Profile, codec: BlockCodec, frames: Int = 60, marker: ((String, Long) -> Unit)? = null) {
        require(!file.exists()) { "refuse_overwrite" }; file.parentFile?.mkdirs()
        RecordWriter(FileSink(file), marker ?: { _, _ -> }).use { records ->
            PrototypeRecorder(records, profile, codec, chunkBoundary = marker ?: { _, _ -> }).use { writer ->
                repeat(frames) { i -> writer.accept(if (i == 27) Observation(i.toLong(), null, i * 40000000L, 384, 288, null, reason = "explicit-synthetic-gap")
                    else frame(i.toLong(), masked = i % 11 == 0)) }
                writer.finish()
            }
        }
    }
}
object PrototypeCommand {
    @JvmStatic fun main(args: Array<String>) {
        when (args[0]) {
            "generate" -> Synthetic.write(File(args[1]), Profile.valueOf(args.getOrElse(2) { "FULL" }), if (args.getOrElse(3) { "1" } == "0") Stored else Deflate1,
                args.getOrElse(4) { "60" }.toInt())
            "kill-target" -> {
                val phase = args[2]; val marker = File(args[3]); val ordinal = args[4].toLong()
                Synthetic.write(File(args[1]), Profile.FULL, Deflate1, 100) { at, ord ->
                    if (at == phase && (ord == ordinal || ordinal == -1L)) { marker.writeText("$at $ord"); while (true) Thread.sleep(1000) }
                }
            }
            "sparse" -> println(String(LmtxJson.encode(SparseStress.write(File(args[1])))))
            "inspect" -> PrototypeReader(File(args[1])).use { reader ->
                var frames = 0L; var gaps = 0L; var chunks = 0L
                reader.forEachChunk { chunks++; it.entries.forEach { entry -> if (entry["reason"] == null) frames++ else gaps++ } }
                println(String(LmtxJson.encode(mapOf("complete" to reader.complete, "frames" to frames,
                    "gaps" to gaps, "chunks" to chunks, "read_bytes" to reader.records.bytesRead, "recovery" to reader.recoveryReason))))
            }
            else -> error("generate/inspect/kill-target")
        }
    }
}
