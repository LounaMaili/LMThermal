package org.lmthermal.r2

import org.junit.Assert.*
import org.junit.Test

/** Profiling is observational: instrumented encoding must remain byte-identical. */
class StageCostsTest {
    @Test fun timingDoesNotChangeChunkBytesAndAttachmentIsScoped() {
        val observations=(0L..3L).map { Synthetic.frame(it,13,7,true) }
        val before=ChunkEncoder(Profile.FULL,Stored).encode(observations)
        val costs=StageCosts()
        val after=costs.attached { ChunkEncoder(Profile.FULL,Stored).encode(observations) }
        assertEquals(before.parts.size,after.parts.size)
        before.parts.zip(after.parts).forEach { (a,b) -> assertArrayEquals(a,b) }
        val snapshot=costs.snapshot(); assertTrue("role_materialize" in snapshot); assertTrue("logical_hash" in snapshot)
        ChunkEncoder(Profile.FULL,Stored).encode(observations)
        assertEquals(snapshot,costs.snapshot())
    }
    @Test fun exceptionalWorkStillReportsAndRestoresAttachment() {
        val costs=StageCosts()
        try { costs.attached { StageCosts.timed("failure") { error("injected") } }; fail() } catch (_:IllegalStateException) { }
        assertTrue("failure" in costs.snapshot())
        StageCosts.timed("outside") { Unit }; assertFalse("outside" in costs.snapshot())
    }
}
