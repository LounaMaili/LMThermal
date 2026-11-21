package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.recorder.PeriodicRecordingSnapshot
import org.lmthermal.recorder.RecorderResult
import org.lmthermal.recorder.RecorderSnapshot

/** The operator projection keeps memory admission, committed observations and END separate. */
class FieldRecordingDisplayTest {
    private val recorder = RecorderSnapshot("recording", 8, 5, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0,
        null, 0, 0, 0)
    private val periodic = PeriodicRecordingSnapshot(48, 39, 0, 9, 8, 1, 3, 3, 12, 2_500_000_000L,
        true, null, 4, 1, 3, 1, 2, 0, 0)
    private val file = FieldRecordingFile("run", "capture.lmtr", 1000, "a".repeat(64), "completed", "user_stop")

    /** Queue losses are direct counters and the cutoff excludes subsequent drain time. */
    @Test fun countersUseMeasuredPopulationsAndRecordedCutoff() {
        val counts = fieldRecordingCounts(recorder, periodic, 500_000_000L, 12_000_000_000L)!!
        assertEquals(9L, counts.selected)
        assertEquals(8L, counts.accepted)
        assertEquals(5L, counts.acknowledged)
        assertEquals(3L, counts.queueLosses)
        assertEquals(3L, counts.missedSlots)
        assertEquals(1L, counts.acknowledgedUnavailable)
        assertEquals(2.5, counts.acknowledgedHz!!, 0.0)
    }

    /** Missing witnesses stay unknown, including a sub-second rate sample. */
    @Test fun absentWitnessesAreNotZeroOrAQualifiedCadence() {
        assertNull(fieldRecordingCounts(null, null, 0, 0))
        val counts = fieldRecordingCounts(recorder, null, 0, 400_000_000)!!
        assertNull(counts.selected)
        assertNull(counts.acknowledgedHz)
        assertNull(counts.materializationLosses)
        assertEquals(5L, counts.acknowledged)
    }

    /** A writer terminal state cannot advance the UI ahead of the read-only check. */
    @Test fun drainRemainsVisibleUntilCheckedTerminalResult() {
        assertEquals(FieldRecordingPhase.READY, fieldRecordingPhase(FieldRecordingPhase.WAITING, true, null))
        assertEquals(FieldRecordingPhase.WAITING, fieldRecordingPhase(FieldRecordingPhase.WAITING, false, null))
        for (state in listOf("draining", "finalizing", "finalized", "failed")) {
            assertEquals(FieldRecordingPhase.DRAINING,
                fieldRecordingPhase(FieldRecordingPhase.RECORDING, true, recorder.copy(state = state)))
        }
        assertEquals(FieldRecordingPhase.STOP_REQUESTED,
            fieldRecordingPhase(FieldRecordingPhase.STOP_REQUESTED, true, recorder))
    }

    /** Only finalized and verified files expose immutable metadata. */
    @Test fun checkedEndAndReadbackDetermineDistinctTerminalStates() {
        val completed = RecorderResult(true, "completed", "user_stop", null, null)
        assertEquals(FieldRecordingPhase.FINALIZED, fieldRecordingTerminal(completed, null, file, true, null).phase)
        val stopped = completed.copy(status = "interrupted", reason = "field_safety_rss")
        val interrupted = fieldRecordingTerminal(stopped, null, file.copy(endStatus = "interrupted"), true, "rss")
        assertEquals(FieldRecordingPhase.INTERRUPTED, interrupted.phase)
        assertEquals("interrupted", interrupted.file?.endStatus)
        val failed = fieldRecordingTerminal(completed, null, file, false, "readback")
        assertEquals(FieldRecordingPhase.ERROR, failed.phase)
        assertNull(failed.file)
        assertEquals(FieldRecordingPhase.ERROR,
            fieldRecordingTerminal(completed.copy(finalized = false), null, null, false, null).phase)
    }

    /** The recorder admission requirement is deliberately distinct from indicative payload size. */
    @Test fun fiveHertzEstimateDoesNotReplaceConservativePreflight() {
        val space = FieldRecordingSpace(null)
        assertNull(space.availableBytes)
        assertEquals(50L * 384 * 288 * 4, space.uncompressedPlaneBytes)
        assertEquals(FieldPreflight(org.lmthermal.recorder.RecordingProfile.ANALYSIS, 10, null).requiredFreeBytes,
            space.preflight.requiredFreeBytes)
        assertFalse(FieldRecordingSpace(space.uncompressedPlaneBytes).preflight.permitted)
    }
}
