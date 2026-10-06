package org.lmthermal.camera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lmthermal.app.R
import org.lmthermal.app.NumericEvidence
import android.os.SystemClock
import org.lmthermal.core.CursorReading

/** Application-level discovery/selection and one-session ownership.
 * Android USB is one discovery provider; test injection supplies non-USB identities without loading a driver.
 * Attach/foreground only probe. Permissions and opening remain tied to an explicit, exact Connect candidate.
 */
class AndroidCameraCoordinator(private val context: Context,
    private val registrations: List<AndroidModuleRegistration> = defaultCameraModules(context),
    private val devices: (() -> List<CameraDeviceIdentity>)? = null) {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registry = CameraModuleRegistry(registrations.map { it.module })
    private val mutableDiagnostics = MutableStateFlow<(@Composable () -> Unit)?>(null)
    val diagnostics = mutableDiagnostics.asStateFlow()
    private val evidence = NumericEvidence(context, "camera-lifetime.jsonl", "LMThermalLifetime").apply { reset() }
    private val ownerId = java.util.UUID.randomUUID().toString()
    private var ownership: Map<String, Any?> = emptyMap()
    private val owner = CameraSessionOwner<Bitmap>(scope,
        activeChanged = { mutableDiagnostics.value = (it as? AndroidCameraSession)?.diagnostics },
        technicalFailure = { Log.e("LMThermalModules", "Module ownership/open failure", it) },
        event = { ownership = it; evidence.record(it + mapOf("owner_id" to ownerId, "monotonic_ms" to SystemClock.elapsedRealtime())) })
    val state = owner.state
    private var foreground = false
    /** Retained UI policy can bind screen-off without holding an Activity reference. */
    internal var screenOff: () -> Unit = { leaveForeground() }
    /** Lifecycle evidence includes acquisition continuity, without module protocol assumptions or private IDs. */
    fun recordLifecycle(name: String) {
        val current = state.value
        evidence.record(ownership + mapOf("owner_id" to ownerId, "event" to name, "monotonic_ms" to SystemClock.elapsedRealtime(),
            "lifecycle" to current.lifecycle.name, "status" to current.status.code.name,
            "session_state" to current.status.detail?.machineCode,
            "received" to current.statistics.received, "callback_fps" to current.statistics.callbackFps))
    }
    private var pendingCameraPermission: CameraCandidate<Bitmap>? = null
    private val fallbackUi = object : CameraUiBindings { override val modelLabel = R.string.app_name }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) { screenOff(); return }
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                if (device != null) {
                    if (pendingCameraPermission?.device?.key == device.deviceName) pendingCameraPermission = null
                    owner.detached(device.deviceName)
                }
            }
            discover()
        }
    }
    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    private fun identities(): List<CameraDeviceIdentity> = devices?.invoke() ?: manager.deviceList.values.map {
        UsbCameraIdentity(it.deviceName, it.vendorId, it.productId)
    }
    fun enterForeground() { foreground = true; discover() }
    fun leaveForeground() { foreground = false; pendingCameraPermission = null; mutableDiagnostics.value = null; owner.background() }
    fun discover() { owner.detected(registry.select(identities())) }

    /** Return true only when this explicit supported Connect needs Android CAMERA authorization. */
    fun connect(): Boolean {
        if (!foreground || state.value.lifecycle in setOf(CameraLifecycle.OPENING, CameraLifecycle.PERMISSION_PENDING, CameraLifecycle.STREAMING)) return false
        val selection = registry.select(identities())
        if (selection !is CameraSelection.Selected) { owner.detected(selection); return false }
        val candidate = selection.candidate
        val registration = registrations.single { it.module.metadata.id == candidate.module.metadata.id }
        if (registration.requiresCameraPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingCameraPermission = candidate
            owner.permissionRequired(candidate)
            return true
        }
        owner.open(candidate)
        return false
    }
    /** Permission response cannot switch target or revive a cancelled/backgrounded Connect. */
    fun cameraPermissionResult(granted: Boolean) {
        val candidate = pendingCameraPermission ?: return
        pendingCameraPermission = null
        if (!foreground) return
        val selection = registry.select(identities())
        if (selection !is CameraSelection.Selected || selection.candidate.device != candidate.device ||
            selection.candidate.module.metadata.id != candidate.module.metadata.id) {
            owner.detectedAfterPermission(selection); return
        }
        if (granted) owner.open(candidate) else owner.permissionDenied(candidate)
    }
    fun close() { pendingCameraPermission = null; mutableDiagnostics.value = null; owner.close() }
    fun perform(action: CameraAction) { owner.perform(action) }
    fun uiFor(id: CameraModuleId?): CameraUiBindings = registrations.firstOrNull { it.module.metadata.id == id }?.ui ?: fallbackUi
    fun measurementEvidence(measurement: ThermalMeasurement): Map<String, Any?> =
        uiFor(measurement.provenance.moduleId).measurementEvidence(measurement)
    fun cursorEvidence(moduleId: CameraModuleId?, reading: CursorReading): Map<String, Any?> =
        uiFor(moduleId).cursorEvidence(reading)
    /** Awaited close completes before cancelling the owner scope; native release cannot be orphaned. */
    fun dispose() {
        foreground = false; pendingCameraPermission = null; mutableDiagnostics.value = null
        context.unregisterReceiver(receiver)
        owner.close(CameraCloseReason.DISPOSED).invokeOnCompletion { scope.cancel() }
    }
}
