package org.lmthermal.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.lmthermal.exchange.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

enum class ExportPhase { IDLE, PREPARING, READY, WRITING, SUCCESS, CANCELLED, ERROR }
data class ExportState(val phase: ExportPhase = ExportPhase.IDLE, val name: String? = null,
    val radiometric: Boolean = false, val errorId: String? = null, val partialCleanupFailed: Boolean = false)

/** One immutable capture/job, independent of USB and Activity lifecycle. SAF never receives an unfinished ZIP.
 * Locale recreation may release the camera; it cannot mutate the retained request or resume camera ownership.
 * A user cancellation removes private staging and best-effort deletes a newly created partial document.
 */
class CaptureExporter(private val context: Context,
    private val openDestination: (Uri) -> OutputStream = { context.contentResolver.openOutputStream(it, "w") ?: error("Provider did not open new document") },
    private val deleteDestination: (Uri) -> Boolean = { DocumentsContract.deleteDocument(context.contentResolver, it) }) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val mutableState = MutableStateFlow(ExportState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var staged: File? = null
    private var generation = 0L
    private val directory = File(context.cacheDir, "lmtx-export").apply {
        mkdirs()
        // Cache grants are temporary; keep successful shares for a day, and bound abandoned process stages.
        listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000 }?.forEach { it.delete() }
    }

    /** Called with immutable source/UI values read once at the gesture, before any destination dialog. */
    @Synchronized fun prepare(source: CameraSessionState<Bitmap>, selection: NativeRoiSelection,
        settings: CelsiusPresentationSettings, point: NativePixel?) {
        if (mutableState.value.phase in setOf(ExportPhase.PREPARING, ExportPhase.READY, ExportPhase.WRITING)) return
        if (!CaptureFreeze.canCapture(source)) return
        staged?.delete(); staged = null
        val token = ++generation
        mutableState.value = ExportState(ExportPhase.PREPARING)
        val created = CaptureClock("unreliable", "host_wall_clock", Instant.now().toString(), "host_clock_not_verified")
        job = scope.launch {
            var file: File? = null
            try {
                val start = SystemClock.elapsedRealtimeNanos()
                val capture = CaptureFreeze.freeze(source, selection, settings, point,
                    CaptureProducer(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME), created, CLOCK_DOMAIN,
                    png = { geometry, argb ->
                        val bitmap = Bitmap.createBitmap(argb, geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
                        try { encodePng(bitmap) } finally { bitmap.recycle() }
                    }, sourcePreviewPng = ::encodePng)
                if (BuildConfig.DEBUG) recordSourceProof(source, capture)
                ensureActive()
                val freezeMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                file = File(directory, capture.captureId + ".lmtx")
                check(file.createNewFile())
                val writeStart = SystemClock.elapsedRealtimeNanos()
                val manifest = LmtxWriter.write(capture, file)
                ensureActive()
                synchronized(this@CaptureExporter) {
                    if (token != generation) { file.delete(); return@launch }
                    staged = file
                    mutableState.value = ExportState(ExportPhase.READY, file.name, capture.temperature != null)
                }
                Log.i("LMThermalExport", org.json.JSONObject(mapOf("event" to "capture_finalized", "capture_id" to capture.captureId,
                    "sequence" to capture.sequence, "snapshot_ms" to freezeMs,
                    "serialization_validation_ms" to (SystemClock.elapsedRealtimeNanos() - writeStart) / 1e6,
                    "archive_bytes" to file.length(), "callback_fps" to source.statistics.callbackFps,
                    "content_class" to manifest["content_class"], "bounded_jobs" to 1,
                    "owned_source_bytes" to (capture.temperature?.size ?: 0) + capture.evidence.payloads.sumOf { it.bytes.size } + (capture.preview?.bytes?.size ?: 0))).toString())
            } catch (cancelled: CancellationException) { file?.delete(); throw cancelled }
            catch (failure: Exception) { file?.delete(); Log.e("LMThermalExport", "Capture export failed", failure)
                update(token) { ExportState(ExportPhase.ERROR, errorId = (failure as? LmtxException)?.code ?: "storage_write_error") } }
        }
    }
    /** URI is from ACTION_CREATE_DOCUMENT only; never overwrite an existing caller-supplied path. */
    @Synchronized fun publish(uri: Uri?) {
        if (mutableState.value.phase != ExportPhase.READY) return
        if (uri == null) { cancel(); return }
        val file = staged ?: return
        val token = generation
        mutableState.value = mutableState.value.copy(phase = ExportPhase.WRITING)
        job = scope.launch {
            var success = false
            var cleanupFailed = false
            try {
                CapturePublication.copy(file,
                    openNewDestination = { openDestination(uri) },
                    deletePartial = { deleteDestination(uri) },
                    cancelled = { ensureActive() }, cleanupFailed = { cleanupFailed = true })
                ensureActive(); success = true
                if (BuildConfig.DEBUG) {
                    // Debug-only provider readback verifies the actual SAF copy, without camera access or public hashes.
                    val sameBytes = runCatching { context.contentResolver.openInputStream(uri)?.use { input ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(8192); var count = 0L
                        while (true) { val size = input.read(buffer); if (size < 0) break
                            count += size; check(count <= file.length()); digest.update(buffer, 0, size) }
                        val expected = file.inputStream().use { stream -> val d = MessageDigest.getInstance("SHA-256")
                            while (true) { val size = stream.read(buffer); if (size < 0) break; d.update(buffer, 0, size) }; d.digest() }
                        count == file.length() && digest.digest().contentEquals(expected)
                    } }.getOrNull()
                    Log.i("LMThermalExport", "SAF readback matches finalized archive: $sameBytes")
                }
                update(token) { it.copy(phase = ExportPhase.SUCCESS) }
                Log.i("LMThermalExport", "Published complete capture after destination close")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Log.e("LMThermalExport", "Destination copy failed", failure)
                update(token) { it.copy(phase = ExportPhase.ERROR, errorId = "storage_write_error") } }
            finally {
                if (!success) {
                    synchronized(this@CaptureExporter) {
                        if (cleanupFailed && (token == generation || token + 1 == generation && state.value.phase == ExportPhase.CANCELLED))
                            mutableState.value = mutableState.value.copy(partialCleanupFailed = true)
                        file.delete(); if (staged == file) staged = null
                    }
                }
            }
        }
    }
    /** Cancel does not reach camera state. Private stages are disposable; finalized shared files survive briefly. */
    @Synchronized fun cancel() {
        generation++
        job?.cancel(); staged?.delete(); staged = null
        mutableState.value = ExportState(ExportPhase.CANCELLED)
    }
    @Synchronized fun shareFile(): File? = staged?.takeIf { state.value.phase == ExportPhase.SUCCESS && it.exists() }
    @Synchronized private fun update(token: Long, change: (ExportState) -> ExportState) {
        if (token == generation) mutableState.value = change(mutableState.value)
    }
    @Synchronized fun dispose() { generation++; job?.cancel(); scope.cancel()
        if (state.value.phase != ExportPhase.SUCCESS) { staged?.delete(); staged = null } }
    /** Private debug evidence only: an independent byte-buffer encoding of the retained source verifies exact bits.
     * Never exported or logged, never opens USB, and never rebuilds thermometry. One latest proof bounds storage.
     */
    private fun recordSourceProof(source: CameraSessionState<Bitmap>, capture: LmtxCapture) {
        val original = source.measurement?.takeIf { capture.temperature != null }
        val digest = original?.let { measurement ->
            val words = measurement.matrix(); val mask = measurement.validityMask()
            val bytes = ByteBuffer.allocate(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            words.forEachIndexed { index, value -> bytes.putInt(if (mask != null && mask[index].toInt() == 0) 0 else value.toRawBits()) }
            MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
        }
        val proof = buildMap<String, Any?> {
            put("capture_id", capture.captureId); put("sequence", capture.sequence)
            put("temperature_source_sha256", digest); put("width_px", capture.geometry.width); put("height_px", capture.geometry.height)
            put("presentation", capture.presentation())
            capture.roi?.let { rect -> put("roi_bounds", listOf(rect.x1, rect.y1, rect.x2, rect.y2))
                original?.let { put("roi_statistics", RoiStatistics.calculate(it.geometry, it.matrix(), rect, it.validityMask()).exchangeJson()) } }
        }
        File(context.filesDir, "lmtx-source-proof.json").writeBytes(LmtxJson.encode(proof))
    }
    private fun encodePng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
    }
    companion object {
        // New process = new monotonic domain. Millisecond receipts convert to ns without claiming finer precision.
        private val CLOCK_DOMAIN = UUID.randomUUID().toString()
    }
}
