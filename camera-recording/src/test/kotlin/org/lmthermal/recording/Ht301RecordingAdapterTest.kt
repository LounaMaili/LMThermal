package org.lmthermal.recording

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Existing sanitized source/gate evidence only; recording never computes a lookup or matrix. */
class Ht301RecordingAdapterTest {
    private fun frame()=Ht301Frame.parse(javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes())
    private fun source(ready:Boolean=true):Ht301AcquiredObservation {
        val f=frame();val state=if(ready)SessionState.RADIOMETRIC_READY else SessionState.RAW14_UNSETTLED
        val m=MeasurementGate.evaluate(state,f,99,42)
        return Ht301AcquiredObservation(CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated")),0,99,42000001,f,f.inspect(),state,m)
    }
    @Test fun freezingUsesExactAlreadyEvaluatedFloatBitsNativeAndTransport() {
        val source=source();val adapter=Ht301RecordingAdapter(source.identity);val offer=adapter.offer(source)
        val prepared=offer.materialize();assertEquals(0L,prepared.sequence)
        val temp=prepared.payloads.first { it.role=="temperature" }.bytes();val input=source.result.measurement!!.matrix()
        val b=ByteBuffer.wrap(temp).order(ByteOrder.LITTLE_ENDIAN);for(v in input)assertEquals(v.toRawBits(),b.int)
        assertArrayEquals(source.frame!!.imageBytes(),prepared.payloads.first { it.role=="native_samples" }.bytes())
        assertArrayEquals(source.frame!!.transportBytes(),prepared.payloads.first { it.role=="acquisition" }.bytes())
        assertEquals("42000001",prepared.facts.value()["receipt"].obj()["stamp"].obj()["ticks"])
        assertNull(prepared.facts.value()["device_sequence"])
        assertEquals("99",prepared.facts.value()["source_evidence"].obj()["host_uvc_callback_sequence"])
        assertEquals(0,prepared.payloads.count { it.role=="validity" }) // all-valid absence is exact, not a synthetic mask.
    }
    @Test fun unavailableNeverCarriesLastGoodCelsiusButRetainsCurrentRawAndSettings() {
        val source=source(false);val prepared=Ht301RecordingAdapter(source.identity).offer(source).materialize()
        assertFalse(prepared.payloads.any { it.role=="temperature" });assertEquals("unavailable",prepared.facts.value()["measurement"].obj()["status"])
        assertEquals("known",prepared.context.value()["settings"].obj()["status"])
        assertTrue(prepared.payloads.any { it.role=="native_samples" })
    }
    @Test fun malformedTransportIsOwnedWithoutManufacturingRawOrTemperatures() {
        val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata);val input=byteArrayOf(1,2,3)
        val source=Ht301AcquiredObservation(identity,0,3,77,null,FrameInspection(FrameMode.INVALID,"transport_size",null,null),
            SessionState.RAW14_UNSETTLED,MeasurementResult(null,"transport_size"),input)
        input[0]=99;val result=Ht301RecordingAdapter(identity).offer(source).materialize()
        assertArrayEquals(byteArrayOf(1,2,3),result.payloads.single().bytes())
        assertEquals("malformed_transport",result.facts.value()["measurement"].obj()["reason"])
    }
    @Test fun callbackOfferDoesNotFreezeLargeEvidenceOrLeakMutableSourceBytes() {
        val source=source();val adapter=Ht301RecordingAdapter(source.identity)
        repeat(200){assertEquals(adapter.maximumCharge,adapter.offer(source).chargedBytes)}
        val transport=source.transportBytes()!!;transport[0]=0
        assertEquals(source.frame!!.word(0),source.transportBytes()!!.let { (it[0].toInt() and 255) or ((it[1].toInt() and 255) shl 8) })
        assertThrows(IllegalArgumentException::class.java){adapter.offer(source(false))}
    }
}
