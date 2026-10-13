package org.lmthermal.field

import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.camera.*
import org.lmthermal.core.NativeImageGeometry
import org.lmthermal.recorder.*
import org.lmthermal.recording.*
import org.lmthermal.lmtr.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

/** Finite test route uses the same tee/binding/clock/header as the APK; no USB or controls. */
class FieldRouteTest {
    private val module=CameraModuleMetadata(CameraModuleId("org.example.field"),"synthetic",CameraCapabilities(true,true,true,true),"simulated")
    private class Source(override val identity:CameraSourceIdentity):CameraObservationSource {
        var consumer:CameraObservationConsumer?=null
        override fun subscribe(consumer:CameraObservationConsumer):AutoCloseable { check(this.consumer==null);this.consumer=consumer;return AutoCloseable {this.consumer=null} }
    }
    private fun session(source:Source)=object:CameraSession<Int> {
        override val state=MutableStateFlow(CameraSessionState<Int>(lifecycle=CameraLifecycle.STREAMING))
        override val observations=source
        override suspend fun perform(action:CameraAction){error("route must never send camera controls")}
        override suspend fun close(reason:CameraCloseReason){}
    }
    private fun observation(identity:CameraSourceIdentity,n:Long,ready:Boolean)=object:CameraAcquiredObservation {
        override val identity=identity;override val ordinal=n;override val sourceSequence=n
        override val receivedMonotonicNs=n;override val measurement:ThermalMeasurement?=null
        override val unavailableReason=if(ready)null else "session_not_ready"
        override val retainedBytes=1024L
    }
    private fun adapter(identity:CameraSourceIdentity):CameraRecordingAdapter {
        val m=SimulatedRecordingModule()
        return object:CameraRecordingAdapter {
            override val identity=identity;override val stream=FrozenJson(m.stream);override val maximumCharge=1048576L
            override fun offer(source:CameraAcquiredObservation)=RecordingOffer("primary",source.ordinal,stream,1048576) {
                if(source.unavailableReason==null)m.observation(source.ordinal) else SimulatedRecordingModule(thermal=false,native=false,acquisition=false).observation(source.ordinal)
            }
            override fun loss(source:CameraSourceLoss)=RecordingSourceMetadata.loss(stream,source)
        }
    }
    @Test fun connectAndPreviewWithoutExplicitStartNeverCreateRecording() {
        var seen=0;var offered=0
        val route=FieldSourceRoute<Int>({seen++},{},{ id ->
            val original=adapter(id)
            object:CameraRecordingAdapter by original { override fun offer(source:CameraAcquiredObservation):RecordingOffer{offered++;return original.offer(source)} }
        })
        val source=Source(CameraSourceIdentity(module));route.opened(session(source))
        repeat(20){source.consumer!!.observation(observation(source.identity,it.toLong(),false))}
        assertEquals(20,seen);assertEquals(0,offered);assertNull(route.stop())
    }
    @Test fun oldCapturedCallbackAfterCloseReopenCannotEnterNewRun() {
        var seen=0;val route=FieldSourceRoute<Int>({seen++},{},::adapter)
        val first=Source(CameraSourceIdentity(module));route.opened(session(first));val old=first.consumer!!
        route.invalidated(CameraCloseReason.USER)
        val next=Source(CameraSourceIdentity(module));route.opened(session(next))
        old.observation(observation(first.identity,0,false));assertEquals(0,seen)
        next.consumer!!.observation(observation(next.identity,0,false));assertEquals(1,seen)
    }
    @Test fun everyLifecycleCutoffUnsubscribesWhileRotationWithoutCutoffRetainsSource() {
        for(reason in CameraCloseReason.entries) {
            val route=FieldSourceRoute<Int>({},{},::adapter);val source=Source(CameraSourceIdentity(module));route.opened(session(source))
            assertNotNull(source.consumer) // rotation does not invalidate the retained ViewModel/owner
            route.invalidated(reason);assertNull(source.consumer)
        }
    }
    @Test fun startBeforeReadyCapturesUnavailableThenReadyWithoutSendingControls() {
        val root=Files.createTempDirectory("field-route-test").toFile();val m=SimulatedRecordingModule()
        try {
            // Keep one immutable stream definition: unavailable facts must not change capabilities.
            val route=FieldSourceRoute<Int>({},{},{ id -> object:CameraRecordingAdapter {
                override val identity=id;override val stream=FrozenJson(m.stream);override val maximumCharge=1048576L
                override fun loss(source:CameraSourceLoss)=RecordingSourceMetadata.loss(stream,source)
                override fun offer(source:CameraAcquiredObservation)=RecordingOffer("primary",source.ordinal,stream,1048576) {
                    val original=m.observation(source.ordinal)
                    if(source.unavailableReason==null)original else RecordingObservation("primary",source.ordinal,m.stream,original.context.value(),
                        original.facts.value()+mapOf("measurement" to mapOf("status" to "unavailable","reason" to "session_not_ready"),
                            "availability" to mapOf("temperature" to mapOf("status" to "unavailable","reason" to "session_not_ready"),
                                "native_samples" to mapOf("status" to "unavailable","reason" to "session_not_ready"),
                                "acquisition" to mapOf("status" to "unavailable","reason" to "session_not_ready"))),emptyList())
                }
            } })
            val source=Source(CameraSourceIdentity(module));route.opened(session(source))
            val clock=FieldClock(System::nanoTime)
            val e=RecorderEngine(clock.header(),listOf(m.stream),RecordingProfile.ANALYSIS,RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(java.io.File(root,"test.lmtr")),now=clock::now)
            assertTrue(route.start(e))
            source.consumer!!.observation(observation(source.identity,0,false))
            source.consumer!!.observation(observation(source.identity,1,true))
            route.stop();assertTrue(e.awaitStop(10000)!!.finalized)
            assertEquals(2L,e.snapshot().acknowledged)
            // Continuing acquisition after Stop is baseline-only, not a stale admitted frame.
            source.consumer!!.observation(observation(source.identity,2,true));assertEquals(2L,e.snapshot().accepted)
            LmtrReader(java.io.File(root,"test.lmtr")).use { assertEquals("completed",it.check(full=true)["declared_end_status"]) }
        } finally {root.deleteRecursively()}
    }
}
