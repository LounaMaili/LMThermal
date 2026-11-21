package org.lmthermal.field

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
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

    /** The RSS Start lock outlives the run. Opening Recordings must leave the evidence intact. */
    @Test fun rssInterruptionPreservesClosingEvidenceAndPermitsMemorySave() =
        assertReleasedRecording("interrupted", forceRss = true)

    /** A normal Stop follows the same teardown requirement. */
    @Test fun normalStopPreservesClosingEvidenceAndPermitsMemorySave() =
        assertReleasedRecording("completed", forceRss = false)

    /** Jobs that are cancelling, lazy or still finishing must not qualify as released. */
    @Test fun pendingFieldWorkKeepsSaveDeferred() {
        val job = Job()
        assertFalse(fieldRecorderReleasedForMemorySave(null, false, job))
        assertFalse(fieldRecorderReleasedForMemorySave(null, true, null))
        job.complete()
        assertTrue(fieldRecorderReleasedForMemorySave(null, false, job, null))
    }

    /** A close timeout retains the run. Later cleanup can finish it once workers release. */
    @Test fun incompleteCloseCanResumeWithoutReopeningAReleasedRun() = runBlocking {
        val finalization = FieldRunFinalization(this)
        finalization.start { run -> assertTrue(run.stopRequested) }
        val first = checkNotNull(finalization.stop({ assertTrue(it) }, { error("Still closing") }))
        first.join()
        assertFalse(fieldRecorderReleasedForMemorySave(null, finalization.active != null))
        val retry = checkNotNull(finalization.stop({ assertFalse(it) }, { run ->
            finalization.writingEvidence(run)
            finalization.release(run) {}
        }))
        retry.join()
        assertTrue(fieldRecorderReleasedForMemorySave(null, finalization.active != null))
        assertNull(finalization.stop({ error("Run released") }, { error("Run released") }))
    }

    /** Exercise the field close coordinator and the real, read-only library using a synthetic LMTR. */
    private fun assertReleasedRecording(expectedEnd: String, forceRss: Boolean) = runBlocking {
        val clock = AtomicLong(0)
        val source = SimulatedRecordingModule()
        val plan = FieldPreflight(RecordingProfile.ANALYSIS, FieldPeriodicDuration.REGRESSION_SECONDS, null)
        val root = temporary.newFolder("r4-field", "session")
        val file = File(root, "capture.lmtr")
        val engine = RecorderEngine(SimulatedRecordingModule.header(listOf(source), codec = plan.codec), listOf(source.stream),
            RecordingProfile.ANALYSIS, plan.limits, LocalRecorderBackend(file), plan.codec, now = clock::get)
        val sampler = Job()
        val finalization = FieldRunFinalization(this)
        val writingEvidence = CompletableDeferred<Unit>()
        val publish = CompletableDeferred<Unit>()
        val unexpectedCutoff: (Boolean) -> Unit = { error("Run already stopped") }
        val unexpectedRetry: suspend (FieldRunFinalization.Run) -> Unit = { error("Run closed twice") }
        var startLocked = true
        var safetyFailure: String? = null
        try {
            finalization.start { run ->
                sampler.join()
                val result = checkNotNull(engine.awaitStop(10000))
                assertTrue(result.finalized)
                assertEquals(expectedEnd, result.status)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (!engine.workersTerminated() && System.nanoTime() < deadline) Thread.sleep(2)
                assertTrue(engine.workersTerminated())
                finalization.writingEvidence(run)
                writingEvidence.complete(Unit)
                publish.await()
                writeFieldClosingEvidence(root, mapOf("termination_status" to result.status, "termination_reason" to result.reason))
                finalization.release(run) { startLocked = safetyFailure != null }
            }
            assertEquals(Admission.ACCEPTED, engine.admit(source.observation(0)))
            assertFalse(fieldRecorderReleasedForMemorySave(engine, true, sampler))
            clock.set(1_000_000_000L)
            val closeJob = checkNotNull(finalization.stop(cutoff = { first ->
                assertTrue(first)
                if (forceRss) {
                    val initialRss = 100L * 1048576
                    val health = FieldHealth(80, 0, plan.requiredFreeBytes, 512L * 1048576, 512L * 1048576,
                        false, initialRss + plan.limits.memoryBudgetBytes + 1, true, true)
                    val refusal = FieldSafety(plan, initialRss, 0, 0).check(health, clock.get(), 25, clock.get())
                    assertEquals("rss_increment_exceeded", refusal)
                    safetyFailure = refusal
                    engine.stop("field_safety_$refusal", true)
                } else {
                    engine.stop()
                }
            }, retry = unexpectedRetry))
            assertFalse(fieldRecorderReleasedForMemorySave(null, false, sampler))
            sampler.complete()
            writingEvidence.await()
            assertFalse(fieldRecorderReleasedForMemorySave(null, finalization.active != null, sampler))
            // Backgrounding during closure waits for the existing job. It cannot change terminal data.
            assertSame(closeJob, finalization.stop(unexpectedCutoff, unexpectedRetry))
            publish.complete(Unit)
            closeJob.join()
            assertEquals(forceRss, startLocked)
            assertTrue(fieldRecorderReleasedForMemorySave(null, false, sampler))
            LmtrReader(file).use { assertEquals(expectedEnd, it.check(full = true)["declared_end_status"]) }

            val evidence = listOf(file, File(root, "private-report.json"), File(root, "inventory.json"))
            val originalHashes = evidence.map(::fieldFileHash)
            val expectedReason = if (forceRss) "field_safety_rss_increment_exceeded" else "user_stop"
            assertTrue(evidence[1].readText().contains(expectedReason))
            assertTrue(evidence[2].readText().contains(originalHashes[1]))
            // FieldActivity.onStop when the library opens, then return and ViewModel cleanup.
            assertNull(finalization.stop(unexpectedCutoff, unexpectedRetry))
            val library = FieldRecordingLibrary(root.parentFile)
            val inspected = library.inspect(library.list().entries.single())
            assertEquals(if (forceRss) FieldRecordingIntegrity.INTERRUPTED else FieldRecordingIntegrity.COMPLETED, inspected.integrity)
            assertEquals(originalHashes[0], inspected.sha256)
            assertNull(finalization.stop(unexpectedCutoff, unexpectedRetry))
            assertNull(finalization.stop(unexpectedCutoff, unexpectedRetry))
            assertEquals(originalHashes, evidence.map(::fieldFileHash))
        } finally {
            sampler.cancel()
            engine.stop()
            engine.awaitStop(10000)
        }
    }
}
