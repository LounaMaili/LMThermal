package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Prototype bookkeeping invariants only; not proof of Android owners, RSS or observer timing. */
class MemoryClaimsTest {
    @Test fun declaredBytesReturnToZeroAndSnapshotDoesNotBorrowState() {
        val c=MemoryClaims();val a=c.begin(MemoryClaims.Stage.WRITER,4);val b=c.begin(MemoryClaims.Stage.WRITER,6)
        val snapshot=c.snapshot();assertEquals(10L,snapshot.live[MemoryClaims.Stage.WRITER.ordinal])
        snapshot.live.fill(999);assertEquals(10L,c.snapshot().live[MemoryClaims.Stage.WRITER.ordinal])
        assertTrue(c.end(a));assertTrue(c.end(b));assertEquals(0,c.snapshot().active)
        assertEquals(10L,c.snapshot().peak[MemoryClaims.Stage.WRITER.ordinal])
    }
    @Test fun fullTelemetryCapacityOmitsRatherThanGrowingOrChangingARecorder() {
        val c=MemoryClaims();val tokens=LongArray(64){c.begin(MemoryClaims.Stage.SOURCE,1)}
        assertEquals(0L,c.begin(MemoryClaims.Stage.SOURCE,1));assertEquals(1L,c.snapshot().omitted)
        tokens.forEach {assertTrue(c.end(it))};assertEquals(0,c.snapshot().active);assertFalse(c.snapshot().qualified)
        assertTrue(c.begin(MemoryClaims.Stage.SOURCE,1)>0)
    }
    @Test fun overflowOrInvalidByteClaimsAreStickyDiagnosticUnknowns() {
        val c=MemoryClaims();val token=c.begin(MemoryClaims.Stage.CODEC,Long.MAX_VALUE)
        assertTrue(token>0);assertEquals(0L,c.begin(MemoryClaims.Stage.CODEC,1));assertTrue(c.end(token))
        assertEquals(0L,c.begin(MemoryClaims.Stage.CODEC,-1));assertFalse(c.snapshot().qualified)
        assertEquals(0L,MemoryClaims(Long.MAX_VALUE).begin(MemoryClaims.Stage.CODEC,1))
    }
    @Test fun duplicateOrStaleReleaseDoesNotSubtractAnotherOwnersBytes() {
        val c=MemoryClaims();val old=c.begin(MemoryClaims.Stage.WRITER,4);assertTrue(c.end(old))
        val current=c.begin(MemoryClaims.Stage.WRITER,7);assertFalse(c.end(old))
        assertEquals(7L,c.snapshot().live[MemoryClaims.Stage.WRITER.ordinal]);assertTrue(c.end(current))
        assertEquals(1L,c.snapshot().badRelease);assertFalse(c.snapshot().qualified)
    }
    @Test fun finallyReleaseWorksWhenTheMeasuredOperationFails() {
        val c=MemoryClaims();val token=c.begin(MemoryClaims.Stage.VALIDATION,100)
        try {throw IllegalStateException("synthetic measured failure")}
        catch(_:IllegalStateException){}finally {c.end(token)}
        assertEquals(0,c.snapshot().active);assertTrue(c.snapshot().qualified)
    }
    @Test fun concurrentClaimsHaveBoundedSlotsAndNoLostOrNegativeBytes() {
        val c=MemoryClaims();val error=AtomicReference<Throwable?>()
        val threads=(0..2).map {Thread {
            try {repeat(2000){val token=c.begin(MemoryClaims.Stage.PREPARATION,64);check(token>0);check(c.end(token))}}
            catch(t:Throwable){error.compareAndSet(null,t)}
        }.apply {start()}}
        threads.forEach {it.join(10000);assertFalse(it.isAlive)};error.get()?.let {throw it}
        assertEquals(0,c.snapshot().active);assertTrue(c.snapshot().qualified)
    }
    @Test fun scalarRingKeepsOnlyLastSixtyFourRowsWithVisibleOmissions() {
        val ring=MemoryProbeRing();val row=LongArray(24){-1};repeat(70){row[0]=it.toLong();ring.record(row)}
        val (values,omitted)=ring.snapshot();assertEquals(6L,omitted);assertEquals(64*24,values.size)
        assertEquals(6L,values[0]);assertEquals(69L,values[63*24]);assertEquals(-1L,values[1])
        values.fill(999);assertEquals(6L,ring.snapshot().first[0])
    }
    @Test fun malformedRingInputIsNotAcceptedAsAPartialOrZeroFilledRow() {
        val ring=MemoryProbeRing();assertFalse(ring.record(LongArray(23)))
        assertEquals(0,ring.snapshot().first.size);assertEquals(1L,ring.snapshot().second)
    }
}
