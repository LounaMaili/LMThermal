package org.lmthermal.recording

import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File

/** Reuse R4's deterministic byte producer; UI cadence never feeds this source callback. */
class RecordingTestObservation(override val identity:CameraSourceIdentity,val original:RecordingObservation,
    override val ordinal:Long,override val receivedMonotonicNs:Long):CameraAcquiredObservation {
    override val sourceSequence:Long=ordinal*2
    override val measurement:ThermalMeasurement?=null // Opaque module-owned recording evidence in this test adapter.
    override val unavailableReason:String?=null
    override val retainedBytes=original.ownedBytes
}
internal class RecordingTestAdapter(override val identity:CameraSourceIdentity,val producer:SimulatedRecordingModule):CameraRecordingAdapter {
    override val stream=FrozenJson(producer.stream+("clocks" to listOf(RecordingSourceMetadata.clock(identity))))
    override val maximumCharge=65536L
    var materializations=0
    override fun offer(source:CameraAcquiredObservation):RecordingOffer {
        require(source is RecordingTestObservation && source.identity==identity)
        return RecordingOffer(producer.id,source.ordinal,stream,maximumCharge){
            materializations++
            RecordingObservation(producer.id,source.ordinal,stream.value(),source.original.context.value(),
                source.original.facts.value()+mapOf("receipt" to RecordingSourceMetadata.receipt(identity,source.receivedMonotonicNs),
                    "source_evidence" to mapOf("module_source_sequence" to source.sourceSequence.toString())),source.original.payloads)
        }
    }
    override fun loss(source:CameraSourceLoss)=RecordingSourceMetadata.loss(stream,source)
}
class RecordingTestSession(val hub:CameraObservationHub,device:CameraDeviceIdentity):CameraSession<Unit> {
    private val latest=LatestFrameState(CameraSessionState<Unit>(module=hub.identity.module,device=device,
        lifecycle=CameraLifecycle.STREAMING,geometry=NativeImageGeometry(3,2),status=CameraStatus(CameraStatusCode.MEASUREMENT_READY)))
    override val state=latest.asStateFlow()
    override val observations:CameraObservationSource=hub
    var closes=0
    override suspend fun perform(action:CameraAction){error("No test camera controls")}
    override suspend fun close(reason:CameraCloseReason){closes++;hub.finish(reason)}
    fun presentation(sequence:Long){latest.value=latest.value.copy(statistics=CameraStatistics(received=sequence))}
}
/** New synthetic sources use explicit matching session/clock facts, not the physical device key. */
internal fun testIdentity(producer:SimulatedRecordingModule)=CameraSourceIdentity(CameraModuleMetadata(CameraModuleId("org.example.recording.synthetic"),
    "Non-USB synthetic module",CameraCapabilities(preview=true,temperatureMeasurement=producer.thermal,explicitMeasurementInitialization=false,touchInspection=producer.thermal),"simulated"),
    producer.stream["session_id"].text(),producer.stream["clocks"].arr().first().obj()["id"].text())
internal fun testEngine(producer:SimulatedRecordingModule,file:File,profile:RecordingProfile=RecordingProfile.FULL,
    hook:()->Unit={},backend:RecorderBackend=LocalRecorderBackend(file))=RecorderEngine(SimulatedRecordingModule.header(listOf(producer),1),
    listOf(producer.stream),profile,RecorderLimits(maxObservationBytes=65536),backend,1,prepareHook=hook)
