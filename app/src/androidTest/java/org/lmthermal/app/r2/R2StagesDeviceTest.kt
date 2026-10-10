package org.lmthermal.app.r2

import android.graphics.Bitmap
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
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.r2.*
import java.io.File
import java.util.ArrayDeque

/** Opt-in controlled ablations. No camera action, synthetic readiness or production recorder.
 * Baseline and legacy freeze run on the original scheduler before optimization.
 */
@RunWith(AndroidJUnit4::class)
class R2StagesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun memory(): Map<String, Any?> {
        val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); val runtime = Runtime.getRuntime()
        return mapOf("java_used" to runtime.totalMemory()-runtime.freeMemory(),
            "native_heap" to Debug.getNativeHeapAllocatedSize(), "pss_bytes" to info.totalPss.toLong()*1024,
            "rss_bytes" to File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
                ?.trim()?.split(Regex("\\s+"))?.get(1)?.toLong()?.times(1024),
            "process_allocated_bytes" to Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull())
    }
    private fun distribution(values: List<Long>): Map<String, Any?> {
        val sorted=values.sorted()
        return mapOf("p50" to sorted.getOrNull((sorted.size*.5).toInt()),
            "p95" to sorted.getOrNull((sorted.size*.95).toInt()), "max" to sorted.lastOrNull(), "samples" to sorted.size)
    }
    /** Constant-space disposable sink; no durability claim, no retained whole recording. */
    private class DiscardSink : AppendSink {
        override fun write(bytes: ByteArray) = Unit
        override fun sync() = Unit
        override fun close() = Unit
    }
    /** Same bytes/files but sync deliberately omitted, explicitly labeled as nondurable ablation. */
    private class NoSyncSink(file: File) : AppendSink {
        private val delegate=FileSink(file)
        override fun write(bytes: ByteArray) = delegate.write(bytes)
        override fun sync() = Unit
        override fun close() = delegate.close()
    }
    /** Queue-only experiment keeps the writer's byte limit, with no unbounded jobs or arrays. */
    private class DiscardQueue(private val costs: StageCosts) {
        private val monitor=Object(); private val queue=ArrayDeque<Pair<Observation,Long>>()
        private var bytes=0L; private var stopping=false
        var drops=0L; var maxBytes=0L
        private val worker=Thread({ while (true) {
            val item=synchronized(monitor) {
                while (queue.isEmpty() && !stopping) monitor.wait()
                if (queue.isEmpty()) null else queue.removeFirst().also { bytes-=it.first.bytes }
            } ?: break
            costs.record("discard_queue_wait",System.nanoTime()-item.second)
        } },"r2-discard-worker").apply { start() }
        fun offer(frame: Observation) = synchronized(monitor) {
            if (bytes+frame.bytes>Bounds.QUEUE || queue.size>=128) drops++
            else { queue.addLast(frame to System.nanoTime()); bytes+=frame.bytes; maxBytes=maxOf(maxBytes,bytes); monitor.notifyAll() }
        }
        fun backlog() = synchronized(monitor) { bytes }
        fun stop() { synchronized(monitor) { stopping=true; monitor.notifyAll() }; worker.join(15000); check(!worker.isAlive) }
    }
    @Test fun controlledStages() = runBlocking {
        assumeTrue(args.getString("r2Stages") != null)
        val stages=args.getString("r2Stages")!!.split(',')
        require(stages.all { it in listOf("baseline","observe","freeze","queue","discard","nosync","file") })
        val duration=args.getString("r2Seconds","60")!!.toInt(); require(duration in 30..600)
        val directory=File(context.filesDir,"recording-r2/"+args.getString("r2Run"))
        require(!directory.exists()); directory.mkdirs()
        val reports=mutableListOf<Map<String,Any?>>()
        fun save(name:String,value:Map<String,Any?>) { File(directory,name).writeBytes(R2Json.encode(value)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model:CameraViewModel
            scenario.onActivity { model=ViewModelProvider(it)[CameraViewModel::class.java]; it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            withTimeout(1200000) { while (true) {
                instrumentation.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()?.let {
                        model=ViewModelProvider(it)[CameraViewModel::class.java]; it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                save("progress.json",mapOf("phase" to "awaiting_operator_ready","state" to model.camera.state.value.status.code.name))
                if (model.camera.state.value.measurement != null) break
                delay(200)
            } }
            for (stage in stages) {
                save("progress.json",mapOf("phase" to "warmup","stage" to stage)); delay(10000)
                System.gc(); delay(1000)
                val costs=StageCosts(Debug::threadCpuTimeNanos)
                val origin=SystemClock.elapsedRealtime(); val before=model.camera.state.value.statistics
                val memoryBefore=memory(); val samples=mutableListOf<Map<String,Any?>>()
                val backlog=mutableListOf<Long>(); val age=mutableListOf<Long>(); val renderMs=mutableListOf<Long>(); val measurementAge=mutableListOf<Long>()
                val writer=if (stage in listOf("discard","nosync","file")) BoundedRecorder(File(directory,"$stage.r2proto"),Profile.ANALYSIS,Stored,costs,
                    sinkFactory = { file -> when(stage) { "discard" -> DiscardSink(); "nosync" -> NoSyncSink(file); else -> FileSink(file) } }) else null
                val discard=if (stage=="queue") DiscardQueue(costs) else null
                var accepted=0L; var unavailable=0L; var missed=0L; var previous=-1L; var rendered=0L; var lastRender=-1L
                val renderJob=launch(Dispatchers.Default) { model.presentation.state.collect { shown ->
                    shown.measurement?.let { if (it.sequence>lastRender) { lastRender=it.sequence; rendered++; renderMs+=(shown.renderMs*1000000).toLong() } }
                } }
                val observer=if(stage=="baseline") null else launch(Dispatchers.Default) {
                    model.camera.state.collect { state ->
                        val seq=state.measurement?.sequence ?: state.preview?.sequence ?: return@collect
                        if(seq<=previous) return@collect
                        if(previous>=0 && seq>previous+1) { missed+=seq-previous-1
                            writer?.offer(Observation(previous+1,null,(SystemClock.elapsedRealtime()-origin)*1000000,384,288,null,reason="source-or-stateflow-unobserved",gapEnd=seq-1)) }
                        previous=seq
                        val measurement=state.measurement as? Ht301ThermalMeasurement
                        if(measurement==null) { unavailable++; writer?.offer(Observation(seq,null,(SystemClock.elapsedRealtime()-origin)*1000000,384,288,null,reason=state.status.detail?.machineCode ?: state.status.code.name)); return@collect }
                        accepted++; measurementAge+=SystemClock.elapsedRealtime()-measurement.receivedMonotonicMs
                        if(stage!="observe") { val frame=costs.attached { StageCosts.timed("freeze_total") { freezeLegacy(measurement,Profile.ANALYSIS,origin) } }
                            writer?.offer(frame); discard?.offer(frame) }
                    }
                }
                repeat(duration) { second -> delay(1000)
                    model.presentation.state.value.measurement?.let { age+=SystemClock.elapsedRealtime()-it.receivedMonotonicMs }
                    samples+=memory(); backlog+=writer?.backlog() ?: discard?.backlog() ?: 0L
                    if(second%5==0) save("progress.json",mapOf("phase" to "running","stage" to stage,"seconds" to second+1,"accepted" to accepted,
                        "writer_drops" to (writer?.drops ?: discard?.drops ?: 0L),"backlog" to backlog.last(),"source_age_ms" to age.lastOrNull()))
                }
                observer?.cancelAndJoin(); renderJob.cancelAndJoin()
                val elapsed=(SystemClock.elapsedRealtime()-origin)/1000.0; val after=model.camera.state.value.statistics
                val finished=writer?.stop(); discard?.stop()
                val beforeGC=memory(); System.gc(); delay(1000); val afterGC=memory()
                reports+=mapOf("stage" to stage,"duration_s" to elapsed,"callback_fps" to (after.received-before.received)/elapsed,
                    "acquired" to after.received-before.received,"replaced" to after.replaced-before.replaced,"malformed" to after.malformed-before.malformed,
                    "accepted" to accepted,"unavailable" to unavailable,"sequence_unobserved" to missed,
                    "writer_drops" to (writer?.drops ?: discard?.drops ?: 0L),"queue_max_bytes" to (writer?.maxBytes ?: discard?.maxBytes ?: 0),
                    "finalized" to finished,"failure" to writer?.failure,"committed_entries" to writer?.recorder?.committedEntries,
                    "chunks" to writer?.recorder?.committedChunks,"file_bytes" to File(directory,"$stage.r2proto").length(),
                    "backlog_bytes_by_second" to backlog,"stages" to costs.snapshot(),"render_fps" to rendered/elapsed,"render_ns" to distribution(renderMs),
                    "display_source_age_ms" to distribution(age),"measurement_observation_age_ms" to distribution(measurementAge),
                    "memory_start" to memoryBefore,"memory_samples" to samples,"memory_before_gc" to beforeGC,"memory_after_gc" to afterGC)
                save("stages.json",mapOf("revision" to args.getString("r2Revision"),"plan" to "continuation-20261010",
                    "optimized" to false,"runs" to reports,"limitations" to listOf("Source StateFlow gaps explicitly counted","Allocation counter is process-wide, not isolated recorder allocation",
                        "Copy counters are lower bounds; BAOS growth is audited separately","Memory sampled at one second; post-GC is observational")))
                writer?.close()
            }
            save("progress.json",mapOf("phase" to "finished"))
        }
    }
}
