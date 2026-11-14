package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Virtual-clock boundary/race tests. These are not scheduling-jitter or physical-cadence claims. */
class RecorderDeadlineTest {
    private fun output()=Files.createTempDirectory("lmtr-deadline-").resolve("capture.lmtr").toFile()
    private fun engine(m:SimulatedRecordingModule,f:File,clock:AtomicLong,target:Long,codec:Int=0,
        hook:()->Unit={},backend:RecorderBackend=LocalRecorderBackend(f))=RecorderEngine(
        SimulatedRecordingModule.header(listOf(m),codec),listOf(m.stream),RecordingProfile.ANALYSIS,
        RecorderLimits(maxObservationBytes=3L*1048576),backend,codec,now=clock::get,prepareHook=hook,
        sampling=FiveHertzSampling(0),stopDeadline=RecordingStopDeadline(target))
    private fun until(test:()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!test()){check(System.nanoTime()<end);Thread.sleep(2)}
    }
    private fun drain(e:RecorderEngine)=until {e.snapshot().let {
        it.preparationQueuedBytes+it.preparationInFlightBytes+it.writerQueuedBytes+it.writerInFlightBytes==0L}}
    private fun done(e:RecorderEngine)=checkNotNull(e.awaitStop(20000)).also {assertTrue(it.toString(),it.finalized)}
    @Test fun tenAndSixtySecondsStopBeforeBoundaryOfferForBothCodecsWithoutInventingTimestamps() {
        for(seconds in listOf(10L,60L))for(codec in 0..1) {
            val m=SimulatedRecordingModule();val f=output();val clock=AtomicLong(0)
            val target=seconds*1000000000;val e=engine(m,f,clock,target,codec)
            repeat((seconds*25).toInt()) {n->clock.set(n*40000000L)
                val decision=e.admit(PeriodicTestSource.offer(m,n.toLong(),clock.get(),available=n!=100))
                if(n%5==0){assertEquals(Admission.ACCEPTED,decision);drain(e)}else assertEquals(Admission.NOT_SELECTED,decision)
            }
            clock.set(target);var frozen=false
            assertEquals(Admission.STOPPED,e.admit(PeriodicTestSource.offer(m,seconds*25,target,onFreeze={frozen=true})))
            assertFalse(frozen);assertEquals(RecordingStopDeadline.REASON,done(e).reason)
            val p=e.periodicSnapshot()!!;assertEquals(seconds*5,p.plannedSlots);assertEquals(seconds*5,p.selected)
            assertEquals(seconds*25,p.acquired);assertEquals(seconds*20,p.notSelected);assertEquals(0L,p.missedJournaled)
            assertEquals(target,e.deadlineSnapshot()!!.cutoffNs);assertEquals(target,e.deadlineSnapshot()!!.firstObservedExpiryNs)
            assertEquals(seconds*5,e.snapshot().acknowledged)
            val entries=PeriodicTestSource.chunks(f).flatMap {it.metadata["entries"].arr().map {x->x.obj()}}
            assertEquals(target.toString(),entries.last()["timeline_ns"])
            assertEquals((target-200000000).toString(),entries.last {it["kind"]=="observation"}["timeline_ns"])
        }
    }
    @Test fun lateNotificationKeepsActualCutoffAndAnExplicitPartialFinalMissedSlot() {
        val m=SimulatedRecordingModule();val f=output();val clock=AtomicLong(0);val target=200000000L
        val e=engine(m,f,clock,target);e.admit(PeriodicTestSource.offer(m,0,0));drain(e)
        clock.set(target+25000000);e.wakeTimers();done(e)
        assertEquals(225000000L,e.deadlineSnapshot()!!.cutoffNs);assertEquals("25000000",e.deadlineSnapshot()!!.report()["overshoot_ns"])
        assertEquals(2L,e.periodicSnapshot()!!.plannedSlots);assertEquals(1L,e.periodicSnapshot()!!.acknowledgedMissed)
        val entries=PeriodicTestSource.chunks(f).flatMap {it.metadata["entries"].arr().map {x->x.obj()}}
        val gap=entries.single {it["kind"]=="gap"};assertEquals("1",gap["scheduled"].obj()["first_slot"])
        assertEquals("225000000",gap["timeline_ns"]);assertEquals("225000000",entries.last()["timeline_ns"])
    }
    @Test fun idleSixtySecondWindowClosesThreeHundredMissesWithoutPerTickQueueing() {
        val m=SimulatedRecordingModule();val f=output();val clock=AtomicLong(0);val e=engine(m,f,clock,60000000000L)
        clock.set(60000000000L);e.wakeTimers();done(e)
        val p=e.periodicSnapshot()!!;assertEquals(300L,p.plannedSlots);assertEquals(300L,p.acknowledgedMissed)
        assertEquals(0L,p.selected);assertEquals(0L,e.snapshot().acknowledged)
        assertTrue(e.snapshot().maxPreparationQueuedBytes<=4194304)
        LmtrReader(f).use {assertEquals("300",it.check(full=true)["summary"].obj()["known_missing_observations"])}
    }
    @Test fun manualOrInterruptedEarlierStopCannotBeReplacedByALaterDeadline() {
        for(interrupted in listOf(false,true)) {
            val m=SimulatedRecordingModule();val clock=AtomicLong(0);val e=engine(m,output(),clock,200000000)
            e.admit(PeriodicTestSource.offer(m,0,0));clock.set(100000001)
            e.stop(if(interrupted)"detached" else "user_stop",interrupted);val result=done(e)
            clock.set(300000000);e.wakeTimers()
            assertEquals(if(interrupted)"interrupted" else "completed",result.status)
            assertEquals(100000001L,e.deadlineSnapshot()!!.cutoffNs);assertNull(e.deadlineSnapshot()!!.firstObservedExpiryNs)
        }
    }
    @Test fun simultaneousExpiryAdmissionAndStopNeverAdmitTheBoundaryFrame() {
        repeat(12) {
            val m=SimulatedRecordingModule();val clock=AtomicLong(200000000);val e=engine(m,output(),clock,200000000)
            val gate=CountDownLatch(1);val problem=AtomicReference<Throwable?>();val answer=AtomicReference<Admission?>()
            val actors=listOf<()->Unit>({answer.set(e.admit(PeriodicTestSource.offer(m,0,200000000)))},{e.wakeTimers()},{e.stop()})
                .map {body->Thread {try {gate.await();body()}catch(t:Throwable){problem.compareAndSet(null,t)}}.apply {start()}}
            gate.countDown();actors.forEach {it.join(10000);assertFalse(it.isAlive)};problem.get()?.let {throw it};done(e)
            assertEquals(Admission.STOPPED,answer.get());assertEquals(0L,e.periodicSnapshot()!!.selected)
            assertEquals(200000000L,e.deadlineSnapshot()!!.cutoffNs)
        }
    }
    @Test fun acceptedBeforeDeadlineStillDrainsAndAcknowledgesAfterBlockedPreparation() {
        val m=SimulatedRecordingModule();val f=output();val clock=AtomicLong(199999999)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=engine(m,f,clock,200000000,hook={entered.countDown();check(release.await(10,TimeUnit.SECONDS))})
        try {
            assertEquals(Admission.ACCEPTED,e.admit(PeriodicTestSource.offer(m,0,199999999)))
            assertTrue(entered.await(5,TimeUnit.SECONDS));clock.set(200000000);e.wakeTimers()
            assertNull(e.awaitStop(10));assertEquals(0L,e.snapshot().acknowledged)
            release.countDown();done(e);assertEquals(1L,e.snapshot().acknowledged)
            val observation=PeriodicTestSource.chunks(f).flatMap {it.metadata["entries"].arr().map {v->v.obj()}}.single {it["kind"]=="observation"}
            assertEquals("199999999",observation["timeline_ns"]);assertEquals(200000000L,e.deadlineSnapshot()!!.cutoffNs)
        } finally {release.countDown();e.stop();e.awaitStop(10000)}
    }
    @Test fun blockedFooterDoesNotBlockDeadlineCutoffOrManufactureAnEarlyAck() {
        val m=SimulatedRecordingModule();val f=output();val clock=AtomicLong(0);val base=LocalRecorderBackend(f)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK){entered.countDown();check(release.await(10,TimeUnit.SECONDS))}
                return base.append(type,ordinal,previous,body)
            }
            override fun close()=base.close()
        }
        val e=engine(m,f,clock,2000000000,backend=backend)
        try {
            e.admit(PeriodicTestSource.offer(m,0,0));drain(e);clock.set(1000000000);e.wakeTimers()
            assertTrue(entered.await(5,TimeUnit.SECONDS));clock.set(2000000000);e.wakeTimers()
            assertEquals(2000000000L,e.deadlineSnapshot()!!.cutoffNs);assertEquals(0L,e.snapshot().acknowledged)
            assertNull(e.awaitStop(10));release.countDown();done(e);assertEquals(1L,e.snapshot().acknowledged)
        } finally {release.countDown();e.stop();e.awaitStop(10000)}
    }
    @Test fun clockFailureOrUnrepresentedLossCannotBecomeASuccessfulDurationEnd() {
        val m=SimulatedRecordingModule();val clock=AtomicLong(100000000)
        val e=engine(m,output(),clock,200000000);e.wakeTimers();clock.set(99999999);e.wakeTimers()
        assertFalse(checkNotNull(e.awaitStop(10000)).finalized);assertFalse(e.deadlineSnapshot()!!.clockQualified)
        val other=engine(m,output(),AtomicLong(0),200000000)
        other.failSourceEvidence("synthetic_unrepresented_callback");other.wakeTimers()
        assertFalse(checkNotNull(other.awaitStop(10000)).finalized)
        assertEquals("synthetic_unrepresented_callback",other.periodicSnapshot()!!.failure)
    }
}
