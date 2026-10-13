package org.lmthermal.recording

import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.camera.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import kotlinx.coroutines.*
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.IOException

/** No camera or UI. Real owner transitions and captured stale callbacks exercise the optional route. */
class CameraRecordingLifecycleTest {
    private fun file()=Files.createTempDirectory("source-r4-").resolve("recording.lmtr").toFile()
    private fun done(binding:CameraRecorderBinding<Unit>):RecorderResult {
        val result=binding.pendingStop()!!.get(10,TimeUnit.SECONDS);assertTrue(result.toString(),result.finalized);return result
    }
    private fun setup():Triple<RecordingTestSession,RecordingTestAdapter,CameraRecorderBinding<Unit>> {
        val p=SimulatedRecordingModule();val identity=testIdentity(p);val session=RecordingTestSession(CameraObservationHub(identity),object:CameraDeviceIdentity{override val key="same_device"})
        val adapter=RecordingTestAdapter(identity,p);val binding=CameraRecorderBinding<Unit>{adapter};binding.opened(session)
        return Triple(session,adapter,binding)
    }
    @Test fun connectingAndConflatedPresentationNeverStartOrFeedRecording() {
        val (session,adapter,binding)=setup()
        repeat(100){session.hub.publish{error("No subscription may freeze data")};session.presentation(it.toLong())}
        assertEquals(0,adapter.materializations);assertNull(binding.pendingStop())
        val e=testEngine(adapter.producer,file());assertTrue(binding.start(e))
        repeat(20){session.hub.publish{n->RecordingTestObservation(session.hub.identity,adapter.producer.observation(n),n,System.nanoTime())}}
        repeat(200){session.presentation(it.toLong())};binding.stop();done(binding)
        assertEquals(20L,e.snapshot().acknowledged);assertEquals(20,adapter.materializations)
    }
    @Test fun everyLifetimeExitCutsOffAndOnlyRotationRetainsSource() {
        for(reason in CameraCloseReason.entries) {
            val (session,a,b)=setup();val e=testEngine(a.producer,file());b.start(e)
            session.hub.publish{n->RecordingTestObservation(a.identity,a.producer.observation(n),n,System.nanoTime())}
            b.invalidated(reason);session.hub.publish{error("No old subscription")};val r=done(b)
            assertEquals(1L,e.snapshot().acknowledged)
            assertEquals(if(reason==CameraCloseReason.FAILED)"failed" else if(reason==CameraCloseReason.USER)"completed" else "interrupted",r.status)
        }
        val (s,a,b)=setup();val e=testEngine(a.producer,file());b.start(e)
        val ui=CameraUiLifetime({b.invalidated(CameraCloseReason.BACKGROUND)})
        val first=ui.started("en");ui.stopped(first,true,false);ui.started("en")
        s.hub.publish{n->RecordingTestObservation(a.identity,a.producer.observation(n),n,System.nanoTime())}
        assertNull(b.pendingStop());ui.screenOff();done(b);assertEquals(1L,e.snapshot().acknowledged)
    }
    @Test fun latestCapturedOldCallbackCannotEnterSameDeviceNewRecording() {
        val p=SimulatedRecordingModule();val identity=testIdentity(p);var captured:CameraObservationConsumer?=null
        val source=object:CameraObservationSource {
            override val identity=identity
            override fun subscribe(consumer:CameraObservationConsumer):AutoCloseable { captured=consumer;return AutoCloseable{} }
        }
        val a=RecordingTestAdapter(identity,p);val b=CameraRecorderBinding<Unit>{a}
        val session=object:CameraSession<Unit> {
            override val state=org.lmthermal.core.LatestFrameState(CameraSessionState<Unit>()).asStateFlow()
            override val observations=source
            override suspend fun perform(action:CameraAction){}
            override suspend fun close(reason:CameraCloseReason){}
        }
        b.opened(session);val first=testEngine(p,file());b.start(first);val old=captured!!
        old.observation(RecordingTestObservation(identity,p.observation(0),0,System.nanoTime()))
        b.invalidated(CameraCloseReason.USER);done(b);b.opened(session)
        val next=testEngine(p,file());assertTrue(b.start(next));val current=captured!!
        old.observation(RecordingTestObservation(identity,p.observation(1),1,System.nanoTime()))
        current.observation(RecordingTestObservation(identity,p.observation(0),0,System.nanoTime()))
        b.stop();done(b);assertEquals(1L,next.snapshot().acknowledged);assertEquals(1L,b.staleCallbacks)
    }
    @Test fun stopDuringPreparationIsNonBlockingAndRepeatedStopHasOneCutoff() {
        val (s,a,b)=setup();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val e=testEngine(a.producer,file(),hook={entered.countDown();release.await(5,TimeUnit.SECONDS)})
        b.start(e);s.hub.publish{n->RecordingTestObservation(a.identity,a.producer.observation(n),n,System.nanoTime())}
        assertTrue(entered.await(5,TimeUnit.SECONDS));val start=System.nanoTime();val one=b.stop();assertSame(one,b.stop())
        assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));assertFalse(one!!.isDone)
        release.countDown();done(b);assertEquals(1L,e.snapshot().acknowledged)
    }
    @Test fun delayedFooterRetainsReservationAndBlocksNewRecordingUntilDrain() {
        val (s,a,b)=setup();val f=file();val base=LocalRecorderBackend(f);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val backend=object:RecorderBackend { override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK){entered.countDown();release.await(5,TimeUnit.SECONDS)}
                return base.append(type,ordinal,previous,body)
            };override fun close()=base.close() }
        val e=testEngine(a.producer,f,backend=backend);b.start(e)
        s.hub.publish{n->RecordingTestObservation(a.identity,a.producer.observation(n),n,System.nanoTime())};b.stop()
        assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(0L,e.snapshot().acknowledged)
        val notStarted=testEngine(a.producer,file());assertFalse(b.start(notStarted));notStarted.stop()
        release.countDown();done(b);assertEquals(1L,e.snapshot().acknowledged)
    }
    @Test fun sourceLossIsNotPreparationWriterOrPresentationLoss() {
        val (s,a,b)=setup();val f=file();val e=testEngine(a.producer,f);b.start(e)
        s.hub.loss("uvc_callback_replaced",3,1,2)
        s.hub.publish{n->RecordingTestObservation(a.identity,a.producer.observation(n),n,System.nanoTime())};b.stop();val r=done(b)
        assertEquals("3",r.report!!["summary"].obj()["known_missing_observations"])
        assertEquals(0L,e.snapshot().preparationDrops);assertEquals(0L,e.snapshot().writerDrops)
    }
    @Test fun observerFailureDoesNotReleaseTheCameraOrSilentlyKeepRecording() {
        val (s,_,_)=setup();var ended=false
        s.hub.subscribe(object:CameraObservationConsumer {
            override fun observation(value:CameraAcquiredObservation){error("Broken observer")}
            override fun loss(value:CameraSourceLoss){}
            override fun ended(reason:CameraCloseReason){ended=reason==CameraCloseReason.FAILED}
        })
        val p=SimulatedRecordingModule();s.hub.publish{n->RecordingTestObservation(s.hub.identity,p.observation(n),n,System.nanoTime())}
        s.hub.publish{error("Broken observer was removed")};assertTrue(ended);assertEquals(0,s.closes)
    }
    @Test fun actualSessionOwnerStopsRecordingBeforeAwaitedCloseAndReplacement()=runBlocking {
        val (s,a,b)=setup();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val device=object:CameraDeviceIdentity{override val key="same_device"}
        val module=object:CameraModule<Unit> {
            override val metadata=s.hub.identity.module
            override fun probe(device:CameraDeviceIdentity)=CameraProbeResult.SUPPORTED
            override suspend fun open(device:CameraDeviceIdentity)=RecordingTestSession(CameraObservationHub(a.identity),device)
        }
        val owner=CameraSessionOwner(scope,lifetime=b)
        owner.open(CameraCandidate(device,module)).join()
        val e=testEngine(a.producer,file());assertTrue(b.start(e))
        owner.close().join();done(b);assertEquals("finalized",e.snapshot().state)
        owner.open(CameraCandidate(device,module)).join();assertTrue(owner.state.value.lifecycle!=CameraLifecycle.ERROR)
        owner.background().join();scope.cancel()
    }
}
