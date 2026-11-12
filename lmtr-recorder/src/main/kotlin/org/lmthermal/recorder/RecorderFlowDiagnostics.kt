package org.lmthermal.recorder

import org.lmthermal.lmtr.Obj

/** Optional scalar boundary markers, not wire entries, decisions or source timestamps. */
enum class RecorderFlowKind { ADMIT, PREPARATION_TAKE, WRITER_TAKE, RESERVE, RELEASE,
    PENDING_ADD, SEAL_BEGIN, ENCODE_VALIDATED, APPEND_BEGIN, BACKEND_BEGIN,
    BACKEND_DURABLE_RETURN, ACK, SEAL_END, STOP, FIRST_FAILURE }

/** Implementations must not block, call the engine, allocate payloads or retain owners.
 * Long.MIN_VALUE means unavailable witness; -1 means unavailable count/sequence.
 * The built-in collector uses its own clock and never calls the recorder's policy clock.
 */
fun interface RecorderFlowObserver {
    fun record(kind:RecorderFlowKind,preparationWitness:Long,writerWitness:Long,pendingWitness:Long,
        currentWitness:Long,controlBytes:Long,acknowledged:Long,preparationBytes:Long,writerBytes:Long,
        pendingBytes:Long,observations:Long,gaps:Long,controlDelta:Long,sequence:Long,runCount:Long)
}

/** Preallocated fixed-prefix collector; truncation is explicit, not a fabricated full trace.
 * Default256*16*8=32768 primitive bytes plus objects. The report allocates bounded JSON
 * only AFTER worker termination. Diagnostic memory is additional, not evidence of ART admission.
 * Lock order is engine -> collector; this collector never calls the engine or an external observer.
 */
class RecorderFlowTrace(private val capacity:Int=256,
    private val clock:RecorderMetricClock=RecorderMetricClock(System::nanoTime)):RecorderFlowObserver {
    private val rows:LongArray
    private var size=0
    private var omitted=0L
    private var clockFailures=0L
    init {require(capacity in 1..4096);rows=LongArray(capacity*16)}
    override fun record(kind:RecorderFlowKind,preparationWitness:Long,writerWitness:Long,pendingWitness:Long,
        currentWitness:Long,controlBytes:Long,acknowledged:Long,preparationBytes:Long,writerBytes:Long,
        pendingBytes:Long,observations:Long,gaps:Long,controlDelta:Long,sequence:Long,runCount:Long) = synchronized(this) {
        if(size==capacity) {if(omitted<Long.MAX_VALUE)omitted++;return@synchronized}
        val at=try {clock.nanoTime()}catch(_:Throwable){if(clockFailures<Long.MAX_VALUE)clockFailures++;Long.MIN_VALUE}
        val i=size++*16
        rows[i]=kind.ordinal.toLong();rows[i+1]=at;rows[i+2]=preparationWitness;rows[i+3]=writerWitness
        rows[i+4]=pendingWitness;rows[i+5]=currentWitness;rows[i+6]=controlBytes;rows[i+7]=acknowledged
        rows[i+8]=preparationBytes;rows[i+9]=writerBytes;rows[i+10]=pendingBytes;rows[i+11]=observations
        rows[i+12]=gaps;rows[i+13]=controlDelta;rows[i+14]=sequence;rows[i+15]=runCount
    }
    /** Post-termination reporting only; nanoseconds are original scalars, not durations inferred from FPS. */
    fun report():Obj=synchronized(this) {
        mapOf("capacity" to capacity,"primitive_bytes" to rows.size*8,"retained" to size,
            "omitted" to omitted.toString(),"clock_failures" to clockFailures.toString(),
            "omitted_at_limit" to (omitted==Long.MAX_VALUE),"clock_failures_at_limit" to (clockFailures==Long.MAX_VALUE),
            "columns" to listOf("kind","diagnostic_monotonic_ns","preparation_oldest_witness","writer_oldest_witness",
                "pending_oldest_witness","current_witness","control_bytes","acknowledged_observations",
                "preparation_queue_bytes","writer_queue_bytes","pending_bytes","observation_entries","gap_entries",
                "control_delta","sequence","run_count"),
            "rows" to (0 until size).map { n -> (0 until 16).map { k ->
                val value=rows[n*16+k]
                if(k==0)RecorderFlowKind.entries[value.toInt()].name else if(value==Long.MIN_VALUE)null else value.toString()
            } },"scope" to "inclusive boundaries; individual body/footer sync timestamps unavailable")
    }
}
