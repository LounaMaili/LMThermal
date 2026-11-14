package org.lmthermal.recording

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.core.LatestFrameState
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.io.IOException

/** Camera-free ownership/rotation regression: a UI instance never becomes the recorder's source. */
class PeriodicCameraLifetimeTest {
    private fun file()=Files.createTempDirectory("lmtr-periodic-lifetime-").resolve("capture.lmtr").toFile()
    /** A result can precede the last worker's return; a new reservation must await real release. */
    private fun released(binding:CameraRecorderBinding<Unit>) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!binding.reservationReleased()){check(System.nanoTime()<deadline);Thread.sleep(2)}
    }
    private class Source(val producer:SimulatedRecordingModule):CameraObservationSource {
        override val identity=testIdentity(producer)
        var current:CameraObservationConsumer?=null;var subscriptions=0;var unsubscriptions=0
        override fun subscribe(consumer:CameraObservationConsumer):AutoCloseable {
            subscriptions++;current=consumer
            return AutoCloseable {unsubscriptions++;if(current===consumer)current=null}
        }
        fun emit(ordinal:Long,receipt:Long) {current!!.observation(RecordingTestObservation(identity,producer.observation(ordinal),ordinal,receipt))}
        fun session()=object:CameraSession<Unit> {
            override val state=LatestFrameState(CameraSessionState<Unit>()).asStateFlow()
            override val observations=this@Source
            override suspend fun perform(action:CameraAction){error("No camera controls in this test")}
            override suspend fun close(reason:CameraCloseReason){}
        }
    }
    private class Adapter(val source:Source,val fail:Boolean=false):CameraRecordingAdapter {
        override val identity=source.identity
        override val stream=FrozenJson(source.producer.stream)
        override val maximumCharge=3L*1048576
        override val preparationProfile=RecordingProfile.ANALYSIS
        override fun loss(source:CameraSourceLoss)=RecordingSourceMetadata.loss(stream,source)
        override fun offer(source:CameraAcquiredObservation):RecordingOffer {
            check(!fail){"Synthetic adapter failure"}
            source as RecordingTestObservation
            val witness=RecordingSourceWitness(identity.receiptClockId,source.receivedMonotonicNs,source.sourceSequence,true,null)
            return RecordingOffer("primary",source.ordinal,stream,maximumCharge,witness,preparationProfile) {
                val original=source.original
                RecordingObservation("primary",source.ordinal,stream.value(),original.context.value(),
                    original.facts.value()+("receipt" to RecordingSourceMetadata.receipt(identity,source.receivedMonotonicNs)),
                    original.payloads.filter {it.role in listOf("temperature","validity")})
            }
        }
    }
    @Test fun rotationRetainsTheSamePolicyWhileOrdinaryBackgroundClosesItWithoutAutomaticReopen() {
        val m=SimulatedRecordingModule();val source=Source(m);val adapter=Adapter(source)
        val clock=AtomicLong(0);val e=PeriodicTestSource.engine(m,file(),clock)
        val binding=CameraRecorderBinding<Unit>{adapter};binding.opened(source.session());assertTrue(binding.start(e))
        var releases=0
        val ui=CameraUiLifetime({releases++;binding.invalidated(CameraCloseReason.BACKGROUND)})
        val first=ui.started("en");source.emit(0,0)
        ui.stopped(first,true,false);val next=ui.started("en")
        clock.set(200000000);source.emit(1,clock.get())
        assertEquals(0,releases);assertEquals(1,source.subscriptions)
        clock.set(250000000);ui.stopped(next,false,false)
        val result=checkNotNull(binding.pendingStop()!!.get(10,TimeUnit.SECONDS))
        assertTrue(result.finalized);assertEquals("interrupted",result.status)
        assertEquals(2L,e.snapshot().acknowledged);assertEquals(1,source.unsubscriptions)
        ui.started("en");assertNull(source.current);assertEquals(1,source.subscriptions)
    }
    @Test fun oldCapturedCallbackCannotEnterTheNextSameDeviceRecording() {
        val m=SimulatedRecordingModule();val source=Source(m);val adapter=Adapter(source);val clock=AtomicLong(0)
        val binding=CameraRecorderBinding<Unit>{adapter};binding.opened(source.session())
        val first=PeriodicTestSource.engine(m,file(),clock);binding.start(first);val old=source.current!!
        source.emit(0,0);binding.stop()!!.get(10,TimeUnit.SECONDS);released(binding)
        clock.set(250000000);binding.opened(source.session())
        val next=PeriodicTestSource.engine(m,file(),clock);assertTrue(binding.start(next))
        old.observation(RecordingTestObservation(source.identity,m.observation(99),99,clock.get()))
        source.emit(0,clock.get());binding.stop()!!.get(10,TimeUnit.SECONDS)
        assertEquals(1L,binding.staleCallbacks);assertEquals(1L,next.periodicSnapshot()!!.acquired)
        assertEquals(1L,next.snapshot().acknowledged)
    }
    @Test fun explicitPreparationProfileMismatchRefusesStartBeforeSubscribingOrFreezing() {
        val m=SimulatedRecordingModule();val source=Source(m);val binding=CameraRecorderBinding<Unit>{Adapter(source)}
        binding.opened(source.session())
        val clock=AtomicLong(0)
        val e=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.FULL,
            RecorderLimits(maxObservationBytes=3L*1048576),LocalRecorderBackend(file()),now=clock::get,sampling=FiveHertzSampling(0))
        assertFalse(binding.start(e));assertEquals(0,source.subscriptions)
        assertTrue(checkNotNull(e.awaitStop(10000)).finalized);assertEquals(0L,e.snapshot().acknowledged)
    }
    @Test fun oldTargetedDeadlineCannotUnsubscribeANewRecordingOnTheSameSource() {
        val m=SimulatedRecordingModule();val source=Source(m);val adapter=Adapter(source);val clock=AtomicLong(0)
        val binding=CameraRecorderBinding<Unit>{adapter};binding.opened(source.session())
        val first=PeriodicTestSource.engine(m,file(),clock);assertTrue(binding.start(first))
        source.emit(0,0);binding.stop()!!.get(10,TimeUnit.SECONDS);released(binding)
        clock.set(250000000);val next=PeriodicTestSource.engine(m,file(),clock);assertTrue(binding.start(next))
        source.emit(0,clock.get())
        val drainedBy=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(next.snapshot().let {it.preparationQueuedBytes+it.preparationInFlightBytes+it.writerQueuedBytes+it.writerInFlightBytes>0}) {
            check(System.nanoTime()<drainedBy);Thread.sleep(2)
        }
        first.wakeTimers();assertNull(binding.stopIfCurrent(first))
        assertNotNull(source.current);assertEquals(1,source.unsubscriptions)
        clock.set(450000000);source.emit(1,clock.get());binding.stop()!!.get(10,TimeUnit.SECONDS)
        assertEquals(2L,next.snapshot().acknowledged);assertEquals(2,source.unsubscriptions)
    }
    @Test fun adapterFailureCannotCloseAnUnrepresentedDeliveredCallbackAsOrdinaryIdle() {
        val m=SimulatedRecordingModule();val source=Source(m);val clock=AtomicLong(0)
        val e=PeriodicTestSource.engine(m,file(),clock);val binding=CameraRecorderBinding<Unit>{Adapter(source,true)}
        binding.opened(source.session());binding.start(e);source.emit(0,0)
        assertFalse(checkNotNull(e.awaitStop(10000)).finalized)
        assertEquals("recording_adapter_failure",e.periodicSnapshot()!!.failure)
        assertFalse(e.periodicSnapshot()!!.ledgerComplete);assertEquals(1,source.unsubscriptions)
    }
    @Test fun preparationChoiceChangesOnlyBeforeStartAndCannotReplaceAnActiveOrDrainingAdapter() {
        val m=SimulatedRecordingModule();val source=Source(m);val clock=AtomicLong(0)
        val a=Adapter(source);val b=Adapter(source);val binding=CameraRecorderBinding<Unit>{a}
        binding.opened(source.session());assertTrue(binding.prepareAdapter(b));assertEquals(0,source.subscriptions)
        val e=PeriodicTestSource.engine(m,file(),clock);assertTrue(binding.start(e))
        assertFalse(binding.prepareAdapter(a));source.emit(0,0)
        binding.stop()!!.get(10,TimeUnit.SECONDS);released(binding)
        assertTrue(binding.prepareAdapter(a));assertEquals(1,source.subscriptions)
        val other=Source(SimulatedRecordingModule(session="00000000-0000-4000-8000-000000000098"))
        assertFalse(binding.prepareAdapter(Adapter(other)))
    }
    @Test fun failedWriterResultDoesNotReleaseAnAdapterWhilePreparationStillOwnsItsSource() {
        val m=SimulatedRecordingModule();val source=Source(m);val clock=AtomicLong(0);val f=file()
        val headerEntered=CountDownLatch(1);val failHeader=CountDownLatch(1)
        val preparationEntered=CountDownLatch(1);val releasePreparation=CountDownLatch(1)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                headerEntered.countDown();check(failHeader.await(10,TimeUnit.SECONDS))
                throw IOException("synthetic_header_failure")
            }
            override fun close(){}
        }
        val e=PeriodicTestSource.engine(m,f,clock,hook={
            preparationEntered.countDown();check(releasePreparation.await(10,TimeUnit.SECONDS))
        },backend=backend)
        val a=Adapter(source);val binding=CameraRecorderBinding<Unit>{a};binding.opened(source.session())
        try {
            assertTrue(headerEntered.await(5,TimeUnit.SECONDS));assertTrue(binding.start(e));source.emit(0,0)
            assertTrue(preparationEntered.await(5,TimeUnit.SECONDS));failHeader.countDown()
            assertFalse(checkNotNull(e.awaitStop(5000)).finalized)
            binding.stop()!!.get(5,TimeUnit.SECONDS)
            assertFalse(e.workersTerminated());assertFalse(binding.reservationReleased())
            assertFalse(binding.prepareAdapter(Adapter(source)))
        } finally {failHeader.countDown();releasePreparation.countDown();e.stop();e.awaitStop(10000)}
        released(binding);assertTrue(binding.prepareAdapter(Adapter(source)))
    }
}
