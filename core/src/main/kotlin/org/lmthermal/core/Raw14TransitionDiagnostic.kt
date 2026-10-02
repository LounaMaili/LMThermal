package org.lmthermal.core

import java.security.MessageDigest

/** No readback or range/shutter API: this experiment can request only one SELECT_RAW14 transfer. */
fun interface Raw14TransitionControl { fun selectRaw14(): Int }

/** Single-command outcome is not radiometric readiness or verification of host range/shutter state. */
data class TransitionSnapshot(val stage: String = "observing", val active: Boolean = false,
    val canStart: Boolean = false, val reason: String? = null, val discarded: Int = 0,
    val distinct: Int = 0)

/** Explicit, connection-owned DISPLAY→raw14 experiment. Failed/successful instances never replay.
 * Uses Desktop's 15-receipt/two-distinct-image rule and existing structural parser, without GET_CUR.
 */
class Raw14TransitionDiagnostic(private val control: Raw14TransitionControl,
    private val generation: Long, private val clockMs: () -> Long,
    private val ownsConnection: () -> Boolean, private val event: (Map<String, Any?>) -> Unit) {
    private enum class Step { OBSERVING, BASELINE, WAIT_CONTROL, DISCARD, VERIFY, SUCCEEDED, FAILED, CANCELLED }
    private var step = Step.OBSERVING
    private var mode = FrameMode.INVALID
    private var reason: String? = null
    private var sequence = 0L
    private var attempts = 0
    private val baseline = mutableListOf<Map<String, Any?>>()
    private var deadline = 0L
    private var completedAt: Long? = null
    private var receipts = 0
    private var discarded = 0
    private var distinct = 0
    private var rejected = 0
    private var held = 0
    private var malformed = 0
    private var previousDigest: ByteArray? = null
    private var firstRaw = false

    /** Eligibility is observational and expires after this instance's one explicit request. */
    fun snapshot() = TransitionSnapshot(step.name.lowercase(), active(),
        step == Step.OBSERVING && mode == FrameMode.DISPLAY, reason, discarded, distinct)
    private fun active() = step in listOf(Step.BASELINE, Step.WAIT_CONTROL, Step.DISCARD, Step.VERIFY)

    /** Always requalify three frames after the operator request; old display observations are insufficient. */
    fun request(requestGeneration: Long): Boolean {
        if (requestGeneration != generation || !ownsConnection() || !snapshot().canStart) return false
        step = Step.BASELINE; emit("single_requested"); return true
    }
    /** Ownership cancellation never invents a rollback command or a second experiment. */
    fun cancel() { if (active()) { step = Step.CANCELLED; reason = "connection_cancelled"; emit("single_cancelled") } }
    /** No payload/time elapsed alone can satisfy transition evidence. */
    fun tick() {
        if (!ownsConnection()) { cancel(); return }
        if (step != Step.WAIT_CONTROL || clockMs() < deadline) return
        try {
            check(ownsConnection())
            val transferred = control.selectRaw14()
            check(ownsConnection())
            check(transferred == Ht301Layout.BYTES_PER_WORD) { "SET transfer length/error: $transferred" }
            completedAt = clockMs(); step = Step.DISCARD
            emit("single_transfer_completed", extra = mapOf("actual_length" to transferred))
        } catch (failure: Exception) {
            if (!ownsConnection()) cancel() else fail("Control failure: ${failure.message}")
        }
    }
    /** Original image bytes define distinctness; normalized preview and trailer changes cannot count. */
    fun observe(frame: Ht301Frame?, inspection: FrameInspection, frameSequence: Long, frameGeneration: Long) {
        if (frameGeneration != generation) return
        if (!ownsConnection()) { cancel(); return }
        sequence = frameSequence; mode = inspection.mode
        if (!active()) return
        val digest = frame?.let { MessageDigest.getInstance("SHA-256").digest(it.imageBytes()) }
        val repeated = digest != null && previousDigest?.contentEquals(digest) == true
        previousDigest = digest
        val validRaw = mode == FrameMode.RAW14 && inspection.reason == null
        if (inspection.mode == FrameMode.INVALID || inspection.reason != null) rejected++
        if (inspection.reason == "transport_size") malformed++
        if (validRaw && repeated) held++
        when (step) {
            Step.BASELINE -> {
                attempts++
                if (mode == FrameMode.RAW14) { fail("Existing raw14 is not a display baseline"); return }
                if (mode == FrameMode.DISPLAY && inspection.reason == null) baseline += mapOf(
                    "sequence" to sequence, "minimum" to inspection.minimum, "maximum" to inspection.maximum,
                    "monotonic_ms" to clockMs()) else baseline.clear()
                if (baseline.size == RadiometricSession.BASELINE_FRAMES) {
                    emit("single_display_baseline", inspection, mapOf("frames" to baseline.toList()))
                    deadline = clockMs() + RadiometricSession.FIRST_DELAY_MS; step = Step.WAIT_CONTROL
                } else if (attempts >= RadiometricSession.STAGE_LIMIT) fail("No three consecutive DISPLAY frames")
            }
            Step.DISCARD, Step.VERIFY -> {
                receipts++
                if (validRaw && !firstRaw) {
                    firstRaw = true; emit("single_first_valid_raw14", inspection,
                        mapOf("receipts_after_set" to receipts, "ms_after_set" to (clockMs() - completedAt!!)))
                }
                if (step == Step.DISCARD) {
                    discarded++
                    if (discarded == RadiometricSession.STAGE_DISCARD) { step = Step.VERIFY; attempts = 0 }
                } else {
                    attempts++
                    distinct = if (validRaw && !repeated) distinct + 1 else 0
                    if (distinct >= RadiometricSession.STAGE_LIVE) { step = Step.SUCCEEDED; emit("single_succeeded", inspection) }
                    else if (attempts >= RadiometricSession.STAGE_LIMIT) fail("No distinct valid raw14 transition within stage limit")
                }
            }
            else -> Unit
        }
        emit("single_frame", inspection, mapOf("repeated_image" to repeated,
            "frame_bytes" to frame?.size, "image_bytes" to frame?.imageBytes()?.size,
            "trailer_bytes" to frame?.trailerBytes()?.size))
        tick()
    }
    private fun fail(message: String) { step = Step.FAILED; reason = message; emit("single_failed") }
    /** Numeric evidence only; never record image payloads, digests or device identifiers. */
    private fun emit(type: String, inspection: FrameInspection? = null, extra: Map<String, Any?> = emptyMap()) {
        event(mapOf("event" to type, "monotonic_ms" to clockMs(), "generation" to generation,
            "stage" to step.name, "mode" to mode.name, "sequence" to sequence, "discarded" to discarded,
            "distinct" to distinct, "rejected" to rejected, "held" to held, "malformed" to malformed,
            "minimum" to inspection?.minimum, "maximum" to inspection?.maximum, "reason" to reason) + extra)
    }
}
