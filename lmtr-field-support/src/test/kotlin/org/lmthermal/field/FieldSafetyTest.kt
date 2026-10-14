package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.recorder.RecordingProfile

/** Safety failures are deterministic host inputs, never injected USB/battery/thermal hardware faults. */
class FieldSafetyTest {
    private val plan=FieldPreflight(RecordingProfile.FULL,20,null)
    private val healthy=FieldHealth(78,0,plan.requiredFreeBytes,512L*1048576,1024L*1048576,false,100L*1048576,true,true)
    @Test fun admissionRequiresAllKnownAuthorizationResourceFacts() {
        assertNull(FieldSafety.refusal(healthy,plan,true))
        for(h in listOf(healthy.copy(batteryPercent=null),healthy.copy(thermalStatus=null),healthy.copy(freeBytes=null),
            healthy.copy(rssBytes=null),healthy.copy(cameraPermission=false),healthy.copy(foregroundUnlocked=false)))
            assertNotNull(FieldSafety.refusal(h,plan,true))
    }
    @Test fun batteryAndThermalBoundaryStopRatherThanRetry() {
        assertNull(FieldSafety.refusal(healthy.copy(batteryPercent=30,thermalStatus=2),plan,true))
        assertEquals("battery_below_30",FieldSafety.refusal(healthy.copy(batteryPercent=29),plan,true))
        assertEquals("thermal_severe",FieldSafety.refusal(healthy.copy(thermalStatus=3),plan,true))
    }
    @Test fun startAndRuntimeStorageThresholdsAreDifferent() {
        val low=healthy.copy(freeBytes=plan.requiredFreeBytes-1)
        assertEquals("storage_reserve_low",FieldSafety.refusal(low,plan,true))
        assertNull(FieldSafety.refusal(low,plan,false))
        assertEquals("storage_reserve_low",FieldSafety.refusal(healthy.copy(freeBytes=plan.headroomBytes-1),plan,false))
    }
    @Test fun logicalReservationDoesNotOverrideActualHeapAndSystemHeadroom() {
        val needed=plan.limits.reservedBytes+FieldSafety.HEAP_MARGIN_BYTES
        assertEquals("java_headroom_low",FieldSafety.refusal(healthy.copy(javaHeadroomBytes=needed-1),plan,true))
        assertEquals("system_headroom_low",FieldSafety.refusal(healthy.copy(systemAvailableBytes=needed-1),plan,true))
        assertEquals("android_low_memory",FieldSafety.refusal(healthy.copy(systemLowMemory=true),plan,false))
    }
    @Test fun idleSourceAndNoValidMeasurementsHaveIndependentCutoffs() {
        assertEquals("source_stalled",FieldSafety(plan,healthy.rssBytes!!,0,0).check(healthy,3000000000,0,3000000000))
        assertEquals("valid_measurement_stalled",FieldSafety(plan,healthy.rssBytes!!,0,0).check(healthy,3000000000,75,null))
        assertNull(FieldSafety(plan,healthy.rssBytes!!,0,0).check(healthy,2000000000,50,0))
    }
    @Test fun cadenceUsesFiveConsecutiveSlowSamplesNotOneJitterSpike() {
        val guard=FieldSafety(plan,healthy.rssBytes!!,0,0)
        for(i in 1L..8)assertNull(guard.check(healthy,i*1000000000,i*10,i*1000000000))
        assertEquals("sustained_acquisition_below_half_nominal",guard.check(healthy,9000000000,90,9000000000))
        val reset=FieldSafety(plan,healthy.rssBytes!!,0,0)
        for(i in 1L..8)assertNull(reset.check(healthy,i*1000000000,i*10,i*1000000000))
        assertNull(reset.check(healthy,9000000000,105,9000000000))
    }
    @Test fun memoryGrowthQueueOverflowAndClockRegressionsRetainFailureReasons() {
        fun guard()=FieldSafety(plan,healthy.rssBytes!!,0,0)
        assertEquals("rss_increment_exceeded",guard().check(healthy.copy(rssBytes=healthy.rssBytes!!+plan.limits.memoryBudgetBytes+1),1000000000,25,1000000000))
        assertEquals("queue_bound_exceeded",guard().check(healthy,1000000000,25,1000000000,false))
        assertEquals("source_clock_future",guard().check(healthy,1000000000,25,1000000001))
        val g=guard();assertNull(g.check(healthy,1000000000,25,1000000000))
        assertEquals("source_or_sample_regressed",g.check(healthy,999999999,24,999999999))
    }
}
