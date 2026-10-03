package org.lmthermal.app

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.lmthermal.camera.*
import org.lmthermal.core.CelsiusPalette
import org.lmthermal.core.CelsiusPresentationSettings
import org.lmthermal.core.CelsiusRange
import org.lmthermal.core.CelsiusRenderer
import org.lmthermal.core.CursorInspection
import org.lmthermal.core.CursorReading
import org.lmthermal.core.LatestFrameState
import org.lmthermal.core.NativePixel

/** A completed render and its generic source measurement are published together; no protocol data is required. */
data class CelsiusPresentationSnapshot(val measurement: ThermalMeasurement? = null,
    val bitmap: Bitmap? = null, val legend: Bitmap? = null,
    val range: CelsiusRange? = null, val palette: CelsiusPalette? = null, val renderMs: Double = 0.0,
    val error: CameraErrorCode? = null)

/** Cancellable latest-request presentation worker, independent of transport/session/thermometry.
 * Geometry comes from measurement data. Registered module callbacks preserve optional numerical diagnostics.
 */
class CelsiusPresenter(context: Context, private val camera: StateFlow<CameraSessionState<Bitmap>>,
    private val measurementEvidence: (ThermalMeasurement) -> Map<String, Any?> = { emptyMap() },
    private val cursorEvidence: (CameraModuleId?, CursorReading) -> Map<String, Any?> = { _, _ -> emptyMap() }) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val mutableSettings = MutableStateFlow(CelsiusPresentationSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutablePixel = MutableStateFlow<NativePixel?>(null)
    val pixel = mutablePixel.asStateFlow()
    private val latest = LatestFrameState(CelsiusPresentationSnapshot())
    val state = latest.asStateFlow()
    val roi = RoiPresenter(context, camera, state)
    private val evidence = NumericEvidence(context, "presentation.jsonl", "LMThermalPresentation")
    private var recording = false
    private var lastLog = 0L
    private var previousAvailable = false
    private var previousSettings = mutableSettings.value
    private var selectedModule: CameraModuleId? = null
    private var skipped = 0L

    init {
        scope.launch {
            combine(camera, mutableSettings) { source, settings -> source to settings }.collectLatest { (source, settings) ->
                source.module?.id?.let { id ->
                    if (selectedModule != null && id != selectedModule) mutablePixel.value = null
                    selectedModule = id
                }
                val measurement = source.measurement
                if (measurement == null) {
                    latest.value = CelsiusPresentationSnapshot()
                    if (previousAvailable) evidence.record(mapOf("event" to "presentation_unavailable",
                        "session_state" to (source.status.detail?.machineCode ?: source.lifecycle.name),
                        "reason" to "Session not ready", "reason_code" to source.status.code.name,
                        "module_id" to source.module?.id?.value,
                        "monotonic_ms" to SystemClock.elapsedRealtime(), "celsius_legend" to false, "cursor_celsius" to null))
                    previousAvailable = false
                    return@collectLatest
                }
                val start = SystemClock.elapsedRealtimeNanos()
                val rendered = try {
                    withContext(Dispatchers.Default) {
                        val colors = CelsiusRenderer.render(measurement, settings)
                        ensureActive()
                        val bitmap = Bitmap.createBitmap(colors.argb(), measurement.geometry.width, measurement.geometry.height, Bitmap.Config.ARGB_8888)
                        val legend = Bitmap.createBitmap(colors.legend(), 256, 1, Bitmap.Config.ARGB_8888)
                        CelsiusPresentationSnapshot(measurement, bitmap, legend, colors.range, colors.palette,
                            (SystemClock.elapsedRealtimeNanos() - start) / 1e6)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    Log.e("LMThermalPresentation", "Module presentation data rejected", failure)
                    latest.value = CelsiusPresentationSnapshot(error = CameraErrorCode.MODULE_DATA_INVALID)
                    return@collectLatest
                }
                if (!scope.isActive || camera.value !== source || mutableSettings.value != settings) { skipped++; return@collectLatest }
                var published = false
                latest.updateIf({ scope.isActive && camera.value === source && mutableSettings.value == settings }) {
                    published = true; rendered
                }
                if (!published) { skipped++; return@collectLatest }
                if (!recording) { evidence.reset(); recording = true }
                val now = SystemClock.elapsedRealtime()
                if (!previousAvailable || previousSettings != settings || now - lastLog >= 2000) {
                    evidence.record(mapOf("event" to "render", "sequence" to measurement.sequence,
                        "monotonic_ms" to now, "render_ms" to rendered.renderMs,
                        "palette" to PaletteResources.evidenceName(settings.palette), "palette_id" to settings.palette.name,
                        "automatic" to settings.automatic, "range" to listOf(rendered.range!!.lower, rendered.range.upper),
                        "matrix_range" to listOf(measurement.matrixMinimum, measurement.matrixMaximum),
                        "high_c" to measurement.high.celsius, "high_xy" to listOf(measurement.high.pixel.x, measurement.high.pixel.y),
                        "low_c" to measurement.low.celsius, "low_xy" to listOf(measurement.low.pixel.x, measurement.low.pixel.y),
                        "cursor" to cursorFields(CursorInspection.read(mutablePixel.value, measurement), measurement.provenance.moduleId),
                        "module_id" to measurement.provenance.moduleId.value, "camera_model" to measurement.provenance.modelId,
                        "native_width" to measurement.geometry.width, "native_height" to measurement.geometry.height,
                        "capabilities" to mapOf("preview" to source.capabilities?.preview,
                            "temperature_measurement" to source.capabilities?.temperatureMeasurement,
                            "explicit_measurement_initialization" to source.capabilities?.explicitMeasurementInitialization,
                            "touch_inspection" to source.capabilities?.touchInspection),
                        "callback_fps" to source.statistics.callbackFps, "replaced" to source.statistics.replaced,
                        "malformed" to source.statistics.malformed, "superseded_completed_renders" to skipped) +
                        measurementEvidence(measurement))
                    lastLog = now
                }
                previousAvailable = true; previousSettings = settings
            }
        }
    }
    /** These methods alter presentation only; no module action or measurement input is touched. */
    fun setPalette(palette: CelsiusPalette) { mutableSettings.value = mutableSettings.value.copy(palette = palette) }
    fun setAutomatic(automatic: Boolean) { mutableSettings.value = mutableSettings.value.copy(automatic = automatic) }
    fun setLocked(range: CelsiusRange) { mutableSettings.value = mutableSettings.value.copy(locked = range, automatic = false) }
    /** Retain a geometry-validated pixel while current readings follow completed live measurements. */
    fun select(pixel: NativePixel) {
        val geometry = camera.value.geometry ?: return
        if (!geometry.contains(pixel) || mutablePixel.value == pixel) return
        mutablePixel.value = pixel
        val selectedAt = SystemClock.elapsedRealtime()
        val displayed = if (camera.value.measurement != null) state.value.measurement else null
        val cursor = CursorInspection.read(pixel, displayed)
        scope.launch { evidence.record(mapOf("event" to "touch", "sequence" to displayed?.sequence,
            "module_id" to displayed?.provenance?.moduleId?.value, "monotonic_ms" to selectedAt,
            "native_xy" to listOf(pixel.x, pixel.y), "reading" to cursorFields(cursor, displayed?.provenance?.moduleId))) }
    }
    private fun cursorFields(reading: CursorReading?, moduleId: CameraModuleId?): Map<String, Any?>? = reading?.let {
        mapOf("x" to it.pixel.x, "y" to it.pixel.y, "celsius" to it.celsius,
            "sample" to it.sample?.let { sample -> mapOf("encoding_id" to sample.encodingId, "value" to sample.value) }) +
            cursorEvidence(moduleId, it)
    }
    fun dispose() { roi.dispose(); scope.cancel(); latest.value = CelsiusPresentationSnapshot() }
}
