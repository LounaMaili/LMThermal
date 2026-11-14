package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.core.*

/** Role scalars do not become a retained-root census or require copying a plane. */
class PublishedMemoryRolesTest {
    private fun source():ThermalMeasurement {
        val owned=OwnedThermalMeasurement(NativeImageGeometry(1,1),floatArrayOf(20f),1,1,
            MeasurementProvenance(CameraModuleId("test"),"synthetic",TemperatureProvenanceKind.SIMULATED))
        return object:ThermalMeasurement by owned {override fun matrix():FloatArray=error("No diagnostic plane access")}
    }
    @Test fun aliasedRolesAreReportedPairwiseWithoutSummingNominalPlaneBytes() {
        val m=source();val v=LongArray(16){-1};PublishedMemoryRoles.fill(CameraSessionState<Any>(measurement=m),m,m,100,10,v)
        assertEquals(1L,v[5]);assertEquals(1L,v[6]);assertEquals(1L,v[7]);assertEquals(4L,v[10])
        assertEquals(MemoryProbeStatus.OK,PublishedMemoryRoles.qualify(v,5,5));assertEquals(5L,v[0])
    }
    @Test fun identicalSequenceDoesNotAssertAliasingOfDifferentGenericObjects() {
        val m=source();val v=LongArray(16){-1};PublishedMemoryRoles.fill(CameraSessionState<Any>(measurement=m),source(),null,null,null,v)
        assertEquals(0L,v[5]);assertEquals(-1L,v[6]);assertEquals(-1L,v[8])
    }
    @Test fun knownHtWrappersReferenceTheSameEvidenceWithoutReadingItsArrays() {
        val bytes=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use {it.readBytes()}
        val evidence=NativeEquivalentThermometry.measure(Ht301Frame.parse(bytes))
        val a=Ht301ThermalMeasurement(evidence);val b=Ht301ThermalMeasurement(evidence);val v=LongArray(16){-1}
        PublishedMemoryRoles.fill(CameraSessionState<Any>(measurement=a),b,b,null,null,v)
        assertEquals(1L,v[5]);assertEquals(1L,v[6]);assertEquals(1L,v[7])
    }
    @Test fun replacementAndUnknownGenerationClearAllAmbiguousRoleClaims() {
        for(after in listOf(8L,null)) {
            val v=LongArray(16){99};assertEquals(MemoryProbeStatus.SOURCE_CHANGED,PublishedMemoryRoles.qualify(v,7,after))
            assertEquals(7L,v[0]);assertEquals(after ?: -1,v[1]);assertTrue(v.drop(2).all {it== -1L})
        }
    }
    @Test fun nextEmptySnapshotDoesNotRecallAnOldOwnerOrBitmap() {
        val a=LongArray(16){-1};PublishedMemoryRoles.fill(CameraSessionState<Any>(measurement=source()),source(),null,20,4,a)
        val b=LongArray(16){-1};PublishedMemoryRoles.fill(CameraSessionState<Any>(),null,null,null,null,b)
        assertEquals(-1L,b[2]);assertEquals(-1L,b[8]);assertEquals(-1L,b[10])
    }
}
