package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test

/** Scripted control/frame source protects Desktop-derived invariants without Android or USB hardware. */
class RadiometricSessionTest {
    private val display = javaClass.getResourceAsStream("/fixtures/display-room-baseline.raw")!!.readBytes()
    private val raw = javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
    private fun changed(index: Int): ByteArray = raw.copyOf().also {
        val value = 5200 + index % 100
        val offset = (100 * Ht301Layout.WIDTH + 100) * 2
        it[offset] = value.toByte(); it[offset + 1] = (value shr 8).toByte()
    }
    private class Control : RadiometricControl {
        var reads = 0
        var value = 0
        var mismatch = false
        var throwOnWrite = false
        var throwOnRead = false
        var afterWrite: () -> Unit = {}
        val writes = mutableListOf<Int>()
        override fun readZoom(): Int {
            reads++
            check(!throwOnRead) { "read failed" }
            return if (mismatch && writes.isNotEmpty()) 1 else value
        }
        override fun execute(command: RadiometricCommand) {
            check(!throwOnWrite) { "write failed" }
            value = command.zoomAbsolute; writes += value; afterWrite()
        }
    }
    private inner class Harness {
        val control = Control()
        var now = 0L
        var owned = true
        var sequence = 0L
        val events = mutableListOf<Map<String, Any?>>()
        val session = RadiometricSession(control, 1, { now }, { owned }, events::add)
        fun frame(bytes: ByteArray = if (control.value == 0) display else changed(sequence.toInt()), generation: Long = 1) {
            now += 40; sequence++
            val parsed = if (bytes.size == Ht301Layout.FRAME_BYTES) Ht301Frame.parse(bytes) else null
            session.observe(parsed, parsed?.inspect() ?: FrameInspection(FrameMode.INVALID, "transport_size", null, null), sequence, generation)
        }
        fun start() { frame(); assertTrue(session.initialize(1)) }
        fun until(predicate: () -> Boolean) {
            repeat(500) { if (predicate()) return; frame() }
            fail("Script did not reach ${session.snapshot()}")
        }
        fun shutter() { start(); until { session.snapshot().state == SessionState.SHUTTER_TRANSIENT } }
        fun ready() { shutter(); until { session.snapshot().state == SessionState.RADIOMETRIC_READY } }
    }
    @Test fun disconnectedBecomesReadOnlyDisplayStream() {
        val h = Harness(); assertEquals(SessionState.DISCONNECTED, h.session.snapshot().state)
        repeat(30) { h.frame() }
        assertEquals(SessionState.DISPLAY_STREAM, h.session.snapshot().state); assertTrue(h.control.writes.isEmpty()); assertEquals(0, h.control.reads)
    }
    @Test fun explicitRequestRequalifiesThreeFreshBaselineFrames() {
        val h = Harness(); repeat(5) { h.frame() }; h.session.initialize(1)
        h.frame(); h.frame(); assertEquals(2, h.session.snapshot().baseline)
        h.now += 1000; h.session.tick(); assertTrue(h.control.writes.isEmpty())
        h.frame(); assertEquals(3, h.session.snapshot().baseline)
        h.now += 499; h.session.tick(); assertTrue(h.control.writes.isEmpty())
        h.now++; h.session.tick(); assertEquals(listOf(32772), h.control.writes)
    }
    @Test fun invalidFrameResetsDisplayBaseline() {
        val h = Harness(); h.start(); h.frame(); h.frame(); h.frame(byteArrayOf(1))
        assertEquals(0, h.session.snapshot().baseline); h.frame(); h.frame()
        assertTrue(h.control.writes.isEmpty())
    }
    @Test fun missingDisplayBaselineFailsWithoutWrite() {
        val h = Harness(); h.start(); repeat(150) { h.frame(byteArrayOf(1)) }
        assertEquals(SessionState.ERROR, h.session.snapshot().state); assertTrue(h.control.writes.isEmpty())
    }
    @Test fun unknownInitialZoomAbortsWithoutWrite() {
        val h = Harness(); h.start(); h.control.value = 32773
        repeat(3) { h.frame(display) }; assertEquals(SessionState.ERROR, h.session.snapshot().state)
        assertTrue(h.control.writes.isEmpty())
    }
    @Test fun exactCommandOrderAndReadbacksMatchDesktop() {
        val h = Harness(); h.ready(); assertEquals(listOf(32772, 32800, 32768), h.control.writes)
        val controls = h.events.filter { it["event"] == "control" }
        assertEquals(h.control.writes, controls.map { it["requested"] })
        assertTrue(controls.all { it["requested"] == it["readback"] })
        val times = controls.map { it["monotonic_ms"] as Long }
        assertTrue(times[1] - times[0] >= 600); assertTrue(times[2] - times[1] >= 500)
    }
    @Test fun commandWaitsForPriorStageEvidence() {
        val h = Harness(); h.start(); h.until { h.control.writes.isNotEmpty() }
        repeat(16) { h.frame(display) }; assertEquals(listOf(32772), h.control.writes)
        assertNotEquals(SessionState.RADIOMETRIC_READY, h.session.snapshot().state)
    }
    @Test fun readbackMismatchStopsBeforeRangeAndShutter() {
        val h = Harness(); h.control.mismatch = true; h.start(); h.until { h.session.snapshot().state == SessionState.ERROR }
        repeat(40) { h.frame() }; assertEquals(listOf(32772), h.control.writes)
    }
    @Test fun writeFailureAbortsSequence() {
        val h = Harness(); h.control.throwOnWrite = true; h.start(); h.until { h.session.snapshot().state == SessionState.ERROR }
        assertTrue(h.control.writes.isEmpty())
    }
    @Test fun baselineReadFailureAbortsSequence() {
        val h = Harness(); h.control.throwOnRead = true; h.start(); h.until { h.session.snapshot().state == SessionState.ERROR }
        assertTrue(h.control.writes.isEmpty())
    }
    @Test fun fifteenReceiptsAreDiscardedBeforeTwoDistinctStageFrames() {
        val h = Harness(); h.start(); h.until { h.control.writes.isNotEmpty() }
        repeat(15) { h.frame() }; assertEquals(15, h.session.snapshot().discarded)
        assertEquals("raw_stage", h.session.snapshot().stage)
        h.frame(); assertEquals("raw_stage", h.session.snapshot().stage)
        h.frame(); assertEquals("wait_range", h.session.snapshot().stage)
        assertEquals(0, h.session.snapshot().live)
    }
    @Test fun heldStageFramesCannotAdvanceCommand() {
        val h = Harness(); h.start(); h.until { h.control.writes.isNotEmpty() }
        repeat(40) { h.frame(raw) }; assertEquals(listOf(32772), h.control.writes)
    }
    @Test fun mixedAndMalformedFramesCannotQualifyStage() {
        val h = Harness(); h.start(); h.until { h.control.writes.isNotEmpty() }
        val mixed = raw.copyOf().also { it[1] = 0x80.toByte() }
        repeat(50) { h.frame(if (it % 2 == 0) mixed else byteArrayOf(1)) }
        assertEquals(listOf(32772), h.control.writes); assertTrue(h.session.snapshot().malformed > 0)
    }
    @Test fun seventyFiveShutterFramesAloneAreNotReady() {
        val h = Harness(); h.shutter(); repeat(75) { h.frame(raw) }
        assertEquals(SessionState.RAW14_UNSETTLED, h.session.snapshot().state)
        assertEquals(0, h.session.snapshot().live); assertEquals(75, h.session.snapshot().shutterFrames)
    }
    @Test fun invalidShutterFramesDoNotCountTowardSeventyFive() {
        val h = Harness(); h.shutter(); repeat(75) { h.frame(byteArrayOf(1)) }
        assertEquals(SessionState.SHUTTER_TRANSIENT, h.session.snapshot().state)
        assertEquals(0, h.session.snapshot().shutterFrames)
    }
    @Test fun fiveChangingValidFramesAfterShutterAreRequired() {
        val h = Harness(); h.shutter(); repeat(75) { h.frame(raw) }
        repeat(4) { h.frame(changed(it)) }; assertNotEquals(SessionState.RADIOMETRIC_READY, h.session.snapshot().state)
        h.frame(changed(4)); assertEquals(SessionState.RADIOMETRIC_READY, h.session.snapshot().state)
    }
    @Test fun heldImagesResetLivenessEvenWhenTrailerChanges() {
        val h = Harness(); h.shutter(); repeat(75) { h.frame(raw) }; h.frame(raw)
        val trailerChanged = raw.copyOf().also { it[Ht301Layout.IMAGE_BYTES]++ }
        h.frame(trailerChanged); assertEquals(0, h.session.snapshot().live)
        assertEquals("held_image", h.session.snapshot().reason)
    }
    @Test fun invalidFrameResetsPartialEvidenceAndFiveNewFramesRecover() {
        val h = Harness(); h.shutter(); repeat(75) { h.frame(raw) }; repeat(3) { h.frame(changed(it)) }
        h.frame(byteArrayOf(1)); assertEquals(0, h.session.snapshot().live)
        repeat(4) { h.frame(changed(it)) }; assertNotEquals(SessionState.RADIOMETRIC_READY, h.session.snapshot().state)
        h.frame(changed(4)); assertEquals(SessionState.RADIOMETRIC_READY, h.session.snapshot().state)
    }
    @Test fun summaryMismatchCannotQualifyReadiness() {
        val h = Harness(); h.shutter(); repeat(75) { h.frame(raw) }
        repeat(10) { i -> h.frame(changed(i).also { it[Ht301Layout.HIGH_XY] = 0; it[Ht301Layout.HIGH_XY + 1] = 2 }) }
        assertEquals(0, h.session.snapshot().live); assertEquals("summary_mismatch", h.session.snapshot().reason)
    }
    @Test fun readyHeldOrMalformedStreamDemotesAndRecoversLikeDesktop() {
        val h = Harness(); h.ready(); h.frame(byteArrayOf(1))
        assertEquals(SessionState.RAW14_UNSETTLED, h.session.snapshot().state)
        repeat(5) { h.frame(changed(it)) }; assertEquals(SessionState.RADIOMETRIC_READY, h.session.snapshot().state)
        h.frame(changed(4)); assertEquals(SessionState.RAW14_UNSETTLED, h.session.snapshot().state)
    }
    @Test fun displayAfterReadinessIsExplicitLossError() {
        val h = Harness(); h.ready(); h.frame(display); assertEquals(SessionState.ERROR, h.session.snapshot().state)
        assertEquals("lost_raw14_mode", h.session.snapshot().reason)
    }
    @Test fun existingRaw14NeverInitializesOrClaimsReadiness() {
        val h = Harness(); repeat(20) { h.frame(changed(it)) }
        assertEquals(SessionState.RAW14_UNSETTLED, h.session.snapshot().state)
        assertEquals("host_range_unverified", h.session.snapshot().reason)
        assertFalse(h.session.initialize(1)); assertTrue(h.control.writes.isEmpty()); assertEquals(0, h.control.reads)
    }
    @Test fun raw14DuringBaselineAbortsInsteadOfForcingMode() {
        val h = Harness(); h.start(); h.frame(raw)
        assertEquals(SessionState.ERROR, h.session.snapshot().state); assertTrue(h.control.writes.isEmpty())
    }
    @Test fun duplicateInitializationRequestDoesNotRestartSequence() {
        val h = Harness(); h.start(); assertFalse(h.session.initialize(1)); h.readyFromStarted()
    }
    private fun Harness.readyFromStarted() { until { session.snapshot().state == SessionState.RADIOMETRIC_READY }; assertEquals(3, control.writes.size) }
    @Test fun disconnectCloseOrBackgroundLeaseStopsRemainingWrites() {
        for (label in listOf("disconnect", "close", "background")) {
            val h = Harness(); h.start(); h.until { h.control.writes.size == 1 }; h.owned = false
            repeat(100) { h.frame() }; h.session.tick()
            assertEquals(label, listOf(32772), h.control.writes); assertEquals(SessionState.DISCONNECTED, h.session.snapshot().state)
        }
    }
    @Test fun cancelledInstanceCannotReplayAfterForeground() {
        val h = Harness(); h.start(); h.session.cancel(); h.now += 2000; h.session.tick()
        repeat(200) { h.frame() }; assertTrue(h.control.writes.isEmpty()); assertFalse(h.session.initialize(1))
    }
    @Test fun obsoleteGenerationFramesAndRequestCannotQualifyNewConnection() {
        val h = Harness(); repeat(200) { h.frame(raw, generation = 0) }
        assertEquals(SessionState.DISCONNECTED, h.session.snapshot().state); assertFalse(h.session.initialize(0))
        h.start(); repeat(200) { h.frame(display, generation = 0) }; assertTrue(h.control.writes.isEmpty())
    }
    @Test fun cancellationDuringControlStopsReadbackAndLaterCommands() {
        val h = Harness(); h.control.afterWrite = { h.owned = false }; h.start()
        h.until { h.control.writes.isNotEmpty() }; repeat(100) { h.frame() }
        assertEquals(listOf(32772), h.control.writes)
        assertEquals(SessionState.DISCONNECTED, h.session.snapshot().state)
        assertFalse(h.events.any { it["event"] == "control" })
    }
    @Test fun shutterHoldAndRecoveryMatchExecutedDesktopOracle() {
        val lines = javaClass.getResourceAsStream("/fixtures/desktop-shutter-oracle.tsv")!!.bufferedReader()
            .use { it.readLines().filterNot { line -> line.startsWith("#") || line.isBlank() } }
        val h = Harness(); h.shutter()
        for ((index, line) in lines.withIndex()) {
            h.frame(if (index < 106) raw else changed(index - 106))
            val fields = line.split('\t')
            assertEquals("Desktop frame ${fields[0]}", fields[1], h.session.snapshot().state.name)
            assertEquals(fields[2], h.session.snapshot().reason ?: "-")
        }
    }

    @Test fun baselineOneAbortsAndPreservesErrorAcrossLaterFrames() {
        val h = Harness(); h.start(); h.control.value = 1
        repeat(3) { h.frame(display) }
        val error = h.session.snapshot().reason
        h.frame(display); assertEquals(error, h.session.snapshot().reason)
        assertTrue(error!!.contains("got 1")); assertTrue(h.control.writes.isEmpty())
    }
    @Test fun explicitRetryRequalifiesBaselineAndDoesNotRetainErrorState() {
        val h = Harness(); h.start(); h.control.value = 1; repeat(3) { h.frame(display) }
        assertTrue(h.session.initialize(1)); assertEquals(SessionState.DISPLAY_STREAM, h.session.snapshot().state)
        h.control.value = 0; repeat(2) { h.frame(display) }
        assertTrue(h.control.writes.isEmpty()); h.readyFromStarted()
    }

}
