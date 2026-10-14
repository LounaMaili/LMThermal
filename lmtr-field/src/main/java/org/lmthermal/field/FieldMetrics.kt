package org.lmthermal.field

import android.os.Debug
import android.os.SystemClock
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301AcquiredObservation
import org.lmthermal.lmtr.*
import java.io.File

/** Bounded numerical diagnostics. Receipts stay private; reports contain aggregates, not scene data.
 * Presentation collector timestamps measure publication/collection freshness, not LCD/photons latency.
 */
class FieldMetrics {
    private val measurementWall=FieldDistribution();private val intervals=FieldDistribution();private val age=FieldDistribution();private val seals=FieldDistribution()
    private val codec=FieldDistribution();private var codecDecoded=0L;private var codecStored=0L
    private val encode=FieldDistribution();private val append=FieldDistribution();private val checkpoints=FieldDistribution()
    private var sourceWindowClosed=false
    private var lastReceipt:Long?=null;private var lastPreview:Long?=null;private var lastCheckpoint:Long?=null
    private var firstReceipt:Long?=null;private var acquisitionCpuFirst:Long?=null;private var acquisitionCpuLast:Long?=null
    private var received=0L;private var valid=0L;private var unavailable=0L;private var held=0L;private var invalid=0L
    private var preview=0L;private var sourceLoss=0L;private var unknownLoss=0L
    private var decoded=0L;private var stored=0L;private val losses=mutableMapOf<String,Long>()
    private val peaks=mutableMapOf<String,Long>();private var firstRss:Long?=null;private var lastRss:Long?=null
    private var samples=0L;private var firstSample:Long?=null;private var lastSample:Long?=null
    private var cpuInitial:Map<String,Long>?=null;private var cpuLatest:Map<String,Long>?=null
    @Synchronized fun observation(v:CameraAcquiredObservation) {
        if(sourceWindowClosed)return
        val tick=v.receivedMonotonicNs
        lastReceipt?.let { if(tick>=it)intervals.add(tick-it) }
        if(firstReceipt==null)firstReceipt=tick
        lastReceipt=tick;received++
        if(v.measurement!=null)valid++ else unavailable++
        val h=v as? Ht301AcquiredObservation
        if(h?.sessionReason=="held_image")held++
        h?.measurementWallNs?.let { measurementWall.add(it) }
        if(h?.inspection?.reason!=null)invalid++
        val cpu=Debug.threadCpuTimeNanos();if(acquisitionCpuFirst==null)acquisitionCpuFirst=cpu;acquisitionCpuLast=cpu
    }
    @Synchronized fun loss(v:CameraSourceLoss) {
        if(sourceWindowClosed)return
        v.count?.let { sourceLoss+=it;losses[v.reason]=(losses[v.reason]?:0)+it } ?: run { unknownLoss++ }
    }
    @Synchronized fun preview(receiptMs:Long) {
        if(sourceWindowClosed)return
        if(lastPreview==receiptMs)return
        lastPreview=receiptMs;preview++
        val latency=SystemClock.elapsedRealtimeNanos()-receiptMs*1000000
        if(latency>=0)age.add(latency)
    }
    /** Exclude heavy post-Stop readback/hash load from the matched acquisition/preview interval.
     * Codec/seal/drain observations remain active so finalization cost is still reported.
     */
    @Synchronized fun closeSourceWindow() { sourceWindowClosed=true }
    @Synchronized fun sealed(encodingNs:Long,appendNs:Long,raw:Long,body:Long) {
        encode.add(encodingNs);append.add(appendNs);seals.add(encodingNs+appendNs);decoded+=raw;stored+=body
    }
    @Synchronized fun codec(wall:Long,raw:Long,compressed:Long) { codec.add(wall);codecDecoded+=raw;codecStored+=compressed }
    @Synchronized fun checkpoint() { val tick=SystemClock.elapsedRealtimeNanos();lastCheckpoint?.let { checkpoints.add(tick-it) };lastCheckpoint=tick }
    /** OS counters are sampled independently of logical reservations; unavailable sources remain null. */
    fun sample():Obj {
        val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
        val runtime=Runtime.getRuntime()
        val rss=runCatching { File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.trim().split(Regex("\\s+"))[1].toLong()*1024 }.getOrNull()
        val data:Map<String,Long?> =mapOf("java_used_bytes" to runtime.totalMemory()-runtime.freeMemory(),
            "native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),"pss_bytes" to memory.totalPss*1024L,"rss_bytes" to rss)
        val cpus=runCatching {
            val result=mutableMapOf<String,Long>()
            for(task in File("/proc/self/task").listFiles().orEmpty()) {
                val name=File(task,"comm").readText().trim()
                if(name.startsWith("lmtr-")) {
                    val fields=File(task,"stat").readText().substringAfterLast(") ").split(' ')
                    result[name]=(result[name]?:0)+fields[11].toLong()+fields[12].toLong()
                }
            };result.toMap()
        }.getOrNull()
        synchronized(this) {
            samples++;val tick=SystemClock.elapsedRealtimeNanos();if(firstSample==null)firstSample=tick;lastSample=tick
            if(firstRss==null)firstRss=rss;lastRss=rss
            for((key,value)in data)if(value!=null)peaks[key]=maxOf(peaks[key]?:0,value)
            if(cpuInitial==null)cpuInitial=cpus;cpuLatest=cpus
        }
        return data.mapValues { it.value?.toString() }+mapOf("gc_runtime" to Debug.getRuntimeStats(),"recorder_thread_cpu_ticks" to cpus?.mapValues { it.value.toString() })
    }
    @Synchronized fun report():Obj {
        val elapsed=if(firstReceipt!=null && lastReceipt!=null)lastReceipt!!-firstReceipt!! else 0
        return mapOf("source_window_closed" to sourceWindowClosed,"observed_gate_deliveries" to received.toString(),"valid" to valid.toString(),"unavailable" to unavailable.toString(),
            "held" to held.toString(),"invalid" to invalid.toString(),"known_source_losses" to sourceLoss.toString(),"unknown_source_loss_signals" to unknownLoss.toString(),
            "source_loss_reasons" to losses.mapValues { it.value.toString() },"gate_fps" to if(elapsed>0)fieldMetricNumber((received-1)*1e9/elapsed) else null,
            "measurement_gate_wall" to measurementWall.report(),"intervals" to intervals.report(),"preview_publications" to preview.toString(),"preview_age" to age.report(),
            "seal_encode_plus_append" to seals.report(),"encode_validation_ns_not_codec_only" to encode.report(),"append_sync" to append.report(),
            "codec_call_wall" to codec.report(),"codec_decoded_bytes" to codecDecoded.toString(),"codec_stored_bytes" to codecStored.toString(),
            "codec_ratio" to if(codecDecoded>0)fieldMetricNumber(codecStored.toDouble()/codecDecoded) else null,
            "preview_publication_fps" to if(elapsed>0)fieldMetricNumber(preview*1e9/elapsed) else null,
            "checkpoint_intervals" to checkpoints.report(),"logical_retained_bytes" to decoded.toString(),"chunk_body_bytes" to stored.toString(),
            "body_to_logical_ratio" to if(decoded>0)fieldMetricNumber(stored.toDouble()/decoded) else null,"sampled_memory_peaks" to peaks.mapValues { it.value.toString() },
            "samples" to samples.toString(),"rss_first_bytes" to firstRss?.toString(),"rss_last_bytes" to lastRss?.toString(),
            "sample_span_ns" to if(firstSample!=null && lastSample!=null)(lastSample!!-firstSample!!).toString() else null,
            "acquisition_thread_cpu_delta_ns" to if(acquisitionCpuFirst!=null)(acquisitionCpuLast!!-acquisitionCpuFirst!!).toString() else null,
            "recorder_thread_cpu_initial_ticks" to cpuInitial?.mapValues { it.value.toString() },"recorder_thread_cpu_final_ticks" to cpuLatest?.mapValues { it.value.toString() },
            "limitations" to listOf("finite_samples_not_infinite_memory_bound","preview_collection_not_display_latency","encode_includes_hash_validation_codec_call_report_is_separate","cpu_ticks_need_os_CLK_TCK_no_guessed_scale"))
    }
}
