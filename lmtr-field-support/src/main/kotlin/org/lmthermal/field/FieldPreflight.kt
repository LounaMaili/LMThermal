package org.lmthermal.field

import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.util.UUID

/** Finite HT test admission, separate from format hard ceilings and sampled process memory.
 * Rates conservatively assume incompressible bytes, full observations and 32 KiB metadata/frame.
 * Unknown capacity stays unknown; the exclusive local backend still enforces real write failures.
 */
data class FieldPreflight(val profile:RecordingProfile,val seconds:Int,val availableBytes:Long?,val codec:Int=1) {
    val limits=RecorderLimits(maxObservationBytes=3L*1048576)
    val payloadBytesPerFrame=when(profile) {
        RecordingProfile.ANALYSIS -> 384L*288*4
        RecordingProfile.NATIVE -> 384L*288*6
        RecordingProfile.FULL -> 384L*288*4+224256 // native is an exact transport view
        else -> error("HT field protocol supports Analysis, Native and Full only")
    }
    val bytesPerSecond=Math.multiplyExact(payloadBytesPerFrame+32768,25)
    // Reserve both LMTR and a bounded pre-transform oracle: hashes/metadata, never a second scene copy.
    val storageCapBytes=Math.addExact(Math.multiplyExact(bytesPerSecond,seconds.toLong()),limits.reservedBytes)
    val headroomBytes=256L*1048576
    val requiredFreeBytes=Math.addExact(storageCapBytes,headroomBytes)
    val permitted:Boolean get()=availableBytes==null || availableBytes>=requiredFreeBytes
    init { require(seconds in 1..146);require(codec in 0..1);require(availableBytes==null || availableBytes>=0) }
    fun report():Obj=mapOf("profile" to profile.wire,"duration_seconds" to seconds,"codec" to codec,
        "estimated_bytes_per_second" to bytesPerSecond.toString(),"storage_cap_bytes" to storageCapBytes.toString(),
        "free_bytes" to availableBytes?.toString(),"capacity_status" to if(availableBytes==null)"unknown" else "known",
        "required_free_bytes" to requiredFreeBytes.toString(),"headroom_bytes" to headroomBytes.toString(),
        "reserved_logical_bytes" to limits.reservedBytes.toString(),"max_observation_bytes" to limits.maxObservationBytes.toString(),
        "preparation_bytes" to limits.preparationBytes.toString(),"writer_bytes" to limits.writerBytes.toString(),
        "preferred_chunk_bytes" to limits.preferredChunkBytes.toString(),"control_bytes" to limits.controlBytes.toString(),
        "permitted" to permitted,"memory_assurance" to "accounting_reservation_not_measured_rss")
}

/** Fresh process/run identity for the actual Android elapsedRealtimeNanos domain (includes suspend).
 * Never correlate System.nanoTime or sensor time by assuming a shared epoch. UTC/resolution unknown.
 * Reset/domain change invalidates this run; resumed recording must create a new header/file.
 */
class FieldClock(private val read:()->Long,val domainId:String=UUID.randomUUID().toString()) {
    val origin=read().also { require(it>=0) }
    private var last=origin
    private var sourceDomain:String?=null
    private var sourceLast:Long?=null
    @Synchronized fun now():Long=read().also { check(it>=last){"field_clock_regressed"};last=it }
    @Synchronized fun source(domain:String,tick:Long) {
        // Trusted HT route declares the same Android API/boot epoch with a separate source UUID.
        // A late receipt may precede HEADER origin; only recorder intake defines the timeline.
        check(sourceDomain==null || sourceDomain==domain){"field_clock_domain_changed"}
        check(tick>=0 && (sourceLast==null || tick>=sourceLast!!)){"source_clock_regressed"}
        sourceDomain=domain;sourceLast=tick
    }
    fun header():Obj=mapOf("format" to "lmthermal-recording","kind" to "recording",
        "container_version" to mapOf("major" to 1,"minor" to 0),"schema_version" to mapOf("major" to 1,"minor" to 0),
        "recording_id" to UUID.randomUUID().toString(),"producer" to mapOf("application_id" to "org.lmthermal.field","version" to "r4-field-1"),
        "creation_time" to mapOf("status" to "unknown","clock_source" to "unknown","reason" to "no_utc_anchor"),
        "required_features" to listOf("org.lmthermal.lmtr.contiguous-native-view-v1","org.lmthermal.lmtr.core-v1"),"codecs" to listOf(0,1),
        "timeline" to mapOf("clock_domain_id" to domainId,"origin_monotonic_ns" to origin.toString(),"resolution_ns" to null,"source" to "host_monotonic"),
        "streams" to listOf(mapOf("id" to "primary","purpose" to "thermal")),
        "privacy" to mapOf("sensitive_metadata" to "omitted","scene_content" to true,"consented_namespaces" to emptyList<Any>()))
}

