package org.lmthermal.recorder

import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.lmtr.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Deterministic host races assert artifact semantics as well as queue counters. No camera is opened. */
class RecorderEngineTest {
    private fun output()=Files.createTempDirectory("lmtr-r4-test-").resolve("recording.lmtr").toFile()
    private fun engine(m:SimulatedRecordingModule, file:File=output(),profile:RecordingProfile=RecordingProfile.FULL,
        codec:Int=0,limits:RecorderLimits=RecorderLimits(maxObservationBytes=1048576),now:()->Long={System.nanoTime()},hook:()->Unit={},backend:RecorderBackend=LocalRecorderBackend(file),
        modules:List<SimulatedRecordingModule> = listOf(m),views:Boolean=false)=RecorderEngine(
            SimulatedRecordingModule.header(modules,codec,views),modules.map { it.stream },profile,limits,backend,codec,now,hook)
    private fun finished(e:RecorderEngine):RecorderResult { e.stop();val r=e.awaitStop(10000) ?: error("Stop timeout ${e.snapshot()}");assertTrue(r.toString(),r.finalized);return r }
    private fun until(message:String,predicate:()->Boolean) { val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!predicate()){if(System.nanoTime()>end)error(message);Thread.sleep(5)} }
    private fun chunks(file:File):List<ChunkData> = java.io.RandomAccessFile(file,"r").use { input ->
        val records=mutableListOf<Record>();var at=0L
        while(at<input.length()){val r=LmtrFraming.read(input,at);records.add(r);at+=r.length}
        val header=LmtrJson.parse(records.first().body)
        records.filter { it.type==LmtrFraming.CHUNK }.map { LmtrChunk.decode(it.body,header) }
    }
    @Test fun allProfilesPreserveExactTemperatureAndInterpretationContext() {
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL))for(codec in 0..1) {
            val m=SimulatedRecordingModule();val file=output();val e=engine(m,file,profile,codec)
            val o=m.observation(0,masked=true);assertEquals(Admission.ACCEPTED,e.admit(o));finished(e)
            val chunk=chunks(file).single();assertArrayEquals(o.payloads.first { it.role=="temperature" }.bytes(),chunk.payloads["1:temperature"])
            val expected=when(profile){RecordingProfile.ANALYSIS->setOf("temperature","validity");RecordingProfile.NATIVE->setOf("temperature","validity","native_samples");else->setOf("temperature","validity","native_samples","acquisition")}
            assertEquals(expected,chunk.payloads.keys.map { it.substringAfter(':') }.toSet())
            assertEquals(1L,e.snapshot().acknowledged);assertEquals(0L,e.snapshot().controlBytes)
        }
    }
    @Test fun sourceCopiesAndUnknownJsonRemainImmutable() {
        val descriptor=mutableMapOf<String,Any?>("role" to "native_samples","unknown" to java.math.BigDecimal("1.234567890123456789"))
        val bytes=byteArrayOf(1,2,3);val p=RecordingPayload(descriptor,bytes);bytes[0]=99;descriptor["role"]="preview"
        val copy=p.bytes();copy[1]=88;assertArrayEquals(byteArrayOf(1,2,3),p.bytes());assertEquals("native_samples",p.role)
        assertEquals(java.math.BigDecimal("1.234567890123456789"),(p.descriptor.value()["unknown"] as JsonNumber).decimal)
    }
    @Test fun temperatureOnlyNeedsNoUsbNativeOrTransport() {
        val m=SimulatedRecordingModule(width=13,height=7,native=false,acquisition=false);val e=engine(m,profile=RecordingProfile.NATIVE)
        e.admit(m.observation(0));assertEquals("completed",finished(e).status)
    }
    @Test fun genuinePreviewOnlyWorksButVisualCannotDropAvailableThermometry() {
        val m=SimulatedRecordingModule(thermal=false,native=false,acquisition=false,preview=true);val e=engine(m,profile=RecordingProfile.VISUAL)
        e.admit(m.observation(0));finished(e)
        assertThrows(IllegalArgumentException::class.java){RecordingProfiles.requested(RecordingProfile.VISUAL,SimulatedRecordingModule().stream["capabilities"].obj())}
    }
    @Test fun sameObservationViewsRequireExactIndependentEvidence() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f,views=true);e.admit(m.observation(0,view=true));finished(e)
        val c=chunks(f).single();assertEquals("view",c.metadata["entries"].arr()[1].obj()["payloads"].arr().map { it.obj() }.single { it["role"]=="native_samples" }["kind"])
        assertArrayEquals(m.observation(0,view=true).payloads.first { it.role=="native_samples" }.bytes(),c.payloads["1:native_samples"])
        val bad=engine(m,views=true);bad.admit(m.observation(0,view=true,badView=true));bad.stop();assertFalse(bad.awaitStop(10000)!!.finalized)
    }
    @Test fun independentNativeAndAcquisitionRemainDistinct() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f,views=true);e.admit(m.observation(0));finished(e)
        val c=chunks(f).single();assertFalse(c.payloads["1:native_samples"]!!.contentEquals(c.payloads["1:acquisition"]!!.copyOfRange(0,12)))
    }
    @Test fun calibrationChangesNeedNewContextNotNewGeometrySegment() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f);e.admit(m.observation(0,1));e.admit(m.observation(1,2));finished(e)
        val c=chunks(f).single();assertEquals(2,c.metadata["contexts"].arr().size);assertEquals(1,c.metadata["segments"].arr().size)
    }
    @Test fun changedGeometryAndSessionCloseGlobalSegmentWithoutReorderingStreams() {
        val m=SimulatedRecordingModule();val other=SimulatedRecordingModule("secondary",13,7);val changed=SimulatedRecordingModule(width=7,height=3,session="00000000-0000-4000-8000-000000000066")
        val f=output();val e=engine(m,f,modules=listOf(m,other));e.admit(m.observation(0));e.admit(other.observation(0));e.admit(changed.observation(0));finished(e)
        val entries=chunks(f).flatMap { it.metadata["entries"].arr().map { it.obj() } }
        assertEquals(listOf("segment_start","observation","observation","segment_end","segment_start","observation","segment_end"),entries.map { it["kind"] })
    }
    @Test fun unknownStreamIsRefusedWithoutDynamicDeclaration() {
        val m=SimulatedRecordingModule();val e=engine(m);assertEquals(Admission.REFUSED,e.admit(SimulatedRecordingModule("other").observation(0)))
        val r=e.awaitStop(10000)!!;assertTrue(r.toString(),r.finalized);assertEquals("failed",r.status)
    }
    @Test fun firstIntakeDeadlineSealsWhileCameraIsIdleAndCheckpointStartsAfterAck() {
        val time=AtomicLong(0);val m=SimulatedRecordingModule();val e=engine(m,now=time::get);e.admit(m.observation(0))
        until("pending"){e.snapshot().pendingBytes>0};assertEquals(0L,e.snapshot().acknowledged)
        time.set(1000000000);e.wakeTimers();until("idle seal"){e.snapshot().acknowledged==1L}
        assertEquals(0L,e.snapshot().checkpoints);time.set(5999999999);e.wakeTimers();Thread.sleep(60);assertEquals(0L,e.snapshot().checkpoints)
        time.set(6000000000);e.wakeTimers();until("idle checkpoint"){e.snapshot().checkpoints==1L};finished(e)
    }
    @Test fun stopIsIdempotentAndCutsOffLateAdmissionDuringTimerRace() {
        val m=SimulatedRecordingModule();val time=AtomicLong(0);val e=engine(m,now=time::get);e.admit(m.observation(0))
        val threads=(0..9).map { Thread { time.set(1000000000);e.wakeTimers();e.stop() }.apply { start() } };threads.forEach { it.join() }
        assertSame(e.stop(),e.stop("other",true));assertEquals(Admission.STOPPED,e.admit(m.observation(1)));finished(e)
        assertEquals(1L,e.snapshot().acknowledged)
    }
    @Test fun preparationOverloadRetainsKnownDeliveredGapAndBoundedBytes() {
        val m=SimulatedRecordingModule();val o=m.observation(0);val cap=o.ownedBytes
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val f=output();val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap*2,maxObservationBytes=cap,preferredChunkBytes=cap*4),hook={entered.countDown();release.await(5,TimeUnit.SECONDS)})
        assertEquals(Admission.ACCEPTED,e.admit(o));assertTrue(entered.await(5,TimeUnit.SECONDS))
        assertEquals(Admission.ACCEPTED,e.admit(m.observation(1)));assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(m.observation(2)))
        release.countDown();finished(e);assertEquals(2L,e.snapshot().acknowledged);assertEquals(1L,e.snapshot().preparationDrops)
        assertTrue(e.snapshot().maxPreparationQueuedBytes<=cap)
        val gaps=chunks(f).flatMap { it.metadata["entries"].arr().map { it.obj() } }.filter { it["kind"]=="gap" }
        assertEquals("2",gaps.single()["observation_first"]);assertEquals("1",gaps.single()["count"])
    }
    @Test fun delayedFooterIsNotCountedAsSavedAndStopTimeoutDoesNotInventSuccess() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend { override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record { if(type==LmtrFraming.CHUNK){entered.countDown();release.await(5,TimeUnit.SECONDS)};return base.append(type,ordinal,previous,body) }
            override fun close()=base.close() }
        val e=engine(m,f,backend=backend);e.admit(m.observation(0));e.stop();assertTrue(entered.await(5,TimeUnit.SECONDS))
        assertEquals(0L,e.snapshot().acknowledged);assertNull(e.awaitStop(20));release.countDown();finished(e)
    }
    @Test fun writerOverloadIsExplicitAndDoesNotIncreaseWriterQueue() {
        val m=SimulatedRecordingModule();val cap=m.observation(0).ownedBytes
        val f=output();val base=LocalRecorderBackend(f);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend { override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record { if(type==LmtrFraming.CHUNK){entered.countDown();release.await(5,TimeUnit.SECONDS)};return base.append(type,ordinal,previous,body) }
            override fun close()=base.close() }
        val time=AtomicLong(0);val e=engine(m,f,backend=backend,now=time::get,limits=RecorderLimits(preparationBytes=cap*20,writerBytes=cap,maxObservationBytes=cap,preferredChunkBytes=cap))
        e.admit(m.observation(0));until("pending"){e.snapshot().pendingBytes>0};time.set(1000000000);e.wakeTimers();assertTrue(entered.await(5,TimeUnit.SECONDS))
        for(i in 1..10)e.admit(m.observation(i.toLong()))
        until("writer loss"){e.snapshot().writerDrops>0};release.countDown();finished(e)
        val s=e.snapshot();assertTrue(s.maxWriterQueuedBytes<=cap);assertEquals(s.accepted,s.acknowledged+s.writerDrops)
    }
    @Test fun sourceDetachFinalizesInterruptedWithNoInventedLostCount() {
        val m=SimulatedRecordingModule();val e=engine(m);e.admit(m.observation(0));e.stop("source_detached",true)
        val r=e.awaitStop(10000)!!;assertTrue(r.toString(),r.finalized);assertEquals("interrupted",r.status);assertEquals(0L,e.snapshot().preparationDrops)
    }
    @Test fun resourceAdmissionAndMemoryPressureRefuseInsteadOfDowngrading() {
        assertThrows(IllegalArgumentException::class.java){RecorderLimits(maxObservationBytes=1048576,memoryBudgetBytes=1)}
        assertThrows(IllegalArgumentException::class.java){RecorderLimits(maxObservationBytes=1048576,availableStorageBytes=1)}
        assertThrows(IllegalArgumentException::class.java){RecorderLimits(maxObservationBytes=5000000)}
        val m=SimulatedRecordingModule();val e=engine(m);e.admit(m.observation(0));e.memoryPressure();val r=e.awaitStop(10000)!!
        assertTrue(r.toString(),r.finalized);assertEquals("failed",r.status);assertEquals(Admission.STOPPED,e.admit(m.observation(1)))
    }
    @Test fun tornAppendPreservesRecoverableVerifiedPrefixAndNeverAddsEnd() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f);var chunks=0
        val backend=object:RecorderBackend { override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK && ++chunks==2) { java.io.RandomAccessFile(f,"rw").use { it.seek(it.length());it.write(byteArrayOf(1,2,3));it.fd.sync() };throw IOException("injected torn body") }
                return base.append(type,ordinal,previous,body)
            };override fun close()=base.close() }
        val time=AtomicLong(0);val e=engine(m,f,backend=backend,now=time::get);e.admit(m.observation(0));until("pending"){e.snapshot().pendingBytes>0}
        time.set(1000000000);e.wakeTimers();until("first ack"){e.snapshot().acknowledged==1L};e.admit(m.observation(1));e.stop()
        assertFalse(e.awaitStop(10000)!!.finalized);assertTrue(f.exists());val r=LmtrReader(f).use { it.check(full=true,recover=true) }
        assertEquals("recovered",r["status"]);assertEquals("full_verified_prefix",r["integrity_scope"])
    }
    @Test fun closeFailureCannotPublishCompletion() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f)
        val b=object:RecorderBackend { override val destination=f;override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray)=base.append(type,ordinal,previous,body)
            override fun close(){base.close();throw IOException("close failure")} }
        val e=engine(m,f,backend=b);e.admit(m.observation(0));e.stop();assertFalse(e.awaitStop(10000)!!.finalized)
    }
    @Test fun sequenceGapsAreNotFabricatedFromCounters() {
        val m=SimulatedRecordingModule();val e=engine(m);e.admit(m.observation(0));assertEquals(Admission.REFUSED,e.admit(m.observation(2)))
        assertEquals("failed",e.awaitStop(10000)!!.status)
    }
    @Test fun emptyRecordingHasNoIdleCheckpointLoop() {
        val m=SimulatedRecordingModule();val e=engine(m);Thread.sleep(60);assertEquals(0L,e.snapshot().checkpoints);finished(e)
    }
    @Test fun qualifiedSourceLossAdvancesOnlyKnownObservationWitnesses() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f);e.admit(m.observation(0))
        val gap=mapOf("category" to "source_loss","reason" to "module_reported_loss","count" to "2",
            "observation_first" to "1","observation_last" to "2","device_first" to null,"device_last" to null,"scheduled" to null,
            "evidence" to mapOf("basis" to "host_observation","before" to null,"after" to null))
        assertEquals(Admission.ACCEPTED,e.admitLoss(RecordingLoss(m.id,m.stream,gap)));e.admit(m.observation(3));finished(e)
        val entries=chunks(f).flatMap { it.metadata["entries"].arr().map { it.obj() } };assertEquals("2",entries.single { it["kind"]=="gap" }["count"])
    }
    @Test fun unknownSourceInterruptionNeverInventsMissingFrames() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f)
        val gap=mapOf("category" to "interruption","reason" to "unknown_source_interruption","count" to null,
            "observation_first" to null,"observation_last" to null,"device_first" to null,"device_last" to null,"scheduled" to null,
            "evidence" to mapOf("basis" to "unknown","before" to null,"after" to null))
        e.admitLoss(RecordingLoss(m.id,m.stream,gap));e.admit(m.observation(0));finished(e)
        assertNull(chunks(f).flatMap { it.metadata["entries"].arr().map { it.obj() } }.single { it["kind"]=="gap" }["count"])
    }
    @Test fun thirtySecondChunkGetsCheckpointBeforeNextCommit() {
        val m=SimulatedRecordingModule();val time=AtomicLong(0);val e=engine(m,now=time::get)
        for(i in 0..32) {
            e.admit(m.observation(i.toLong()));until("pending $i"){e.snapshot().pendingBytes>0}
            time.addAndGet(1000000000);e.wakeTimers();until("ack $i"){e.snapshot().acknowledged==i+1L}
            if(i==31)assertTrue(e.snapshot().checkpoints>=1)
        }
        finished(e)
    }
    @Test fun slowPreparationUsesOriginalIntakeDeadlineNotCompletionTime() {
        val m=SimulatedRecordingModule();val time=AtomicLong(0);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=engine(m,now=time::get,hook={entered.countDown();release.await(5,TimeUnit.SECONDS)})
        e.admit(m.observation(0));assertTrue(entered.await(5,TimeUnit.SECONDS));time.set(2000000000);release.countDown()
        until("late preparation seals immediately"){e.snapshot().acknowledged==1L};finished(e)
    }
    @Test fun exhaustedLossReservePreservesIncompleteFileInsteadOfCertifyingSilentLoss() {
        val m=SimulatedRecordingModule();val cap=m.observation(0).ownedBytes;val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=engine(m,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,preferredChunkBytes=cap*4,controlBytes=32768),
            hook={entered.countDown();release.await(5,TimeUnit.SECONDS)})
        e.admit(m.observation(0));assertTrue(entered.await(5,TimeUnit.SECONDS));e.admit(m.observation(1))
        assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(m.observation(2)))
        assertEquals(Admission.REFUSED,e.admit(m.observation(3)));release.countDown();assertFalse(e.awaitStop(10000)!!.finalized)
    }
    @Test fun laterPreparationFailureCannotClearUnjournaledLossEvidence() {
        val m=SimulatedRecordingModule();val cap=m.observation(0).ownedBytes
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=engine(m,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,
            preferredChunkBytes=cap*4,controlBytes=32768),hook={
                entered.countDown();check(release.await(5,TimeUnit.SECONDS));throw IOException("Injected preparation failure")
            })
        e.admit(m.observation(0));assertTrue(entered.await(5,TimeUnit.SECONDS))
        e.admit(m.observation(1));assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(m.observation(2)))
        assertEquals(Admission.REFUSED,e.admit(m.observation(3)))
        release.countDown();val result=e.awaitStop(10000)!!
        assertFalse(result.finalized);assertTrue(result.failure!!.contains("Cannot certify unjournaled"))
    }
    @Test fun representedSourceFailureThenWriterJournalExhaustionCannotFinalize() {
        val m=SimulatedRecordingModule();val cap=m.observation(0).ownedBytes;val f=output();val base=LocalRecorderBackend(f)
        val headerEntered=CountDownLatch(1);val headerRelease=CountDownLatch(1)
        val prepEntered=CountDownLatch(1);val prepRelease=CountDownLatch(1)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.HEADER){headerEntered.countDown();check(headerRelease.await(5,TimeUnit.SECONDS))}
                return base.append(type,ordinal,previous,body)
            }
            override fun close()=base.close()
        }
        val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap*3,writerBytes=cap,maxObservationBytes=cap,
            preferredChunkBytes=cap*4,controlBytes=32768),now={0L},hook={prepEntered.countDown();check(prepRelease.await(5,TimeUnit.SECONDS))},backend=backend)
        try {
            assertTrue(headerEntered.await(5,TimeUnit.SECONDS))
            e.admit(RecordingOffer(m.id,0,FrozenJson(m.stream),cap){throw IllegalArgumentException("Bad source evidence")})
            assertTrue(prepEntered.await(5,TimeUnit.SECONDS))
            assertEquals(Admission.ACCEPTED,e.admit(m.observation(1)))
            assertEquals(Admission.ACCEPTED,e.admit(m.observation(2)))
            // Bad source reserves the sole loss entry, then the already-admitted writer
            // backlog needs another loss reservation. A later worker error changes diagnostics.
            prepRelease.countDown();until("worker failed after second loss refusal"){e.snapshot().error=="preparation_failed"}
            val stopped=e.stop();assertSame(stopped,e.stop("source_detached",true));headerRelease.countDown()
            assertFalse(e.awaitStop(10000)!!.finalized)
            assertEquals(0L,e.snapshot().acknowledged)
        } finally { prepRelease.countDown();headerRelease.countDown();e.stop() }
    }
    @Test fun stopDetachSourceErrorAndRepeatedFailureCannotClearUnrepresentedLoss() {
        for(exit in listOf("stop","detach","source_error","memory_pressure")) {
            val m=SimulatedRecordingModule();val cap=m.observation(0).ownedBytes;val f=output()
            val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,
                preferredChunkBytes=cap*4,controlBytes=32768),hook={
                    entered.countDown();check(release.await(5,TimeUnit.SECONDS));throw IOException("Later worker error")
                })
            try {
                e.admit(m.observation(0));assertTrue(entered.await(5,TimeUnit.SECONDS));e.admit(m.observation(1))
                assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(m.observation(2)))
                assertEquals(Admission.REFUSED,e.admit(m.observation(3)))
                val race=Thread { when(exit){
                    "detach" -> e.stop("source_detached",true)
                    "source_error" -> e.failSource("acquisition_error")
                    "memory_pressure" -> e.memoryPressure()
                    else -> e.stop()
                } }.apply { start() }
                val completion=e.stop();release.countDown();race.join()
                val result=e.awaitStop(10000)!!;assertFalse(result.toString(),result.finalized)
                val bytes=LmtrChunk.sha(f.readBytes());val acknowledged=e.snapshot().acknowledged
                repeat(3){assertSame(completion,e.stop());assertSame(completion,e.failSource("later_error"));assertSame(result,e.awaitStop(10))}
                assertEquals(bytes,LmtrChunk.sha(f.readBytes()));assertEquals(acknowledged,e.snapshot().acknowledged)
            } finally { release.countDown();e.stop() }
        }
    }
    @Test fun completelyJournaledSourceFailureIsHonestlyFinalizable() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f)
        e.admit(RecordingOffer(m.id,0,FrozenJson(m.stream),m.observation(0).ownedBytes){throw IllegalArgumentException("Bad current source")})
        val result=e.awaitStop(10000)!!
        assertTrue(result.toString(),result.finalized);assertEquals("failed",result.status)
        assertEquals(0L,e.snapshot().acknowledged)
        val full=LmtrReader(f).use { it.check(full=true) }
        assertEquals("1",full["summary"].obj()["gap_events"])
        assertEquals("1",full["summary"].obj()["known_missing_observations"])
        assertEquals("failed",full["declared_end_status"])
    }
    @Test fun unexpectedPreparationExitDuringStopCannotHideAbandonedAdmission() {
        val m=SimulatedRecordingModule();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=engine(m,hook={entered.countDown();check(release.await(5,TimeUnit.SECONDS));throw IOException("Unexpected worker exit")})
        try {
            assertEquals(Admission.ACCEPTED,e.admit(m.observation(0)));assertTrue(entered.await(5,TimeUnit.SECONDS))
            val completion=e.stop();assertFalse(completion.isDone);release.countDown()
            val result=e.awaitStop(10000)!!;assertFalse(result.finalized);assertEquals(0L,e.snapshot().acknowledged)
            assertTrue(result.failure!!.contains("Cannot certify unjournaled"))
        } finally { release.countDown();e.stop() }
    }
    @Test fun malformedOriginalMaskCannotReceivePromisedChunkFooter() {
        val m=SimulatedRecordingModule();val o=m.observation(0,masked=true)
        val bad=o.payloads.map { if(it.role=="validity")RecordingPayload(it.descriptor.value(),ByteArray(it.size){2}) else it }
        val invalid=RecordingObservation(m.id,0,m.stream,o.context.value(),o.facts.value(),bad)
        val e=engine(m);e.admit(invalid);e.stop();assertFalse(e.awaitStop(10000)!!.finalized);assertEquals(0L,e.snapshot().acknowledged)
    }
    @Test fun declaredRequiredEvidenceNeedsExplicitAbsenceReason() {
        val m=SimulatedRecordingModule();val o=m.observation(0)
        val missing=RecordingObservation(m.id,0,m.stream,o.context.value(),o.facts.value(),o.payloads.filter { it.role!="native_samples" })
        assertThrows(IllegalArgumentException::class.java){RecordingProfiles.select(missing,RecordingProfile.FULL)}
    }
    @Test fun regressedIntakeClockIsRejectedWithoutSubstitutingReceiptClock() {
        val m=SimulatedRecordingModule();val time=AtomicLong(10);val e=engine(m,now=time::get);e.admit(m.observation(0));time.set(9)
        assertThrows(IllegalArgumentException::class.java){e.admit(m.observation(1))};time.set(10);finished(e)
    }

    @Test fun failedJournalOwnerCannotPublishAnotherCheckpointOrEnd() {
        val m=SimulatedRecordingModule();val file=output();val base=LocalRecorderBackend(file);var writes=0
        val owner=LmtrAppendSession(SimulatedRecordingModule.header(listOf(m)),LmtrRecordSink { type,ordinal,previous,body ->
            writes++;base.append(type,ordinal,previous,body)
        })
        assertThrows(LmtrFailure::class.java){owner.appendChunk(byteArrayOf(1,2))}
        assertThrows(IllegalStateException::class.java){owner.checkpoint()}
        assertThrows(IllegalStateException::class.java){owner.finish("completed","user_stop",mapOf("status" to "unknown","clock_source" to "host_utc","reason" to "not_recorded"))}
        assertEquals(1,writes);base.close()
    }

    @Test fun suppliedIncorrectStatisticsAreRejectedRatherThanSilentlyReplaced() {
        val m=SimulatedRecordingModule();val o=m.observation(0);val temperature=o.payloads.first { it.role=="temperature" }.bytes()
        val cache=LmtrChunk.statistics(temperature,null,3,2)+mapOf("method" to "finite-valid-row-major-f64-v1",
            "temperature_payload_id" to "temperature","unit" to "Cel","mean" to java.math.BigDecimal("999"))
        val facts=o.facts.value()+("measurement" to (o.facts.value()["measurement"].obj()+("statistics" to cache)))
        val e=engine(m);e.admit(RecordingObservation(m.id,0,m.stream,o.context.value(),facts,o.payloads));e.stop()
        assertFalse(e.awaitStop(10000)!!.finalized);assertEquals(0L,e.snapshot().acknowledged)
    }

    @Test fun chunkAcknowledgmentRemainsTruthfulWhenFollowingNavigationFails() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.INDEX)throw IOException("navigation write failure after source acknowledgment")
                return base.append(type,ordinal,previous,body)
            }
            override fun close()=base.close()
        }
        val e=engine(m,f,backend=backend);e.admit(m.observation(0));e.stop();val result=e.awaitStop(10000)!!
        assertFalse(result.finalized);assertEquals(1L,e.snapshot().acknowledged)
        val sourceHash=LmtrChunk.sha(f.readBytes())
        val prefix=LmtrReader(f).use { it.check(full=true,recover=true) }
        assertEquals("1",prefix["summary"].obj()["observations"])
        assertEquals(true,prefix["unknown_tail"]);assertNull(prefix["declared_end_status"])
        assertSame(e.stop(),e.stop("later_source_detach",true))
        assertEquals(sourceHash,LmtrChunk.sha(f.readBytes()));assertEquals(1L,e.snapshot().acknowledged)
    }

    @Test fun lateSegmentClosureAndStopDoNotExtendOldChunkTimelineSpan() {
        val m=SimulatedRecordingModule();val f=output();val time=AtomicLong(0);val e=engine(m,f,now=time::get)
        e.admit(m.observation(0));until("pending"){e.snapshot().pendingBytes>0};time.set(2000000000)
        val changed=SimulatedRecordingModule(width=7,height=3,session="00000000-0000-4000-8000-000000000066")
        e.admit(changed.observation(0));time.set(4000000000);finished(e)
        for(chunk in chunks(f)) {
            val entries=chunk.metadata["entries"].arr().map { it.obj() }
            assertTrue(entries.last()["timeline_ns"].d()-entries.first()["timeline_ns"].d()<=1000000000L)
        }
    }

    @Test fun negotiatedObservationAbovePreferredTargetStaysWholeInSingleton() {
        val m=SimulatedRecordingModule();val f=output();val cap=m.observation(0).ownedBytes
        val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,preferredChunkBytes=1))
        e.admit(m.observation(0));finished(e)
        val observationChunk=chunks(f).single { it.metadata["entries"].arr().any { it.obj()["kind"]=="observation" } }
        assertEquals(1,observationChunk.metadata["entries"].arr().size)
        assertArrayEquals(m.observation(0).payloads.first { it.role=="temperature" }.bytes(),observationChunk.payloads["1:temperature"])
    }

}
