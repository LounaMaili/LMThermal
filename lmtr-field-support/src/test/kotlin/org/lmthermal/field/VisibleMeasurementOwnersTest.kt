package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.core.*
import org.lmthermal.lmtr.*

/** Role identity is not sequence equality and a sampled census is not a live-heap bound. */
class VisibleMeasurementOwnersTest {
    private fun source(sequence:Long)=OwnedThermalMeasurement(NativeImageGeometry(1,1),floatArrayOf(20f),sequence,sequence,
        MeasurementProvenance(CameraModuleId("test"),"synthetic",TemperatureProvenanceKind.SIMULATED))

    @Test fun sameObjectIsSharedButEqualSequenceDoesNotMergeDifferentOwners() {
        val first=source(1);val other=source(1)
        val report=VisibleMeasurementOwners.report(mapOf("camera" to first,"render" to first,"roi" to other))
        assertEquals(2,report["distinct_visible_owners"])
        val roles=report["roles"].obj()
        assertEquals(roles["camera"].obj()["owner"],roles["render"].obj()["owner"])
        assertNotEquals(roles["camera"].obj()["owner"],roles["roi"].obj()["owner"])
        LmtrJson.encode(report)
    }

    @Test fun explicitHtWrappersShareEvidenceWithoutReadingOrChangingSourcePlanes() {
        val raw=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
        val frame=Ht301Frame.parse(raw)
        val evidence=checkNotNull(MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY,frame,1,1).measurement)
        val before=evidence.matrix().map {it.toRawBits()}
        val first=Ht301ThermalMeasurement(evidence);val second=Ht301ThermalMeasurement(evidence)
        val report=VisibleMeasurementOwners.report(mapOf("camera" to first,"render" to second,"roi" to null))
        assertEquals(1,report["distinct_visible_owners"])
        assertNull(report["roles"].obj()["roi"])
        assertEquals(before,evidence.matrix().map {it.toRawBits()})
        assertArrayEquals(raw,frame.transportBytes())
    }

    @Test fun nextSampleStartsANewCensusWithoutRetainingEarlierRoles() {
        val first=source(1)
        assertEquals(1,VisibleMeasurementOwners.report(mapOf("camera" to first))["distinct_visible_owners"])
        val empty=VisibleMeasurementOwners.report(mapOf("camera" to null))
        assertEquals(0,empty["distinct_visible_owners"])
        assertEquals(false,empty["strong_history_retained"])
        assertThrows(IllegalArgumentException::class.java){VisibleMeasurementOwners.report((0..8).associate {"r$it" to first})}
    }
}
