package org.lmthermal.camera.ht301

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import org.lmthermal.app.BuildConfig
import org.lmthermal.app.R
import org.lmthermal.camera.*
import org.lmthermal.core.CursorReading
import org.lmthermal.core.RadiometricSession
import org.lmthermal.core.UsbPhase

/** Registered resource namespace and numerical compatibility fields; no module name branching is needed in common UI. */
object Ht301UiBindings : CameraUiBindings {
    override val modelLabel = R.string.camera_ht301_model
    override val initializeLabel = R.string.camera_ht301_initialize_radiometric
    override val accuracyWarning = R.string.measurement_native_equivalent_warning
    override fun status(status: CameraStatus): Int? = if (status.code == CameraStatusCode.MEASUREMENT_UNAVAILABLE &&
        status.detail == Ht301SessionStatus.RADIOMETRIC_READY) R.string.camera_status_measurement_unavailable
        else when (status.detail as? Ht301SessionStatus) {
        Ht301SessionStatus.DISPLAY_STREAM -> R.string.camera_ht301_status_display_stream
        Ht301SessionStatus.SWITCHING_TO_RAW14 -> R.string.camera_ht301_status_switching_raw14
        Ht301SessionStatus.RAW14_UNSETTLED -> R.string.camera_ht301_status_raw14_unsettled
        Ht301SessionStatus.SHUTTER_TRANSIENT -> R.string.camera_ht301_status_shutter_transient
        Ht301SessionStatus.RADIOMETRIC_READY -> R.string.camera_ht301_status_ready
        else -> null
    }
    override fun sampleLabel(encodingId: String): Int =
        if (encodingId == "ht301.raw14") R.string.camera_ht301_raw14_sample else super.sampleLabel(encodingId)
    override fun measurementEvidence(measurement: ThermalMeasurement): Map<String, Any?> =
        (measurement as? Ht301ThermalMeasurement)?.evidence?.let {
            mapOf("trailer_center_c" to it.trailerCenter.celsius, "literal_center_c" to it.literalCenter.celsius,
                "warning" to org.lmthermal.core.NativeEquivalentThermometry.WARNING)
        } ?: emptyMap()
    override fun cursorEvidence(reading: CursorReading): Map<String, Any?> =
        if (reading.sample?.encodingId == "ht301.raw14") mapOf("raw14" to reading.sample!!.value) else emptyMap()
}

/** DEBUG-only protocol diagnostics retain richer source evidence/actions inside the HT-301 module boundary. */
@Composable
fun Ht301DiagnosticPanel(controller: Ht301CameraController) {
    if (!BuildConfig.DEBUG) return
    val source by controller.state.collectAsState()
    Column {
        Text(source.identity)
        Text(stringResource(R.string.camera_ht301_diagnostic_permission, source.permission))
        Text(stringResource(R.string.camera_ht301_diagnostic_transport, source.mode.name, source.size, source.range))
        Text(stringResource(R.string.camera_ht301_diagnostic_session, source.session.state.name,
            source.session.baseline, RadiometricSession.BASELINE_FRAMES, source.session.discarded,
            source.session.shutterFrames, RadiometricSession.SHUTTER_DISCARD, source.session.live, RadiometricSession.READY_LIVE))
        source.measurement?.let { Text(stringResource(R.string.camera_ht301_diagnostic_centers,
            it.trailerCenter.celsius, it.literalCenter.celsius)) }
        OutlinedButton(onClick = controller::readZoomInventory,
            enabled = source.usb.phase == UsbPhase.STREAMING && !source.session.active && !source.transition.active) {
            Text(stringResource(R.string.camera_ht301_zoom_inventory))
        }
        Text(source.inventory)
        OutlinedButton(onClick = controller::testRaw14Transition,
            enabled = source.transition.canStart && !source.session.active) { Text(stringResource(R.string.camera_ht301_raw14_test)) }
        Text(stringResource(R.string.camera_ht301_diagnostic_single, source.transition.stage,
            source.transition.discarded, source.transition.distinct))
    }
}
