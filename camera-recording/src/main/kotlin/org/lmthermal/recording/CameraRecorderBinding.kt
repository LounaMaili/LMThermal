package org.lmthermal.recording

import org.lmthermal.camera.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.util.concurrent.CompletableFuture

/** Module adapter owns interpretation, encoding and source lifetime; common binding never decodes a camera. */
interface CameraRecordingAdapter {
    val identity:CameraSourceIdentity
    val stream:FrozenJson
    val maximumCharge:Long
    fun offer(source:CameraAcquiredObservation):RecordingOffer
    fun loss(source:CameraSourceLoss):RecordingLoss
}

/** Disabled by default: composition roots opt into this lifetime observer explicitly.
 * Start takes an already-preflighted engine; create it off UI. Connect/open never starts recording.
 * Epoch check and admission/Stop are serialized under a short lock, without freeze/copy/hash/I/O.
 * An old captured callback cannot enter the next recording, even for the same physical device.
 * No background service; invalidation stops intake immediately, draining completes off UI.
 */
class CameraRecorderBinding<P>(private val adapterFor:(CameraSourceIdentity)->CameraRecordingAdapter?):CameraSessionLifetime<P> {
    private val lock=Any()
    private var generation=0L
    private var source:CameraObservationSource?=null
    private var adapter:CameraRecordingAdapter?=null
    private var subscription:AutoCloseable?=null
    private var engine:RecorderEngine?=null
    private var draining:CompletableFuture<RecorderResult>?=null
    var staleCallbacks=0L;private set
    override fun opened(session:CameraSession<P>) {
        val acquired=session.observations
        synchronized(lock) {
            stopLocked("session_replaced",true);generation++
            source=acquired;adapter=acquired?.let { adapterFor(it.identity) }
        }
    }
    override fun invalidated(reason:CameraCloseReason) = synchronized(lock) {
        generation++
        when(reason) {
            CameraCloseReason.FAILED -> stopLocked("acquisition_error",true,true)
            else -> stopLocked(reason.name.lowercase(),reason!=CameraCloseReason.USER)
        }
        source=null;adapter=null
    }
    /** Explicit feature/test request; refuse overlapping reservations while an earlier engine drains. */
    fun start(preflighted:RecorderEngine):Boolean = synchronized(lock) {
        val current=source;val module=adapter
        if(engine===preflighted)return@synchronized true
        if(current==null || module==null || engine!=null || draining?.isDone==false) {
            preflighted.stop("start_refused",true);return@synchronized false
        }
        val token=generation
        engine=preflighted
        val consumer=object:CameraObservationConsumer {
            override fun observation(value:CameraAcquiredObservation) = synchronized(lock) {
                if(token!=generation || engine!==preflighted || value.identity!=module.identity){staleCallbacks++;return@synchronized}
                try { preflighted.admit(module.offer(value)) }
                catch(_:Exception){stopLocked("recording_adapter_failure",true,true)}
            }
            override fun loss(value:CameraSourceLoss) = synchronized(lock) {
                if(token!=generation || engine!==preflighted || value.identity!=module.identity){staleCallbacks++;return@synchronized}
                try { preflighted.admitLoss(module.loss(value)) }
                catch(_:Exception){stopLocked("source_loss_evidence_failure",true,true)}
            }
            override fun ended(reason:CameraCloseReason) = synchronized(lock) {
                if(token!=generation || engine!==preflighted){staleCallbacks++;return@synchronized}
                generation++;stopLocked(reason.name.lowercase(),true,reason==CameraCloseReason.FAILED);source=null;adapter=null
            }
        }
        try { subscription=current.subscribe(consumer);true }
        catch(_:Exception){engine=null;draining=preflighted.failSource("source_already_ended");false}
    }
    /** Idempotent non-blocking cutoff; source stays available for a later explicit Start after drain. */
    fun stop():CompletableFuture<RecorderResult>? = synchronized(lock) { generation++;stopLocked("user_stop",false);draining }
    fun pendingStop():CompletableFuture<RecorderResult>? = synchronized(lock){draining}
    private fun stopLocked(reason:String,interrupted:Boolean,failed:Boolean=false) {
        subscription?.close();subscription=null
        val previous=engine;engine=null
        if(previous!=null)draining=if(failed)previous.failSource(reason) else previous.stop(reason,interrupted)
    }
}

/** Clock/loss serialization uses genuine source receipt stamps. No inferred sensor/device/UTC clock. */
object RecordingSourceMetadata {
    val unknownUtc:Obj=mapOf("status" to "unknown","clock_source" to "unknown","reason" to "no_utc_anchor")
    fun clock(identity:CameraSourceIdentity):Obj=mapOf("id" to identity.receiptClockId,"kind" to "host_monotonic","quality" to "known",
        "unit" to "ticks","resolution_ticks" to null,"tick_period_ns" to mapOf("numerator" to "1","denominator" to "1"))
    fun receipt(identity:CameraSourceIdentity,ns:Long):Obj=mapOf("status" to "known","stamp" to mapOf(
        "clock_domain_id" to identity.receiptClockId,"ticks" to ns.toString()),"utc" to unknownUtc)
    val acquisition:Obj=mapOf("status" to "unknown","stamp" to null,"utc" to unknownUtc,"reason" to "sensor_timestamp_not_exposed")
    fun loss(stream:FrozenJson,value:CameraSourceLoss):RecordingLoss {
        fun stamp(ns:Long?)=ns?.let { mapOf("clock_domain_id" to value.identity.receiptClockId,"ticks" to it.toString()) }
        return RecordingLoss(stream.value()["stream_id"].text(),stream.value(),mapOf("category" to "source_loss","reason" to value.reason,
            "count" to value.count?.toString(),"observation_first" to null,"observation_last" to null,"device_first" to null,"device_last" to null,
            "scheduled" to null,"evidence" to mapOf("basis" to if(value.count==null)"unknown" else "explicit_signal",
                "before" to stamp(value.beforeMonotonicNs),"after" to stamp(value.afterMonotonicNs))))
    }
}
