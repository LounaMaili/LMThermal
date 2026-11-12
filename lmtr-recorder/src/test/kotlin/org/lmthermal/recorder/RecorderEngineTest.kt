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
    @Test fun firstSourceFailureSurvivesConcurrentStopSecondaryPreparationAndCleanup() {
        val m=SimulatedRecordingModule();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=engine(m,now={0L},hook={entered.countDown();check(release.await(5,TimeUnit.SECONDS));throw IOException("secondary worker failure")})
        try {
            e.admit(m.observation(0));assertTrue(entered.await(5,TimeUnit.SECONDS))
            e.failSource("initial_source_error");val first=e.snapshot().firstFailure!!
            assertEquals(RecorderFailureStage.SOURCE,first.stage);assertEquals(RecorderFailureCode.SOURCE_FAILURE,first.code)
            assertEquals("initial_source_error",first.detailCode)
            assertEquals(1L,first.accepted);assertEquals(0L,first.acknowledged);assertEquals(0L,first.lastIntakeWitnessNs)
            val race=Thread {e.stop("source_detached",true);e.failSource("secondary_source_error")}.apply {start()}
            val stopped=e.stop();release.countDown();race.join(5000);assertFalse(race.isAlive)
            val result=e.awaitStop(10000)!!;until("workers released"){e.workersTerminated()}
            assertFalse(result.finalized);assertEquals("write_or_validation_failure",result.reason)
            assertTrue(result.failure!!.contains("Cannot certify unjournaled"))
            assertSame(first,result.firstFailure);assertSame(first,e.snapshot().firstFailure)
            assertEquals(1L,first.accepted);assertEquals(0L,first.acknowledged)
            repeat(3){assertSame(stopped,e.stop());e.failSource("late_error");e.memoryPressure()}
            assertSame(first,e.snapshot().firstFailure)
        } finally {release.countDown();e.stop()}
    }
    @Test fun invalidPreparationIsRecordedBeforeItsLossReservationFails() {
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
        val e=engine(m,f,backend=backend,now={0L},limits=RecorderLimits(preparationBytes=cap,
            writerBytes=cap*3,maxObservationBytes=cap,controlBytes=32768),hook={prepEntered.countDown();check(prepRelease.await(5,TimeUnit.SECONDS))})
        try {
            assertTrue(headerEntered.await(5,TimeUnit.SECONDS))
            e.admit(RecordingOffer(m.id,0,FrozenJson(m.stream),cap){throw IllegalArgumentException("initial bad evidence")})
            assertTrue(prepEntered.await(5,TimeUnit.SECONDS));e.admit(m.observation(1))
            assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(m.observation(2)))
            prepRelease.countDown();until("reservation exhausted"){e.snapshot().unrepresentedLoss}
            val first=e.snapshot().firstFailure!!
            assertEquals(RecorderFailureStage.PREPARATION,first.stage);assertEquals(RecorderFailureCode.INVALID_MODULE_EVIDENCE,first.code)
            assertEquals(RecorderExceptionKind.ARGUMENT,first.exceptionKind);assertEquals(0L,first.observationSequence)
            assertEquals(m.id,first.streamId);assertEquals(32768L,first.controlBytes);assertFalse(first.unrepresentedLoss)
            headerRelease.countDown();val result=e.awaitStop(10000)!!
            assertFalse(result.finalized);assertSame(first,result.firstFailure);assertTrue(e.snapshot().unrepresentedLoss)
        } finally {prepRelease.countDown();headerRelease.countDown();e.stop()}
    }
    @Test fun writerOverloadReserveExhaustionRetainsItsStageBeforeGenericPreparationFailure() {
        val m=SimulatedRecordingModule();val o=m.observation(0);val cap=o.ownedBytes
        val prepared=RecordingProfiles.select(o,RecordingProfile.FULL).ownedBytes
        val f=output();val base=LocalRecorderBackend(f);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.HEADER){entered.countDown();check(release.await(5,TimeUnit.SECONDS))}
                return base.append(type,ordinal,previous,body)
            }
            override fun close()=base.close()
        }
        val e=engine(m,f,backend=backend,now={0L},limits=RecorderLimits(preparationBytes=cap*3,
            writerBytes=maxOf(cap,prepared),maxObservationBytes=cap,controlBytes=32768))
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));e.admit(o)
            until("first queued writer owner"){e.snapshot().writerQueuedBytes==prepared}
            e.admit(m.observation(1));until("represented writer gap"){e.snapshot().writerDrops==1L}
            assertNull("Represented overload is a gap, not a terminal failure",e.snapshot().firstFailure)
            e.admit(m.observation(2));until("preparation exit"){e.snapshot().error=="preparation_failed"}
            val first=e.snapshot().firstFailure!!
            assertEquals(RecorderFailureStage.WRITER_LOSS_RESERVE,first.stage);assertEquals(RecorderFailureCode.LOSS_JOURNAL_EXHAUSTED,first.code)
            assertEquals(2L,first.observationSequence);assertEquals(3L,first.accepted);assertEquals(1L,first.writerDrops)
            assertEquals(32768L,first.controlBytes);assertTrue(first.unrepresentedLoss)
            release.countDown();val result=e.awaitStop(10000)!!
            assertFalse(result.finalized);assertSame(first,result.firstFailure);assertSame(first,e.snapshot().firstFailure)
        } finally {release.countDown();e.stop()}
    }
    @Test fun laterAllocationFailureCannotReplaceAnEarlierSourceWitness() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                entered.countDown();check(release.await(5,TimeUnit.SECONDS));throw OutOfMemoryError("injected")
            }
            override fun close()=base.close()
        }
        val e=engine(m,f,backend=backend,now={0L})
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));e.failSource("initial_source_error")
            val first=e.snapshot().firstFailure!!;release.countDown();val result=e.awaitStop(10000)!!
            assertEquals("allocation_failed",result.reason);assertFalse(result.finalized)
            assertSame(first,result.firstFailure);assertEquals(RecorderFailureCode.SOURCE_FAILURE,first.code)
            assertTrue(e.snapshot().unrepresentedLoss)
        } finally {release.countDown();e.stop()}
    }
    @Test fun appendAndReportedSyncFailuresAreRecordedBeforeSecondaryCloseFailure() {
        for(mode in listOf("before_write","torn_body","reported_sync_failure")) {
            val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f);var closes=0
            val backend=object:RecorderBackend {
                override val destination=f
                override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                    if(type==LmtrFraming.CHUNK) {
                        if(mode=="torn_body")java.io.RandomAccessFile(f,"rw").use {it.seek(it.length());it.write(byteArrayOf(1,2,3));it.fd.sync()}
                        // This backend reports a sync failure after a complete record. Real sync here
                        // succeeded: this checks uncertain acknowledgment, not power-loss durability.
                        if(mode=="reported_sync_failure")base.append(type,ordinal,previous,body)
                        throw IOException("initial $mode")
                    }
                    return base.append(type,ordinal,previous,body)
                }
                override fun close(){closes++;base.close();throw IllegalStateException("secondary close failure")}
            }
            val e=engine(m,f,backend=backend,now={0L});e.admit(m.observation(0));e.stop()
            val result=e.awaitStop(10000)!!;val first=result.firstFailure!!
            assertFalse(result.finalized);assertEquals(RecorderFailureStage.BACKEND_APPEND,first.stage)
            assertEquals(RecorderExceptionKind.IO,first.exceptionKind);assertEquals(LmtrFraming.CHUNK,first.recordType)
            assertEquals(1L,first.recordOrdinal);assertEquals(0L,first.acknowledged);assertEquals(0L,e.snapshot().acknowledged)
            assertEquals(1,closes);assertSame(first,e.snapshot().firstFailure)
            val bytes=f.readBytes();val recovery=LmtrReader(f).use {it.check(full=true,recover=true)}
            assertNull(recovery["declared_end_status"]);assertArrayEquals(bytes,f.readBytes())
        }
    }
    @Test fun refusedOversizeIdentityDoesNotRetainUnboundedDiagnosticText() {
        val m=SimulatedRecordingModule();val e=engine(m,now={0L})
        assertEquals(Admission.REFUSED,e.admit(SimulatedRecordingModule("x".repeat(16384)).observation(0)))
        val result=e.awaitStop(10000)!!;val first=result.firstFailure!!
        assertEquals(RecorderFailureStage.ADMISSION,first.stage)
        assertEquals(RecorderFailureCode.OVERSIZE_METADATA,first.code);assertNull(first.streamId)
        assertEquals(0L,first.accepted);assertSame(first,e.snapshot().firstFailure)
    }
    @Test fun sourceReasonProseCannotBecomeRetainedDiagnosticMessage() {
        val m=SimulatedRecordingModule();val e=engine(m,now={0L})
        e.failSource("short exception-like prose: private details")
        val result=e.awaitStop(10000)!!;assertNull(result.firstFailure!!.detailCode)
        assertEquals(RecorderFailureCode.SOURCE_FAILURE,result.firstFailure!!.code)
    }
    @Test fun successfulTerminationCannotGainAFailureFromLatePublicCallbacks() {
        val m=SimulatedRecordingModule();val e=engine(m,now={0L});e.admit(m.observation(0));val result=finished(e)
        assertNull(result.firstFailure);e.failSource("late_source_error");e.memoryPressure();e.stop()
        assertNull(e.snapshot().firstFailure);assertFalse(e.snapshot().unrepresentedLoss)
    }
    @Test fun allProfilesPreserveExactTemperatureAndInterpretationContext() {
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL))for(codec in 0..1) {
            val m=SimulatedRecordingModule();val file=output();val e=engine(m,file,profile,codec)
            val o=m.observation(0,masked=true)
            // External copy mutation cannot alter the immutable owners used directly by encoding.
            val external=o.payloads.first { it.role=="temperature" }.bytes();external[0]=(external[0].toInt() xor 255).toByte()
            assertEquals(Admission.ACCEPTED,e.admit(o));finished(e)
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
        val writerCap=maxOf(cap,RecordingProfiles.select(o,RecordingProfile.FULL).ownedBytes)*2
        val f=output();val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap,writerBytes=writerCap,maxObservationBytes=cap,preferredChunkBytes=cap*4),hook={entered.countDown();release.await(5,TimeUnit.SECONDS)})
        assertEquals(Admission.ACCEPTED,e.admit(o));assertTrue(entered.await(5,TimeUnit.SECONDS))
        assertEquals(Admission.ACCEPTED,e.admit(m.observation(1)));assertEquals(Admission.PREPARATION_OVERLOAD,e.admit(m.observation(2)))
        release.countDown();finished(e);assertEquals(2L,e.snapshot().acknowledged);assertEquals(1L,e.snapshot().preparationDrops)
        assertEquals("This test isolates preparation, not writer overload",0L,e.snapshot().writerDrops);assertNull(e.snapshot().firstFailure)
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
        val m=SimulatedRecordingModule()
        // Observation 10 has longer sequence/receipt JSON than 0. Negotiate every offered owner,
        // otherwise this overload test silently includes an unrelated admission-refused failure.
        val offered=(0L..10L).map {m.observation(it)};val cap=offered.maxOf {it.ownedBytes}
        val f=output();val base=LocalRecorderBackend(f);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend { override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record { if(type==LmtrFraming.CHUNK){entered.countDown();release.await(5,TimeUnit.SECONDS)};return base.append(type,ordinal,previous,body) }
            override fun close()=base.close() }
        val writerCap=maxOf(cap,offered.maxOf {RecordingProfiles.select(it,RecordingProfile.FULL).ownedBytes})
        val time=AtomicLong(0);val e=engine(m,f,backend=backend,now=time::get,limits=RecorderLimits(preparationBytes=cap*20,writerBytes=writerCap,maxObservationBytes=cap,preferredChunkBytes=cap))
        e.admit(m.observation(0));until("pending"){e.snapshot().pendingBytes>0};time.set(1000000000);e.wakeTimers();assertTrue(entered.await(5,TimeUnit.SECONDS))
        for(o in offered.drop(1))assertEquals(Admission.ACCEPTED,e.admit(o))
        until("writer loss"){e.snapshot().writerDrops>0};release.countDown();finished(e)
        val s=e.snapshot();assertTrue(s.maxWriterQueuedBytes<=writerCap);assertTrue(s.acknowledged>0)
        assertEquals(s.accepted,s.acknowledged+s.writerDrops)
        assertNull("${s.firstFailure?.stage}/${s.firstFailure?.code}, observation=${s.firstFailure?.observationSequence}",s.firstFailure)
    }
    @Test fun writerAccountsPreparedOwnersWithoutChargingTheDeferredSourceAgain() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f);val time=AtomicLong(0)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val cap=1048576L
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK){entered.countDown();check(release.await(5,TimeUnit.SECONDS))}
                return base.append(type,ordinal,previous,body)
            }
            override fun close()=base.close()
        }
        val e=engine(m,f,backend=backend,now=time::get,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap))
        fun offer(n:Long):RecordingOffer {val original=m.observation(n);return RecordingOffer(m.id,n,original.stream,cap){original}}
        e.admit(offer(0));until("pending"){e.snapshot().pendingBytes>0};time.set(1000000000);e.wakeTimers()
        assertTrue(entered.await(5,TimeUnit.SECONDS));var expected=0L
        try {
            for(n in 1L..4L) {
                expected+=RecordingProfiles.select(m.observation(n),RecordingProfile.FULL).ownedBytes
                assertEquals(Admission.ACCEPTED,e.admit(offer(n)))
                until("prepared owner $n"){e.snapshot().let {it.preparationQueuedBytes==0L && it.preparationInFlightBytes==0L}}
            }
            assertEquals(expected,e.snapshot().writerQueuedBytes);assertTrue(expected<cap)
            assertEquals(0L,e.snapshot().writerDrops)
        } finally {release.countDown()}
        finished(e);assertEquals(5L,e.snapshot().acknowledged);assertTrue(e.snapshot().maxWriterQueuedBytes<=cap)
    }

    @Test fun sourceDetachFinalizesInterruptedWithNoInventedLostCount() {
        val m=SimulatedRecordingModule();val e=engine(m);e.admit(m.observation(0));e.stop("source_detached",true)
        val r=e.awaitStop(10000)!!;assertTrue(r.toString(),r.finalized);assertEquals("interrupted",r.status);assertEquals(0L,e.snapshot().preparationDrops);assertNull(r.firstFailure)
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
    @Test fun preparationAllocationFailurePoisonsEndAndPreservesAcknowledgedPrefix() {
        val m=SimulatedRecordingModule();val f=output();val time=AtomicLong(0);var preparation=0
        val e=engine(m,f,now=time::get,hook={if(++preparation==2)throw OutOfMemoryError("injected, not heap exhaustion")})
        e.admit(m.observation(0));until("first pending"){e.snapshot().pendingBytes>0}
        time.set(1000000000);e.wakeTimers();until("first ack"){e.snapshot().acknowledged==1L}
        e.admit(m.observation(1));val result=e.awaitStop(10000)!!
        assertFalse(result.finalized);assertEquals("allocation_failed",result.reason)
        assertEquals(RecorderFailureCode.ALLOCATION_FAILED,result.firstFailure!!.code)
        assertEquals(RecorderExceptionKind.ALLOCATION,result.firstFailure!!.exceptionKind)
        assertSame(result.firstFailure,e.snapshot().firstFailure)
        until("workers release"){e.workersTerminated()}
        assertEquals(Admission.STOPPED,e.admit(m.observation(2)))
        assertEquals(0L,e.snapshot().preparationQueuedBytes);assertEquals(0L,e.snapshot().writerQueuedBytes)
        val before=f.readBytes();val report=LmtrReader(f).use {it.check(full=true,recover=true)}
        assertEquals("recovered",report["status"]);assertEquals("full_verified_prefix",report["integrity_scope"])
        assertArrayEquals(before,f.readBytes())
    }

    @Test fun allocationFailureRejectsLatePreparationDuringSlowBackendClose() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f);val time=AtomicLong(0)
        val appending=CountDownLatch(1);val failWriter=CountDownLatch(1);val preparing=CountDownLatch(1)
        val finishPreparation=CountDownLatch(1);val closing=CountDownLatch(1);val finishClose=CountDownLatch(1);var prepared=0
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK){appending.countDown();check(failWriter.await(5,TimeUnit.SECONDS));throw OutOfMemoryError("injected")}
                return base.append(type,ordinal,previous,body)
            }
            override fun close(){closing.countDown();check(finishClose.await(5,TimeUnit.SECONDS));base.close()}
        }
        val e=engine(m,f,backend=backend,now=time::get,hook={if(++prepared==2){preparing.countDown();check(finishPreparation.await(5,TimeUnit.SECONDS))}})
        try {
            e.admit(m.observation(0));until("first pending"){e.snapshot().pendingBytes>0}
            time.set(1000000000);e.wakeTimers();assertTrue(appending.await(5,TimeUnit.SECONDS))
            e.admit(m.observation(1));assertTrue(preparing.await(5,TimeUnit.SECONDS))
            failWriter.countDown();assertTrue(closing.await(5,TimeUnit.SECONDS));finishPreparation.countDown()
            until("late preparation released"){e.snapshot().preparationInFlightBytes==0L}
            assertEquals(0L,e.snapshot().writerQueuedBytes);assertEquals("failed",e.snapshot().state)
        } finally {failWriter.countDown();finishPreparation.countDown();finishClose.countDown()}
        assertFalse(e.awaitStop(10000)!!.finalized);until("workers release"){e.workersTerminated()}
    }

    @Test fun writerAllocationFailureNeverCertifiesPartialBodyOrRepairEnd() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f);var chunks=0
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK && ++chunks==2) {
                    java.io.RandomAccessFile(f,"rw").use {it.seek(it.length());it.write(byteArrayOf(1,2,3));it.fd.sync()}
                    throw OutOfMemoryError("injected after partial write")
                }
                return base.append(type,ordinal,previous,body)
            }
            override fun close()=base.close()
        }
        val time=AtomicLong(0);val e=engine(m,f,backend=backend,now=time::get)
        e.admit(m.observation(0));until("first pending"){e.snapshot().pendingBytes>0}
        time.set(1000000000);e.wakeTimers();until("first ack"){e.snapshot().acknowledged==1L}
        e.admit(m.observation(1));e.stop();val result=e.awaitStop(10000)!!
        assertFalse(result.finalized);assertEquals("allocation_failed",result.reason)
        assertEquals(RecorderFailureCode.ALLOCATION_FAILED,result.firstFailure!!.code)
        assertEquals(RecorderExceptionKind.ALLOCATION,result.firstFailure!!.exceptionKind)
        assertSame(result.firstFailure,e.snapshot().firstFailure)
        until("workers release"){e.workersTerminated()};assertEquals(1L,e.snapshot().acknowledged)
        val before=f.readBytes();val report=LmtrReader(f).use {it.check(full=true,recover=true)}
        assertEquals("recovered",report["status"]);assertEquals("full_verified_prefix",report["integrity_scope"])
        assertArrayEquals(before,f.readBytes())
    }

    @Test fun closeFailureCannotPublishCompletion() {
        val m=SimulatedRecordingModule();val f=output();val base=LocalRecorderBackend(f)
        val b=object:RecorderBackend { override val destination=f;override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray)=base.append(type,ordinal,previous,body)
            override fun close(){base.close();throw IOException("close failure")} }
        val e=engine(m,f,backend=b);e.admit(m.observation(0));e.stop();val result=e.awaitStop(10000)!!
        assertFalse(result.finalized);assertEquals(RecorderFailureStage.BACKEND_CLOSE,result.firstFailure!!.stage)
        assertEquals(RecorderExceptionKind.IO,result.firstFailure!!.exceptionKind);assertSame(result.firstFailure,e.snapshot().firstFailure)
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
        assertEquals(Admission.REFUSED,e.admit(m.observation(3)))
        val first=e.snapshot().firstFailure!!;assertEquals(RecorderFailureStage.PREPARATION_LOSS_RESERVE,first.stage)
        assertEquals(3L,first.observationSequence);assertTrue(first.unrepresentedLoss)
        release.countDown();val result=e.awaitStop(10000)!!;assertFalse(result.finalized);assertSame(first,result.firstFailure)
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
        val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap*3,writerBytes=maxOf(cap,RecordingProfiles.select(m.observation(1),RecordingProfile.FULL).ownedBytes),maxObservationBytes=cap,
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
        assertEquals(RecorderFailureCode.INVALID_MODULE_EVIDENCE,result.firstFailure!!.code)
        assertEquals(RecorderFailureStage.PREPARATION,result.firstFailure!!.stage)
        assertFalse(e.snapshot().unrepresentedLoss);assertSame(result.firstFailure,e.snapshot().firstFailure)
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
    @Test fun idlePreparationInterruptionHasNoUnrepresentedAdmission() {
        val m=SimulatedRecordingModule();val f=output();val e=engine(m,f)
        // Fault injection targets this owned worker only, avoiding other test engines.
        // No runtime API exposes arbitrary thread interruption to a product caller.
        val field=RecorderEngine::class.java.getDeclaredField("preparationThread").apply { isAccessible=true }
        val worker=field.get(e) as Thread
        worker.interrupt();val result=e.awaitStop(10000)!!
        assertTrue(result.toString(),result.finalized);assertEquals("failed",result.status)
        assertEquals(0L,e.snapshot().accepted);assertEquals(0L,e.snapshot().acknowledged)
        assertEquals(RecorderFailureCode.PREPARATION_INTERRUPTED,result.firstFailure!!.code)
        assertEquals(RecorderExceptionKind.INTERRUPTED,result.firstFailure!!.exceptionKind)
        assertFalse(e.snapshot().unrepresentedLoss)
        val full=LmtrReader(f).use { it.check(full=true) }
        assertEquals("failed",full["declared_end_status"]);assertEquals("0",full["summary"].obj()["observations"])
    }
    @Test fun malformedOriginalMaskCannotReceivePromisedChunkFooter() {
        val m=SimulatedRecordingModule();val o=m.observation(0,masked=true)
        val bad=o.payloads.map { if(it.role=="validity")RecordingPayload(it.descriptor.value(),ByteArray(it.size){2}) else it }
        val invalid=RecordingObservation(m.id,0,m.stream,o.context.value(),o.facts.value(),bad)
        // A tiny preferred target forces sealing while the observation Work is still active.
        // The diagnostic must not infer that this Work identifies every payload in a CHUNK.
        val e=engine(m,limits=RecorderLimits(maxObservationBytes=1048576,preferredChunkBytes=1))
        e.admit(invalid);e.stop();val result=e.awaitStop(10000)!!
        assertFalse(result.finalized);assertEquals(0L,e.snapshot().acknowledged)
        assertEquals(RecorderFailureStage.CHUNK_ENCODE,result.firstFailure!!.stage)
        assertEquals(RecorderExceptionKind.CONTRACT,result.firstFailure!!.exceptionKind)
        assertEquals("invalid_payload",result.firstFailure!!.detailCode)
        assertNull(result.firstFailure!!.streamId);assertEquals(-1L,result.firstFailure!!.observationSequence)
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

    @Test fun largeOptionalDescriptorsCannotHideBehindTinyPendingPayloads() {
        val m=SimulatedRecordingModule();val f=output()
        val e=engine(m,f,now={0L},limits=RecorderLimits(maxObservationBytes=262144,preferredChunkBytes=65536))
        for(n in 0L..2L) {
            val original=m.observation(n)
            val payloads=original.payloads.mapIndexed {i,p->if(i==0)RecordingPayload(
                p.descriptor.value()+("future_optional" to List(20){"x".repeat(4096)}),p.bytes()) else p}
            assertEquals(Admission.ACCEPTED,e.admit(RecordingObservation(m.id,n,original.stream.value(),
                original.context.value(),original.facts.value(),payloads)))
        }
        finished(e)
        val containing=chunks(f).filter {it.metadata["entries"].arr().any {e->e.obj()["kind"]=="observation"}}
        assertEquals(3,containing.size)
        for(chunk in containing) {
            val entry=chunk.metadata["entries"].arr().map {it.obj()}.single {it["kind"]=="observation"}
            assertEquals(20,entry["payloads"].arr().first().obj()["future_optional"].arr().size)
        }
        assertTrue(e.snapshot().maxPendingBytes>=81920)
    }

    @Test fun resolvedMetadataGrowthCannotExceedWriterByteCapacity() {
        val m=SimulatedRecordingModule();val f=output();val original=m.observation(0);val cap=original.ownedBytes
        assertTrue(RecordingProfiles.select(original,RecordingProfile.FULL).ownedBytes>cap)
        val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,preferredChunkBytes=1))
        e.admit(original);finished(e)
        assertEquals(1L,e.snapshot().writerDrops);assertEquals(0L,e.snapshot().acknowledged)
        assertEquals(0L,e.snapshot().maxWriterQueuedBytes)
        assertEquals("writer_overload",chunks(f).flatMap {it.metadata["entries"].arr().map {e->e.obj()}}.single {it["kind"]=="gap"}["category"])
    }

    @Test fun exactObservationMetadataCapPreservesOptionalPrecisionAndNextByteRefuses() {
        val m=SimulatedRecordingModule();val original=m.observation(0)
        val facts=original.facts.value()+mapOf("future_decimal" to java.math.BigDecimal("1.2345678901234567890123456789"),"future_padding" to "")
        val occupied=original.stream.byteSize+original.context.byteSize+LmtrJson.encode(facts).size
        val padding="x".repeat(16384-occupied)
        val o=RecordingObservation(m.id,0,original.stream.value(),original.context.value(),facts+("future_padding" to padding),original.payloads)
        assertEquals(16384,o.stream.byteSize+o.context.byteSize+o.facts.byteSize)
        for(codec in 0..1) {
            val f=output();val e=engine(m,f,codec=codec)
            assertEquals(Admission.ACCEPTED,e.admit(o));finished(e)
            val entry=chunks(f).single().metadata["entries"].arr().map {it.obj()}.single {it["kind"]=="observation"}
            assertEquals(padding,entry["future_padding"])
            assertEquals(java.math.BigDecimal("1.2345678901234567890123456789"),(entry["future_decimal"] as JsonNumber).decimal)
            val oversized=RecordingObservation(m.id,0,original.stream.value(),original.context.value(),facts+("future_padding" to padding+"x"),original.payloads)
            val rejected=engine(m,codec=codec)
            assertEquals(Admission.REFUSED,rejected.admit(oversized));rejected.stop();assertNotNull(rejected.awaitStop(10000))
            assertEquals(0L,rejected.snapshot().acknowledged)
        }
    }

    @Test fun nearHardLimitOptionalDescriptorCannotBeSilentlyDroppedToCompleteChunk() {
        val m=SimulatedRecordingModule();val original=m.observation(0)
        val source=original.payloads.first()
        // Individually legal descriptor, but its required CHUNK placement/closure exceeds1 MiB.
        val base=source.descriptor.value()+("future_optional" to List(63){"x".repeat(16384)})
        val slack=LmtrJson.MAX_JSON_BYTES-32-LmtrJson.encode(base+("tail" to "")).size
        val descriptor=base+("tail" to "y".repeat(slack))
        assertEquals(LmtrJson.MAX_JSON_BYTES-32,LmtrJson.encode(descriptor).size)
        val payloads=listOf(RecordingPayload(descriptor,source.bytes()))+original.payloads.drop(1)
        for(codec in 0..1) {
            val f=output();val e=engine(m,f,codec=codec,limits=RecorderLimits(maxObservationBytes=2097152))
            assertEquals(Admission.ACCEPTED,e.admit(RecordingObservation(m.id,0,original.stream.value(),original.context.value(),original.facts.value(),payloads)))
            e.stop();assertFalse(e.awaitStop(10000)!!.finalized)
            assertEquals(0L,e.snapshot().acknowledged)
            val recovered=LmtrReader(f).use {it.check(full=true,recover=true)}
            assertNull(recovered["declared_end_status"])
        }
    }

    @Test fun negotiatedObservationAbovePreferredTargetStaysWholeInSingleton() {
        val m=SimulatedRecordingModule();val f=output();val original=m.observation(0)
        // A singleton must negotiate the resolved policy/retention JSON as well as payload bytes.
        val cap=maxOf(original.ownedBytes,RecordingProfiles.select(original,RecordingProfile.FULL).ownedBytes)
        val e=engine(m,f,limits=RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,preferredChunkBytes=1))
        e.admit(m.observation(0));finished(e)
        val observationChunk=chunks(f).single { it.metadata["entries"].arr().any { it.obj()["kind"]=="observation" } }
        assertEquals(1,observationChunk.metadata["entries"].arr().size)
        assertArrayEquals(m.observation(0).payloads.first { it.role=="temperature" }.bytes(),observationChunk.payloads["1:temperature"])
    }

}

