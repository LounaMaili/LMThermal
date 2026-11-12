package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*

/** Counter correctness is deterministic; these tests make no ART or throughput claim. */
class WriterMetricsTest {
    @Test fun exactTotalsAndExtremaIncludeSamplesBeyondTheFixedQuantileBuffer() {
        val distribution=FieldDistribution(2)
        distribution.add(10);distribution.add(20);distribution.add(100)
        val report=distribution.report()
        assertEquals(2,report["count"]);assertEquals("3",report["observed_count"])
        assertEquals("1",report["omitted"]);assertEquals("130",report["sum_ns"])
        assertEquals("10",report["min_ns"]);assertEquals("100",report["max_all_ns"])
        assertEquals("10",report["p50_ns"]);assertEquals("10",report["p95_ns"])
        assertEquals("20",report["max_ns"])
    }

    @Test fun sumOverflowIsStickyAndNeverExportedAsAnExactSaturatedTotal() {
        val distribution=FieldDistribution(1)
        distribution.add(Long.MAX_VALUE);distribution.add(1);distribution.add(0)
        val report=distribution.report()
        assertNull(report["sum_ns"]);assertEquals(true,report["sum_overflow"])
        assertEquals("3",report["valid_count"]);assertEquals("2",report["omitted"])
        assertEquals(Long.MAX_VALUE.toString(),report["max_all_ns"])
    }

    @Test fun invalidAndInterruptedCallsAreCountedSeparatelyFromValidSampleOmission() {
        val distribution=FieldDistribution(0)
        distribution.add(-1,false);distribution.add(3,false);distribution.add(0)
        val report=distribution.report()
        assertEquals("3",report["observed_count"]);assertEquals("2",report["valid_count"])
        assertEquals("1",report["invalid"]);assertEquals("2",report["interrupted"])
        assertEquals("2",report["omitted"]);assertEquals("3",report["sum_ns"])
        assertNull(report["p50_ns"]);assertEquals("0",report["min_ns"])
    }

    @Test fun phaseStatusAndRecordTypeRemainIndependentAndUnknownAttributionStaysEmpty() {
        val metrics=FieldWriterMetrics(2)
        metrics.record(RecorderPhase.BACKEND_APPEND,11,true,2,55,-1,-1)
        metrics.record(RecorderPhase.BACKEND_APPEND,17,false,2,77,-1,-1)
        metrics.record(RecorderPhase.BACKEND_APPEND,9,true,999,7,-1,-1)
        metrics.record(RecorderPhase.CHUNK_BUILD,5,true,2,55,3,4)
        val report=metrics.report()
        val backend=report["backend_cells"].arr().map { it.obj() }
        val chunk=backend.single { it["record_type"]=="CHUNK" }
        assertEquals("11",chunk["completed"].obj()["duration"].obj()["sum_ns"])
        assertEquals("17",chunk["interrupted"].obj()["duration"].obj()["sum_ns"])
        assertEquals("0",chunk["completed"].obj()["observation_entries"].obj()["observed_count"])
        assertNull(chunk["completed"].obj()["observation_entries"].obj()["sum_entries"])
        assertEquals("9",backend.single { it["record_type"]=="OTHER" }["completed"].obj()["duration"].obj()["sum_ns"])
        val build=report["phase_cells"].arr().map { it.obj() }.single { it["phase"]=="CHUNK_BUILD" }["completed"].obj()
        assertEquals("3",build["observation_entries"].obj()["sum_entries"])
        assertEquals("4",build["gap_entries"].obj()["sum_entries"])
        assertEquals("55",build["body_bytes"].obj()["sum_bytes"])
        assertEquals(6,backend.size)
    }
    @Test fun concurrentScalarUpdatesDoNotLoseTotalsWhenTheSampleBufferIsFull() {
        val distribution=FieldDistribution(8)
        val threads=List(4) { Thread { repeat(128) { distribution.add(3) } } }
        threads.forEach { it.start() };threads.forEach { it.join() }
        val report=distribution.report()
        assertEquals("512",report["observed_count"]);assertEquals("1536",report["sum_ns"])
        assertEquals("504",report["omitted"]);assertEquals(8,report["count"])
        assertEquals(false,report["sum_overflow"])
    }

}
