package org.lmthermal.field

import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lmthermal.lmtr.LmtrReader
import org.lmthermal.recorder.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Use the real safety predicate, recorder workers and LMTR reader with a synthetic source. */
class FieldMemorySaveTest {
    @get:Rule val temporary = TemporaryFolder()

    /** An RSS interruption keeps its END while the drained recorder releases diagnostic export. */
    @Test fun rssInterruptionPermitsSaveAfterTeardown() = assertReleasedRecording("interrupted", forceRss = true)

    /** A normal Stop follows the same teardown requirement. */
    @Test fun normalStopPermitsSaveAfterTeardown() = assertReleasedRecording("completed", forceRss = false)

    /** Jobs that are cancelling, lazy or still finishing must not qualify as released. */
    @Test fun pendingFieldWorkKeepsSaveDeferred() {
        val job = Job()
        assertFalse(fieldRecorderReleasedForMemorySave(null, false, job))
        assertFalse(fieldRecorderReleasedForMemorySave(null, true, null))
        job.complete()
        assertTrue(fieldRecorderReleasedForMemorySave(null, false, job, null))
    }

    /** Preserve the genuine result and defer saving until the field owner has cleared the engine. */
    private fun assertReleasedRecording(expectedEnd: String, forceRss: Boolean) {
        val clock = AtomicLong(0)
        val source = SimulatedRecordingModule()
        val plan = FieldPreflight(RecordingProfile.ANALYSIS, FieldPeriodicDuration.REGRESSION_SECONDS, null)
        val file = File(temporary.root, "capture.lmtr")
        val engine = RecorderEngine(SimulatedRecordingModule.header(listOf(source), codec = plan.codec), listOf(source.stream),
            RecordingProfile.ANALYSIS, plan.limits, LocalRecorderBackend(file), plan.codec, now = clock::get)
        val sampler = Job()
        try {
            assertEquals(Admission.ACCEPTED, engine.admit(source.observation(0)))
            assertFalse(fieldRecorderReleasedForMemorySave(engine, true, sampler))
            clock.set(1_000_000_000L)
            if (forceRss) {
                val initialRss = 100L * 1048576
                val health = FieldHealth(80, 0, plan.requiredFreeBytes, 512L * 1048576, 512L * 1048576,
                    false, initialRss + plan.limits.memoryBudgetBytes + 1, true, true)
                val refusal = FieldSafety(plan, initialRss, 0, 0).check(health, clock.get(), 25, clock.get())
                assertEquals("rss_increment_exceeded", refusal)
                engine.stop("field_safety_$refusal", true)
            } else {
                engine.stop()
            }
            val result = checkNotNull(engine.awaitStop(10000))
            assertTrue(result.finalized)
            assertEquals(expectedEnd, result.status)
            assertFalse(fieldRecorderReleasedForMemorySave(engine, false, sampler))
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!engine.workersTerminated() && System.nanoTime() < deadline) Thread.sleep(2)
            assertTrue(engine.workersTerminated())
            assertFalse(fieldRecorderReleasedForMemorySave(null, false, sampler))
            sampler.complete()
            assertTrue(fieldRecorderReleasedForMemorySave(null, false, sampler))
            LmtrReader(file).use { assertEquals(expectedEnd, it.check(full = true)["declared_end_status"]) }
        } finally {
            sampler.cancel()
            engine.stop()
            engine.awaitStop(10000)
        }
    }
}
