package org.lmthermal.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lmthermal.core.LatestFrameState

/** One active session, with awaited close-before-open and generation-bound publication.
 * Immediate invalidation clears stale data while slow native/SDK close completes off the UI thread.
 * Selection/probing is independent of this owner and never invokes a module factory.
 */
class CameraSessionOwner<P>(private val scope: CoroutineScope,
    private val activeChanged: (CameraSession<P>?) -> Unit = {},
    private val technicalFailure: (Throwable) -> Unit = {},
    private val event: (Map<String, Any?>) -> Unit = {}) {
    private val mutex = Mutex()
    private val latest = LatestFrameState(CameraSessionState<P>())
    val state = latest.asStateFlow()
    @Volatile private var generation = 0L
    private var active: CameraSession<P>? = null
    private var activeGeneration = -1L
    private var collector: Job? = null
    private var nextSession = 0L
    private var sessionId: Long? = null
    private var opens = 0L
    private var releases = 0L
    private var actions = 0L

    /** Local ordinals and counts only: no USB path, hardware serial, frame bytes or scene fingerprint. */
    private fun record(name: String, reason: CameraCloseReason? = null) {
        runCatching { event(mapOf("event" to name, "session_id" to sessionId, "opens" to opens,
            "releases" to releases, "active_owners" to if (active == null) 0 else 1,
            "explicit_actions" to actions, "reason" to reason?.name)) }
    }
    /** A fatal source ERROR or action failure must release too. Schedule outside the collector/mutex so close can await it. */
    @Synchronized private fun failed(token: Long, error: CameraError) {
        if (token != generation) return
        invalidate(unavailable(state.value, CameraLifecycle.ERROR, CameraStatus(CameraStatusCode.ERROR), error))
        scope.launch {
            mutex.withLock {
                if (activeGeneration != token) return@withLock
                try { release(CameraCloseReason.FAILED) }
                catch (failure: Exception) { technicalFailure(failure) }
            }
        }
    }

    /** Serialize generation change with eventual publication; no old observer can revive invalidated data. */
    @Synchronized private fun invalidate(snapshot: CameraSessionState<P>): Long {
        generation++
        latest.value = snapshot
        return generation
    }
    /** Read-only detection can update an idle screen but cannot replace or initialize a live session. */
    fun detected(selection: CameraSelection<P>) {
        if (state.value.lifecycle in setOf(CameraLifecycle.OPENING, CameraLifecycle.PERMISSION_PENDING, CameraLifecycle.STREAMING)) return
        val snapshot = when (selection) {
            is CameraSelection.Selected -> CameraSessionState<P>(
                module = selection.candidate.module.metadata, device = selection.candidate.device,
                lifecycle = CameraLifecycle.DETECTED, status = CameraStatus(CameraStatusCode.DETECTED),
                actions = setOf(CameraAction.CONNECT))
            is CameraSelection.None -> CameraSessionState<P>(error = selection.error)
            is CameraSelection.Ambiguous -> CameraSessionState<P>(lifecycle = CameraLifecycle.ERROR,
                status = CameraStatus(CameraStatusCode.ERROR), error = CameraError(CameraErrorCode.CAMERA_MATCH_AMBIGUOUS,
                    mapOf("matches" to selection.candidates.size.toString())))
        }
        invalidate(snapshot)
    }
    /** Authorization belongs to the pending explicit candidate, before any session factory is invoked. */
    fun permissionRequired(candidate: CameraCandidate<P>) {
        invalidate(CameraSessionState(module = candidate.module.metadata, device = candidate.device,
            lifecycle = CameraLifecycle.PERMISSION_PENDING, status = CameraStatus(CameraStatusCode.PERMISSION_REQUIRED),
            error = CameraError(CameraErrorCode.PERMISSION_REQUIRED), actions = setOf(CameraAction.CLOSE)))
    }
    fun permissionDenied(candidate: CameraCandidate<P>) {
        invalidate(CameraSessionState(module = candidate.module.metadata, device = candidate.device,
            lifecycle = CameraLifecycle.ERROR, status = CameraStatus(CameraStatusCode.ERROR),
            error = CameraError(CameraErrorCode.PERMISSION_DENIED), actions = setOf(CameraAction.CONNECT)))
    }
    /** Permission resolution must leave pending state even if the exact supported device disappeared. */
    fun detectedAfterPermission(selection: CameraSelection<P>) {
        invalidate(CameraSessionState())
        detected(selection)
    }
    /** Explicit request. A stale/cancelled open is closed before it can publish or accept actions. */
    fun open(candidate: CameraCandidate<P>): Job {
        val token = invalidate(CameraSessionState(module = candidate.module.metadata, device = candidate.device,
            lifecycle = CameraLifecycle.OPENING, status = CameraStatus(CameraStatusCode.OPENING),
            actions = setOf(CameraAction.CLOSE)))
        return scope.launch {
            mutex.withLock {
                if (token != generation) return@withLock
                try {
                    release(CameraCloseReason.REPLACED)
                    if (token != generation) return@withLock
                    val session = candidate.module.open(candidate.device)
                    if (token != generation) { session.close(CameraCloseReason.REPLACED); return@withLock }
                    active = session; activeGeneration = token; sessionId = ++nextSession; opens++
                    activeChanged(session); record("session_opened")
                    collector = scope.launch {
                        session.state.collect { snapshot ->
                            if (!valid(candidate, snapshot)) {
                                latest.updateIf({ token == generation }) { unavailable(it, CameraLifecycle.ERROR,
                                    CameraStatus(CameraStatusCode.ERROR), CameraError(CameraErrorCode.MODULE_DATA_INVALID)) }
                            } else if (snapshot.lifecycle == CameraLifecycle.ERROR) {
                                failed(token, snapshot.error ?: CameraError(CameraErrorCode.STREAM_FAILED))
                            } else latest.updateIf({ token == generation }) { snapshot }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    technicalFailure(failure)
                    latest.updateIf({ token == generation }) { unavailable(it, CameraLifecycle.ERROR,
                        CameraStatus(CameraStatusCode.ERROR), CameraError(CameraErrorCode.STREAM_OPEN_FAILED)) }
                }
            }
        }
    }
    /** Only the current owned source receives an explicitly supported action; detection never uses this path. */
    fun perform(action: CameraAction): Job {
        if (action == CameraAction.CLOSE) return close()
        val token = generation
        return scope.launch {
            mutex.withLock {
                if (token != generation) return@withLock
                if (!CameraUiPolicy.canPerform(state.value, action)) {
                    latest.updateIf({ token == generation }) { it.copy(error = CameraError(CameraErrorCode.ACTION_UNAVAILABLE,
                        mapOf("action" to action.name))) }
                } else {
                    try { active?.let { actions++; record("explicit_action"); it.perform(action) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        technicalFailure(failure)
                        failed(token, CameraError(CameraErrorCode.STREAM_FAILED))
                    }
                }
            }
        }
    }
    /** Clear current data synchronously, then await release. A newer open is serialized after that release. */
    fun close(reason: CameraCloseReason = CameraCloseReason.USER): Job {
        val previous = state.value
        val token = invalidate(unavailable(previous, CameraLifecycle.CLOSED, CameraStatus(CameraStatusCode.CLOSED)))
        return scope.launch {
            mutex.withLock {
                if (activeGeneration > token) return@withLock
                try { release(reason) }
                catch (failure: Exception) {
                    technicalFailure(failure)
                    latest.updateIf({ token == generation }) { unavailable(it, CameraLifecycle.ERROR,
                        CameraStatus(CameraStatusCode.ERROR), CameraError(CameraErrorCode.STREAM_FAILED)) }
                }
            }
        }
    }
    /** An unrelated peripheral cannot close the active camera. */
    fun detached(deviceKey: String): Job? = if (state.value.device?.key == deviceKey) close(CameraCloseReason.DETACHED) else null
    fun background(): Job = close(CameraCloseReason.BACKGROUND)

    /** The observer is cancelled first; source close must finish even if its caller is cancelled. */
    private suspend fun release(reason: CameraCloseReason) = withContext(NonCancellable) {
        collector?.cancelAndJoin(); collector = null
        val old = active; activeChanged(null)
        if (old != null) { record("release_started", reason); old.close(reason); releases++ }
        // Retain ownership if release throws: a later open must retry it, never overlap an unreleased source.
        active = null; activeGeneration = -1L
        if (old != null) record("session_released", reason)
        sessionId = null
    }
    /** Validate module/geometry provenance at the boundary without interpreting any camera protocol. */
    private fun valid(candidate: CameraCandidate<P>, snapshot: CameraSessionState<P>): Boolean {
        if (snapshot.module != candidate.module.metadata || snapshot.device != candidate.device ||
            snapshot.capabilities != candidate.module.metadata.capabilities) return false
        if (snapshot.preview != null && (!candidate.module.metadata.capabilities.preview ||
            snapshot.geometry != snapshot.preview.geometry)) return false
        val measurement = snapshot.measurement
        return measurement == null || (candidate.module.metadata.capabilities.temperatureMeasurement &&
            measurement.validity == MeasurementValidity.VALID && measurement.geometry == snapshot.geometry &&
            measurement.provenance.moduleId == candidate.module.metadata.id &&
            measurement.provenance.modelId == candidate.module.metadata.modelId &&
            measurement.geometry.contains(measurement.high.pixel) && measurement.geometry.contains(measurement.low.pixel))
    }
    private fun unavailable(previous: CameraSessionState<P>, lifecycle: CameraLifecycle, status: CameraStatus,
        error: CameraError? = null) = previous.copy(lifecycle = lifecycle, status = status, error = error,
        preview = null, measurement = null, actions = if (previous.module != null) setOf(CameraAction.CONNECT) else emptySet(),
        statistics = CameraStatistics())
}
