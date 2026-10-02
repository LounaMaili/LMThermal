package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test

/** Single-command fixtures protect the experiment's narrow write boundary and frame evidence. */
class Raw14TransitionDiagnosticTest {
    private val display = javaClass.getResourceAsStream("/fixtures/display-room-baseline.raw")!!.readBytes()
    private val raw = javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
    private fun changed(index: Int) = raw.copyOf().also { it[10000] = index.toByte() }
    private inner class Harness {
        var now = 0L; var owned = true; var seq = 0L; var writes = 0; var transferred = 2
        val events = mutableListOf<Map<String, Any?>>()
        val diagnostic = Raw14TransitionDiagnostic({ writes++; transferred }, 1, { now }, { owned }, events::add)
        fun frame(bytes: ByteArray = display, generation: Long = 1) {
            now += 40; seq++
            val frame = if (bytes.size == Ht301Layout.FRAME_BYTES) Ht301Frame.parse(bytes) else null
            diagnostic.observe(frame, frame?.inspect() ?: FrameInspection(FrameMode.INVALID,"transport_size",null,null), seq, generation)
        }
        fun start() { frame(); assertTrue(diagnostic.request(1)) }
        fun write() { start(); repeat(3) { frame() }; now += 500; diagnostic.tick() }
    }
    @Test fun explicitRequestRequalifiesBaselineAndAllowsExactlyOneWrite() {
        val h = Harness(); repeat(10) { h.frame() }; assertEquals(0,h.writes); h.start()
        h.frame(); h.frame(); h.now += 1000; h.diagnostic.tick(); assertEquals(0,h.writes)
        h.frame(); h.now += 499; h.diagnostic.tick(); assertEquals(0,h.writes)
        h.now++; h.diagnostic.tick(); assertEquals(1,h.writes)
        assertFalse(h.diagnostic.request(1)); repeat(200) { h.frame() }; assertEquals(1,h.writes)
    }
    @Test fun malformedBaselineResetsConsecutiveCount() {
        val h=Harness(); h.start(); h.frame(); h.frame(); h.frame(byteArrayOf(1)); repeat(2) { h.frame() }
        h.now+=1000; h.diagnostic.tick(); assertEquals(0,h.writes)
    }
    @Test fun partialZeroNegativeOrOversizedTransferFailsImmediately() {
        for (length in listOf(-9,0,1,3)) {
            val h=Harness(); h.transferred=length; h.write(); repeat(30) { h.frame(raw) }
            assertEquals("failed",h.diagnostic.snapshot().stage); assertEquals(1,h.writes)
            assertFalse(h.events.any { it["event"]=="single_succeeded" })
        }
    }
    @Test fun discardThenTwoDistinctStructurallyValidRawImagesAreRequired() {
        val h=Harness(); h.write(); repeat(15) { h.frame(changed(it)) }
        assertEquals(15,h.diagnostic.snapshot().discarded); assertEquals(0,h.diagnostic.snapshot().distinct)
        h.frame(changed(16)); assertEquals("verify",h.diagnostic.snapshot().stage)
        h.frame(changed(17)); assertEquals("succeeded",h.diagnostic.snapshot().stage)
        repeat(100) { h.frame(raw) }; assertEquals(1,h.writes)
        val first=h.events.first { it["event"]=="single_first_valid_raw14" }
        assertEquals(1,first["receipts_after_set"]); assertEquals(40L,first["ms_after_set"])
    }
    @Test fun repeatedImagesAndTrailerOnlyChangesCannotQualify() {
        val h=Harness(); h.write(); repeat(15) { h.frame(raw) }
        repeat(150) { h.frame(raw.copyOf().also { b -> b[Ht301Layout.IMAGE_BYTES]=it.toByte() }) }
        assertEquals("failed",h.diagnostic.snapshot().stage); assertEquals(1,h.writes)
    }
    @Test fun displayMixedAndMalformedFramesCannotQualifyOrSendAnotherCommand() {
        val h=Harness(); h.write(); val mixed=raw.copyOf().also { it[1]=0x80.toByte() }
        repeat(165) { h.frame(when(it%3) { 0->display; 1->mixed; else->byteArrayOf(1) }) }
        assertEquals("failed",h.diagnostic.snapshot().stage); assertEquals(1,h.writes)
    }
    @Test fun existingRaw14AndObsoleteGenerationNeverWrite() {
        val h=Harness(); h.frame(raw); assertFalse(h.diagnostic.request(1))
        h.frame(display); assertFalse(h.diagnostic.request(0)); h.start(); repeat(150) { h.frame(generation=0) }
        assertEquals(0,h.writes)
    }
    @Test fun ownershipLossOrCancellationPreventsReplay() {
        val h=Harness(); h.start(); repeat(3) { h.frame() }; h.owned=false; h.now+=1000; h.diagnostic.tick()
        assertEquals(0,h.writes); h.owned=true; assertFalse(h.diagnostic.request(1))
        val written=Harness(); written.write(); written.owned=false; written.frame(raw)
        assertEquals("cancelled",written.diagnostic.snapshot().stage); assertEquals(1,written.writes)
    }
}