/** Fixed identities and an independent fake diagnostic clock isolate bytes from timing overhead.
 * Kept in the engine-owned test file: field tests share only public fixture helpers and exclude
 * this file, while these tests deliberately exercise recorder-internal validation boundaries.
 */
class RecorderInstrumentationTest {
    private data class Event(val phase: RecorderPhase, val ns: Long, val complete: Boolean,
        val type: Int, val bytes: Long, val observations: Long, val gaps: Long)
    private fun metrics(events: MutableList<Event>, clock: RecorderMetricClock = RecorderMetricClock(AtomicLong()::incrementAndGet)) =
        RecorderInstrumentation({ p, n, c, t, b, o, g -> events.add(Event(p,n,c,t,b,o,g)) }, clock)

    /** A one-observation finite file has deterministic source/timeline IDs and no race-dependent drop. */
    private fun record(codec: Int, instrumentation: RecorderInstrumentation?, fail: Boolean = false): Pair<ByteArray, RecorderResult> {
        val module = SimulatedRecordingModule()
        val file = Files.createTempDirectory("r4-metrics-").resolve("capture.lmtr").toFile()
        val local = LocalRecorderBackend(file)
        val backend = object : RecorderBackend {
            override val destination: File = file
            override fun append(type: Int, ordinal: Long, previous: Long, body: ByteArray): Record {
                if (fail && type == LmtrFraming.CHUNK) throw IOException("injected append failure")
                return local.append(type, ordinal, previous, body)
            }
            override fun close() = local.close()
        }
        val engine = RecorderEngine(SimulatedRecordingModule.header(listOf(module),codec,true),
            listOf(module.stream),RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576),
            backend,codec,now={0L},instrumentation=instrumentation)
        assertEquals(Admission.ACCEPTED,engine.admit(module.observation(0,view=true,masked=true)))
        engine.stop()
        val result = engine.awaitStop(10000) ?: error("bounded Stop timed out")
        val until = System.nanoTime()+5_000_000_000L
        while (!engine.workersTerminated()) { check(System.nanoTime()<until); Thread.yield() }
        if (!fail) {
            assertTrue(result.toString(),result.finalized)
            assertNull(result.firstFailure)
            LmtrReader(file).use { it.check(full=true) }
        } else {
            assertFalse(result.finalized)
            assertEquals(RecorderFailureStage.BACKEND_APPEND,result.firstFailure!!.stage)
            assertEquals(0L,engine.snapshot().acknowledged)
            assertNull(LmtrReader(file).use { it.check(full=true,recover=true) }["declared_end_status"])
        }
        return file.readBytes() to result
    }

    @Test fun bothCodecsRetainExactFileBytesWithFineMetricsAndIndependentClock() {
        for (codec in 0..1) {
            val events = mutableListOf<Event>()
            val baseline = record(codec,null)
            val observed = record(codec,metrics(events))
            assertArrayEquals(baseline.first,observed.first)
            assertEquals(1,events.count { it.phase==RecorderPhase.CHUNK_FIRST_VALIDATION })
            assertEquals(1,events.count { it.phase==RecorderPhase.CHUNK_BUILD })
            assertTrue(events.all { it.complete && it.ns>=0 })
            val encode = events.single { it.phase==RecorderPhase.CHUNK_ENCODE_TOTAL }
            assertTrue(encode.ns > events.single { it.phase==RecorderPhase.CHUNK_FIRST_VALIDATION }.ns)
            assertEquals(1L,encode.observations); assertEquals(0L,encode.gaps)
            val types = events.filter { it.phase==RecorderPhase.BACKEND_APPEND }.map { it.type }.toSet()
            assertEquals(setOf(1,2,3,4,5),types)
            assertTrue(events.filter { it.phase==RecorderPhase.BACKEND_APPEND }.all { it.bytes>0 && it.observations == -1L })
        }
    }

    @Test fun throwingObserversIncludingAllocationErrorCannotPoisonDurableFiles() {
        for (codec in 0..1) {
            val baseline = record(codec,null).first
            for (allocation in listOf(false,true)) {
                val instrumentation = RecorderInstrumentation({ _,_,_,_,_,_,_ ->
                    if(allocation) throw OutOfMemoryError("injected observer only") else error("observer only")
                })
                assertArrayEquals(baseline,record(codec,instrumentation).first)
                assertTrue(instrumentation.failures().second>0)
            }
        }
    }

    @Test fun invalidOrThrowingDiagnosticClockDoesNotReplaceRecorderClockOrOutcome() {
        val baseline = record(0,null).first
        val events = mutableListOf<Event>()
        val bad = metrics(events,RecorderMetricClock { throw IllegalStateException("clock only") })
        assertArrayEquals(baseline,record(0,bad).first)
        assertTrue(events.all { it.complete && it.ns == -1L })
        assertTrue(bad.failures().first>0)
    }

    @Test fun failedAppendIsInterruptedWithoutAcknowledgmentOrRepairEnd() {
        val events = mutableListOf<Event>()
        val reference = record(0,null,fail=true)
        val candidate = record(0,metrics(events),fail=true)
        assertArrayEquals(reference.first,candidate.first)
        assertEquals(reference.second.reason,candidate.second.reason)
        assertFalse(events.single { it.phase==RecorderPhase.APPEND_CHUNK }.complete)
        assertFalse(events.single { it.phase==RecorderPhase.BACKEND_APPEND && it.type==2 }.complete)
        assertTrue(events.single { it.phase==RecorderPhase.CHUNK_FIRST_VALIDATION }.complete)
        assertTrue(events.none { it.phase==RecorderPhase.BACKEND_APPEND && it.type==5 })
    }

    @Test fun failedAssemblyAndFailedFirstValidationRetainDistinctInterruptedScopes() {
        val module = SimulatedRecordingModule()
        val header = SimulatedRecordingModule.header(listOf(module),0,false)
        for (empty in listOf(true,false)) {
            val events = mutableListOf<Event>()
            val entries = if(empty) emptyList() else listOf(PendingEntry(mapOf("kind" to "gap"),mapOf("id" to "invalid")))
            try { RecordingChunk.encode(header,entries,0,134217728,instrumentation=metrics(events)); fail("invalid input accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: LmtrFailure) { }
            assertFalse(events.single { it.phase==RecorderPhase.CHUNK_ENCODE_TOTAL }.complete)
            assertEquals(!empty,events.single { it.phase==RecorderPhase.CHUNK_BUILD }.complete)
            if(empty) assertTrue(events.none { it.phase==RecorderPhase.CHUNK_FIRST_VALIDATION })
            else assertFalse(events.single { it.phase==RecorderPhase.CHUNK_FIRST_VALIDATION }.complete)
        }
    }

    @Test fun separateClockScopesHaveExactDurationsAndRejectRegressionOrOverflow() {
        for ((start,end,expected) in listOf(Triple(-5L,7L,12L),Triple(20L,19L,-1L),
            Triple(Long.MIN_VALUE+1,Long.MAX_VALUE,-1L),Triple(0L,Long.MAX_VALUE,Long.MAX_VALUE))) {
            var n=0;val events=mutableListOf<Event>()
            val instrument=metrics(events,RecorderMetricClock { if(n++==0)start else end })
            assertEquals("result",instrument.measure(RecorderPhase.CHECKPOINT) { "result" })
            assertEquals(expected,events.single().ns)
        }
    }
    @Test fun instrumentationCannotCertifyEndAfterLossReserveExhaustion() {
        val m=SimulatedRecordingModule();val cap=m.observation(0).ownedBytes
        val file=Files.createTempDirectory("r4-metrics-loss-").resolve("capture.lmtr").toFile()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val events=mutableListOf<Event>()
        val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(m),0,false),listOf(m.stream),
            RecordingProfile.FULL,RecorderLimits(preparationBytes=cap,writerBytes=cap,
                maxObservationBytes=cap,preferredChunkBytes=cap*4,controlBytes=32768),LocalRecorderBackend(file),
            now={0L},prepareHook={entered.countDown();check(release.await(5,TimeUnit.SECONDS))},instrumentation=metrics(events))
        try {
            assertEquals(Admission.ACCEPTED,engine.admit(m.observation(0)))
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            assertEquals(Admission.ACCEPTED,engine.admit(m.observation(1)))
            assertEquals(Admission.PREPARATION_OVERLOAD,engine.admit(m.observation(2)))
            assertEquals(Admission.REFUSED,engine.admit(m.observation(3)))
            val first=engine.snapshot().firstFailure!!
            assertEquals(RecorderFailureStage.PREPARATION_LOSS_RESERVE,first.stage)
            release.countDown()
            val result=engine.awaitStop(10000)!!
            assertFalse(result.finalized);assertSame(first,result.firstFailure)
            assertTrue(engine.snapshot().unrepresentedLoss)
            assertTrue(events.none { it.phase==RecorderPhase.BACKEND_APPEND && it.type==LmtrFraming.END })
            assertNull(LmtrReader(file).use { it.check(full=true,recover=true) }["declared_end_status"])
        } finally { release.countDown();engine.stop() }
    }

    @Test fun idleSealAndPeriodicCheckpointKeepTheirWitnessesWithInstrumentation() {
        val m=SimulatedRecordingModule();val events=mutableListOf<Event>();val clock=AtomicLong()
        val file=Files.createTempDirectory("r4-metrics-timer-").resolve("capture.lmtr").toFile()
        val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(m),0,false),listOf(m.stream),
            RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(file),
            now=clock::get,instrumentation=metrics(events))
        fun waitFor(condition:()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            while(!condition()){check(System.nanoTime()<deadline);Thread.sleep(2)}
        }
        try {
            assertEquals(Admission.ACCEPTED,engine.admit(m.observation(0)))
            waitFor { engine.snapshot().pendingBytes>0 }
            clock.set(1_000_000_000);engine.wakeTimers()
            waitFor { engine.snapshot().acknowledged==1L }
            clock.set(6_000_000_000);engine.wakeTimers()
            waitFor { engine.snapshot().checkpoints==1L }
            engine.stop();assertTrue(engine.awaitStop(10000)!!.finalized)
            waitFor { engine.workersTerminated() }
            assertEquals(1,events.count { it.phase==RecorderPhase.CHECKPOINT && it.complete })
            assertEquals(2,events.count { it.phase==RecorderPhase.BACKEND_APPEND && it.type==LmtrFraming.CHECKPOINT })
            // Idle already sealed the observation; Stop must still commit a separate segment_end.
            val validations=events.filter { it.phase==RecorderPhase.CHUNK_FIRST_VALIDATION }
            assertEquals(2,validations.size)
            assertEquals(listOf(1L,0L),validations.map { it.observations })
            assertTrue(validations.all { it.complete && it.gaps==0L })
            LmtrReader(file).use { it.check(full=true) }
        } finally {engine.stop()}
    }

}
