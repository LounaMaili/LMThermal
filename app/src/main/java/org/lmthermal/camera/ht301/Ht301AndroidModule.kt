package org.lmthermal.camera.ht301

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.lmthermal.camera.*
import org.lmthermal.core.*

/** First real integrated module. It adapts the validated worker instead of rewriting transport/session arithmetic. */
class Ht301AndroidModule(private val context: Context) : CameraModule<Bitmap> {
    override val metadata = Ht301ModuleProfile.metadata
    override fun probe(device: CameraDeviceIdentity) = Ht301ModuleProfile.probe(device)
    override suspend fun open(device: CameraDeviceIdentity): CameraSession<Bitmap> {
        require(probe(device) == CameraProbeResult.SUPPORTED)
        val target = context.getSystemService(UsbManager::class.java).deviceList[device.key]
            ?: error("Selected HT-301 USB device is no longer attached")
        return Ht301ModuleSession(Ht301CameraController(context, target), device)
    }
}

/** Protocol state is private to the module; richer immutable measurement evidence is preserved by its adapter. */
class Ht301ModuleSession(private val controller: Ht301CameraController,
    private val device: CameraDeviceIdentity) : AndroidCameraSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    init { controller.enterForeground(); controller.connect() }
    override val state = controller.state.map { adapt(it) }.stateIn(scope, SharingStarted.Eagerly, adapt(controller.state.value))
    override val diagnostics: @Composable () -> Unit = { Ht301DiagnosticPanel(controller) }
    override suspend fun perform(action: CameraAction) {
        require(action == CameraAction.INITIALIZE_MEASUREMENT)
        controller.initializeRadiometric()
    }
    override suspend fun close(reason: CameraCloseReason) { controller.shutdown(reason); scope.cancel() }

    /** Metadata mapping does not reinterpret frames, change readiness or infer physical calibration. */
    private fun adapt(source: Ht301CameraSnapshot): CameraSessionState<Bitmap> {
        val lifecycle = when (source.usb.phase) {
            UsbPhase.ABSENT -> CameraLifecycle.DISCONNECTED
            UsbPhase.ATTACHED -> CameraLifecycle.DETECTED
            UsbPhase.PERMISSION_PENDING -> CameraLifecycle.PERMISSION_PENDING
            UsbPhase.PERMISSION_DENIED, UsbPhase.ERROR -> CameraLifecycle.ERROR
            UsbPhase.OPENING -> CameraLifecycle.OPENING
            UsbPhase.STREAMING -> CameraLifecycle.STREAMING
            UsbPhase.CLOSED -> CameraLifecycle.CLOSED
        }
        val statusCode = when {
            lifecycle == CameraLifecycle.DISCONNECTED -> CameraStatusCode.NO_CAMERA
            lifecycle == CameraLifecycle.DETECTED -> CameraStatusCode.DETECTED
            lifecycle == CameraLifecycle.PERMISSION_PENDING -> CameraStatusCode.PERMISSION_REQUIRED
            lifecycle == CameraLifecycle.OPENING -> CameraStatusCode.OPENING
            lifecycle == CameraLifecycle.ERROR -> CameraStatusCode.ERROR
            lifecycle == CameraLifecycle.CLOSED -> CameraStatusCode.CLOSED
            source.measurement != null -> CameraStatusCode.MEASUREMENT_READY
            source.session.active -> CameraStatusCode.INITIALIZING
            source.mode == FrameMode.DISPLAY -> CameraStatusCode.PREVIEW_ONLY
            else -> CameraStatusCode.MEASUREMENT_UNAVAILABLE
        }
        val error = when (source.usb.phase) {
            UsbPhase.PERMISSION_DENIED -> CameraError(CameraErrorCode.PERMISSION_DENIED)
            UsbPhase.ERROR -> CameraError(CameraErrorCode.STREAM_FAILED)
            else -> null
        }
        val actions = buildSet {
            if (lifecycle in setOf(CameraLifecycle.OPENING, CameraLifecycle.PERMISSION_PENDING, CameraLifecycle.STREAMING)) add(CameraAction.CLOSE)
            else add(CameraAction.CONNECT)
            if (lifecycle == CameraLifecycle.STREAMING && source.session.canInitialize && !source.transition.active)
                add(CameraAction.INITIALIZE_MEASUREMENT)
        }
        val preview = source.bitmap?.let {
            check(it.width == Ht301ModuleProfile.GEOMETRY.width && it.height == Ht301ModuleProfile.GEOMETRY.height)
            CameraPreview(Ht301ModuleProfile.GEOMETRY, source.sequence, source.receivedMonotonicMs, it)
        }
        return CameraSessionState(module = Ht301ModuleProfile.metadata, device = device, lifecycle = lifecycle,
            geometry = Ht301ModuleProfile.GEOMETRY,
            status = CameraStatus(statusCode, if (lifecycle == CameraLifecycle.STREAMING)
                Ht301SessionStatus.valueOf(source.session.state.name) else null),
            error = error, preview = preview, measurement = source.measurement?.let(::Ht301ThermalMeasurement),
            actions = actions, statistics = CameraStatistics(source.fps, source.received, source.replaced, source.malformed))
    }
}

/** Namespaced enum mirrors existing session machine codes; translated descriptions are registered separately. */
enum class Ht301SessionStatus : ModuleStatusIdentifier {
    DISCONNECTED, DISPLAY_STREAM, SWITCHING_TO_RAW14, RAW14_UNSETTLED, SHUTTER_TRANSIENT, RADIOMETRIC_READY, ERROR;
    override val moduleId get() = Ht301ModuleProfile.ID
    override val machineCode get() = name
}
