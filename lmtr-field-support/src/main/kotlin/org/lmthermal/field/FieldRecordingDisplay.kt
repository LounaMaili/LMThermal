package org.lmthermal.field

import org.lmthermal.recorder.PeriodicRecordingSnapshot
import org.lmthermal.recorder.RecorderSnapshot
import org.lmthermal.recorder.RecordingProfile
import org.lmthermal.recorder.RecorderResult

/** UI phases describe the field run and its readback, not extra recorder states. */
enum class FieldRecordingPhase {
    WAITING, READY, CHECKING, RECORDING, STOP_REQUESTED, DRAINING, FINALIZED, INTERRUPTED, ERROR;

    /** UI request availability is separate from the existing busy/resource gate. */
    val isActive: Boolean
        get() = when (this) {
            CHECKING, RECORDING, STOP_REQUESTED, DRAINING -> true
            else -> false
        }
}

/** Counts have different populations. Accepted and ACK come from the same recorder snapshot. */
data class FieldRecordingCounts(
    val selected: Long?,
    val accepted: Long,
    val acknowledged: Long,
    val preparationLosses: Long,
    val writerLosses: Long,
    val materializationLosses: Long?,
    val missedSlots: Long?,
    val acknowledgedUnavailable: Long?,
    val unclassifiedCallbacks: Long?,
    val acknowledgedHz: Double?
) {
    /** A scoped loss count, excluding missed slots and unclassified source callbacks. */
    val queueLosses: Long get() = preparationLosses + writerLosses
}

/** File metadata captured only after the existing close and read-only checks. */
data class FieldRecordingFile(
    val runId: String,
    val name: String,
    val bytes: Long,
    val sha256: String,
    val endStatus: String,
    val endReason: String
)

/** At most one run and one scalar snapshot are retained by the recorder UI. */
data class FieldRecordingProgress(
    val phase: FieldRecordingPhase = FieldRecordingPhase.WAITING,
    val counts: FieldRecordingCounts? = null,
    val file: FieldRecordingFile? = null,
    val detail: String? = null
)

/** Descriptive size estimate uses the 5 Hz policy without weakening storage preflight. */
data class FieldRecordingSpace(val availableBytes: Long?) {
    val preflight = FieldPreflight(RecordingProfile.ANALYSIS, FieldPeriodicDuration.REGRESSION_SECONDS, availableBytes)
    val uncompressedPlaneBytes = preflight.payloadBytesPerFrame * FieldPeriodicDuration.REGRESSION_SECONDS * 5
}

/** Project engine state and its counters without inferring losses from accepted minus ACK. */
fun fieldRecordingCounts(snapshot: RecorderSnapshot?, periodic: PeriodicRecordingSnapshot?, originNs: Long,
    sampledNs: Long?): FieldRecordingCounts? {
    if (snapshot == null) return null
    val end = periodic?.cutoffNs ?: sampledNs
    val span = end?.minus(originNs)?.takeIf { it >= 1_000_000_000 }
    return FieldRecordingCounts(periodic?.selected, snapshot.accepted, snapshot.acknowledged,
        snapshot.preparationDrops, snapshot.writerDrops, periodic?.materializationLosses, periodic?.missedDetected,
        periodic?.acknowledgedUnavailable, periodic?.unclassifiedCallbacks,
        span?.let { snapshot.acknowledged * 1_000_000_000.0 / it })
}

/** Finalizing and closing are shown as draining until the checked result is published. */
fun fieldRecordingPhase(phase: FieldRecordingPhase, eligible: Boolean, snapshot: RecorderSnapshot?): FieldRecordingPhase =
    when (phase) {
        FieldRecordingPhase.WAITING -> if (eligible) FieldRecordingPhase.READY else phase
        FieldRecordingPhase.RECORDING -> when (snapshot?.state) {
            "draining", "finalizing", "finalized", "failed" -> FieldRecordingPhase.DRAINING
            else -> phase
        }
        else -> phase
    }

/** An END written by the engine and a successful read-only check are separate requirements. */
fun fieldRecordingTerminal(result: RecorderResult?, counts: FieldRecordingCounts?, file: FieldRecordingFile?,
    readbackOk: Boolean, diagnosticFailure: String?): FieldRecordingProgress {
    val phase = when {
        result?.finalized != true || !readbackOk || file == null -> FieldRecordingPhase.ERROR
        result.status == "interrupted" -> FieldRecordingPhase.INTERRUPTED
        result.status == "completed" && diagnosticFailure == null -> FieldRecordingPhase.FINALIZED
        else -> FieldRecordingPhase.ERROR
    }
    return FieldRecordingProgress(phase, counts, file.takeIf { readbackOk && result?.finalized == true },
        diagnosticFailure ?: result?.failure ?: result?.reason)
}