/** Fixed maximum sample capacity: finite protocol does not grow an unbounded metrics list. */
class FieldDistribution(private val maximum:Int=8192) {
    private val values=LongArray(maximum);private var size=0;private var omitted=0L
    @Synchronized fun add(value:Long) { require(value>=0);if(size<maximum)values[size++]=value else omitted++ }
    @Synchronized fun report():Obj {
        val sorted=values.copyOf(size).sortedArray()
        fun at(fraction:Double)=if(size==0)null else sorted[((size-1)*fraction).toInt()].toString()
        return mapOf("count" to size,"omitted" to omitted.toString(),"p50_ns" to at(.5),"p95_ns" to at(.95),"max_ns" to at(1.0))
    }
}

/** Stable machine-readable snapshot, separate from private exception messages and OS process memory. */
fun fieldSnapshot(snapshot:RecorderSnapshot):Obj=mapOf(
    "state" to snapshot.state,"accepted" to snapshot.accepted.toString(),"acknowledged" to snapshot.acknowledged.toString(),
    "preparation_drops" to snapshot.preparationDrops.toString(),"writer_drops" to snapshot.writerDrops.toString(),
    "preparation_queued_bytes" to snapshot.preparationQueuedBytes.toString(),"writer_queued_bytes" to snapshot.writerQueuedBytes.toString(),
    "preparation_in_flight_bytes" to snapshot.preparationInFlightBytes.toString(),"writer_in_flight_bytes" to snapshot.writerInFlightBytes.toString(),
    "pending_bytes" to snapshot.pendingBytes.toString(),"control_bytes" to snapshot.controlBytes.toString(),
    "max_preparation_queued_bytes" to snapshot.maxPreparationQueuedBytes.toString(),"max_writer_queued_bytes" to snapshot.maxWriterQueuedBytes.toString(),
    "max_preparation_in_flight_bytes" to snapshot.maxPreparationInFlightBytes.toString(),"max_writer_in_flight_bytes" to snapshot.maxWriterInFlightBytes.toString(),
    "max_pending_bytes" to snapshot.maxPendingBytes.toString(),"chunks" to snapshot.chunks.toString(),"checkpoints" to snapshot.checkpoints.toString(),
    "error" to snapshot.error)

/** Executable camera-free estimate from the same preflight used on Android; no storage/device probe. */
object FieldPreflightCli {
    @JvmStatic fun main(args:Array<String>) {
        val free=args.firstOrNull()?.takeIf { it!="unknown" }?.toLong()
        println(LmtrJson.encode(mapOf("physical_executed" to false,"plans" to listOf(
            FieldPreflight(RecordingProfile.FULL,10,free).report())+listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL).map {
            FieldPreflight(it,146,free).report() })).toString(Charsets.UTF_8))
    }
}

/** Failure evidence is never a positive gate to launch a longer physical run. Baseline has no file. */
fun fieldReopenFailed(report:Obj?,recording:Boolean):Boolean = recording && (report==null || report["classification"]=="readback_failed")
