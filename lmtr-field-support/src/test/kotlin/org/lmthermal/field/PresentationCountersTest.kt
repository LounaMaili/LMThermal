package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.core.*

/** Deterministic capacity/queue accounting, not a GC or full reachable-heap certificate. */
class PresentationCountersTest {
    @Test fun finiteDiagnosticRatesAreExplicitDecimalsWithoutRelaxingWireJson() {
        val encoded=org.lmthermal.lmtr.LmtrJson.encode(mapOf("fps" to fieldMetricNumber(25.1)))
        assertEquals("{\"fps\":25.1}",encoded.toString(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java){org.lmthermal.lmtr.LmtrJson.encode(mapOf("fps" to 25.1))}
        assertThrows(IllegalArgumentException::class.java){fieldMetricNumber(Double.NaN)}
    }
    private fun source(sequence:Long)=OwnedThermalMeasurement(NativeImageGeometry(1,1),floatArrayOf(20f),sequence,sequence,
        MeasurementProvenance(CameraModuleId("test"),"synthetic",TemperatureProvenanceKind.SIMULATED))
    @Test fun weakHistoryCapacityAndPendingReplacementStayBounded() {
        val counters=PresentationCounters()
        repeat(300){counters.event("request",source(it.toLong()),0)}
        val row=counters.report();assertEquals(128,row["observed_weak_owners"])
        assertEquals("299",row["pending_sequence"])
        assertEquals(299L,(row["events"] as Map<*,*>)["pending_replaced"])
    }
    @Test fun startedCompletedFailedAndRoiEventsReflectWorkerLifetimes() {
        val counters=PresentationCounters();val m=source(1)
        counters.event("request",m,0);counters.event("started",m,0)
        assertNull(counters.report()["pending_sequence"]);assertEquals("1",counters.report()["active_sequence"])
        counters.event("completed",m,100);counters.event("roi_completed",m,50)
        assertNull(counters.report()["active_sequence"])
        counters.event("started",m,0);counters.event("failed",m,0);assertNull(counters.report()["active_sequence"])
    }
}
