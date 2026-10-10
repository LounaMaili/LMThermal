package org.lmthermal.app.r2

import android.os.*
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.app.*
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.r2.*
import java.io.File

/** Staged optimized candidate, test APK only. Explicit operator connection/init, no controls here.
 * Complete grids/closure/integrity are retained. Stage escalation stops on any preparation/writer loss.
 */
@RunWith(AndroidJUnit4::class)
class R2CandidateDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun memory(detailed: Boolean): Map<String, Any?> {
        val runtime = Runtime.getRuntime()
        val result = linkedMapOf<String, Any?>("java_used" to runtime.totalMemory() - runtime.freeMemory(),
            "native_heap" to Debug.getNativeHeapAllocatedSize(),
            "process_allocated_bytes" to Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull(),
            "process_gc_count" to Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull())
        if (detailed) {
            val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info)
            result["pss_bytes"] = info.totalPss.toLong() * 1024
            result["rss_bytes"] = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
                ?.trim()?.split(Regex("\\s+"))?.get(1)?.toLong()?.times(1024)
        }
        return result
    }
    private fun distribution(values: List<Long>): Map<String, Any?> {
        val sorted = values.sorted()
        return mapOf("p50" to sorted.getOrNull((sorted.size * .5).toInt()), "p95" to sorted.getOrNull((sorted.size * .95).toInt()),
            "max" to sorted.lastOrNull(), "samples" to sorted.size)
    }
    @Test fun stagedCandidate() = runBlocking {
        assumeTrue(args.getString("r2Candidate") != null)
        val stages = args.getString("r2Candidate")!!.split(',')
        val supported = listOf("baseline", "analysis-stored", "native-stored", "full-stored", "analysis-deflate-1", "native-deflate-1", "full-deflate-1")
        require(stages.all { it in supported })
        val duration = args.getString("r2Seconds", "120")!!.toInt(); require(duration in 120..600)
        val preferredChunkMiB = args.getString("r2ChunkTargetMiB", "16")!!.toInt()
        require(preferredChunkMiB in 1..16)
        val preferredChunkBytes = preferredChunkMiB * Bounds.MIB
        val directory = File(context.filesDir, "recording-r2/" + args.getString("r2Run"))
        require(!directory.exists()); directory.mkdirs()
        val reports = mutableListOf<Map<String, Any?>>()
        fun save(name: String, value: Map<String, Any?>) { File(directory, name).writeBytes(R2Json.encode(value)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: CameraViewModel
            scenario.onActivity { model = ViewModelProvider(it)[CameraViewModel::class.java]; it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            withTimeout(1200000) { while (true) {
                instrumentation.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()?.let {
                        model = ViewModelProvider(it)[CameraViewModel::class.java]; it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                save("progress.json", mapOf("phase" to "awaiting_operator_ready", "state" to model.camera.state.value.status.code.name))
                if (model.camera.state.value.measurement != null) break
                delay(200)
            } }
            for (stage in stages) {
                save("progress.json", mapOf("phase" to "warmup", "stage" to stage)); delay(20000)
                System.gc(); delay(1000)
                val costs = StageCosts(Debug::threadCpuTimeNanos)
                val origin = SystemClock.elapsedRealtime(); val before = model.camera.state.value.statistics
                val memoryStart = memory(true); val samples = mutableListOf<Map<String, Any?>>()
                val cheapPeaks = mutableMapOf<String, Long>(); val ownedPeak = mutableMapOf<String, Long>()
                val backlog = mutableListOf<Map<String, Any?>>()
                val age = mutableListOf<Long>(); val renderMs = mutableListOf<Long>(); val measurementAge = mutableListOf<Long>()
                val profile = if (stage == "baseline") null else Profile.valueOf(stage.substringBefore('-').uppercase())
                val codec = if (stage.endsWith("deflate-1")) Deflate1 else Stored
                val writer = profile?.let { BoundedRecorder(File(directory, "$stage.r2proto"), it, codec, costs, preferredChunkBytes) }
                val prep = profile?.let { Ht301Preparation(writer!!, it, origin, costs) }
                var accepted = 0L; var unavailable = 0L; var missed = 0L; var previous = -1L; var rendered = 0L; var lastRender = -1L
                val states = mutableMapOf<String, Long>()
                val renderJob = launch(Dispatchers.Default) { model.presentation.state.collect { shown ->
                    shown.measurement?.let { if (it.sequence > lastRender) { lastRender = it.sequence; rendered++; renderMs += (shown.renderMs * 1000000).toLong() } }
                } }
                // Only scalar sequence/state accounting and immutable-reference handoff here.
                val observer = launch(Dispatchers.Default) { model.camera.state.collect { state ->
                    val seq = state.measurement?.sequence ?: state.preview?.sequence ?: return@collect
                    if (seq <= previous) return@collect
                    val measurement = state.measurement as? Ht301ThermalMeasurement
                    val receipt = measurement?.receivedMonotonicMs ?: state.preview?.receivedMonotonicMs ?: SystemClock.elapsedRealtime()
                    val relative = (receipt - origin).coerceAtLeast(0) * 1000000
                    if (previous >= 0 && seq > previous + 1) { missed += seq - previous - 1
                        prep?.offer(OwnedObservation.gap(previous + 1, seq - 1, relative, "source-or-stateflow-unobserved")) }
                    previous = seq
                    val code = state.status.detail?.machineCode ?: state.status.code.name; states[code] = (states[code] ?: 0) + 1
                    if (measurement == null) { unavailable++; prep?.offer(OwnedObservation.gap(seq, seq, relative, code)) }
                    else { accepted++; measurementAge += SystemClock.elapsedRealtime() - measurement.receivedMonotonicMs
                        costs.attached { StageCosts.timed("immutable_handoff") { prep?.offer(measurement) } } }
                } }
                save("progress.json", mapOf("phase" to "running", "stage" to stage))
                repeat(duration * 10) { tick -> delay(100)
                    val cheap = memory(false)
                    for (key in listOf("java_used", "native_heap")) (cheap[key] as? Long)?.let { cheapPeaks[key] = maxOf(cheapPeaks[key] ?: 0, it) }
                    // Observed live array inventory, conservative if references overlap. Does not
                    // replace measured process increments; object headers/codec scratch excluded.
                    val owned = mapOf("preparation_queue_arrays" to (prep?.backlog() ?: 0), "preparation_inflight_source_arrays" to (prep?.inFlightBytes ?: 0),
                        "writer_queue_payload_context" to (writer?.backlog() ?: 0), "writer_inflight_payload_context" to (writer?.inFlightBytes ?: 0),
                        "chunk_pending_payload" to (writer?.recorder?.pendingPayloadBytes ?: 0), "chunk_encoding_arrays" to (writer?.recorder?.encodingBufferBytes ?: 0))
                    for ((key, value) in owned) ownedPeak[key] = maxOf(ownedPeak[key] ?: 0, value)
                    ownedPeak["sum_observed_slots"] = maxOf(ownedPeak["sum_observed_slots"] ?: 0, owned.values.sum())
                    if (tick % 10 == 0) {
                        model.presentation.state.value.measurement?.let { age += SystemClock.elapsedRealtime() - it.receivedMonotonicMs }
                        samples += memory(true)
                        backlog += mapOf("second" to tick / 10, "preparation" to (prep?.backlog() ?: 0), "writer" to (writer?.backlog() ?: 0))
                        if (tick % 50 == 0) save("progress.json", mapOf("phase" to "running", "stage" to stage, "seconds" to tick / 10,
                            "accepted" to accepted, "preparation_drops" to (prep?.drops ?: 0), "writer_drops" to (writer?.drops ?: 0),
                            "backlog" to backlog.last(), "source_age_ms" to age.lastOrNull()))
                    }
                }
                observer.cancelAndJoin(); renderJob.cancelAndJoin()
                val elapsed = (SystemClock.elapsedRealtime() - origin) / 1000.0; val after = model.camera.state.value.statistics
                val stopStart = SystemClock.elapsedRealtime(); val prepared = prep?.stop(); val finalized = writer?.stop()
                val stopMs = SystemClock.elapsedRealtime() - stopStart
                val beforeGC = memory(true); System.gc(); delay(1000); val afterGC = memory(true)
                val pass = profile == null || prepared == true && finalized == true && prep!!.drops == 0L && prep.lostSourceGapSequences == 0L && writer!!.drops == 0L &&
                    accepted > elapsed * 20 && writer.recorder.committedMeasurements == accepted
                reports += mapOf("stage" to stage, "duration_s" to elapsed, "callback_fps" to (after.received - before.received) / elapsed,
                    "preferred_chunk_bytes" to preferredChunkBytes,
                    "acquired" to after.received - before.received, "replaced" to after.replaced - before.replaced, "malformed" to after.malformed - before.malformed,
                    "accepted" to accepted, "unavailable" to unavailable, "sequence_unobserved" to missed, "states" to states,
                    "preparation_drops" to (prep?.drops ?: 0), "preparation_lost_source_gap_sequences" to (prep?.lostSourceGapSequences ?: 0),
                    "writer_drops" to (writer?.drops ?: 0), "preparation_queue_max_bytes" to (prep?.maxBytes ?: 0), "writer_queue_max_bytes" to (writer?.maxBytes ?: 0),
                    "finalized" to finalized, "preparation_failure" to prep?.failure, "failure" to writer?.failure, "stop_ms" to stopMs,
                    "committed_entries" to writer?.recorder?.committedEntries, "saved_measurements" to writer?.recorder?.committedMeasurements,
                    "committed_gap_sequences" to writer?.recorder?.committedGapSequences, "chunks" to writer?.recorder?.committedChunks,
                    "file_bytes" to File(directory, "$stage.r2proto").length(), "physical_bytes" to writer?.recorder?.physicalBytes,
                    "stored_bytes" to writer?.recorder?.storedBytes, "backlog_by_second" to backlog, "stages" to costs.snapshot(),
                    "render_fps" to rendered / elapsed, "render_ns" to distribution(renderMs), "display_source_age_ms" to distribution(age),
                    "measurement_observation_age_ms" to distribution(measurementAge), "memory_start" to memoryStart, "memory_samples" to samples,
                    "memory_100ms_peaks" to cheapPeaks, "owned_slots_peak_bytes" to ownedPeak, "memory_before_gc" to beforeGC, "memory_after_gc" to afterGC,
                    "throughput_gate" to pass)
                save("candidate.json", mapOf("revision" to args.getString("r2Revision"), "optimized" to true, "runs" to reports,
                    "limitations" to listOf("Source StateFlow gaps distinct from preparation/writer drops", "Process allocations include camera/presentation",
                        "Java/native 100ms; PSS/RSS 1s sampled peaks may miss transients", "Owned slot counters exclude object headers, encoder text and codec scratch; overlapping references conservatively double counted")))
                writer?.close()
                if (!pass) { save("progress.json", mapOf("phase" to "escalation_stopped", "stage" to stage)); return@use }
            }
            save("progress.json", mapOf("phase" to "finished"))
        }
    }
}
