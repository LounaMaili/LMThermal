package org.lmthermal.field

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.lmthermal.recorder.RecordingStopDeadline
import org.lmthermal.lmtr.Obj

/** Explicit field-only durations. Acquisition-following buttons do not acquire periodic semantics. */
object FieldPeriodicDuration {
    const val REGRESSION_SECONDS=10
    const val QUALIFICATION_SECONDS=60
    const val NANOS_PER_SECOND=1000000000L
    fun deadline(startNs:Long,seconds:Int):RecordingStopDeadline {
        require(startNs>=0 && seconds in setOf(REGRESSION_SECONDS,QUALIFICATION_SECONDS))
        return RecordingStopDeadline(Math.addExact(startNs,Math.multiplyExact(seconds.toLong(),NANOS_PER_SECOND)))
    }
}

/** One cancellable coroutine on the field's existing dispatcher, not a new thread/timer queue.
 * Every wake recomputes the remaining time against the absolute target. Early wakes cannot
 * expire it, and delayed wakes never substitute the target for an actual clock reading.
 * The caller must close over only the original run/engine, never stop a replacement implicitly.
 */
class FieldRunDeadline(private val target:RecordingStopDeadline,private val now:()->Long) {
    suspend fun awaitExpiry(pause:suspend (Long)->Unit={delay(it)},expired:()->Unit) {
        var previous:Long?=null
        while(true) {
            currentCoroutineContext().ensureActive()
            val tick=now();require(tick>=0 && (previous==null || tick>=previous!!)){"Deadline clock regressed"}
            previous=tick
            if(tick>=target.monotonicNs){expired();return}
            val remaining=target.monotonicNs-tick
            // Ceiling in integer ms without overflowing remaining+999999 near signed-64 limits.
            pause(remaining/1000000L+if(remaining%1000000L==0L)0L else 1L)
        }
    }
}

/** Immutable counters around the real Stop request, not a last 1 Hz sample presented as a total. */
data class FieldStopCounters(val received:Long,val replaced:Long,val malformed:Long) {
    fun report():Obj=mapOf("received" to received.toString(),"replaced" to replaced.toString(),"malformed" to malformed.toString())
}

/** One per run; original deadline callbacks retain this instance, never a successor's witness.
 * First assignment is stable. Repeated cutoff requests remain idempotent at the binding/engine.
 * Counter reads bracket that request; they are not exact sensor exposure/packet timestamps.
 * No callback/owner is retained after record returns, and no I/O runs inside the close action.
 */
class FieldStopTiming {
    private var requestNs:Long?=null;private var wallNs:Long?=null
    private var before:FieldStopCounters?=null;private var after:FieldStopCounters?=null
    @Synchronized fun record(now:()->Long,counters:()->FieldStopCounters?,close:()->Unit) {
        if(requestNs!=null){close();return}
        requestNs=now();before=counters()
        try {close()} finally {after=counters();wallNs=now()-requestNs!!}
    }
    @Synchronized fun requestedNs():Long?=requestNs
    @Synchronized fun callWallNs():Long?=wallNs
    @Synchronized fun afterCounters():FieldStopCounters?=after
    @Synchronized fun report():Obj=mapOf("first_request_monotonic_ns" to requestNs?.toString(),
        "call_wall_ns" to wallNs?.toString(),"before_counters" to before?.report(),"after_counters" to after?.report(),
        "scope" to "counter_read_bracket_around_first_request_not_exact_sensor_frame_boundary")
}
