package org.lmthermal.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.lmthermal.core.*

/** Immutable UI snapshot; bitmap is created once on the worker and never mutated thereafter. */
data class CameraSnapshot(
    val usb: UsbState = UsbState(),
    val identity: String = "1514:0001 — not attached",
    val permission: String = "Not requested",
    val mode: FrameMode = FrameMode.INVALID,
    val reason: String? = null,
    val size: Int = 0,
    val range: String = "—",
    val fps: Double = 0.0,
    val received: Long = 0,
    val invalid: Long = 0,
    val replaced: Long = 0,
    val malformed: Long = 0,
    val bitmap: Bitmap? = null,
)

/** Owns discovery, permission and transport lifetime. No camera mode controls or thermometry.
 * Native read/close run on one dispatcher: cancellation cannot free a handle during a JNI read.
 * StateFlow has one current snapshot; the native callback also has a one-frame replacement slot.
 */
class CameraController(private val context: Context) {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val mutableState = LatestFrameState(CameraSnapshot())
    val state = mutableState.asStateFlow()
    private var streamJob: Job? = null
    private var foreground = false
    @Volatile private var generation = 0L
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private var wantedDevice: String? = null

    /** System attach/detach are notifications; only an explicit Connect action starts acquisition. */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device?.deviceName == wantedDevice) close("Camera unplugged")
                    discover()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> discover()
                permissionAction -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device == null || device.deviceName != wantedDevice || !foreground) return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && manager.hasPermission(device))
                        open(device)
                    else mutableState.value = mutableState.value.copy(
                        usb = UsbState(UsbPhase.PERMISSION_DENIED, "USB permission denied"), permission = "Denied")
                }
            }
        }
    }
    init {
        // USB broadcasts include framework-owned UsbDevice extras; permission PendingIntent is package-scoped.
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    /** Enumerate only the validated VID/PID; avoid opening unrelated webcams. */
    private fun camera(): UsbDevice? = manager.deviceList.values.firstOrNull {
        it.vendorId == Ht301Layout.VID && it.productId == Ht301Layout.PID
    }
    /** Recheck already-attached devices on foreground/recreation without taking ownership automatically. */
    fun enterForeground() { foreground = true; discover() }
    /** Background releases bandwidth and fd; returning requires an explicit Connect. */
    fun leaveForeground() { foreground = false; close("Closed in background") }
    /** Read-only discovery leaves an active stream intact. */
    fun discover() {
        if (streamJob?.isActive == true) return
        val device = camera()
        mutableState.value = if (device == null) CameraSnapshot() else CameraSnapshot(
            usb = UsbState().attached(), identity = "${device.manufacturerName ?: "Infiray"} ${device.productName ?: "HT-301"} · 1514:0001",
            permission = if (manager.hasPermission(device)) "Granted" else "Not requested")
    }
    /** Android 9+ requires CAMERA permission as well as the per-device USB permission for UVC. */
    fun connect() {
        if (!foreground || streamJob?.isActive == true) return
        val device = camera() ?: run { discover(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = mutableState.value.copy(permission = "Camera permission required")
            return
        }
        wantedDevice = device.deviceName
        if (manager.hasPermission(device)) open(device) else {
            mutableState.value = mutableState.value.copy(
                usb = UsbState(UsbPhase.PERMISSION_PENDING, "Awaiting Android USB permission"), permission = "Pending")
            manager.requestPermission(device, PendingIntent.getBroadcast(context, 0,
                Intent(permissionAction).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }
    /** Report denial without bypassing Android authorization. */
    fun cameraPermissionDenied() {
        mutableState.value = mutableState.value.copy(usb = UsbState(UsbPhase.PERMISSION_DENIED, "Camera permission denied"), permission = "Denied")
    }
    /** Worker owns transport and finally-release; stale work from an earlier connection cannot update UI. */
    private fun open(device: UsbDevice) {
        val token = ++generation
        streamJob?.cancel()
        mutableState.value = mutableState.value.copy(usb = UsbState(UsbPhase.OPENING, "Opening read-only YUYV stream"), permission = "Granted")
        streamJob = scope.launch {
            try {
                val connection = manager.openDevice(device) ?: error("UsbManager.openDevice returned null")
                NativeUvcTransport(connection).use { transport ->
                    var validationSaved = false
                    var invalid = 0L
                    var previousCount = 0L
                    var fps = 0.0
                    var cadenceStart = SystemClock.elapsedRealtime()
                    var lastFrameAt = cadenceStart
                    var lastLog = cadenceStart
                    while (isActive && token == generation) {
                        val bytes = transport.read()
                        ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        if (bytes == null) {
                            if (now - lastFrameAt > 5000) error("No UVC payload for 5 seconds; close/reconnect")
                            continue
                        }
                        lastFrameAt = now
                        val counters = transport.statistics()
                        if (now - cadenceStart >= 1000) {
                            fps = (counters[0] - previousCount) * 1000.0 / (now - cadenceStart)
                            previousCount = counters[0]; cadenceStart = now
                        }
                        val frame = if (bytes.size == Ht301Layout.FRAME_BYTES) Ht301Frame.parse(bytes) else null
                        val inspection = frame?.inspect() ?: FrameInspection(FrameMode.INVALID, "transport_size", null, null)
                        // One app-private debug payload permits byte-for-byte host verification of trailer preservation.
                        // Never write these unsanitized camera bytes to Git or shared storage.
                        if (BuildConfig.DEBUG && frame != null && inspection.mode != FrameMode.INVALID &&
                            inspection.reason == null && !validationSaved) {
                            context.filesDir.resolve("validation-frame.raw").writeBytes(frame.transportBytes())
                            validationSaved = true
                        }
                        if (inspection.mode == FrameMode.INVALID || inspection.reason != null) invalid++
                        val image = if (frame != null && inspection.mode != FrameMode.INVALID) Bitmap.createBitmap(
                            PreviewRenderer.grayscale(frame, inspection), Ht301Layout.WIDTH, Ht301Layout.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888) else null
                        mutableState.updateIf({ token == generation }) { previous -> previous.copy(
                            usb = UsbState(UsbPhase.STREAMING, "Streaming · no radiometric readiness claimed"),
                            mode = inspection.mode, reason = inspection.reason, size = bytes.size,
                            range = "${inspection.minimum ?: "—"}..${inspection.maximum ?: "—"}",
                            fps = fps, received = counters[0], invalid = invalid, replaced = counters[1], malformed = counters[2], bitmap = image) }
                        if (now - lastLog >= 2000) {
                            // Numeric diagnostics only: no camera serial or private scene is written to logs.
                            Log.i("LMThermal", "frame size=${bytes.size} image=${frame?.imageBytes()?.size} trailer=${frame?.trailerBytes()?.size} mode=${inspection.mode} reason=${inspection.reason} summary=${inspection.summaryValid} words=${inspection.minimum}..${inspection.maximum} fps=$fps received=${counters[0]} replaced=${counters[1]} malformed=${counters[2]}")
                            lastLog = now
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("LMThermal", "Transport failed", error)
                mutableState.updateIf({ token == generation }) { previous -> previous.copy(
                    usb = UsbState(UsbPhase.ERROR, error.message ?: "Transport error"), bitmap = null) }
            } finally { Log.i("LMThermal", "Stream released generation=$token") }
        }
    }
    /** Immediately clear stale presentation; worker cancellation releases the native handle asynchronously. */
    fun close(message: String = "Closed") {
        generation++
        streamJob?.cancel(); streamJob = null
        wantedDevice = null
        // Closing the stream does not revoke USB authorization or physically detach the device.
        // A detach notification calls discover() afterward to clear its identity/permission.
        val previous = mutableState.value
        mutableState.value = CameraSnapshot(usb = UsbState(UsbPhase.CLOSED, message),
            identity = previous.identity, permission = previous.permission)
    }
    /** Final ViewModel release unregisters discovery and cancels work without blocking the UI thread. */
    fun dispose() { close(); context.unregisterReceiver(receiver); scope.cancel() }
}
