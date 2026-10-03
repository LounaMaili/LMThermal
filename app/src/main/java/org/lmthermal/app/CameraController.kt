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
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
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
    val session: SessionSnapshot = SessionSnapshot(),
    val inventory: String = "Not requested",
    val transition: TransitionSnapshot = TransitionSnapshot(),
    val measurement: RadiometricMeasurement? = null,
    val measurementReason: String? = "Session not ready",
)

/** Owns discovery, permission and transport lifetime. Explicit session controls remain separate from parsing/rendering and core thermometry.
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
    private val initializeRequest = AtomicLong(-1L)
    private val inventoryRequest = AtomicLong(-1L)
    private val transitionRequest = AtomicLong(-1L)
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
    /** UI requests an operation, never executes USB controls or queues duplicate sequences. */
    fun initializeRadiometric() {
        val current = mutableState.value
        if (!current.session.canInitialize || current.usb.phase != UsbPhase.STREAMING ||
            current.transition.active || transitionRequest.get() != -1L) return
        initializeRequest.compareAndSet(-1L, generation)
        mutableState.updateIf({ initializeRequest.get() == generation }) {
            it.copy(session = it.session.copy(canInitialize = false))
        }
    }
    /** Explicit diagnostic request uses a GET-only interface; no initialization is requested. */
    fun readZoomInventory() {
        val current = mutableState.value
        if (current.usb.phase != UsbPhase.STREAMING || current.session.active || current.transition.active) return
        inventoryRequest.compareAndSet(-1L, generation)
    }
    /** Debug-only one-shot action, mutually exclusive with the full session and inventory. */
    fun testRaw14Transition() {
        val current = mutableState.value
        if (!BuildConfig.DEBUG || current.usb.phase != UsbPhase.STREAMING || !current.transition.canStart ||
            current.session.active || initializeRequest.get() != -1L || inventoryRequest.get() != -1L) return
        if (transitionRequest.compareAndSet(-1L, generation)) mutableState.updateIf({ transitionRequest.get() == generation }) {
            it.copy(transition = it.transition.copy(canStart = false))
        }
    }
    /** Worker owns transport and finally-release; stale work from an earlier connection cannot update UI. */
    private fun open(device: UsbDevice) {
        val token = ++generation
        streamJob?.cancel()
        mutableState.value = mutableState.value.copy(usb = UsbState(UsbPhase.OPENING, "Opening read-only YUYV stream"), permission = "Granted")
        streamJob = scope.launch {
            try {
                val connection = manager.openDevice(device) ?: error("UsbManager.openDevice returned null")
                val singleEvidence = NumericEvidence(context, "raw14-transition.jsonl", "LMThermalRaw14")
                val sessionEvidence = NumericEvidence(context, "radiometric-session.jsonl", "LMThermalSession")
                val thermometryEvidence = NumericEvidence(context, "thermometry.jsonl", "LMThermalTemperature")
                var collectingSingle = false
                NativeUvcTransport(connection, { evidence ->
                    if (collectingSingle) singleEvidence.record(evidence) else sessionEvidence.record(evidence)
                }).use { transport ->
                    val session = RadiometricSession(transport, token, SystemClock::elapsedRealtime,
                        ownsConnection = { token == generation && isActive },
                        event = { sessionEvidence.record(it + numericContext(transport)) })
                    val transition = Raw14TransitionDiagnostic(transport, token, SystemClock::elapsedRealtime,
                        { token == generation && isActive }, { singleEvidence.record(it + numericContext(transport)) })
                    var invalid = 0L
                    var previousCount = 0L
                    var fps = 0.0
                    var cadenceStart = SystemClock.elapsedRealtime()
                    var lastFrameAt = cadenceStart
                    var lastLog = cadenceStart
                    var lastMeasurementLog = cadenceStart
                    var previousMeasurementAvailable = false
                    try {
                        while (isActive && token == generation) {
                            if (inventoryRequest.getAndSet(-1L) == token && !session.snapshot().active && !transition.snapshot().active) {
                                try {
                                    // Persist only this explicit control inventory: logcat may rotate during ADB loss.
                                    // The bounded file contains no scene payloads, serials or frame hashes.
                                    val inventoryFile = java.io.File(context.filesDir, "zoom-inventory.jsonl")
                                    inventoryFile.bufferedWriter().use { writer ->
                                        ZoomInventory(transport, { token == generation && isActive }, SystemClock::elapsedRealtime,
                                            {
                                                val json = JSONObject(it).toString()
                                                Log.i("LMThermalZoom", json)
                                                writer.write(json); writer.newLine(); writer.flush()
                                            }).run()
                                    }
                                    mutableState.updateIf({ token == generation }) { it.copy(inventory = "Read-only inventory logged") }
                                } catch (failure: Exception) {
                                    mutableState.updateIf({ token == generation }) { it.copy(inventory = "Inventory: ${failure.message}") }
                                }
                            }
                            val singleRequest = transitionRequest.getAndSet(-1L)
                            if (singleRequest == token && !session.snapshot().active) {
                                singleEvidence.reset(); collectingSingle = true
                                transition.request(singleRequest)
                            }
                            val request = initializeRequest.getAndSet(-1L)
                            if (request == token && !transition.snapshot().active) {
                                sessionEvidence.reset(); thermometryEvidence.reset(); collectingSingle = false; session.initialize(request)
                            }
                            transition.tick()
                            session.tick()
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
                            transition.observe(frame, inspection, counters[4], token)
                            session.observe(frame, inspection, counters[4], token)
                            // Always evaluate the current post-observation session state. Held/invalid frames
                            // demote readiness before this gate; no last-good Celsius is carried forward.
                            val measurementStart = SystemClock.elapsedRealtimeNanos()
                            val measurement = MeasurementGate.evaluate(session.snapshot().state, frame, counters[4], now)
                            val measurementNs = SystemClock.elapsedRealtimeNanos() - measurementStart
                            val available = measurement.measurement != null
                            if (available != previousMeasurementAvailable || now - lastMeasurementLog >= 2000) {
                                thermometryEvidence.record(ThermometryDiagnostics.event(measurement, session.snapshot().state,
                                    counters[4], now, measurementNs) + numericContext(transport).filterKeys { it != "session_state" })
                                previousMeasurementAvailable = available; lastMeasurementLog = now
                            }
                            if (inspection.mode == FrameMode.INVALID || inspection.reason != null) invalid++
                            val image = if (frame != null && inspection.mode != FrameMode.INVALID) Bitmap.createBitmap(
                                PreviewRenderer.grayscale(frame, inspection), Ht301Layout.WIDTH, Ht301Layout.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888) else null
                            mutableState.updateIf({ token == generation }) { previous -> previous.copy(
                                usb = UsbState(UsbPhase.STREAMING, "Streaming · explicit radiometric session"),
                                mode = inspection.mode, reason = inspection.reason, size = bytes.size,
                                range = "${inspection.minimum ?: "—"}..${inspection.maximum ?: "—"}",
                                fps = fps, received = counters[0], invalid = invalid, replaced = counters[1], malformed = counters[2], bitmap = image, session = session.snapshot(), transition = transition.snapshot(),
                                measurement = measurement.measurement, measurementReason = measurement.reason) }
                            if (now - lastLog >= 2000) {
                                // Numeric diagnostics only: no camera serial or private scene is written to logs.
                                Log.i("LMThermal", "frame size=${bytes.size} image=${frame?.imageBytes()?.size} trailer=${frame?.trailerBytes()?.size} mode=${inspection.mode} reason=${inspection.reason} summary=${inspection.summaryValid} words=${inspection.minimum}..${inspection.maximum} fps=$fps received=${counters[0]} replaced=${counters[1]} malformed=${counters[2]} session=${session.snapshot().state} live=${session.snapshot().live} held=${session.snapshot().held} rejected=${session.snapshot().rejected}")
                                lastLog = now
                            }
                        }
                    } finally {
                        transition.cancel()
                        if (session.snapshot().active) session.cancel()
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("LMThermal", "Transport failed", error)
                mutableState.updateIf({ token == generation }) { previous -> previous.copy(
                    usb = UsbState(UsbPhase.ERROR, error.message ?: "Transport error"), bitmap = null,
                    measurement = null, measurementReason = "Transport error",
                    session = previous.session.copy(state = SessionState.ERROR, active = false,
                        canInitialize = false, live = 0, reason = error.message ?: "Transport error")) }
            } finally { Log.i("LMThermal", "Stream released generation=$token") }
        }
    }
    /** Acquisition-only counters and current UI state; no scene content enters developer reports. */
    private fun numericContext(transport: UvcTransport): Map<String, Any?> {
        val counters = transport.statistics()
        val current = mutableState.value
        return mapOf("callback_count" to counters[0], "replaced_count" to counters[1],
            "native_malformed" to counters[2], "parser_rejected" to current.invalid,
            "callback_fps" to current.fps,
            "session_state" to current.session.state.name)
    }
    /** Immediately clear stale presentation; worker cancellation releases the native handle asynchronously. */
    fun close(message: String = "Closed") {
        generation++
        initializeRequest.set(-1L)
        inventoryRequest.set(-1L)
        transitionRequest.set(-1L)
        Log.i("LMThermalSession", JSONObject(mapOf("event" to "connection_cancelled", "monotonic_ms" to SystemClock.elapsedRealtime(), "generation" to generation, "reason" to message)).toString())
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
