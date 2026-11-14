package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** The new opt-in plan changes only diagnostic call opportunities. Missing data are never
 * stale samples, and blocked/erroring diagnostics retain the same Stop/export boundary.
 */
class TieredMemoryTest {
    private fun offset(d:MemoryDomain)=BracketedMemoryWindow.HEADER+d.ordinal*BracketedMemoryWindow.STRIDE
    private fun export(w:BracketedMemoryWindow):List<Obj> {
        val text=StringBuilder();w.writeTo(text)
        return text.lines().filter {it.isNotBlank()}.map {LmtrJson.parse(it.toByteArray()).obj()}
    }
    private fun window(probe:MemoryScalarProbe):BracketedMemoryWindow {
        val tick=AtomicLong(100)
        return BracketedMemoryWindow(9,probe,tick::incrementAndGet,MemoryProbePlan.TIERED)
    }
    private fun unsupported()=MemoryScalarProbe {_,_->MemoryProbeStatus.UNSUPPORTED}

    @Test fun referenceRemainsDefaultWithOriginalSevenStatusesAndNoSchedule() {
        val tick=AtomicLong(100);val w=BracketedMemoryWindow(9,unsupported(),tick::incrementAndGet)
        w.sample();w.requestStop();val h=export(w)[0]
        assertEquals(MemoryProbePlan.REFERENCE,w.plan);assertEquals("r4-memory-probes-1",h["format"])
        assertFalse(h.containsKey("schedule"));assertEquals(7,h["statuses"].arr().size)
        assertEquals("SOURCE_CHANGED",h["statuses"].arr().last())
        assertEquals(92168,BracketedMemoryWindow.PRIMITIVE_BYTES)
    }
    @Test fun twentyRowsCallMemoryInfoFourTimesAndLeaveAllSkippedValuesAndTimesUnknown() {
        val calls=IntArray(MemoryDomain.entries.size)
        val w=window {d,v->calls[d.ordinal]++;d.fields.indices.forEach {v[it]=calls[d.ordinal].toLong()};MemoryProbeStatus.OK}
        repeat(20){assertTrue(w.sample())};w.requestStop();val lines=export(w)
        assertEquals("r4-memory-probes-2",lines[0]["format"])
        assertEquals(5,lines[0]["schedule"].obj()["android_memory_every_rows"].i())
        for(d in MemoryDomain.entries)assertEquals(if(d==MemoryDomain.ANDROID_MEMORY)4 else 20,calls[d.ordinal])
        for(index in 0 until 20) {
            val v=lines[index+1]["values"].arr();val i=offset(MemoryDomain.ANDROID_MEMORY)
            if(index%5!=0) {
                assertNull(v[i]);assertNull(v[i+1]);assertEquals(MemoryProbeStatus.NOT_SCHEDULED.ordinal.toLong(),v[i+2].d())
                repeat(BracketedMemoryWindow.VALUES){assertNull(v[i+3+it])}
            } else {assertNotNull(v[i]);assertNotNull(v[i+1]);assertEquals(0L,v[i+2].d())}
        }
    }
    @Test fun failedDueReadIsNotRetriedEarlyOrReusedOnSkippedRows() {
        var calls=0
        val w=window {d,v->if(d==MemoryDomain.ANDROID_MEMORY){calls++;v[0]=37;throw java.io.IOException()};MemoryProbeStatus.UNSUPPORTED}
        repeat(7){w.sample()};w.requestStop();val lines=export(w)
        assertEquals(2,calls);assertEquals(true,lines[0]["probe_failed"])
        val i=offset(MemoryDomain.ANDROID_MEMORY)
        assertEquals(37L,lines[1]["values"].arr()[i+3].d())
        assertNull(lines[2]["values"].arr()[i+3]);assertEquals(37L,lines[6]["values"].arr()[i+3].d())
    }
    @Test fun probesCannotManufactureAnUnscheduledStatusOnADueRead() {
        val w=window {_,_->MemoryProbeStatus.NOT_SCHEDULED};w.sample();w.requestStop()
        val lines=export(w);assertEquals(true,lines[0]["probe_failed"])
        assertEquals(MemoryProbeStatus.FAILED.ordinal.toLong(),lines[1]["values"].arr()[offset(MemoryDomain.ANDROID_MEMORY)+2].d())
    }
    @Test fun stopDuringBlockedDueReadPreservesPartialRowAndLeaseUntilActualCompletion() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val lease=MemoryProbeLease();val token=lease.acquire()
        val w=window {d,_->if(d==MemoryDomain.ANDROID_MEMORY){entered.countDown();check(release.await(10,TimeUnit.SECONDS))};MemoryProbeStatus.UNSUPPORTED}
        val worker=Thread {try {w.sample()}finally {lease.release(token)}}
        try {
            worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(w.sample())
            w.requestStop();assertFalse(w.quiescent());assertEquals(0L,lease.acquire())
            assertThrows(IllegalStateException::class.java){w.writeTo(StringBuilder())}
            release.countDown();worker.join(10000);assertFalse(worker.isAlive);assertTrue(w.quiescent())
            val next=lease.acquire();assertTrue(next>token);assertFalse(lease.release(token));assertTrue(lease.release(next))
            val lines=export(w);assertEquals(1L,lines[0]["contended_attempts"].d())
            assertEquals(1L,lines[1]["values"].arr()[4].d());assertNull(lines[1]["values"].arr()[offset(MemoryDomain.GC_AFTER)+2])
        } finally {release.countDown();worker.join(10000)}
    }
    @Test fun stoppedAndSaturatedWindowsNeverStartCatchUpReads() {
        var calls=0;val w=window {d,_->if(d==MemoryDomain.ANDROID_MEMORY)calls++;MemoryProbeStatus.UNSUPPORTED}
        repeat(64){assertTrue(w.sample())};assertFalse(w.sample());assertTrue(w.quiescent())
        assertEquals(13,calls);val lines=export(w);assertEquals(true,lines[0]["capacity_exceeded"])
        assertEquals(64,w.rowCount());assertFalse(w.sample());assertEquals(13,calls)
        val stopped=window {_,_->fail("stopped diagnostic called a probe");MemoryProbeStatus.UNSUPPORTED}
        stopped.requestStop();assertFalse(stopped.sample());assertEquals(0,stopped.rowCount())
    }
    @Test fun exportFailureKeepsTieredResultsAndNeverConsumesTheWindow() {
        val w=window(unsupported());repeat(6){w.sample()};w.requestStop();val before=export(w)
        val fail=object:Appendable {override fun append(s:CharSequence?):Appendable=throw java.io.IOException()
            override fun append(s:CharSequence?,a:Int,b:Int):Appendable=throw java.io.IOException()
            override fun append(c:Char):Appendable=throw java.io.IOException()}
        assertThrows(java.io.IOException::class.java){w.writeTo(fail)};assertEquals(before,export(w))
    }
    @Test fun sourceReplacementDiscardsAmbiguousRolesAndNextRowNeverRecallsThem() {
        var roles=0
        val w=window {d,v->
            if(d!=MemoryDomain.PUBLISHED_ROLES || ++roles>1)MemoryProbeStatus.UNSUPPORTED
            else {v.fill(99);PublishedMemoryRoles.qualify(v,2,3)}
        }
        repeat(2){w.sample()};w.requestStop();val lines=export(w);val i=offset(MemoryDomain.PUBLISHED_ROLES)
        val first=lines[1]["values"].arr();assertEquals(MemoryProbeStatus.SOURCE_CHANGED.ordinal.toLong(),first[i+2].d())
        assertEquals(2L,first[i+3].d());assertEquals(3L,first[i+4].d())
        repeat(14){assertNull(first[i+5+it])};assertNull(lines[2]["values"].arr()[i+3])
    }
    @Test fun allocationOrCancellationDuringADueReadStopsDiagnosticsOnly() {
        for(error in listOf(OutOfMemoryError("synthetic"),java.util.concurrent.CancellationException())) {
            val w=window {d,_->if(d==MemoryDomain.ANDROID_MEMORY)throw error;MemoryProbeStatus.UNSUPPORTED}
            assertTrue(w.sample());assertTrue(w.quiescent());assertFalse(w.sample())
            val lines=export(w);assertEquals(true,lines[0]["probe_failed"])
            assertEquals(1L,lines[1]["values"].arr()[4].d())
        }
    }
    @Test fun tieredClockFailureRemainsVisibleWithoutInventingSkippedTimestamps() {
        val now=AtomicLong(1000);val w=BracketedMemoryWindow(9,unsupported(),now::decrementAndGet,MemoryProbePlan.TIERED)
        repeat(2){w.sample()};w.requestStop();val lines=export(w)
        assertEquals(true,lines[0]["clock_failed"])
        val i=offset(MemoryDomain.ANDROID_MEMORY);assertNull(lines[2]["values"].arr()[i])
        assertEquals(MemoryProbeStatus.NOT_SCHEDULED.ordinal.toLong(),lines[2]["values"].arr()[i+2].d())
    }
}
