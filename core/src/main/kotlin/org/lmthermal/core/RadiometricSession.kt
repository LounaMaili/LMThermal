package org.lmthermal.core

import java.security.MessageDigest

/** Only the three evidence-backed HT-301 operations; callers cannot invent control values. */
enum class RadiometricCommand(val zoomAbsolute: Int) {
    SELECT_RAW14(32772), SELECT_NORMAL_RANGE(32800), SHUTTER_REFRESH(32768)
}

/** Serialized by the camera worker, sharing the streaming device and its lifetime. */
interface RadiometricControl {
    /** Return actual transferred bytes/error; completion alone never proves camera effect. */
    fun execute(command: RadiometricCommand): Int
}

enum class SessionState {
    DISCONNECTED, DISPLAY_STREAM, SWITCHING_TO_RAW14, RAW14_UNSETTLED,
    SHUTTER_TRANSIENT, RADIOMETRIC_READY, ERROR
}

/** Structural readiness only. The future thermometry layer must separately validate LUT outputs. */
data class SessionSnapshot(
    val state: SessionState = SessionState.DISCONNECTED,
    val stage: String = "observing",
    val reason: String? = null,
    val active: Boolean = false,
    val canInitialize: Boolean = false,
    val baseline: Int = 0,
    val discarded: Int = 0,
    val shutterFrames: Int = 0,
    val live: Int = 0,
    val rejected: Int = 0,
    val held: Int = 0,
    val malformed: Int = 0,
    val timeToReadyMs: Long? = null,
)

/** Event-driven Desktop session port, independent of Android, rendering and thermometry.
 * Minimum delays gate commands; image evidence gates progression. No sleeps or queued writes.
 * Each connection owns a fresh instance: pre-existing raw14 has unknown host range and is not ready.
 * See docs/ANDROID_RADIOMETRIC_SESSION.md for the explicitly deferred Desktop LUT gate.
 */
