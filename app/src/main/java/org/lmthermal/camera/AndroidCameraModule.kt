package org.lmthermal.camera

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import org.lmthermal.app.R
import org.lmthermal.camera.ht301.Ht301AndroidModule
import org.lmthermal.camera.ht301.Ht301UiBindings
import org.lmthermal.camera.simulated.SimulatedCameraModule
import org.lmthermal.core.CursorReading

/** Android-specific resource/diagnostic extension; stable model IDs and core data never contain translated labels. */
interface CameraUiBindings {
    val modelLabel: Int
    val initializeLabel: Int get() = R.string.camera_initialize_measurement
    val accuracyWarning: Int? get() = null
    fun status(status: CameraStatus): Int? = null
    fun sampleLabel(encodingId: String): Int = R.string.measurement_native_sample
    fun measurementEvidence(measurement: ThermalMeasurement): Map<String, Any?> = emptyMap()
    fun cursorEvidence(reading: CursorReading): Map<String, Any?> = emptyMap()
}

interface AndroidCameraSession : CameraSession<Bitmap> {
    val diagnostics: (@Composable () -> Unit)?
}

/** Composition root registers drivers and their localized UI binding together. No registry entry is a fallback. */
data class AndroidModuleRegistration(val module: CameraModule<Bitmap>, val ui: CameraUiBindings,
    val requiresCameraPermission: Boolean = false)

fun defaultCameraModules(context: android.content.Context): List<AndroidModuleRegistration> = listOf(
    AndroidModuleRegistration(Ht301AndroidModule(context), Ht301UiBindings, requiresCameraPermission = true),
    AndroidModuleRegistration(SimulatedCameraModule(previewFactory = { image ->
        Bitmap.createBitmap(image.pixels(), image.geometry.width, image.geometry.height, Bitmap.Config.ARGB_8888)
    }, clock = android.os.SystemClock::elapsedRealtime), object : CameraUiBindings { override val modelLabel = R.string.camera_simulated_model })
)
