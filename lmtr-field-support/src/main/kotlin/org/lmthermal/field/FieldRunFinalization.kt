package org.lmthermal.field

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns one field run through setup, sampling and closure, independently of the Start lock. */
class FieldRunFinalization(private val scope: CoroutineScope) {
    /** FieldModel publishes setup and cutoff together under the coordinator monitor. */
    class Run internal constructor() {
        internal lateinit var task: Job
        var stopRequested = false
            internal set
        var writingEvidence = false
            internal set
    }

    @Volatile var active: Run? = null
        private set

    /** Keep setup and the first close attempt under one job, even when Stop cancels sampling. */
    @Synchronized fun start(work: suspend (Run) -> Unit) {
        check(active == null)
        val run = Run()
        active = run
        run.task = scope.launch { work(run) }
    }

    /** Late lifecycle events cannot acquire a released run or cancel an in-flight finalizer. */
    @Synchronized fun stop(cutoff: (firstRequest: Boolean) -> Unit, retry: suspend (Run) -> Unit): Job? {
        val run = active ?: return null
        val firstRequest = !run.stopRequested
        run.stopRequested = true
        if (!run.writingEvidence) cutoff(firstRequest)
        // A timed-out or failed close may still own workers or unfinished evidence.
        if (run.task.isCompleted) run.task = scope.launch { retry(run) }
        return run.task
    }

    /** Once workers are released, freeze terminal witnesses before producing closing files. */
    @Synchronized fun writingEvidence(run: Run) {
        check(active === run)
        run.writingEvidence = true
    }

    /** Publish the terminal state and relinquish ownership before another lifecycle action runs. */
    @Synchronized fun release(run: Run, publish: () -> Unit) {
        check(active === run)
        publish()
        active = null
    }
}
