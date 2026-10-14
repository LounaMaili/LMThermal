package org.lmthermal.camera

import java.util.UUID

/** Ephemeral source identities contain no device path/serial. Receipt clock is a genuine host domain. */
data class CameraSourceIdentity(val module:CameraModuleMetadata,
    val sessionId:String=UUID.randomUUID().toString(),val receiptClockId:String=UUID.randomUUID().toString())

/** Module-owned current acquisition evidence, before UI conflation. Implementations must be immutable.
 * Ordinal counts observations actually delivered to the module gate; sourceSequence is separately
 * qualified module evidence, never implicitly a device counter. Receipt is captured at source intake.
 * Recording adapters may retain this owner, but never borrow mutable/recycled transport buffers.
 */
interface CameraAcquiredObservation {
    val identity:CameraSourceIdentity
    val ordinal:Long
    val sourceSequence:Long?
    val receivedMonotonicNs:Long
    val measurement:ThermalMeasurement?
    val unavailableReason:String?
    val retainedBytes:Long
}

/** Explicit source loss signals are distinct from recorder queues and presentation conflation.
 * Unknown count remains unknown. Before/after are genuine host receipt bounds when available.
 */
data class CameraSourceLoss(val identity:CameraSourceIdentity,val reason:String,val count:Long?,
    val beforeMonotonicNs:Long?,val afterMonotonicNs:Long?)

/** Consumers perform only bounded non-blocking admission. No image hashing/compression/disk or UI waits. */
interface CameraObservationConsumer {
    fun observation(value:CameraAcquiredObservation)
    fun loss(value:CameraSourceLoss)
    fun ended(reason:CameraCloseReason)
}
interface CameraObservationSource {
    val identity:CameraSourceIdentity
    fun subscribe(consumer:CameraObservationConsumer):AutoCloseable
}

/** Optional single-consumer source route, inert without explicit subscription. No StateFlow/preview input.
 * Publication captures a registration then calls outside the hub lock. A subscriber must guard its epoch
 * against that last captured callback racing Close. A replaced registration cannot remove its successor.
 */
class CameraObservationHub(override val identity:CameraSourceIdentity):CameraObservationSource {
    private val lock=Any()
    private var consumer:CameraObservationConsumer?=null
    private var closed:CameraCloseReason?=null
    private var nextOrdinal=0L
    override fun subscribe(consumer:CameraObservationConsumer):AutoCloseable = synchronized(lock) {
        check(closed==null && this.consumer==null)
        this.consumer=consumer
        AutoCloseable { synchronized(lock){if(this.consumer===consumer)this.consumer=null} }
    }
    /** Counts every acquired observation; materialize the module owner only for an active consumer. */
    fun publish(factory:(Long)->CameraAcquiredObservation) {
        val captured=synchronized(lock){if(closed!=null)return;val ordinal=nextOrdinal++;consumer to ordinal}
        captured.first?.let { target -> deliver(target){target.observation(factory(captured.second))} }
    }
    fun loss(reason:String,count:Long?,beforeNs:Long?,afterNs:Long?) {
        val target=synchronized(lock){if(closed!=null)return;consumer}
        target?.let { deliver(it){it.loss(CameraSourceLoss(identity,reason,count,beforeNs,afterNs))} }
    }
    /** A broken optional recorder observer must not turn into a camera transport failure. */
    private fun deliver(target:CameraObservationConsumer,action:()->Unit) {
        try { action() } catch(_:Exception) {
            synchronized(lock){if(consumer===target)consumer=null}
            runCatching { target.ended(CameraCloseReason.FAILED) }
        }
    }
    /** Invalidate immediately; never await source release, encoder or disk from this callback. */
    fun finish(reason:CameraCloseReason) {
        val old=synchronized(lock){if(closed!=null)return;closed=reason;consumer.also { consumer=null }}
        old?.let { runCatching { it.ended(reason) } }
    }
}

/** Optional owner notifications, independent of conflated UI and existing camera acquisition ownership. */
interface CameraSessionLifetime<P> {
    fun opened(session:CameraSession<P>)
    fun invalidated(reason:CameraCloseReason)
}
