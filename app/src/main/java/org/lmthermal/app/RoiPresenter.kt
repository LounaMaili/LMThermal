package org.lmthermal.app

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.lmthermal.camera.*
import org.lmthermal.core.*

enum class InspectionMode { POINT, ROI }

/** Measurement identity ties readings to the displayed frame, never to the palette bitmap's pixels. */
data class RoiPresentationSnapshot(val selection: NativeRoiSelection = NativeRoiSelection(),
    val measurement: ThermalMeasurement? = null, val statistics: RoiStatistics? = null, val calculationMs: Double = 0.0)

/** Shared gesture/worker identity; a drag from a replaced source must not select its successor. */
internal fun roiSourceKey(source: CameraSessionState<*>): RoiSourceKey? = source.module?.let {
    RoiSourceKey(it.id, it.modelId, source.device?.key)
}

/** Bounded latest-request analysis worker. Geometry persists independently of frame validity.
 * Palette/range are absent from the request; a render of the same measurement reuses its analysis.
 * Camera loss gates publication and the screen immediately; no previous ROI temperature can revive.
 */
class RoiPresenter(context: Context, private val camera: StateFlow<CameraSessionState<Bitmap>>,
    private val presentation: StateFlow<CelsiusPresentationSnapshot>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val mutableSelection = MutableStateFlow(NativeRoiSelection())
    val selection = mutableSelection.asStateFlow()
    private val mutableMode = MutableStateFlow(InspectionMode.POINT)
    val mode = mutableMode.asStateFlow()
    private val latest = LatestFrameState(RoiPresentationSnapshot())
    val state = latest.asStateFlow()
    private val evidence = NumericEvidence(context, "roi.jsonl", "LMThermalRoi")
    private var recording = false
    private var lastLog = 0L
    private var lastLoggedRect: NativeRect? = null
    private var previouslyAvailable = false

    private data class Request(val source: RoiSourceKey?, val geometry: NativeImageGeometry?,
        val selected: NativeRoiSelection, val measurement: ThermalMeasurement?)

    init {
        scope.launch {
            combine(camera, presentation, mutableSelection) { source, displayed, selected ->
                Request(roiSourceKey(source), source.geometry, selected,
                    displayed.measurement?.takeIf { compatible(source, it) })
            }.distinctUntilChanged().collectLatest { request ->
                val bound = request.selected.bind(request.source, request.geometry)
                if (bound != request.selected) {
                    mutableSelection.update { it.bind(request.source, request.geometry) }
                    return@collectLatest
                }
                val rect = request.selected.rect
                val measurement = request.measurement
                val start = SystemClock.elapsedRealtimeNanos()
                val statistics = if (rect == null || measurement == null) null else try {
                    request.selected.statistics(measurement)
                } catch (failure: IllegalArgumentException) {
                    Log.e("LMThermalRoi", "Invalid ROI measurement data", failure)
                    null
                }
                ensureActive()
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                val result = RoiPresentationSnapshot(request.selected, measurement, statistics, elapsed)
                var published = false
                latest.updateIf({ scope.isActive && mutableSelection.value == request.selected &&
                    roiSourceKey(camera.value) == request.source &&
                    (measurement == null || (compatible(camera.value, measurement) &&
                        presentation.value.measurement === measurement)) }) { published = true; result }
                if (!published) return@collectLatest
                val now = SystemClock.elapsedRealtime()
                if (rect != lastLoggedRect || (statistics != null) != previouslyAvailable ||
                    (rect != null && now - lastLog >= 2000)) {
                    if (!recording && rect != null) { evidence.reset(); recording = true }
                    if (recording) evidence.record(mapOf("event" to "roi", "sequence" to measurement?.sequence,
                        "module_id" to request.source?.moduleId?.value,
                        "native_width" to request.geometry?.width, "native_height" to request.geometry?.height,
                        "bounds" to rect?.let { listOf(it.x1, it.y1, it.x2, it.y2) },
                        "method" to RoiStatistics.METHOD, "calculation_ms" to elapsed,
                        "statistics" to statistics?.let { mapOf("pixel_count" to it.pixelCount,
                            "valid_pixel_count" to it.validPixelCount, "min_c" to it.minC, "max_c" to it.maxC,
                            "mean_c" to it.meanC, "min_xy" to it.minPixel?.let { p -> listOf(p.x, p.y) },
                            "max_xy" to it.maxPixel?.let { p -> listOf(p.x, p.y) }) },
                        "provenance" to measurement?.provenance?.kind?.name,
                        "callback_fps" to camera.value.statistics.callbackFps, "monotonic_ms" to now))
                    lastLog = now; lastLoggedRect = rect; previouslyAvailable = statistics != null
                }
            }
        }
    }
    fun setMode(mode: InspectionMode) { mutableMode.value = mode }
    /** The gesture supplies strict native bounds; this action cannot reach a camera command. */
    fun select(rect: NativeRect) {
        select(rect, roiSourceKey(camera.value), camera.value.geometry)
    }
    /** Bind a live gesture to its original source/geometry, including during replacement/recomposition races. */
    fun select(rect: NativeRect, expectedSource: RoiSourceKey?, expectedGeometry: NativeImageGeometry?) {
        val source = camera.value
        if (!CameraUiPolicy.canInspect(source) || expectedSource == null || expectedGeometry == null ||
            roiSourceKey(source) != expectedSource || source.geometry != expectedGeometry) return
        mutableSelection.update { it.bind(expectedSource, expectedGeometry).select(rect) }
    }
    fun clear() { mutableSelection.update { it.clear() } }
    fun dispose() { scope.cancel(); latest.value = RoiPresentationSnapshot() }

    /** A completed render may lag the newest frame, but must still belong to the active measurable source. */
    private fun compatible(source: CameraSessionState<*>, measurement: ThermalMeasurement): Boolean =
        source.lifecycle == CameraLifecycle.STREAMING && CameraUiPolicy.canInspect(source) &&
            source.geometry == measurement.geometry && source.module?.id == measurement.provenance.moduleId &&
            source.module?.modelId == measurement.provenance.modelId && measurement.validity == MeasurementValidity.VALID
}
