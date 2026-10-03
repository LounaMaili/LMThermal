package org.lmthermal.app

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.lmthermal.core.*

/** One completed render and its source measurement are published together, never mixed between frames. */
data class CelsiusPresentationSnapshot(val measurement: RadiometricMeasurement? = null,
    val bitmap: Bitmap? = null, val legend: Bitmap? = null,
    val range: CelsiusRange? = null, val palette: CelsiusPalette? = null, val renderMs: Double = 0.0)

/** Presentation-only, cancellable latest-request worker; never executes transport or thermometry.
 * combine/collectLatest keep one in-flight render and replace obsolete pending inputs.
 * A generation/source check rejects work completed after a camera/settings change.
 */
class CelsiusPresenter(context: Context, private val camera: StateFlow<CameraSnapshot>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val mutableSettings = MutableStateFlow(CelsiusPresentationSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutablePixel = MutableStateFlow<NativePixel?>(null)
    val pixel = mutablePixel.asStateFlow()
    private val latest = LatestFrameState(CelsiusPresentationSnapshot())
    val state = latest.asStateFlow()
    private val evidence = NumericEvidence(context, "presentation.jsonl", "LMThermalPresentation")
    private var recording = false
    private var lastLog = 0L
    private var previousAvailable = false
    private var previousSettings = mutableSettings.value
    private var skipped = 0L

    init {
        scope.launch {
            combine(camera, mutableSettings) { source, settings -> source to settings }.collectLatest { (source, settings) ->
                val measurement = source.measurement
                if (measurement == null) {
                    latest.value = CelsiusPresentationSnapshot()
                    if (previousAvailable) evidence.record(mapOf("event" to "presentation_unavailable",
                        "session_state" to source.session.state.name, "reason" to source.measurementReason,
                        "monotonic_ms" to SystemClock.elapsedRealtime(), "celsius_legend" to false, "cursor_celsius" to null))
                    previousAvailable = false
                    return@collectLatest
                }
                val start = SystemClock.elapsedRealtimeNanos()
                val rendered = withContext(Dispatchers.Default) {
                    val colors = CelsiusRenderer.render(measurement, settings)
                    ensureActive()
                    val bitmap = Bitmap.createBitmap(colors.argb(), Ht301Layout.WIDTH, Ht301Layout.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
                    val legend = Bitmap.createBitmap(colors.legend(), 256, 1, Bitmap.Config.ARGB_8888)
                    CelsiusPresentationSnapshot(measurement, bitmap, legend, colors.range, colors.palette,
                        (SystemClock.elapsedRealtimeNanos() - start) / 1e6)
                }
                // Rendering cannot restore stale Celsius after invalid/detached/closed state.
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
                        "palette" to settings.palette.label, "automatic" to settings.automatic,
                        "range" to listOf(rendered.range!!.lower, rendered.range.upper),
                        "matrix_range" to listOf(measurement.matrixMinimum, measurement.matrixMaximum),
                        "trailer_center_c" to measurement.trailerCenter.celsius,
                        "literal_center_c" to measurement.literalCenter.celsius,
                        "high_c" to measurement.high.celsius, "high_xy" to listOf(measurement.high.x, measurement.high.y),
                        "low_c" to measurement.low.celsius, "low_xy" to listOf(measurement.low.x, measurement.low.y),
                        "cursor" to cursorFields(CursorInspection.read(mutablePixel.value, measurement)),
                        "callback_fps" to source.fps, "replaced" to source.replaced,
                        "malformed" to source.malformed, "superseded_completed_renders" to skipped,
                        "warning" to NativeEquivalentThermometry.WARNING))
                    lastLog = now
                }
                previousAvailable = true; previousSettings = settings
            }
        }
    }
    /** Changing palette/range only changes a render request, never the measurement/camera. */
    fun setPalette(palette: CelsiusPalette) { mutableSettings.value = mutableSettings.value.copy(palette = palette) }
    fun setAutomatic(automatic: Boolean) { mutableSettings.value = mutableSettings.value.copy(automatic = automatic) }
    /** Caller must construct validated finite bounds before applying; invalid text does not affect current rendering. */
    fun setLocked(range: CelsiusRange) { mutableSettings.value = mutableSettings.value.copy(locked = range, automatic = false) }
    /** Store native coordinates. Displayed readings are recalculated from the latest completed measurement. */
    fun select(pixel: NativePixel) {
        if (mutablePixel.value == pixel) return
        mutablePixel.value = pixel
        val selectedAt = SystemClock.elapsedRealtime()
        val displayed = if (camera.value.measurement != null) state.value.measurement else null
        val cursor = CursorInspection.read(pixel, displayed)
        scope.launch { evidence.record(mapOf("event" to "touch", "sequence" to displayed?.sequence,
            "monotonic_ms" to selectedAt, "native_xy" to listOf(pixel.x, pixel.y),
            "reading" to cursorFields(cursor))) }
    }
    private fun cursorFields(reading: CursorReading?): Map<String, Any>? = reading?.let {
        mapOf("x" to it.pixel.x, "y" to it.pixel.y, "raw14" to it.raw14, "celsius" to it.celsius)
    }
    /** The camera ViewModel owns this worker; it cannot outlive the source or revive a closed frame. */
    fun dispose() { scope.cancel(); latest.value = CelsiusPresentationSnapshot() }
}
