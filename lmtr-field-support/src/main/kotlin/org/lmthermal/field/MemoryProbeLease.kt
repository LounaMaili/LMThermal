package org.lmthermal.field

import java.util.concurrent.atomic.AtomicLong

/** Scalar-only single-flight lease; it owns no model, coroutine, frame or probe.
 * A blocked old collector must prevent another field ViewModel from spawning a replacement.
 * Release is token-checked and occurs only on actual job completion, never on Stop request.
 */
class MemoryProbeLease(start:Long=0) {
    private val next=AtomicLong(start)
    private val owner=AtomicLong()
    init {require(start>=0)}
    fun acquire():Long {
        val before=next.getAndUpdate {if(it==Long.MAX_VALUE)it else it+1}
        if(before==Long.MAX_VALUE)return 0
        val token=before+1
        return if(owner.compareAndSet(0,token))token else 0
    }
    fun release(token:Long):Boolean=token>0 && owner.compareAndSet(token,0)
}

/** Process-wide exclusion for field diagnostic collectors, not a recorder/source ownership rule. */
object MemoryProbeProcessLease {val lease=MemoryProbeLease()}
