package org.lmthermal.r2

import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.exchange.LmtxJson
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class PrototypeTest {
    private fun file(): File = File.createTempFile("r2-", ".r2proto").also { it.delete(); it.deleteOnExit() }
    private fun rejected(block: () -> Unit) { try { block(); fail("accepted hostile input") } catch (_: IllegalArgumentException) { } }
    @Test fun profilesPreserveEveryLogicalByteAndFullDeduplicates() {
        for (profile in Profile.entries) for (codec in listOf(Stored, Deflate1)) {
            val source = file(); Synthetic.write(source, profile, codec)
            PrototypeReader(source).use { reader ->
                assertTrue(reader.complete); var frames = 0; var gaps = 0
                reader.forEachChunk { decoded -> decoded.entries.zip(decoded.bytes).forEach { (entry, payloads) ->
                    val seq = entry.long("sequence"); val expected = Synthetic.frame(seq, masked = seq % 11 == 0L)
                    if (entry["reason"] != null) { gaps++; assertTrue(payloads.isEmpty()) }
                    else { frames++; assertArrayEquals(expected.temperature, payloads["temperature"])
                        if (profile != Profile.ANALYSIS) assertArrayEquals(expected.native, payloads["native"])
                        if (profile == Profile.FULL) { assertArrayEquals(expected.acquisition, payloads["acquisition"])
                            assertFalse(decoded.metadata.list("blocks").any { it.objectMap()["role"] == "native" }) }
                    }
                } }; assertEquals(59, frames); assertEquals(1, gaps)
                assertNotNull(reader.seek(31)); assertNull(reader.seek(100))
            }
        }
    }
    @Test fun alternateGeometryViewsAndValidityAreExact() {
        val frame = Synthetic.frame(0, 13, 7, true); val encoded = ChunkEncoder(Profile.FULL, Deflate1).encode(listOf(frame))
        val source = file(); RecordWriter(FileSink(source)).use { writer ->
            writer.json(RecordType.HEADER, mapOf("test" to true)); val ref = writer.append(RecordType.CHUNK, encoded.parts)
            RecordReader(source).use { reader -> val decoded = ChunkDecoder().decode(reader, reader.record(ref.offset))
                assertArrayEquals(frame.native, decoded.bytes.single()["native"]); assertArrayEquals(frame.mask, decoded.bytes.single()["validity"]) }
        }
    }
    @Test fun missingEndAndMultipleTornBoundariesRecoverOnlyCommits() {
        val source = file(); Synthetic.write(source, Profile.FULL, Deflate1)
        val refs = mutableListOf<RecordReader.Record>(); RecordReader(source).use { reader -> var at = 0L
            while (at < reader.size) { val r = reader.record(at); refs += r; at += r.ref.length } }
        val chunks = refs.filter { it.type == RecordType.CHUNK }
        val last = chunks.last(); val bytes = source.readBytes()
        for (length in listOf(last.ref.offset.toInt() + 41, last.ref.offset.toInt() + last.ref.length.toInt()/2,
            (last.ref.offset + last.ref.length - 1).toInt(), bytes.size - 1)) {
            val torn = file(); torn.writeBytes(bytes.copyOf(length))
            PrototypeReader(torn).use { reader -> assertFalse(reader.complete); var entries = 0
                reader.forEachChunk { entries += it.entries.size }; assertEquals(chunks.filter { it.ref.offset + it.ref.length <= length }.sumOf { record ->
                    RecordReader(source).use { ChunkDecoder().decode(it, it.record(record.ref.offset)).entries.size } }, entries) }
        }
    }
    @Test fun committedPayloadCorruptionIsIntegrityFailure() {
        val source = file(); Synthetic.write(source, Profile.FULL, Deflate1)
        val bytes = source.readBytes(); RecordReader(source).use { reader ->
            val header = reader.record(0); bytes[(header.ref.length + 100).toInt()] = (bytes[(header.ref.length + 100).toInt()].toInt() xor 1).toByte() }
        source.writeBytes(bytes)
        rejected { PrototypeReader(source).use { it.forEachChunk { } } }
    }
    @Test fun unknownFeaturesOverflowDimensionsAndExpansionAreRejected() {
        rejected { Bounds.add(Long.MAX_VALUE, 1) }; rejected { Bounds.pixels(16384, 16384) }
        rejected { Deflate1.decode(Deflate1.encode(ByteArray(1000)), 3) }
        rejected { Deflate1.decode(Deflate1.encode(ByteArray(1000)) + byteArrayOf(0), 1000) }
        rejected { LmtxJson.decode(("{\"x\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}").toByteArray()) }
        val source = file(); RecordWriter(FileSink(source)).use { writer ->
            writer.json(RecordType.HEADER, mapOf("artifact" to "noncanonical-r2", "revision" to 1, "required_features" to listOf("unknown"), "codecs" to listOf(0))) }
        rejected { PrototypeReader(source).close() }
    }
    @Test fun indexPagesStayLazyAcrossHundredsOfChunks() {
        val source = file()
        RecordWriter(FileSink(source)).use { records -> PrototypeRecorder(records, Profile.FULL, Deflate1).use { writer ->
            repeat(600) { i -> writer.accept(Synthetic.frame(i.toLong(), 3, 2).copy(relativeNs = i * 2000000000L)) }
            writer.finish(); assertEquals(600, writer.committedChunks.toInt()) } }
        PrototypeReader(source).use { reader -> val before = reader.records.bytesRead
            assertEquals("512", reader.seek(512)!!.entries.single()["sequence"])
            assertTrue(reader.records.bytesRead - before < 1024 * 1024); assertTrue(reader.complete) }
    }
    @Test fun invalidViewFormsContextAndHashAreRejected() {
        val encoded = ChunkEncoder(Profile.FULL, Stored).encode(listOf(Synthetic.frame(0, 3, 2)))
        val original = LmtxJson.decode(encoded.parts[1])
        fun altered(change: (MutableMap<String, Any?>) -> Unit) {
            val entry = original.list("entries").single().objectMap().toMutableMap()
            val payloads = entry.getValue("payloads").objectMap().toMutableMap()
            val native = payloads.getValue("native").objectMap().toMutableMap(); change(native); payloads["native"] = native; entry["payloads"] = payloads
            val metadata = original.toMutableMap(); metadata["entries"] = listOf(entry)
            val body = encoded.parts.drop(2).fold(byteArrayOf()) { a, b -> a + b }
            rejected { ChunkDecoder().decodeMetadata(metadata, body.size) { off, n -> body.copyOfRange(off.toInt(), off.toInt()+n) } }
        }
        altered { it["offset"] = -1 }; altered { it["offset"] = Long.MAX_VALUE }; altered { it["length"] = 13 }
        altered { it["parent"] = "native" }; altered { it["stride"] = 2 }; altered { it["transform"] = "mirror" }
        altered { it["frame"] = 1 }; altered { it["chunk"] = 1 }; altered { it["dtype"] = "u16be" }
        altered { it["shape"] = listOf(3, 2) }; altered { it["hash"] = "0".repeat(64) }
    }
    @Test fun storageRefusalNeverAdvancesCommitCounters() {
        val source = file(); var refuse = false
        val delegate = FileSink(source)
        val sink = object: AppendSink { override fun write(bytes: ByteArray) { if (refuse) throw java.io.IOException("ENOSPC injected"); delegate.write(bytes) }
            override fun sync() = delegate.sync(); override fun close() = delegate.close() }
        PrototypeRecorder(RecordWriter(sink), Profile.FULL, Stored).use { writer ->
            writer.accept(Synthetic.frame(0, 3, 2)); writer.seal(); refuse = true; writer.accept(Synthetic.frame(1, 3, 2))
            try { writer.seal(); fail() } catch (_: java.io.IOException) { }
            assertEquals(1L, writer.committedEntries); assertFalse(writer.finalized)
        }
        PrototypeReader(source).use { reader -> var frames = 0; reader.forEachChunk { frames += it.entries.size }; assertEquals(1, frames) }
    }
}
