package org.lmthermal.app.r2

import android.graphics.Bitmap
import android.os.*
import android.content.Intent
import android.content.IntentFilter
import android.view.Choreographer
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.take
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.app.CameraViewModel
import org.lmthermal.app.MainActivity
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.r2.R2Json as LmtxJson
import org.lmthermal.r2.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Explicit opt-in harness, test APK only. Operator uses unchanged Connect/Initialize actions.
 * Latest-state conflation is measured/disclosed; callback FPS never implies every callback saved.
 */
@RunWith(AndroidJUnit4::class)
class R2LiveDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var directory: File
    private val reports = mutableListOf<Map<String, Any?>>()
    private val args get() = InstrumentationRegistry.getArguments()
    private fun save(name: String, value: Map<String, Any?>) {
        File(directory, name).writeBytes(LmtxJson.encode(value))
    }
    private fun memory(): Map<String, Any?> {
        val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        return mapOf("java_used" to runtime.totalMemory() - runtime.freeMemory(), "native_heap" to Debug.getNativeHeapAllocatedSize(),
            "pss_bytes" to info.totalPss.toLong() * 1024, "rss_bytes" to File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("VmRSS:") }?.trim()?.split(Regex("\\s+"))?.get(1)?.toLong()?.times(1024))
    }
    private fun power(): Map<String, Any?> {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager
        val thermal = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        return mapOf("battery_percent" to manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "current_ua" to manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            "battery_temperature_tenths_c" to battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1),
            "thermal_status" to if (Build.VERSION.SDK_INT >= 29) thermal.currentThermalStatus else null)
    }
    private fun percentile(values: List<Long>, percentile: Double): Double? = if (values.isEmpty()) null
        else values.sorted()[((values.size - 1) * percentile).toInt()].toDouble()
    private class TimedCodec(private val codec: BlockCodec): BlockCodec {
        val wall = AtomicLong(); val cpu = AtomicLong(); val allocations = AtomicLong()
        override val id get() = codec.id; override val name get() = codec.name
        override fun encode(input: ByteArray): ByteArray {
            val w = System.nanoTime(); val c = Debug.threadCpuTimeNanos()
            return codec.encode(input).also { wall.addAndGet(System.nanoTime() - w); cpu.addAndGet(Debug.threadCpuTimeNanos() - c)
                allocations.addAndGet(input.size.toLong() + it.size) }
        }
        override fun decode(input: ByteArray, size: Int) = codec.decode(input, size)
    }
    private fun owned(measurement: Ht301ThermalMeasurement, profile: Profile, originMs: Long): Observation {
        val matrix = measurement.matrix(); val temperature = little(matrix.size * 4)
        matrix.forEach(temperature::putFloat)
        val metadata = R2HtContext.metadata(measurement.evidence)
        return Observation(measurement.sequence, measurement.receivedMonotonicMs * 1000000,
            (measurement.receivedMonotonicMs - originMs).coerceAtLeast(0) * 1000000, measurement.geometry.width, measurement.geometry.height,
            temperature.array(), measurement.validityMask(), if (profile != Profile.ANALYSIS) measurement.evidence.source.imageBytes() else null,
            if (profile == Profile.FULL) measurement.evidence.source.transportBytes() else null,
            mapOf("module" to "ht301", "provenance" to "real-ht301-native-equivalent", "metadata" to metadata,
                "warning" to "Native-equivalent temperatures; absolute physical accuracy not yet independently validated.")).also { it.contextBytes }
    }
    @Test fun sustainedReadyStreamAndCodecStudy() = runBlocking {
        assumeTrue(args.getString("r2Live") == "true")
        directory = File(context.filesDir, "recording-r2/" + args.getString("r2Run", "run-" + System.currentTimeMillis()))
        require(!directory.exists()); directory.mkdirs()
        val plan = args.getString("r2Plan", "unknown"); val revision = args.getString("r2Revision", "unknown")
        save("progress.json", mapOf("phase" to "awaiting_operator_ready", "revision" to revision))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: CameraViewModel
            scenario.onActivity { model = ViewModelProvider(it)[CameraViewModel::class.java]; it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            // USB app entry may replace the Activity started by ActivityScenario. Follow the
            // visible owner; never create another coordinator or initialize from the harness.
            withTimeout(1200000) {
                while (true) {
                    instrumentation.runOnMainSync {
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>().singleOrNull()?.let {
                                model = ViewModelProvider(it)[CameraViewModel::class.java]
                                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                    }
                    save("progress.json", mapOf("phase" to "awaiting_operator_ready",
                        "state" to model.camera.state.value.status.code.name,
                        "session" to model.camera.state.value.status.detail?.machineCode))
                    if (model.camera.state.value.measurement != null) break
                    delay(200)
                }
            }
            val sample = mutableListOf<Observation>()
            val sampleOrigin = SystemClock.elapsedRealtime()
            withTimeout(15000) { model.camera.state.mapNotNull { it.measurement as? Ht301ThermalMeasurement }
                .distinctUntilChangedBy { it.sequence }.take(25).collect { sample += owned(it, Profile.FULL, sampleOrigin) } }
            val heartbeat = mutableListOf<Long>(); var heartbeatActive = true; var heartbeatLast = 0L
            val callback = object: Choreographer.FrameCallback {
                override fun doFrame(time: Long) { synchronized(heartbeat) { if (heartbeatLast != 0L && heartbeat.size < 20000) heartbeat += time - heartbeatLast; heartbeatLast = time }
                    if (heartbeatActive) Choreographer.getInstance().postFrameCallback(this) }
            }
            instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback(callback) }
            val codecs = listOf<BlockCodec>(Stored, Deflate1, NativeZstd)
            suspend fun run(name: String, profile: Profile?, codec: BlockCodec?) {
                save("progress.json", mapOf("phase" to "warmup", "run" to name, "seconds" to 20))
                delay(20000)
                val origin = SystemClock.elapsedRealtime(); val begin = model.camera.state.value.statistics
                val cpuBegin = Process.getElapsedCpuTime(); val memoryBegin = memory(); val powerBegin = power()
                synchronized(heartbeat) { heartbeat.clear(); heartbeatLast = 0 }
                val timed = codec?.let(::TimedCodec)
                val file = File(directory, "$name.r2proto")
                val writer = if (profile != null) BoundedRecorder(file, profile, timed!!) else null
                var accepted = 0L; var unavailable = 0L; var heldStates = 0L; var sequenceMissing = 0L; var copiedBytes = 0L; var freezeNs = 0L
                var previous = -1L; var receipt = -1L; var previousObserved = -1L
                val intervals = mutableListOf<Long>(); val fingerprints = mutableSetOf<Long>()
                val statusCounts = mutableMapOf<String, Long>(); val memSamples = mutableListOf<Map<String, Any?>>()
                val displayAge = mutableListOf<Long>(); val renderTimes = mutableListOf<Long>()
                var observedCompletedRenders = 0L; var previousRenderSequence = -1L
                val renderCollect = launch(Dispatchers.Default) {
                    model.presentation.state.collect { shown ->
                        val measurement=shown.measurement
                        if (measurement != null && measurement.sequence>previousRenderSequence) {
                            previousRenderSequence=measurement.sequence; observedCompletedRenders++
                            renderTimes+=(shown.renderMs*1000000).toLong()
                        }
                    }
                }
                var detached = false
                var noSequenceUnavailable = 0L; var previousCallbackCount = -1L
                val collect = launch(Dispatchers.Default) {
                    model.camera.state.collect { state ->
                        val now = SystemClock.elapsedRealtime(); val seq = state.measurement?.sequence ?: state.preview?.sequence
                        val code = state.status.detail?.machineCode ?: state.status.code.name
                        if (seq == null) {
                            if (state.statistics.received != previousCallbackCount) { noSequenceUnavailable++; previousCallbackCount = state.statistics.received }
                            if (state.lifecycle != CameraLifecycle.STREAMING) detached = true
                            return@collect
                        }
                        if (seq <= previousObserved) return@collect
                        previousObserved = seq; statusCounts[code] = (statusCounts[code] ?: 0) + 1
                        if (previous >= 0 && seq > previous + 1) {
                            sequenceMissing += seq - previous - 1
                            writer?.offer(Observation(previous + 1, null, ((state.measurement?.receivedMonotonicMs ?: state.preview?.receivedMonotonicMs ?: now)-origin).coerceAtLeast(0)*1000000, 384, 288, null,
                                reason = "source-or-stateflow-unobserved", gapEnd = seq - 1))
                        }
                        previous = seq
                        val measurement = state.measurement as? Ht301ThermalMeasurement
                        if (measurement == null) {
                            unavailable++; if (code.contains("HELD") || code.contains("TRANSIENT")) heldStates++
                            writer?.offer(Observation(seq, null, ((state.measurement?.receivedMonotonicMs ?: state.preview?.receivedMonotonicMs ?: now)-origin).coerceAtLeast(0)*1000000, 384, 288, null, reason = code))
                        } else {
                            accepted++
                            if (receipt >= 0) intervals += measurement.receivedMonotonicMs - receipt
                            receipt = measurement.receivedMonotonicMs
                            var fingerprint = 0L
                            repeat(16) { i -> fingerprint = fingerprint * 31 + measurement.evidence.source.pixel((i*23)%384, (i*17)%288) }
                            fingerprints += fingerprint
                            if (profile != null) {
                                val start = System.nanoTime(); val frame = owned(measurement, profile, origin)
                                freezeNs += System.nanoTime() - start; copiedBytes += frame.bytes
                                writer?.offer(frame)
                            }
                        }
                    }
                }
                save("progress.json", mapOf("phase" to "sustained", "run" to name, "seconds" to 120))
                repeat(120) { second -> delay(1000)
                    model.presentation.state.value.measurement?.let { displayAge+=SystemClock.elapsedRealtime()-it.receivedMonotonicMs }
                    if (second % 2 == 0) memSamples += memory()
                    if (second % 10 == 0) save("progress.json", mapOf("phase" to "sustained", "run" to name,
                        "elapsed_s" to second + 1, "accepted" to accepted, "writer_drops" to (writer?.drops ?: 0), "backlog" to (writer?.backlog() ?: 0))) }
                collect.cancelAndJoin(); renderCollect.cancelAndJoin()
                val elapsed = (SystemClock.elapsedRealtime() - origin)/1000.0
                val end = model.camera.state.value.statistics; val cpuEnd = Process.getElapsedCpuTime()
                val stopStart = SystemClock.elapsedRealtime(); val stop = writer?.stop(if (detached) "usb_or_session_interruption" else "user_stop")
                val stopMs = SystemClock.elapsedRealtime() - stopStart
                val ui = synchronized(heartbeat) { heartbeat.map { it/1000000 } }
                val report = mapOf("run" to name, "profile" to profile?.name, "codec" to codec?.name,
                    "duration_s" to elapsed, "callback_fps" to (end.received - begin.received)/elapsed,
                    "acquired" to end.received - begin.received, "replacement_delta" to end.replaced - begin.replaced,
                    "malformed_delta" to end.malformed - begin.malformed, "accepted" to accepted, "unavailable_states" to unavailable,
                    "held_or_transient_states" to heldStates, "unavailable_without_sequence" to noSequenceUnavailable, "sequence_unobserved" to sequenceMissing, "statuses" to statusCounts,
                    "receipt_interval_ms_p50" to percentile(intervals, .5), "receipt_interval_ms_p95" to percentile(intervals, .95),
                    "receipt_interval_ms_p99" to percentile(intervals, .99), "distinct_scene_fingerprints" to fingerprints.size,
                    "writer_drops" to (writer?.drops ?: 0), "queue_max_bytes" to (writer?.maxBytes ?: 0), "queue_max_depth" to (writer?.maxDepth ?: 0),
                    "writer_failure" to writer?.failure, "finalized" to stop, "stop_ms" to stopMs,
                    "committed_entries" to writer?.recorder?.committedEntries, "chunks" to writer?.recorder?.committedChunks,
                    "physical_bytes" to writer?.recorder?.physicalBytes, "logical_bytes" to writer?.recorder?.logicalBytes,
                    "stored_bytes" to writer?.recorder?.storedBytes, "file_bytes" to if (writer == null) null else file.length(),
                    "max_chunk_bytes" to writer?.recorder?.maxChunkBytes, "codec_wall_ns" to timed?.wall?.get(), "codec_cpu_ns" to timed?.cpu?.get(),
                    "codec_input_output_allocation_lower_bound_bytes" to timed?.allocations?.get(), "freeze_wall_ns" to freezeNs,
                    "owned_retained_copy_lower_bound_bytes" to copiedBytes, "process_cpu_ms" to cpuEnd - cpuBegin,
                    "ui_heartbeat_p99_ms" to percentile(ui, .99), "ui_heartbeat_max_ms" to ui.maxOrNull(), "ui_heartbeat_samples" to ui.size,
                    "observed_completed_renders" to observedCompletedRenders,
                    "observed_completed_render_fps" to observedCompletedRenders/elapsed,
                    "render_wall_ns_p95" to percentile(renderTimes,.95),
                    "displayed_source_age_ms_p50" to percentile(displayAge,.5),
                    "displayed_source_age_ms_p95" to percentile(displayAge,.95),
                    "displayed_source_age_ms_max" to displayAge.maxOrNull(),
                    "memory_start" to memoryBegin, "memory_peak" to memoryBegin.keys.associateWith { key -> memSamples.mapNotNull { it[key] as? Long }.maxOrNull() },
                    "memory_end" to memory(), "power_start" to powerBegin, "power_end" to power(), "detached" to detached)
                reports += report; save("runs.json", mapOf("plan" to plan, "prototype" to revision, "device" to "Pixel 8", "android" to Build.VERSION.RELEASE,
                    "api" to Build.VERSION.SDK_INT, "runs" to reports, "limitations" to listOf("Latest StateFlow; sequence gaps disclosed", "Process CPU is not isolated thermometry CPU",
                        "Memory sampled at 2 seconds; instantaneous peaks may be higher", "Fair codec study runs after sustained profiles to avoid allocator-cache confounding", "Codec source frozen before baseline warmup; baseline has no recording freeze/write")))
                writer?.close()
            }
            try {
                run("baseline", null, null)
                if (args.getString("r2BaselineOnly") != "true") {
                    for (codec in codecs) for (profile in Profile.entries) run(profile.name.lowercase() + "-" + codec.name, profile, codec)
                    codecStudy(sample, codecs)
                }
                if (args.getString("r2Detach") == "true") {
                    val origin = SystemClock.elapsedRealtime()
                    val detachedWriter = BoundedRecorder(File(directory, "usb-detach.r2proto"), Profile.FULL, Deflate1)
                    var lastSequence = -1L
                    val detachCollect = launch(Dispatchers.Default) {
                        model.camera.state.collect { state ->
                            val measurement = state.measurement as? Ht301ThermalMeasurement
                            if (measurement != null && measurement.sequence > lastSequence) {
                                lastSequence = measurement.sequence
                                detachedWriter.offer(owned(measurement, Profile.FULL, origin))
                            }
                        }
                    }
                    delay(5000)
                    save("progress.json", mapOf("phase" to "awaiting_operator_usb_detach", "accepted" to detachedWriter.intake))
                    withTimeout(300000) { while (model.camera.state.value.lifecycle == CameraLifecycle.STREAMING) delay(200) }
                    detachCollect.cancelAndJoin()
                    val stopStart = SystemClock.elapsedRealtime(); val finalized = detachedWriter.stop("usb_detach")
                    val stopMs = SystemClock.elapsedRealtime() - stopStart; val intake = detachedWriter.intake
                    delay(3000)
                    save("detach.json", mapOf("finalized" to finalized, "stop_ms" to stopMs, "intake" to intake,
                        "committed" to detachedWriter.recorder.committedEntries, "writer_drops" to detachedWriter.drops,
                        "failure" to detachedWriter.failure, "post_detach_measurement" to (model.camera.state.value.measurement != null),
                        "post_detach_lifecycle" to model.camera.state.value.lifecycle.name,
                        "automatic_reinitialize" to false, "source_interruption_reason" to "usb_detach"))
                    detachedWriter.close()
                }
                save("progress.json", mapOf("phase" to "finished", "runs" to reports.size))
            } finally {
                heartbeatActive = false
                instrumentation.runOnMainSync { Choreographer.getInstance().removeFrameCallback(callback) }
            }
        }
    }
    private fun codecStudy(sample: List<Observation>, codecs: List<BlockCodec>) {
        require(sample.size == 25)
        val rows = mutableListOf<Map<String, Any?>>()
        for (count in listOf(1, 25)) {
            fun join(role: (Observation) -> ByteArray?) = ByteArrayOutputStream().also { stream -> sample.take(count).forEach { role(it)?.let(stream::write) } }.toByteArray()
            val roles = mapOf("temperature" to join { it.temperature }, "native" to join { it.native }, "acquisition" to join { it.acquisition },
                "full_physical" to ByteArrayOutputStream().also { out -> sample.take(count).forEach { out.write(it.temperature!!); out.write(it.acquisition!!) } }.toByteArray())
            for ((role, original) in roles) for (codec in codecs) {
                codec.decode(codec.encode(original), original.size)
                repeat(5) { pass ->
                    val mem = memory(); val cpu = Debug.threadCpuTimeNanos(); val wall = System.nanoTime()
                    val compressed = codec.encode(original); val encodeWall = System.nanoTime()-wall; val encodeCpu = Debug.threadCpuTimeNanos()-cpu
                    val dCpu = Debug.threadCpuTimeNanos(); val dWall = System.nanoTime(); val decoded = codec.decode(compressed, original.size)
                    val decodeWall = System.nanoTime()-dWall; val decodeCpu = Debug.threadCpuTimeNanos()-dCpu
                    require(original.contentEquals(decoded))
                    rows += mapOf("frames" to count, "role" to role, "codec" to codec.name, "pass" to pass,
                        "original_bytes" to original.size, "stored_bytes" to compressed.size, "ratio" to compressed.size.toDouble()/original.size,
                        "encode_wall_ns" to encodeWall, "encode_cpu_ns" to encodeCpu, "decode_wall_ns" to decodeWall, "decode_cpu_ns" to decodeCpu,
                        "source_sha256" to hex(sha(original)), "roundtrip" to true, "memory_before" to mem, "memory_after" to memory())
                }
            }
        }
        save("codecs.json", mapOf("rows" to rows, "source" to "Same 25 owned real HT301 frames from baseline; private scene bytes", "roundtrip" to true))
    }
}