class RadiometricSession(
    private val control: RadiometricControl,
    private val generation: Long,
    private val clockMs: () -> Long,
    private val ownsConnection: () -> Boolean = { true },
    private val event: (Map<String, Any?>) -> Unit = {},
) {
    companion object {
        const val BASELINE_FRAMES = 3
        const val STAGE_DISCARD = 15
        const val STAGE_LIVE = 2
        const val SHUTTER_DISCARD = 75
        const val READY_LIVE = 5
        const val STAGE_LIMIT = 150
        const val FIRST_DELAY_MS = 500L
        const val RANGE_DELAY_MS = 600L
        const val SHUTTER_DELAY_MS = 500L
    }
    private enum class Step { OBSERVE, BASELINE, WAIT_RAW, RAW_STAGE, WAIT_RANGE, RANGE_STAGE, WAIT_SHUTTER, SETTLING, FINISHED, FAILED, CANCELLED }
    private var step = Step.OBSERVE
    private var state = SessionState.DISCONNECTED
    private var reason: String? = null
    private var previousDigest: ByteArray? = null
    private var stageDigest: ByteArray? = null
    private var mode = FrameMode.INVALID
    private var sequence = 0L
    private var baseline = 0
    private val baselineFrames = mutableListOf<Map<String, Any?>>()
    private var attempts = 0
    private var discarded = 0
    private var stageDiscarded = 0
    private var stageLive = 0
    private var shutterFrames = 0
    private var live = 0
    private var rejected = 0
    private var held = 0
    private var malformed = 0
    private var normalRangeConfirmed = false
    private var deadline = 0L
    private var started: Long? = null
    private var readyAt: Long? = null

    /** Retry requires newly observed DISPLAY and unknown host state; it still requalifies three frames. */
    fun snapshot() = SessionSnapshot(state, step.name.lowercase(), reason,
        step !in listOf(Step.OBSERVE, Step.FINISHED, Step.FAILED, Step.CANCELLED),
        mode == FrameMode.DISPLAY && step in listOf(Step.OBSERVE, Step.FAILED),
        baseline, discarded, shutterFrames, live, rejected, held, malformed,
        readyAt?.let { it - (started ?: it) })

    /** Explicit request only; previously seen display frames do not substitute for a fresh baseline. */
    fun initialize(requestGeneration: Long): Boolean {
        if (requestGeneration != generation || !ownsConnection() || !snapshot().canInitialize) return false
        step = Step.BASELINE; state = SessionState.DISPLAY_STREAM; baseline = 0; attempts = 0; started = clockMs(); readyAt = null
        normalRangeConfirmed = false; live = 0; previousDigest = null; reason = null; baselineFrames.clear()
        emit("initialize_requested")
        return true
    }

    /** Cancellation is irreversible for this instance; no rollback command is invented. */
    fun cancel() {
        step = Step.CANCELLED; state = SessionState.DISCONNECTED; live = 0
        normalRangeConfirmed = false; reason = "cancelled"; emit("cancelled")
    }

    /** A receipt from an obsolete connection can neither qualify evidence nor issue a control. */
    fun observe(frame: Ht301Frame?, inspection: FrameInspection, frameSequence: Long, frameGeneration: Long) {
        if (frameGeneration != generation || step == Step.CANCELLED) return
        if (!ownsConnection()) { cancel(); return }
        val wasActive = snapshot().active
        sequence = frameSequence; mode = inspection.mode
        val digest = frame?.let { MessageDigest.getInstance("SHA-256").digest(it.imageBytes()) }
        val repeated = digest != null && previousDigest?.contentEquals(digest) == true
        previousDigest = digest
        val validRaw = mode == FrameMode.RAW14 && inspection.reason == null
        if (validRaw && repeated) held++
        if (step != Step.FAILED) reason = inspection.reason
        if (mode == FrameMode.INVALID || inspection.reason != null) {
            rejected++; live = 0
            if (inspection.reason == "transport_size") malformed++
            if (state == SessionState.RADIOMETRIC_READY) state = SessionState.RAW14_UNSETTLED
        }
        when (step) {
            Step.BASELINE -> qualifyBaseline(inspection)
            Step.RAW_STAGE, Step.RANGE_STAGE -> qualifyStage(validRaw, repeated, digest)
            Step.SETTLING, Step.FINISHED, Step.OBSERVE -> observeStream(inspection, validRaw, repeated)
            Step.FAILED -> Unit // Observe mode to offer a safe explicit retry, but retain the error.
            else -> Unit // Frames during minimum timing windows never count as stage/ready evidence.
        }
        if (step == Step.SETTLING && ++attempts >= STAGE_LIMIT + SHUTTER_DISCARD)
            fail("No valid live post-shutter stream within frame limit")
        advanceDueCommand()
        if (wasActive || snapshot().active) emit("frame", inspection, mapOf("repeated_image" to repeated, "summary_valid" to inspection.summaryValid,
            "inspection_reason" to inspection.reason, "frame_bytes" to frame?.size,
            "image_bytes" to frame?.imageBytes()?.size, "trailer_bytes" to frame?.trailerBytes()?.size))
    }

    /** Timeouts with no payload do not advance evidence; the transport owner handles acquisition failure. */
    fun tick() {
        if (!ownsConnection()) { if (step != Step.CANCELLED) cancel(); return }
        advanceDueCommand()
    }

    /** Portable baseline is three fresh display frames; direct GET_CUR is query-dependent on HT-301. */
    private fun qualifyBaseline(inspection: FrameInspection) {
        if (mode == FrameMode.RAW14) { fail("Existing raw14 requires a known session; reconnect for display baseline"); return }
        attempts++
        if (mode == FrameMode.DISPLAY && inspection.reason == null) {
            baseline++
            baselineFrames += mapOf("sequence" to sequence, "minimum" to inspection.minimum,
                "maximum" to inspection.maximum, "monotonic_ms" to clockMs())
        } else { baseline = 0; baselineFrames.clear() }
        if (baseline >= BASELINE_FRAMES) {
            try {
                checkOwnership()
                emit("display_baseline", inspection, mapOf("frames" to baselineFrames.toList()))
                step = Step.WAIT_RAW; deadline = clockMs() + FIRST_DELAY_MS
            } catch (failure: Exception) { controlFailure(failure) }
        } else if (attempts >= STAGE_LIMIT) fail("Three consecutive display baseline frames not obtained")
    }

    /** Fifteen post-control receipts are discarded, then two distinct valid raw14 frames are required.
     * Like Desktop, summary consistency is a readiness gate, not a mode-transition gate.
     */
    private fun qualifyStage(valid: Boolean, repeated: Boolean, digest: ByteArray?) {
        if (stageDiscarded < STAGE_DISCARD) { stageDiscarded++; discarded++; reason = "stage_discard"; return }
        attempts++
        if (valid && !repeated && stageDigest?.contentEquals(digest) != true) stageLive++ else stageLive = 0
        stageDigest = digest
        if (stageLive >= STAGE_LIVE) {
            state = SessionState.RAW14_UNSETTLED
            if (step == Step.RAW_STAGE) { step = Step.WAIT_RANGE; deadline = clockMs() + RANGE_DELAY_MS }
            else { normalRangeConfirmed = true; step = Step.WAIT_SHUTTER; deadline = clockMs() + SHUTTER_DELAY_MS }
            emit("stage_verified")
        } else if (attempts >= STAGE_LIMIT) fail("No distinct valid raw14 transition within stage frame limit")
    }

    /** Image-only digests reject held frames: trailer changes alone cannot manufacture liveness.
     * The 75th structurally valid shutter frame is still rejected; five later changing, summary-valid
     * frames qualify readiness. Invalid evidence resets the streak, including after readiness.
     */
    private fun observeStream(inspection: FrameInspection, valid: Boolean, repeated: Boolean) {
        if (mode == FrameMode.DISPLAY) {
            live = 0
            if (state == SessionState.RADIOMETRIC_READY) fail("lost_raw14_mode")
            else if (state == SessionState.SHUTTER_TRANSIENT) reason = "shutter_display_frame"
            else state = SessionState.DISPLAY_STREAM
        } else if (valid) {
            if (state == SessionState.SHUTTER_TRANSIENT) {
                shutterFrames++; discarded++; reason = "shutter_settling"
                if (shutterFrames >= SHUTTER_DISCARD) { state = SessionState.RAW14_UNSETTLED; live = 0; previousDigest = null }
            } else if (repeated) {
                live = 0; reason = "held_image"
                if (state == SessionState.RADIOMETRIC_READY) state = SessionState.RAW14_UNSETTLED
            } else if (!normalRangeConfirmed) {
                state = SessionState.RAW14_UNSETTLED; live++; reason = "host_range_unverified"
            } else if (!inspection.summaryValid) {
                state = SessionState.RAW14_UNSETTLED; live = 0; reason = "summary_mismatch"
            } else {
                live++
                if (live >= READY_LIVE) {
                    state = SessionState.RADIOMETRIC_READY
                    if (readyAt == null) { readyAt = clockMs(); step = Step.FINISHED; emit("radiometric_ready") }
                } else reason = "awaiting_live_evidence"
            }
        }
        if (reason != null && inspection.reason == null) rejected++
    }

    /** All control calls remain on the source worker. Check lease around each operation so cancellation
     * stops later operations; a USB transfer already in flight may finish without a rollback/replay.
     */
    private fun advanceDueCommand() {
        if (clockMs() < deadline) return
        val command = when (step) {
            Step.WAIT_RAW -> RadiometricCommand.SELECT_RAW14
            Step.WAIT_RANGE -> RadiometricCommand.SELECT_NORMAL_RANGE
            Step.WAIT_SHUTTER -> RadiometricCommand.SHUTTER_REFRESH
            else -> return
        }
        try {
            checkOwnership()
            val transferred = control.execute(command)
            checkOwnership()
            check(transferred == Ht301Layout.BYTES_PER_WORD) { "SET transfer length/error: $transferred" }
            emit("control", extra = mapOf("requested" to command.zoomAbsolute, "actual_length" to transferred,
                "control_transfer_completed" to true))
            attempts = 0; stageLive = 0; stageDiscarded = 0; stageDigest = null
            when (command) {
                RadiometricCommand.SELECT_RAW14 -> { step = Step.RAW_STAGE; state = SessionState.SWITCHING_TO_RAW14 }
                RadiometricCommand.SELECT_NORMAL_RANGE -> step = Step.RANGE_STAGE
                RadiometricCommand.SHUTTER_REFRESH -> {
                    step = Step.SETTLING; state = SessionState.SHUTTER_TRANSIENT
                    shutterFrames = 0; live = 0; previousDigest = null
                }
            }
            emit("control_stage_started")
        } catch (failure: Exception) { controlFailure(failure) }
    }
    private fun checkOwnership() { check(ownsConnection() && step != Step.CANCELLED) { "Connection cancelled" } }
    private fun controlFailure(failure: Exception) {
        if (!ownsConnection()) cancel() else fail("Control failure: ${failure.message}")
    }
    private fun fail(message: String) { state = SessionState.ERROR; step = Step.FAILED; live = 0; reason = message; emit("error") }
    /** Numeric/enum evidence only, no raw bytes or digests/private scene content in diagnostics. */
    private fun emit(type: String, inspection: FrameInspection? = null, extra: Map<String, Any?> = emptyMap()) {
        event(mapOf("event" to type, "monotonic_ms" to clockMs(), "generation" to generation,
            "state" to state.name, "stage" to step.name, "mode" to mode.name, "sequence" to sequence,
            "baseline" to baseline, "discarded" to discarded, "shutter_frames" to shutterFrames,
            "live" to live, "rejected" to rejected, "held" to held, "malformed" to malformed,
            "reason" to reason, "minimum" to inspection?.minimum, "maximum" to inspection?.maximum) + extra)
    }
}
