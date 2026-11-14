package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Original synthetic gate facts, not USB/sensor clocks. Shared by finite interop and race tests. */
internal object PeriodicTestSource {
    const val PERIOD=200000000L
    const val CLOCK="00000000-0000-4000-8000-000000000384"
    fun offer(module:SimulatedRecordingModule,ordinal:Long,receipt:Long,available:Boolean=true,
        masked:Boolean=false,profile:RecordingProfile=RecordingProfile.ANALYSIS,
        onFreeze:()->Unit={}):RecordingOffer {
        val witness=RecordingSourceWitness(CLOCK,receipt,ordinal,available,if(available)null else "synthetic_unavailable")
        return RecordingOffer(module.id,ordinal,FrozenJson(module.stream),3L*1048576,witness,profile) {
            onFreeze()
            val original=module.observation(ordinal,masked=masked)
            val requested=RecordingProfiles.requested(profile,module.stream["capabilities"].obj()).toSet()+"validity"
            val payloads=original.payloads.filter {it.role in requested && (available || it.role !in listOf("temperature","validity"))}
            val facts=original.facts.value()+mapOf("receipt" to mapOf("status" to "known",
                "stamp" to mapOf("clock_domain_id" to CLOCK,"ticks" to receipt.toString()),
                "utc" to mapOf("status" to "unknown","clock_source" to "unknown","reason" to "synthetic_clock")))+
                if(available)emptyMap() else mapOf("measurement" to mapOf("status" to "unavailable","reason" to "synthetic_unavailable"),
                    "availability" to mapOf("temperature" to mapOf("status" to "unavailable","reason" to "synthetic_unavailable")))
            RecordingObservation(module.id,ordinal,module.stream,original.context.value(),facts,payloads)
        }
    }
    /** Original synthetic invalid/all-invalid module planes; never normalizes an input NaN. */
    fun planeOffer(m:SimulatedRecordingModule,allInvalid:Boolean):RecordingOffer {
        val source=m.observation(0,masked=allInvalid)
        val original=source.payloads.first {it.role=="temperature"}
        val bytes=if(allInvalid)ByteArray(original.size) else original.bytes().apply {
            ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(0,0x7fc00042)
        }
        val payloads=buildList {
            add(RecordingPayload(original.descriptor.value(),bytes))
            if(allInvalid) {val mask=source.payloads.first {it.role=="validity"};add(RecordingPayload(mask.descriptor.value(),ByteArray(mask.size)))}
        }
        val facts=source.facts.value()+if(allInvalid)mapOf("measurement" to (source.facts.value()["measurement"].obj()+("validity" to "no_valid_pixels"))) else emptyMap()
        return RecordingOffer(m.id,0,FrozenJson(m.stream),3L*1048576,
            RecordingSourceWitness(PeriodicTestSource.CLOCK,0,0,true,null),RecordingProfile.ANALYSIS) {
            RecordingObservation(m.id,0,m.stream,source.context.value(),facts,payloads)
        }
    }
    fun engine(module:SimulatedRecordingModule,file:File,clock:AtomicLong,codec:Int=0,
        hook:()->Unit={},backend:RecorderBackend=LocalRecorderBackend(file))=RecorderEngine(
        SimulatedRecordingModule.header(listOf(module),codec),listOf(module.stream),RecordingProfile.ANALYSIS,
        RecorderLimits(maxObservationBytes=3L*1048576),backend,codec,now=clock::get,prepareHook=hook,
        sampling=FiveHertzSampling(clock.get()))
    fun chunks(file:File):List<ChunkData> = RandomAccessFile(file,"r").use { input ->
        var at=0L;var header:Obj?=null;val chunks=mutableListOf<ChunkData>()
        while(at<input.length()) {
            val record=LmtrFraming.read(input,at);at+=record.length
            if(record.type==LmtrFraming.HEADER)header=LmtrJson.parse(record.body)
            if(record.type==LmtrFraming.CHUNK)chunks.add(LmtrChunk.decode(record.body,header!!))
        }
        chunks
    }
}

