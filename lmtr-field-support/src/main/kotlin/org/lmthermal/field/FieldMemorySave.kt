package org.lmthermal.field

import kotlinx.coroutines.Job
import org.lmthermal.recorder.RecorderEngine

/**
 * The field owner clears its recorder reference only after Stop and worker teardown. A safety
 * refusal can keep Start locked after that point. Cancelled jobs still own work until completion.
 */
fun fieldRecorderReleasedForMemorySave(recorder: RecorderEngine?, fieldRunInProgress: Boolean,
    vararg fieldJobs: Job?): Boolean =
    recorder == null && !fieldRunInProgress && fieldJobs.all { it == null || it.isCompleted }

/** The same window's completion handler must have returned its lease after the final probe. */
fun fieldCollectorReleasedForMemorySave(window: BracketedMemoryWindow, job: Job?, releasedGeneration: Long): Boolean =
    job?.isCompleted == true && releasedGeneration == window.generation && window.quiescent()
