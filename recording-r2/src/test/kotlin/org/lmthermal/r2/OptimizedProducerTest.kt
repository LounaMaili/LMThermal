package org.lmthermal.r2

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.core.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.math.BigDecimal

/** Producer optimizations preserve exact payloads and all context semantics; hostile paths stay strict. */
class OptimizedProducerTest {
    private fun measurement() = Ht301ThermalMeasurement(NativeEquivalentThermometry.measure(Ht301Frame.parse(
        File("../core/src/test/resources/thermometry/warm-hand-settled.raw").readBytes())))
    @Test fun trustedProducerRetainsExactFloatBitsNativeTransportAndCompleteContext() {
        val source = measurement(); val hash = hex(sha(source.evidence.source.transportBytes()))
        val values = source.matrix(); val expected = little(values.size * 4).also { b -> values.forEach(b::putFloat) }.array()
        for (profile in Profile.entries) for (codec in listOf(Stored, Deflate1)) {
            val owned = OwnedObservation.ht301(source, profile, 0)
            // Test has module access; the public API never exposes these mutable arrays.
            assertArrayEquals(expected, owned.frame.temperature)
            val file = File.createTempFile("r2-owned", ".r2proto")
            try {
                PrototypeRecorder(RecordWriter(FileSink(file)), profile, codec).use { it.accept(owned); it.finish() }
                PrototypeReader(file).use { reader -> reader.forEachChunk { chunk ->
                    val bytes = chunk.bytes.single()
                    assertArrayEquals(expected, bytes["temperature"])
                    if (profile != Profile.ANALYSIS) assertArrayEquals(source.evidence.source.imageBytes(), bytes["native"])
                    if (profile == Profile.FULL) assertArrayEquals(source.evidence.source.transportBytes(), bytes["acquisition"])
                    assertEquals(R2Json.decode(owned.frame.contextBytes), chunk.metadata.list("contexts").single())
                } }
            } finally { file.delete() }
        }
        assertEquals(hash, hex(sha(source.evidence.source.transportBytes())))
    }
    @Test fun genericMutableProducerStillRejectsMutationAfterAdmission() {
        val frame = Synthetic.frame(0)
        val file = File.createTempFile("r2-hostile", ".r2proto")
        try {
            PrototypeRecorder(RecordWriter(FileSink(file)), Profile.ANALYSIS, Stored).use { recorder ->
                recorder.accept(frame)
                ByteBuffer.wrap(frame.temperature!!).order(ByteOrder.LITTLE_ENDIAN).putInt(0x7fc00000)
                assertThrows(IllegalArgumentException::class.java) { recorder.seal() }
            }
        } finally { file.delete() }
    }
    @Test fun directJsonEncoderRejectsAllReaderBoundariesWithoutDiscardedParseTree() {
        for (value in listOf("\ud800", "\udc00", "\ud800x", "€".repeat(5462), Double.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { R2Json.encode(mapOf("x" to value)) }
        }
        val valid = mapOf("astral" to "\ud83d\ude00", "unknown" to BigDecimal("1.2345678901234567890123456789"),
            "zero" to -0.0f, "ascii_limit" to "x".repeat(16384), "escaped" to "\n\u0000\"")
        assertEquals(org.lmthermal.exchange.LmtxJson.decode(org.lmthermal.exchange.LmtxJson.encode(valid)), R2Json.decode(R2Json.encode(valid)))
        var nested: Any? = 1
        repeat(32) { nested = listOf(nested) }
        assertThrows(IllegalArgumentException::class.java) { R2Json.encode(mapOf("x" to nested)) }
        assertThrows(IllegalArgumentException::class.java) { R2Json.encode(mapOf("x" to List(65536) { 1 })) }
        val invalidNumber = object : Number() {
            override fun toByte() = 0.toByte(); override fun toShort() = 0.toShort(); override fun toInt() = 0
            override fun toLong() = 0L; override fun toFloat() = 0f; override fun toDouble() = 0.0
            override fun toString() = "1e999999999999999999"
        }
        assertThrows(IllegalArgumentException::class.java) { R2Json.encode(mapOf("x" to invalidNumber)) }
    }
    @Test fun storedEncodingConsumesOwnedBlockWithoutSecondCopy() {
        val owned = byteArrayOf(0, 1, 2)
        assertSame(owned, Stored.encode(owned)); assertNotSame(owned, Stored.decode(owned, owned.size))
    }
    @Test fun preparationRunsOffObserverAndDrainsBeforeFinalization() {
        val source = measurement(); val file = File.createTempFile("r2-prep", ".r2proto").also { it.delete() }
        val costs = StageCosts()
        try {
            val writer = BoundedRecorder(file, Profile.FULL, Stored, costs)
            val prep = Ht301Preparation(writer, Profile.FULL, 0, costs)
            assertTrue(prep.offer(source)); assertTrue(prep.stop()); assertTrue(writer.stop())
            assertEquals(1L, writer.recorder.committedMeasurements); assertEquals(0L, prep.drops)
            assertTrue(prep.maxBytes <= Bounds.QUEUE)
            val threads = costs.snapshot().getValue("freeze_total").objectMap().list("threads")
            assertEquals(listOf("r2-preparation"), threads)
            PrototypeReader(file).use { assertTrue(it.complete) }
        } finally { file.delete() }
    }
}