/** Exact journal/clock/ownership assertions, including forced races on the unchanged byte capacities. */
class PeriodicRecorderTest {
    private fun output()=Files.createTempDirectory("lmtr-periodic-test-").resolve("capture.lmtr").toFile()
    private fun until(message:String,test:()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!test()){check(System.nanoTime()<end){message};Thread.sleep(2)}
    }
    private fun checkedThread(error:AtomicReference<Throwable?>,body:()->Unit)=Thread {
        try {body()} catch(failure:Throwable){error.compareAndSet(null,failure)}
    }
    private fun drained(e:RecorderEngine)=until("preparation/writer did not drain") {
        e.snapshot().let {it.preparationQueuedBytes+it.preparationInFlightBytes+it.writerQueuedBytes+it.writerInFlightBytes==0L}
    }
    private fun finish(e:RecorderEngine):RecorderResult {
        e.stop();return checkNotNull(e.awaitStop(30000)).also {assertTrue(it.toString(),it.finalized)}
    }
    private fun entries(file:File)=PeriodicTestSource.chunks(file).flatMap {it.metadata["entries"].arr().map {e->e.obj()}}
    private fun ledger(file:File)=entries(file).last {it["kind"]=="segment_end"}["extensions"].obj()
        .getValue(PeriodicRecordingEvidence.NAMESPACE).obj()["data"].obj()

    @Test fun tenVirtualSecondsKeepDenseSelectedCountersOriginalOrdinalsMasksAndUnavailableSlots() {
        for(codec in 0..1) {
            val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0)
            val e=PeriodicTestSource.engine(m,file,clock,codec)
            try {
                repeat(250) { i ->
                    clock.set(i*40000000L)
                    val selected=i%5==0
                    assertEquals(if(selected)Admission.ACCEPTED else Admission.NOT_SELECTED,
                        e.admit(PeriodicTestSource.offer(m,i.toLong(),clock.get(),i!=100,masked=i%10==0)))
                    if(selected)drained(e)
                }
                clock.set(10000000000L);finish(e)
                val p=e.periodicSnapshot()!!
                assertEquals(250L,p.acquired);assertEquals(200L,p.notSelected)
                assertEquals(50L,p.selected);assertEquals(49L,p.selectedAvailable);assertEquals(1L,p.selectedUnavailable)
                assertEquals(50L,p.plannedSlots);assertEquals(0L,p.missedJournaled);assertTrue(p.ledgerComplete)
                assertEquals(49L,p.acknowledgedAvailable);assertEquals(1L,p.acknowledgedUnavailable)
                assertEquals(50L,e.snapshot().acknowledged)
                val observed=entries(file).filter {it["kind"]=="observation"}
                for((index,entry) in observed.withIndex()) {
                    assertEquals(index.toString(),entry["observation_sequence"])
                    assertEquals(index.toString(),entry["scheduled"].obj()["first_slot"])
                    assertEquals((index*5L*40000000).toString(),entry["receipt"].obj()["stamp"].obj()["ticks"])
                    val evidence=entry["extensions"].obj().getValue(PeriodicRecordingEvidence.NAMESPACE).obj()["data"].obj()
                    assertEquals((index*5).toString(),evidence["original_acquisition_ordinal"])
                    assertEquals(index!=20,entry["measurement"].obj()["status"]=="available")
                    assertEquals(index!=20 && index%2==0,entry["payloads"].arr().any {it.obj()["role"]=="validity"})
                }
                assertEquals("200",ledger(file)["intentionally_not_selected"])
                LmtrReader(file).use {assertEquals("completed",it.check(full=true)["declared_end_status"])}
            } finally {e.stop();e.awaitStop(30000)}
        }
    }

    @Test fun idleTimerDetectsMissingWindowsWithoutCallbacksAndStopCoversThePartialLastWindow() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
        clock.set(450000000);e.wakeTimers()
        until("idle windows not detected"){e.periodicSnapshot()!!.missedJournaled==2L}
        finish(e)
        val p=e.periodicSnapshot()!!;assertEquals(3L,p.plannedSlots);assertEquals(3L,p.acknowledgedMissed)
        assertEquals(0L,p.acquired);assertTrue(p.ledgerComplete)
        assertTrue(entries(file).filter {it["kind"]=="gap"}.all {it["category"]=="missed_schedule" && it["observation_first"]==null})
    }

    @Test fun lateOldReceiptIsNotReusedButARealLateNewReceiptKeepsItsActualTime() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
        clock.set(250000000)
        assertEquals(Admission.NOT_SELECTED,e.admit(PeriodicTestSource.offer(m,0,20000000)))
        clock.set(260000000)
        assertEquals(Admission.ACCEPTED,e.admit(PeriodicTestSource.offer(m,1,250000000)))
        clock.set(399000000);finish(e)
        val p=e.periodicSnapshot()!!;assertEquals(1L,p.lateNotSelected);assertEquals(1L,p.missedJournaled)
        val entry=entries(file).single {it["kind"]=="observation"}
        assertEquals("260000000",entry["timeline_ns"])
        assertEquals("250000000",entry["receipt"].obj()["stamp"].obj()["ticks"])
        assertEquals("200000000",entry["scheduled"].obj()["first_deadline_ns"])
    }

    @Test fun stopExcludesUnopenedBoundaryButRetainsAnAdmissionAlreadyOrderedAtTheSameReading() {
        for(preordered in listOf(false,true)) {
            val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
            e.admit(PeriodicTestSource.offer(m,0,0));clock.set(PeriodicTestSource.PERIOD)
            if(preordered)e.admit(PeriodicTestSource.offer(m,1,clock.get()))
            finish(e);assertEquals(if(preordered)2L else 1L,e.periodicSnapshot()!!.plannedSlots)
            assertEquals(if(preordered)2L else 1L,e.snapshot().acknowledged)
            assertEquals(0L,e.periodicSnapshot()!!.missedJournaled)
        }
    }

    @Test fun zeroDurationStopStillPreservesAnEmptyPolicyLedgerWithoutInventingASlot() {
        val m=SimulatedRecordingModule();val file=output();val e=PeriodicTestSource.engine(m,file,AtomicLong(0))
        finish(e);assertEquals(0L,e.periodicSnapshot()!!.plannedSlots)
        assertEquals(listOf("segment_start","segment_end"),entries(file).map {it["kind"]})
        assertEquals("0",ledger(file)["selected_observations"])
    }

    @Test fun timerAndCallbackAtTheSameBoundaryCannotDuplicateMissingOrSelectedSlots() {
        repeat(5) {
            val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
            clock.set(PeriodicTestSource.PERIOD)
            val start=CountDownLatch(1);val error=AtomicReference<Throwable?>(null)
            val timer=checkedThread(error) {start.await();e.wakeTimers()};val callback=checkedThread(error) {start.await();assertEquals(Admission.ACCEPTED,e.admit(PeriodicTestSource.offer(m,0,clock.get())))}
            timer.start();callback.start();start.countDown();timer.join(5000);callback.join(5000)
            assertFalse(timer.isAlive);assertFalse(callback.isAlive);assertNull(error.get())
            clock.set(201000000);finish(e)
            assertEquals(1L,e.periodicSnapshot()!!.acknowledgedMissed);assertEquals(1L,e.snapshot().acknowledged)
        }
    }

    @Test fun concurrentStopAndBoundaryAdmissionHaveAnExplicitLinearizableOutcome() {
        repeat(5) {
            val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
            // This isolates Stop/admission ordering, not the separately tested 4 MiB queue.
            // A still-queued 3 MiB first offer can otherwise make the second a legitimate overload.
            e.admit(PeriodicTestSource.offer(m,0,0));drained(e);clock.set(PeriodicTestSource.PERIOD)
            val start=CountDownLatch(1);var admission:Admission?=null;val error=AtomicReference<Throwable?>(null)
            val stop=checkedThread(error) {start.await();e.stop()};val callback=checkedThread(error) {start.await();admission=e.admit(PeriodicTestSource.offer(m,1,clock.get()))}
            stop.start();callback.start();start.countDown();stop.join(5000);callback.join(5000);assertFalse(stop.isAlive);assertFalse(callback.isAlive);assertNull(error.get());finish(e)
            assertTrue(admission in listOf(Admission.ACCEPTED,Admission.STOPPED))
            val expected=if(admission==Admission.ACCEPTED)2L else 1L
            assertEquals(expected,e.periodicSnapshot()!!.plannedSlots);assertEquals(expected,e.snapshot().acknowledged)
        }
    }

    @Test fun preparationOverloadRetainsSlotOriginalOrdinalAndReportedAvailabilityAcrossConcurrentStop() {
        for(codec in 0..1) {
            val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0)
            val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val e=PeriodicTestSource.engine(m,file,clock,codec,hook={entered.countDown();check(release.await(10,TimeUnit.SECONDS))})
            try {
                e.admit(PeriodicTestSource.offer(m,0,0));assertTrue(entered.await(5,TimeUnit.SECONDS))
                clock.set(200000000);e.admit(PeriodicTestSource.offer(m,1,clock.get()))
                clock.set(400000000);assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(PeriodicTestSource.offer(m,2,clock.get(),false)))
                clock.set(450000000);val stopping=e.stop();assertFalse(stopping.isDone)
                release.countDown();finish(e)
                val loss=entries(file).single {it["kind"]=="gap"}
                assertEquals("preparation_overload",loss["category"]);assertEquals("2",loss["scheduled"].obj()["first_slot"])
                val original=loss["extensions"].obj().getValue(PeriodicRecordingEvidence.NAMESPACE).obj()["data"].obj()
                assertEquals("2",original["original_acquisition_ordinal"]);assertEquals(false,original["measurement_available_at_source"])
                assertEquals(1L,e.periodicSnapshot()!!.acknowledgedPreparationLoss)
                assertTrue(e.periodicSnapshot()!!.ledgerComplete)
            } finally {release.countDown();e.stop();e.awaitStop(30000)}
        }
    }

    @Test fun writerOverloadAndBlockedFooterCannotAcknowledgeEarlyOrLoseSelectedSlotIdentity() {
        for(codec in 0..1) {
            val m=SimulatedRecordingModule(width=384,height=288);val file=output();val clock=AtomicLong(0)
            val entered=CountDownLatch(1);val release=CountDownLatch(1);val delegate=LocalRecorderBackend(file)
            val backend=object:RecorderBackend {
                override val destination=file
                override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                    if(type==LmtrFraming.CHUNK && release.count>0) {entered.countDown();check(release.await(15,TimeUnit.SECONDS))}
                    return delegate.append(type,ordinal,previous,body)
                }
                override fun close()=delegate.close()
            }
            val e=PeriodicTestSource.engine(m,file,clock,codec,backend=backend)
            try {
                repeat(20) {i->
                    clock.set(i*PeriodicTestSource.PERIOD)
                    assertEquals(Admission.ACCEPTED,e.admit(PeriodicTestSource.offer(m,i.toLong(),clock.get())))
                    until("preparation blocked") {e.snapshot().let {it.preparationQueuedBytes+it.preparationInFlightBytes==0L}}
                    if(i==6)assertTrue(entered.await(5,TimeUnit.SECONDS))
                }
                assertEquals(0L,e.snapshot().acknowledged);assertTrue(e.snapshot().writerDrops>0)
                assertEquals(0L,e.snapshot().preparationDrops)
                clock.set(3900000000);e.stop();release.countDown();finish(e)
                for(loss in entries(file).filter {it["kind"]=="gap" && it["category"]=="writer_overload"}) {
                    assertEquals(loss["observation_first"],loss["scheduled"].obj()["first_slot"])
                    assertEquals(loss["observation_first"],loss["extensions"].obj().getValue(PeriodicRecordingEvidence.NAMESPACE).obj()["data"].obj()["original_acquisition_ordinal"])
                }
                assertEquals(20L,e.snapshot().acknowledged+e.snapshot().writerDrops)
                assertEquals(e.snapshot().writerDrops,e.periodicSnapshot()!!.acknowledgedWriterLoss)
            } finally {release.countDown();e.stop();e.awaitStop(30000)}
        }
    }

    @Test fun exhaustedOriginalControlReserveRefusesEndRatherThanPretendingAllSelectedFramesWereRepresented() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=PeriodicTestSource.engine(m,file,clock,hook={entered.countDown();check(release.await(10,TimeUnit.SECONDS))})
        try {
            e.admit(PeriodicTestSource.offer(m,0,0));assertTrue(entered.await(5,TimeUnit.SECONDS))
            clock.set(200000000);e.admit(PeriodicTestSource.offer(m,1,clock.get()))
            repeat(33) {i->clock.set((i+2)*PeriodicTestSource.PERIOD)
                assertEquals(if(i<32)Admission.PREPARATION_OVERLOAD else Admission.REFUSED,
                    e.admit(PeriodicTestSource.offer(m,(i+2).toLong(),clock.get())))}
            release.countDown();val result=checkNotNull(e.awaitStop(30000));assertFalse(result.finalized)
            assertFalse(e.periodicSnapshot()!!.ledgerComplete)
            LmtrReader(file).use {assertEquals(true,it.check(full=true,recover=true)["unknown_tail"])}
        } finally {release.countDown();e.stop();e.awaitStop(30000)}
    }

    @Test fun sourceLossRemainsIndependentOfScheduledMissingWindowsAndExplicitDetachClosesInterrupted() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
        e.admit(PeriodicTestSource.offer(m,0,0));clock.set(450000000)
        e.admitLoss(RecordingLoss(m.id,m.stream,mapOf("category" to "source_loss","reason" to "synthetic_source_signal",
            "count" to null,"scheduled" to null,"device_first" to null,"device_last" to null,
            "observation_first" to null,"observation_last" to null,"evidence" to mapOf("basis" to "unknown","before" to null,"after" to null))))
        e.stop("detached",true);val r=finish(e);assertEquals("interrupted",r.status)
        assertEquals(2L,e.periodicSnapshot()!!.missedJournaled)
        assertEquals(1,entries(file).count {it["kind"]=="gap" && it["category"]=="source_loss"})
    }

    @Test fun sourceReplacementEndsTheOldPolicyInsteadOfSilentlyChangingItsClockOrProfile() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
        e.admit(PeriodicTestSource.offer(m,0,0));clock.set(200000000)
        val other=SimulatedRecordingModule(session="00000000-0000-4000-8000-000000000099")
        assertEquals(Admission.REFUSED,e.admit(PeriodicTestSource.offer(other,0,clock.get())))
        assertEquals("interrupted",finish(e).status);assertEquals(1L,e.periodicSnapshot()!!.acquired)
    }

    @Test fun regressedClockPoisonsEndAndKeepsTheFirstPeriodicFailureAfterCleanup() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
        e.admit(PeriodicTestSource.offer(m,0,0));drained(e)
        clock.set(2000000000);e.wakeTimers();until("no committed prefix"){e.snapshot().chunks>0}
        clock.set(1000000000);e.wakeTimers()
        val result=checkNotNull(e.awaitStop(30000));assertFalse(result.finalized)
        e.stop();assertEquals("periodic_clock_regressed",e.periodicSnapshot()!!.failure)
        LmtrReader(file).use {val r=it.check(full=true,recover=true);assertEquals(true,r["unknown_tail"]);assertNull(r["declared_end_status"])}
    }

    @Test fun unknownClockMappingOrProfileMismatchIsRefusedWithoutFallback() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file,clock)
        assertEquals(Admission.REFUSED,e.admit(PeriodicTestSource.offer(m,0,0,profile=RecordingProfile.FULL)))
        assertFalse(checkNotNull(e.awaitStop(30000)).finalized)
        val badHeader=SimulatedRecordingModule.header(listOf(m))+("timeline" to mapOf("clock_domain_id" to "00000000-0000-4000-8000-000000000999",
            "origin_monotonic_ns" to "0","resolution_ns" to null,"source" to "host_monotonic"))
        assertThrows(NoSuchElementException::class.java) {RecorderEngine(badHeader,listOf(m.stream),RecordingProfile.ANALYSIS,
            RecorderLimits(maxObservationBytes=3L*1048576),LocalRecorderBackend(output()),sampling=FiveHertzSampling(0),now=clock::get)}
    }

    @Test fun missingEndRecoveryDoesNotInventACompleteSelectionLedgerOrMutateTheSource() {
        val m=SimulatedRecordingModule();val file=output();val clock=AtomicLong(0);val delegate=LocalRecorderBackend(file)
        val backend=object:RecorderBackend {
            override val destination=file
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.END)throw IOException("Synthetic END failure")
                return delegate.append(type,ordinal,previous,body)
            }
            override fun close()=delegate.close()
        }
        val e=PeriodicTestSource.engine(m,file,clock,backend=backend)
        e.admit(PeriodicTestSource.offer(m,0,0));clock.set(100000000);e.stop()
        assertFalse(checkNotNull(e.awaitStop(30000)).finalized)
        val before=LmtrChunk.sha(file.readBytes())
        LmtrReader(file).use {val r=it.check(full=true,recover=true);assertEquals(true,r["unknown_tail"]);assertNull(r["declared_end_status"])}
        assertEquals(before,LmtrChunk.sha(file.readBytes()))
    }
}
