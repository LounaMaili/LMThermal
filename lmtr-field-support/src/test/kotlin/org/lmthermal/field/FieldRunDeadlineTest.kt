package org.lmthermal.field

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.recorder.RecordingStopDeadline
import java.util.concurrent.atomic.AtomicLong

/** No Android runtime or wall-clock sleeps: absolute targets, cancellation and scalar witnesses. */
class FieldRunDeadlineTest {
    @Test fun onlyExplicitTenAndSixtySecondPeriodicActionsHaveCheckedAbsoluteTargets() {
        assertEquals(10000000007L,FieldPeriodicDuration.deadline(7,10).monotonicNs)
        assertEquals(60000000007L,FieldPeriodicDuration.deadline(7,60).monotonicNs)
        for(seconds in listOf(0,9,11,30,61,120,146)) {
            try {FieldPeriodicDuration.deadline(0,seconds);fail("Unadvertised periodic duration")}
            catch(_:IllegalArgumentException){}
        }
        try {FieldPeriodicDuration.deadline(Long.MAX_VALUE-1,60);fail("Overflow must refuse")}
        catch(_:ArithmeticException){}
    }
    @Test fun earlyWakeRecomputesRemainingAndDoesNotAccumulateSamplerWork()=runBlocking {
        val clock=AtomicLong(0);val waits=mutableListOf<Long>();var stoppedAt:Long?=null
        FieldRunDeadline(RecordingStopDeadline(60000000000L),clock::get).awaitExpiry(pause={ms->
            waits.add(ms)
            clock.set(when(waits.size){1->1000000000L;2->59999500000L;else->60000500000L})
        },expired={stoppedAt=clock.get()})
        assertEquals(listOf(60000L,59000L,1L),waits);assertEquals(60000500000L,stoppedAt)
    }
    @Test fun delayedWakePreservesItsActualClockAndCallsExpiryOnce()=runBlocking {
        val clock=AtomicLong(0);var callbacks=0;var observed=0L
        FieldRunDeadline(RecordingStopDeadline(60000000000L),clock::get).awaitExpiry(pause={
            assertEquals(60000L,it);clock.set(60000023000L)
        },expired={callbacks++;observed=clock.get()})
        assertEquals(1,callbacks);assertEquals(60000023000L,observed)
    }
    @Test fun cancellationDoesNotDeliverALateExpiryToARunThatAlreadyStopped()=runBlocking {
        val entered=CompletableDeferred<Unit>();val hold=CompletableDeferred<Unit>();var callbacks=0
        val job=launch {FieldRunDeadline(RecordingStopDeadline(60000000000L),{0}).awaitExpiry(
            pause={entered.complete(Unit);hold.await()},expired={callbacks++})}
        entered.await();job.cancelAndJoin();hold.complete(Unit);yield()
        assertEquals(0,callbacks);assertFalse(job.isActive)
    }
    @Test fun clockRegressionRefusesInsteadOfWaitingAgainstAnInventedMonotonicReading()=runBlocking {
        val clock=AtomicLong(10);var callbacks=0
        try {
            FieldRunDeadline(RecordingStopDeadline(100),clock::get).awaitExpiry(
                pause={clock.set(9)},expired={callbacks++});fail("Regressed deadline clock")
        } catch(_:IllegalArgumentException){}
        assertEquals(0,callbacks)
    }
    @Test fun firstStopRequestIncludingZeroIsStableAndCountersAreRequestBrackets() {
        val clock=AtomicLong(0);val timing=FieldStopTiming();var received=1L;var closes=0
        timing.record(clock::get,{FieldStopCounters(received,0,0)}){received++;clock.set(10);closes++}
        val original=timing.report();assertEquals(0L,timing.requestedNs());assertEquals(10L,timing.callWallNs())
        clock.set(1000);timing.record(clock::get,{FieldStopCounters(999,0,0)}){closes++}
        assertEquals(original,timing.report());assertEquals(2,closes);assertEquals(2L,timing.afterCounters()!!.received)
    }
    @Test fun unknownSourceCounterWitnessStaysUnknownInsteadOfUsingALastSampleAsAnExactTotal() {
        val timing=FieldStopTiming();val clock=AtomicLong(3)
        timing.record(clock::get,{null}){clock.set(8)}
        assertNull(timing.afterCounters());assertNull(timing.report()["before_counters"])
        assertEquals(5L,timing.callWallNs())
    }
}
