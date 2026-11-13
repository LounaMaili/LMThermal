package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.recorder.*
import org.lmthermal.recording.*
import org.lmthermal.lmtr.*

/** Independent source owners are still frozen on the charged worker, never borrowed by the prototype. */
internal object OfflineProfileSource {
    val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),
        "22222222-2222-4222-8222-222222222222","11111111-1111-4111-8111-111111111111")
    val fixture=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use { it.readBytes() }
    fun observation(index:Long,state:SessionState=SessionState.RADIOMETRIC_READY):Ht301AcquiredObservation {
        val frame=Ht301Frame.parse(fixture)
        return Ht301AcquiredObservation(identity,index,index,index*1000000,frame,frame.inspect(),state,
            MeasurementGate.evaluate(state,frame,index,index))
    }
}

/** Profile omission must change preparation work only, not selected evidence or unavailable facts. */
class OfflineProfileStudyTest {
    @Test fun selectedBytesContextAndFactsMatchEagerFreezeForEveryProfileAndReadiness() {
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL))
            for(state in listOf(SessionState.RADIOMETRIC_READY,SessionState.RAW14_UNSETTLED)) {
                val source=OfflineProfileSource.observation(0,state)
                val eager=Ht301RecordingAdapter(source.identity).offer(source).materialize()
                val lazy=OfflineProfileAdapter(source.identity,profile).offer(source).materialize()
                val first=RecordingProfiles.select(eager,profile);val second=RecordingProfiles.select(lazy,profile)
                assertArrayEquals(LmtrJson.encode(first.stream.value()),LmtrJson.encode(second.stream.value()))
                assertArrayEquals(LmtrJson.encode(first.context.value()),LmtrJson.encode(second.context.value()))
                assertArrayEquals(LmtrJson.encode(first.facts.value()),LmtrJson.encode(second.facts.value()))
                assertEquals(first.ownedBytes,second.ownedBytes)
                assertEquals(first.payloads.map {it.role},second.payloads.map {it.role})
                for((a,b) in first.payloads.zip(second.payloads)) {
                    assertArrayEquals(a.bytes(),b.bytes());assertArrayEquals(LmtrJson.encode(a.descriptor.value()),LmtrJson.encode(b.descriptor.value()))
                    assertEquals(a.acquisitionViewOffset,b.acquisitionViewOffset)
                }
                assertEquals(3L*1024*1024,OfflineProfileAdapter(source.identity,profile).maximumCharge)
            }
    }
    @Test fun omittedOwnersAreNotMaterializedAndFullStillRetainsEveryOriginalRole() {
        val source=OfflineProfileSource.observation(1)
        for((profile,roles) in listOf(RecordingProfile.ANALYSIS to listOf("temperature"),
            RecordingProfile.NATIVE to listOf("temperature","native_samples"),
            RecordingProfile.FULL to listOf("temperature","native_samples","acquisition"))) {
            val frozen=OfflineProfileAdapter(source.identity,profile).offer(source).materialize()
            assertEquals(roles,frozen.payloads.map {it.role})
            assertTrue(frozen.ownedBytes<=3L*1024*1024)
        }
    }
}
