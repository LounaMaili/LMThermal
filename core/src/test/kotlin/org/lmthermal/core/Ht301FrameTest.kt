package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Golden frame content is reused unchanged from the sanitized Desktop fixture set. */
class Ht301FrameTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }
    private fun raw() = fixture("radiometric-room-settled.raw")
    private fun display() = fixture("display-room-baseline.raw")
    private fun putWord(bytes: ByteArray, offset: Int, word: Int) {
        bytes[offset] = word.toByte(); bytes[offset + 1] = (word shr 8).toByte()
    }
    @Test fun exactTransportSizeAcceptedAndAllTrailerRowsRetained() {
        val source = raw(); val frame = Ht301Frame.parse(source)
        assertEquals(224256, frame.size); assertEquals(221184, frame.imageBytes().size)
        assertEquals(3072, frame.trailerBytes().size)
        assertArrayEquals(source, frame.imageBytes() + frame.trailerBytes())
        assertEquals(384 * 288, Ht301Layout.IMAGE_WORDS)
    }
    @Test fun shortFrameRejected() { assertThrows(IllegalArgumentException::class.java) { Ht301Frame.parse(raw().dropLast(1).toByteArray()) } }
    @Test fun longFrameRejected() { assertThrows(IllegalArgumentException::class.java) { Ht301Frame.parse(raw() + byteArrayOf(0)) } }
    @Test fun knownDisplayFrameRecognizedWithoutThermometry() {
        val result = Ht301Frame.parse(display()).inspect()
        assertEquals(FrameMode.DISPLAY, result.mode); assertNull(result.reason); assertFalse(result.summaryValid)
        assertEquals(32768, result.minimum); assertEquals(32980, result.maximum)
    }
    @Test fun knownRaw14FrameMatchesDesktopInspection() {
        val frame = Ht301Frame.parse(raw()); val result = frame.inspect()
        assertEquals(FrameMode.RAW14, result.mode); assertNull(result.reason); assertTrue(result.summaryValid)
        assertEquals(5128, result.minimum); assertEquals(5537, result.maximum)
        assertEquals(result.maximum, frame.word(Ht301Layout.HIGH_INDEX))
        assertEquals(result.minimum, frame.word(Ht301Layout.LOW_INDEX))
    }
    @Test fun raw14FrameWithHighBitsSetIsRejectedWithoutMasking() {
        for (value in listOf(0x4000, 0x8000, 0xffff)) {
            val bytes = raw(); putWord(bytes, 0, value)
            val frame = Ht301Frame.parse(bytes)
            assertEquals(value, frame.pixel(0, 0)); assertEquals(FrameMode.INVALID, frame.inspect().mode)
        }
    }
    @Test fun mixedDisplayAndRawFrameRejected() {
        val bytes = display(); putWord(bytes, 0, 5000)
        assertEquals("mixed_or_out_of_range_words", Ht301Frame.parse(bytes).inspect().reason)
    }
    @Test fun trailerWordsAreExcludedFromImageRange() {
        val bytes = raw(); putWord(bytes, Ht301Layout.IMAGE_BYTES, 0xffff)
        assertEquals(Ht301Frame.parse(raw()).inspect(), Ht301Frame.parse(bytes).inspect())
    }
    @Test fun calibrationCopyMismatchRetainsCandidateButRejectsItsEvidence() {
        val bytes = raw(); bytes[Ht301Layout.CALIBRATION_COPY] = (bytes[Ht301Layout.CALIBRATION_COPY].toInt() xor 1).toByte()
        val result = Ht301Frame.parse(bytes).inspect()
        assertEquals(FrameMode.RAW14, result.mode); assertEquals("calibration_copy_mismatch", result.reason)
    }
    @Test fun invalidSettingsRejectReadinessEvidence() {
        val bytes = raw(); putWord(bytes, Ht301Layout.PARAMETERS + 20, 0)
        assertEquals("invalid_settings_or_calibration", Ht301Frame.parse(bytes).inspect().reason)
    }
    @Test fun outOfRangeSummaryIndexRejectsEvidence() {
        val bytes = raw(); putWord(bytes, Ht301Layout.CENTER_INDEX, 0x4000)
        assertEquals("summary_index_out_of_range", Ht301Frame.parse(bytes).inspect().reason)
    }
    @Test fun wrongSummaryCoordinatesDoNotInventConsistency() {
        val bytes = raw(); putWord(bytes, Ht301Layout.HIGH_XY, Ht301Layout.WIDTH)
        val result = Ht301Frame.parse(bytes).inspect()
        assertEquals(FrameMode.RAW14, result.mode); assertNull(result.reason); assertFalse(result.summaryValid)
    }
    @Test fun nativeCoordinatesCannotSelectTrailerRows() {
        val frame = Ht301Frame.parse(raw())
        assertThrows(IllegalArgumentException::class.java) { frame.pixel(0, 288) }
        assertThrows(IllegalArgumentException::class.java) { frame.pixel(384, 0) }
    }
    @Test fun sourceAndReturnedArraysCannotMutateOwnedFrame() {
        val source = raw(); val expected = source.copyOf(); val frame = Ht301Frame.parse(source)
        source.fill(0); frame.transportBytes().fill(0); frame.imageBytes().fill(0); frame.trailerBytes().fill(0)
        assertArrayEquals(expected, frame.transportBytes())
    }
    @Test fun bothPreviewModesPreserveEverySourceByte() {
        for (bytes in listOf(raw(), display())) {
            val frame = Ht301Frame.parse(bytes); val pixels = PreviewRenderer.grayscale(frame, frame.inspect())
            assertEquals(Ht301Layout.IMAGE_WORDS, pixels.size); assertArrayEquals(bytes, frame.transportBytes())
        }
    }
    @Test fun invalidPreviewCannotNormalizeHighBitsAway() {
        val bytes = raw(); putWord(bytes, 0, 0x8001); val frame = Ht301Frame.parse(bytes)
        assertThrows(IllegalArgumentException::class.java) { PreviewRenderer.grayscale(frame, frame.inspect()) }
    }
    @Test fun latestFrameReplacesPendingWorkAndDoesNotQueue() = runBlocking {
        val latest = LatestFrameState<Int?>(null)
        val observed = mutableListOf<Int?>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            latest.asStateFlow().collect { observed.add(it); yield() }
        }
        // A busy producer must not make a slow consumer replay 10,000 obsolete snapshots.
        for (i in 0..10000) latest.value = i
        yield()
        collector.cancelAndJoin()
        assertEquals(listOf(null, 10000), observed)
        latest.value = null
        assertNull(latest.asStateFlow().value)
    }
    @Test fun releasedSourceCannotPublishAStaleFrameAfterReset() {
        val latest = LatestFrameState("streaming")
        val owned = java.util.concurrent.atomic.AtomicBoolean(true)
        val publishing = java.util.concurrent.CountDownLatch(1)
        val finishPublishing = java.util.concurrent.CountDownLatch(1)
        val invalidated = java.util.concurrent.CountDownLatch(1)
        val closed = java.util.concurrent.CountDownLatch(1)
        val publisher = Thread {
            latest.updateIf({ owned.get() }) {
                publishing.countDown()
                finishPublishing.await(5, java.util.concurrent.TimeUnit.SECONDS)
                "stale frame"
            }
        }
        val closer = Thread {
            owned.set(false)
            invalidated.countDown()
            latest.value = "closed"
            closed.countDown()
        }
        publisher.start()
        assertTrue(publishing.await(5, java.util.concurrent.TimeUnit.SECONDS))
        closer.start()
        assertTrue(invalidated.await(5, java.util.concurrent.TimeUnit.SECONDS))
        // Reset must wait for an already-entered publication; otherwise it can be overwritten afterward.
        val resetFinishedTooSoon = closed.await(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        finishPublishing.countDown()
        publisher.join(5000); closer.join(5000)
        assertFalse(resetFinishedTooSoon)
        assertEquals("closed", latest.value)
        latest.updateIf({ owned.get() }) { "another old frame" }
        assertEquals("closed", latest.value)
    }
    @Test fun disconnectResetsUsbStateIndependentOfUi() {
        assertEquals(UsbState(), UsbState(UsbPhase.STREAMING, "old camera").disconnected())
        assertEquals(UsbPhase.ATTACHED, UsbState().attached().phase)
    }
    @Test fun fixturesMatchTheirPublishedSanitizedHashes() {
        val hashes = mapOf("display-room-baseline.raw" to "d9daecccb563d15386b32f290c33caf564661f0fd3f3581077dfec4129a0248b",
            "radiometric-room-settled.raw" to "fde6a4b803b68fcea07ab7b428f4769d22e896f01055969f43102bdc9270f5d6")
        for ((file, hash) in hashes) assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(fixture(file)).joinToString("") { "%02x".format(it) })
    }
}
