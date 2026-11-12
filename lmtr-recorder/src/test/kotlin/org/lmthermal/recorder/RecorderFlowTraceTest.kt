package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*

/** Clock errors and fixed-prefix saturation are explicit diagnostic limits, not engine failures. */
class RecorderFlowTraceTest {
    @Test fun diagnosticCountersAtLongLimitCannotWrap() {
        val trace=RecorderFlowTrace(1,RecorderMetricClock{throw IllegalStateException("clock")})
        val failures=trace.javaClass.getDeclaredField("clockFailures");failures.isAccessible=true;failures.setLong(trace,Long.MAX_VALUE)
        trace.record(RecorderFlowKind.STOP,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
        val omitted=trace.javaClass.getDeclaredField("omitted");omitted.isAccessible=true;omitted.setLong(trace,Long.MAX_VALUE)
        trace.record(RecorderFlowKind.STOP,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
        val report=trace.report();assertEquals(Long.MAX_VALUE.toString(),report["omitted"])
        assertEquals(true,report["omitted_at_limit"]);assertEquals(true,report["clock_failures_at_limit"])
    }

    @Test fun capacityClockFailureAndUnknownWitnessesRemainBounded() {
        val trace=RecorderFlowTrace(1,RecorderMetricClock{throw IllegalStateException("clock")})
        repeat(4){trace.record(RecorderFlowKind.RESERVE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,
            Long.MIN_VALUE,32768,0,0,0,0,-1,-1,32768,0,1)}
        val r=trace.report();assertEquals(128,r["primitive_bytes"]);assertEquals(1,r["retained"])
        assertEquals("3",r["omitted"]);assertEquals("1",r["clock_failures"])
        assertNull(r["rows"].arr()[0].arr()[1]);assertNull(r["rows"].arr()[0].arr()[2])
    }
}
