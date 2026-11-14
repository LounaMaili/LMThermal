package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Diagnostic errors and Stop are independent from recorder/source control; no Android or GC. */
class BracketedMemoryTest {
    private fun probe()=MemoryScalarProbe {d,v->d.fields.indices.forEach {v[it]=it.toLong()};MemoryProbeStatus.OK}
    private fun window(p:MemoryScalarProbe=probe()):BracketedMemoryWindow {
        val tick=AtomicLong(100);return BracketedMemoryWindow(7,p,tick::incrementAndGet)
    }
    private fun lines(w:BracketedMemoryWindow):List<Obj> {val s=StringBuilder();w.writeTo(s);return s.lines().filter {it.isNotBlank()}.map {LmtrJson.parse(it.toByteArray()).obj()}}
    private fun values(row:Obj)=row["values"].arr()
    private fun offset(d:MemoryDomain)=BracketedMemoryWindow.HEADER+d.ordinal*BracketedMemoryWindow.STRIDE

    @Test fun eachDomainHasItsOwnOrderedBracketAndNoAtomicSnapshotClaim() {
        val w=window();assertTrue(w.sample());w.requestStop();val rows=lines(w)
        assertEquals(false,rows[0]["atomic"]);assertEquals(1,rows[0]["rows"].i())
        val v=values(rows[1]);var previous=v[2].d()
        for(d in MemoryDomain.entries) {val i=offset(d);assertTrue(v[i].d()>previous);assertTrue(v[i+1].d()>v[i].d());previous=v[i+1].d()}
        assertTrue(v[3].d()>previous)
    }
    @Test fun gcAdvancementIsAQualifiedCounterDifferenceNotAnAtomicHeapFloor() {
        val w=window {d,v->d.fields.indices.forEach {v[it]=0};if(d in listOf(MemoryDomain.GC_BEFORE,MemoryDomain.GC_AFTER))v[2]=if(d==MemoryDomain.GC_BEFORE)9 else 10;MemoryProbeStatus.OK}
        w.sample();w.requestStop();assertEquals(2L,values(lines(w)[1])[5].d())
    }
    @Test fun gcCounterRegressionAndMissingCounterRemainDistinct() {
        for(missing in listOf(false,true)) {
            val w=window {d,v->d.fields.indices.forEach {v[it]=0};if(d==MemoryDomain.GC_BEFORE)v[2]=9;if(d==MemoryDomain.GC_AFTER)v[2]=if(missing)-1 else 8;MemoryProbeStatus.OK}
            w.sample();w.requestStop();assertEquals(if(missing)0L else 3L,values(lines(w)[1])[5].d())
        }
    }
    @Test fun failedPartialReadRetainsKnownFieldsAndDoesNotReusePreviousScratch() {
        val w=window {d,v->if(d==MemoryDomain.JAVA){v[0]=99;throw java.io.IOException("synthetic")};MemoryProbeStatus.UNSUPPORTED}
        w.sample();w.requestStop();val rows=lines(w);val v=values(rows[1]);val i=offset(MemoryDomain.JAVA)
        assertEquals(true,rows[0]["probe_failed"]);assertEquals(MemoryProbeStatus.FAILED.ordinal.toLong(),v[i+2].d())
        assertEquals(99L,v[i+3].d());assertNull(v[i+4]);assertNull(v[offset(MemoryDomain.RSS)+3])
    }
    @Test fun missingNativeCapacityIsUnsupportedRatherThanManufacturedZero() {
        val w=window {_,_->MemoryProbeStatus.UNSUPPORTED};w.sample();w.requestStop();val v=values(lines(w)[1]);val i=offset(MemoryDomain.NATIVE_CAPACITY)
        assertEquals(MemoryProbeStatus.UNSUPPORTED.ordinal.toLong(),v[i+2].d());assertNull(v[i+3])
    }
    @Test fun negativeOrIncompleteValuesMakeAnOkProbeExplicitlyPartial() {
        val w=window {_,v->v[0]=-7;MemoryProbeStatus.OK};w.sample();w.requestStop();val v=values(lines(w)[1]);val i=offset(MemoryDomain.JAVA)
        assertEquals(MemoryProbeStatus.PARTIAL.ordinal.toLong(),v[i+2].d());assertNull(v[i+3])
    }
    @Test fun stopDoesNotWaitForABlockedProbeAndIncompleteExportIsRefused() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val w=window {d,_->if(d==MemoryDomain.RSS){entered.countDown();check(release.await(10,TimeUnit.SECONDS))};MemoryProbeStatus.UNSUPPORTED}
        val worker=Thread {w.sample()};worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS))
        w.requestStop();assertTrue(w.stopped());assertFalse(w.quiescent())
        assertThrows(IllegalStateException::class.java){w.writeTo(StringBuilder())}
        release.countDown();worker.join(10000);assertFalse(worker.isAlive);assertTrue(w.quiescent())
        val v=values(lines(w)[1]);assertEquals(1L,v[4].d());assertNull(v[offset(MemoryDomain.GC_AFTER)])
        assertFalse(w.sample())
    }
    @Test fun aConcurrentCollectorIsCountedAndNeverQueuesAnotherRead() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val w=window {_,_->entered.countDown();check(release.await(10,TimeUnit.SECONDS));MemoryProbeStatus.UNSUPPORTED}
        val worker=Thread {w.sample()};worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(w.sample())
        w.requestStop();release.countDown();worker.join(10000);assertFalse(worker.isAlive)
        assertEquals(1L,lines(w)[0]["contended_attempts"].d());assertEquals(1,w.rowCount())
    }
    @Test fun cancellationAndAllocationErrorRemainLocalAndStopOnlyDiagnostics() {
        for(error in listOf(java.util.concurrent.CancellationException(),OutOfMemoryError("synthetic"))) {
            val w=window {_,_->throw error};assertTrue(w.sample());assertTrue(w.quiescent());assertEquals(true,lines(w)[0]["probe_failed"])
        }
    }
    @Test fun capacityPreservesAllFirstRowsInsteadOfOverwritingAnOriginalSample() {
        val w=window();repeat(64){assertTrue(w.sample())};assertFalse(w.sample());assertTrue(w.quiescent())
        val rows=lines(w);assertEquals(65,rows.size);assertEquals(true,rows[0]["capacity_exceeded"])
        assertEquals(0L,values(rows[1])[0].d());assertEquals(63L,values(rows.last())[0].d())
    }
    @Test fun clockFailureAndRegressionAreVisibleAndNeverReplaceSourceClocks() {
        val tick=AtomicLong(1000);val w=BracketedMemoryWindow(9,probe()){tick.decrementAndGet()}
        w.sample();w.requestStop();val rows=lines(w);assertEquals(true,rows[0]["clock_failed"])
        assertEquals(MemoryProbeStatus.CLOCK_INVALID.ordinal.toLong(),values(rows[1])[offset(MemoryDomain.JAVA)+2].d())
    }
    @Test fun exportFailureDoesNotDestroyTheClosedScalarWindow() {
        val w=window();w.sample();w.requestStop();val expected=lines(w)
        val fail=object:Appendable {override fun append(x:CharSequence?):Appendable=throw java.io.IOException()
            override fun append(x:CharSequence?,s:Int,e:Int):Appendable=throw java.io.IOException()
            override fun append(x:Char):Appendable=throw java.io.IOException()}
        assertThrows(java.io.IOException::class.java){w.writeTo(fail)};assertEquals(expected,lines(w))
    }
}
