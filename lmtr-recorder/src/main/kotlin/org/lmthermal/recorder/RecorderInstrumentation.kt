package org.lmthermal.recorder

import java.util.concurrent.atomic.AtomicLong

/** Fixed diagnostic vocabulary, not wire events or scheduling instructions. Nested scopes overlap. */
enum class RecorderPhase {
    CHUNK_BUILD, CHUNK_FIRST_VALIDATION, CHUNK_ENCODE_TOTAL, APPEND_CHUNK,
    BACKEND_APPEND, ENTRY_PROCESSING, CHECKPOINT, FINALIZATION, BACKEND_CLOSE, READBACK
}

/** A separate wall clock must never advance or replace the recorder's intake/timer witnesses. */
fun interface RecorderMetricClock { fun nanoTime(): Long }

/** Scalar-only synchronous observer. It must be bounded, nonblocking and retain no payload owner.
 * elapsedNs=-1 denotes unavailable/invalid timing; completed=false denotes an operation that threw,
 * not necessarily Thread interruption. Unknown attribution uses -1, never an invented zero.
 * Gap counts describe entry records, not the number of missing source observations in a gap.
 */
fun interface RecorderMetricObserver {
    fun record(phase: RecorderPhase, elapsedNs: Long, completed: Boolean, recordType: Int,
               bodyBytes: Long, observations: Long, gapEntries: Long)
}

/** Optional diagnostics allocated before workers start. No event list, exception, body or ID is kept.
 * Clock/observer failures cannot replace a recorder failure or poison an acknowledged footer.
 * Counters saturate explicitly; even this isolation cannot promise survival of an exhausted ART VM.
 */
class RecorderInstrumentation(private val observer: RecorderMetricObserver,
    private val clock: RecorderMetricClock = RecorderMetricClock(System::nanoTime)) {
    private val clockFailures = AtomicLong()
    private val observerFailures = AtomicLong()

    /** Read diagnostics after worker termination; these totals do not certify metrics completeness. */
    fun failures(): Pair<Long, Long> = clockFailures.get() to observerFailures.get()

    /** A sentinel reserves one clock value; negative nanoTime origins otherwise remain legal. */
    @PublishedApi internal fun begin(): Long = try { clock.nanoTime() }
        catch (_: Throwable) { increment(clockFailures); Long.MIN_VALUE }

    /** Preserve the original operation's exception even when the clock or observer also fails. */
    @PublishedApi internal fun end(phase: RecorderPhase, start: Long, completed: Boolean,
        recordType: Int = -1, bodyBytes: Long = -1, observations: Long = -1, gapEntries: Long = -1) {
        val finish = if (start == Long.MIN_VALUE) Long.MIN_VALUE else begin()
        val elapsed = if (start == Long.MIN_VALUE || finish == Long.MIN_VALUE || finish < start ||
            (start < 0 && finish > Long.MAX_VALUE + start)) -1L else finish - start
        try { observer.record(phase, elapsed, completed, recordType, bodyBytes, observations, gapEntries) }
        catch (_: Throwable) { increment(observerFailures) }
    }

    /** Inline scopes create no per-operation event/lambda owner and report failures in finally. */
    internal inline fun <T> measure(phase: RecorderPhase, recordType: Int = -1, bodyBytes: Long = -1,
        observations: Long = -1, gapEntries: Long = -1, operation: () -> T): T {
        val start = begin()
        var completed = false
        try { val result = operation(); completed = true; return result }
        finally { end(phase, start, completed, recordType, bodyBytes, observations, gapEntries) }
    }

    /** Saturation never wraps a diagnostic into a misleading negative failure count. */
    private fun increment(counter: AtomicLong) {
        while (true) {
            val value = counter.get()
            if (value == Long.MAX_VALUE || counter.compareAndSet(value, value + 1)) return
        }
    }
}
