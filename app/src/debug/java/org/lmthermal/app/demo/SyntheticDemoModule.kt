package org.lmthermal.app.demo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lmthermal.app.R
import org.lmthermal.camera.*
import org.lmthermal.camera.simulated.SimulatedCameraIdentity

/** Debug adapter reuses the same identity/module injection as device tests. It cannot match USB identities.
 * Acquisition stays bounded and off the UI thread; the normal coordinator owns close/reopen/background.
 */
class SyntheticDemoModule : CameraModule<Bitmap> {
    companion object {
        val identity = SimulatedCameraIdentity("debug-thermal-scene", "thermal-ux-demo")
        val ui = object : CameraUiBindings {
            override val modelLabel = R.string.camera_demo_model
            override val accuracyWarning = R.string.camera_demo_warning
        }
    }
    override val metadata = CameraModuleMetadata(CameraModuleId("debug-thermal-scene"), "analytic-scene-384x288-v1",
        CameraCapabilities(preview = true, temperatureMeasurement = true,
            explicitMeasurementInitialization = false, touchInspection = true), sourceOrigin = "simulated")

    override fun probe(device: CameraDeviceIdentity) = if (device == identity) CameraProbeResult.SUPPORTED else CameraProbeResult.UNSUPPORTED

    override suspend fun open(device: CameraDeviceIdentity): CameraSession<Bitmap> {
        require(probe(device) == CameraProbeResult.SUPPORTED)
        return Session(device)
    }

    private enum class Command { TOGGLE, STEP, RESET }
    private data class Playback(val paused: Boolean = false, val frame: Long = 0)

    /** No Activity is captured. Closing awaits this worker before the owner permits a replacement session. */
    private inner class Session(device: CameraDeviceIdentity) : AndroidCameraSession {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        private val commands = Channel<Command>(Channel.CONFLATED)
        private val playback = MutableStateFlow(Playback())
        private val latest = MutableStateFlow(CameraSessionState<Bitmap>(module = metadata, device = device,
            lifecycle = CameraLifecycle.OPENING, geometry = SyntheticScene.geometry, status = CameraStatus(CameraStatusCode.OPENING)))
        override val state = latest.asStateFlow()
        private val job = scope.launch {
            var current = Playback()
            var sequence = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val matrix = SyntheticScene.matrix(current.frame)
                val measurement = OwnedThermalMeasurement(SyntheticScene.geometry, matrix, ++sequence, now,
                    MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED))
                // Preview is only a fallback. The existing CelsiusPresenter creates all palette images.
                val pixels = IntArray(matrix.size) { index ->
                    val gray = ((matrix[index] - 10) / 30 * 255).toInt().coerceIn(0, 255)
                    (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
                val bitmap = Bitmap.createBitmap(pixels, SyntheticScene.geometry.width, SyntheticScene.geometry.height, Bitmap.Config.ARGB_8888)
                ensureActive()
                latest.value = latest.value.copy(lifecycle = CameraLifecycle.STREAMING,
                    status = CameraStatus(CameraStatusCode.MEASUREMENT_READY), measurement = measurement,
                    preview = CameraPreview(SyntheticScene.geometry, sequence, now, bitmap), actions = setOf(CameraAction.CLOSE),
                    statistics = CameraStatistics(callbackFps = if (current.paused) 0.0 else 4.0, received = sequence))
                playback.value = current
                val command = if (current.paused) commands.receive() else withTimeoutOrNull(SyntheticScene.FRAME_INTERVAL_MS) { commands.receive() }
                current = when (command) {
                    Command.TOGGLE -> current.copy(paused = !current.paused)
                    Command.STEP -> Playback(paused = true, frame = current.frame + 1)
                    Command.RESET -> Playback(paused = true, frame = 0)
                    null -> current.copy(frame = current.frame + 1)
                }
            }
        }
        override val diagnostics: @Composable () -> Unit = {
            val current by playback.collectAsState()
            Text(stringResource(R.string.camera_demo_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.camera_demo_frame, current.frame))
            Text(stringResource(R.string.camera_demo_reference))
            Button(onClick = { commands.trySend(Command.TOGGLE) }, modifier = Modifier.heightIn(min = 48.dp).testTag("demo-play-pause")) {
                Text(stringResource(if (current.paused) R.string.camera_demo_play else R.string.camera_demo_pause))
            }
            OutlinedButton(onClick = { commands.trySend(Command.STEP) }, modifier = Modifier.heightIn(min = 48.dp).testTag("demo-step")) {
                Text(stringResource(R.string.camera_demo_step))
            }
            OutlinedButton(onClick = { commands.trySend(Command.RESET) }, modifier = Modifier.heightIn(min = 48.dp).testTag("demo-reset")) {
                Text(stringResource(R.string.camera_demo_reset))
            }
        }
        override suspend fun perform(action: CameraAction) {
            require(action == CameraAction.CLOSE)
            close(CameraCloseReason.USER)
        }
        override suspend fun close(reason: CameraCloseReason) {
            scope.cancel(); job.join(); commands.close()
            latest.value = latest.value.copy(lifecycle = CameraLifecycle.CLOSED, status = CameraStatus(CameraStatusCode.CLOSED),
                preview = null, measurement = null, actions = emptySet(), statistics = CameraStatistics())
        }
    }
}
