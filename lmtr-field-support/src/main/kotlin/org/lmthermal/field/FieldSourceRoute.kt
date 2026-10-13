package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301ModuleProfile
import org.lmthermal.recording.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.util.concurrent.CompletableFuture

/** Test-only tee on the existing single acquisition owner, not another camera stream.
 * Baseline counts the same pre-UI observations as recording. Epoch guards reject captured old callbacks.
 * Diagnostics stay small on acquisition; oracle hashing happens only on the charged preparation worker.
 */
class FieldSourceRoute<P>(private val observer:(CameraAcquiredObservation)->Unit,
    private val lossObserver:(CameraSourceLoss)->Unit,
    private val adapterFactory:(CameraSourceIdentity)->CameraRecordingAdapter?) : CameraSessionLifetime<P> {
    private val lock=Any()
    private var epoch=0L
    private var upstream:AutoCloseable?=null
    private var target:CameraObservationConsumer?=null
    @Volatile var adapter:CameraRecordingAdapter?=null;private set
    private val binding=CameraRecorderBinding<P> { adapter }
    override fun opened(session:CameraSession<P>) = synchronized(lock) {
        upstream?.close();target=null;epoch++
        val source=session.observations
        adapter=source?.let { adapterFactory(it.identity) }
        if(source==null || adapter==null){binding.opened(session);return@synchronized}
        val token=epoch
        val tee=object:CameraObservationSource {
            override val identity=source.identity
            override fun subscribe(consumer:CameraObservationConsumer):AutoCloseable=synchronized(lock) {
                check(token==epoch && target==null);target=consumer
                AutoCloseable { synchronized(lock){if(target===consumer)target=null} }
            }
        }
        binding.opened(object:CameraSession<P> {
            override val state=session.state
            override val observations=tee
            override suspend fun perform(action:CameraAction)=session.perform(action)
            override suspend fun close(reason:CameraCloseReason)=session.close(reason)
        })
        upstream=source.subscribe(object:CameraObservationConsumer {
            override fun observation(value:CameraAcquiredObservation)=synchronized(lock) {
                if(token!=epoch)return@synchronized
                observer(value);target?.observation(value)
            }
            override fun loss(value:CameraSourceLoss)=synchronized(lock) {
                if(token!=epoch)return@synchronized
                lossObserver(value);target?.loss(value)
            }
            override fun ended(reason:CameraCloseReason)=synchronized(lock) {
                if(token!=epoch)return@synchronized
                target?.ended(reason);target=null;adapter=null;epoch++
            }
        })
    }
    override fun invalidated(reason:CameraCloseReason)=synchronized(lock) {
        epoch++;upstream?.close();upstream=null;target=null;adapter=null;binding.invalidated(reason)
    }
    fun start(engine:RecorderEngine,expectedIdentity:CameraSourceIdentity?=adapter?.identity):Boolean=synchronized(lock) {
        if(adapter?.identity!=expectedIdentity){engine.stop("field_source_replaced",true);false} else binding.start(engine)
    }
    fun stop():CompletableFuture<RecorderResult>?=synchronized(lock){binding.stop()}
}

/** Capture independent original-owner hashes before adapter transformation, off the acquisition thread.
 * The sink must stream bounded JSONL. A sink failure fails recording rather than hiding oracle loss.
 * Context JSON is separately retained from the adapter and explicitly not an independent context oracle.
 */
class FieldOracleAdapter(private val delegate:CameraRecordingAdapter,
    private val capture:(CameraAcquiredObservation)->Obj,
    private val sink:(Obj)->Unit, private val now:()->Long):CameraRecordingAdapter {
    override val identity=delegate.identity
    override val stream=delegate.stream
    override val maximumCharge=delegate.maximumCharge
    override fun loss(source:CameraSourceLoss)=delegate.loss(source)
    override fun offer(source:CameraAcquiredObservation):RecordingOffer {
        val lower=now()
        val offered=delegate.offer(source)
        return RecordingOffer(offered.streamId,offered.sequence,offered.stream,offered.chargedBytes) {
            val upper=now()
            val independent=capture(source)+mapOf("intake_lower_monotonic_ns" to lower.toString(),"intake_upper_monotonic_ns" to upper.toString())
            val original=offered.materialize()
            sink(independent+mapOf("stream_id" to original.streamId,"sequence" to original.sequence.toString(),
                "session_id" to original.stream.value()["session_id"],"adapter_context" to original.context.value(),
                "adapter_facts" to original.facts.value()))
            original
        }
    }
}

/** Match the actual registry ID, not the namespaced payload encoding ID. */
object HtFieldRoute {
    fun native(identity:CameraSourceIdentity):Ht301RecordingAdapter? =
        if(identity.module.id!=Ht301ModuleProfile.ID)null else Ht301RecordingAdapter(identity)
    fun adapter(identity:CameraSourceIdentity,sink:(Obj)->Unit,now:()->Long):CameraRecordingAdapter? =
        native(identity)?.let { FieldOracleAdapter(it,HtFieldOracle::capture,sink,now) }
}
