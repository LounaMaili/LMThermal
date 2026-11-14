package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Distinguish original plane validity, actual synced ACK, complete ledger and complete file. */
class PeriodicFailureBoundaryTest {
    private fun file()=Files.createTempDirectory("lmtr-periodic-failure-").resolve("capture.lmtr").toFile()
    @Test fun moduleProvidedAllInvalidMaskAndFullPositiveZeroPlaneRemainAvailableWithoutReadings() {
        for(codec in 0..1) {
            val m=SimulatedRecordingModule();val f=file();val e=PeriodicTestSource.engine(m,f,AtomicLong(0),codec)
            e.admit(PeriodicTestSource.planeOffer(m,true));e.stop();assertTrue(checkNotNull(e.awaitStop(10000)).finalized)
            assertEquals(1L,e.periodicSnapshot()!!.acknowledgedAvailable)
            LmtrReader(f).use {r->assertEquals("0",r.check(full=true)["summary"].obj()["measurable_planes"])}
            val chunk=PeriodicTestSource.chunks(f).single()
            assertArrayEquals(ByteArray(24),chunk.payloads["1:temperature"])
            assertArrayEquals(ByteArray(6),chunk.payloads["1:validity"])
        }
    }
    @Test fun allValidNonfinitePlaneIsRejectedWithoutMaskingItOrAcknowledgingIt() {
        for(codec in 0..1) {
            val m=SimulatedRecordingModule();val f=file();val e=PeriodicTestSource.engine(m,f,AtomicLong(0),codec)
            e.admit(PeriodicTestSource.planeOffer(m,false));e.stop()
            assertFalse(checkNotNull(e.awaitStop(10000)).finalized);assertEquals(0L,e.snapshot().acknowledged)
            LmtrReader(f).use {assertEquals(true,it.check(full=true,recover=true)["unknown_tail"])}
        }
    }
    @Test fun unavailableClockAfterFinalFooterKeepsTheSyncedAckButCannotPublishEnd() {
        val m=SimulatedRecordingModule();val f=file();val broken=AtomicBoolean(false);val delegate=LocalRecorderBackend(f)
        val backend=object:RecorderBackend {
            override val destination=f
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                val record=delegate.append(type,ordinal,previous,body)
                if(type==LmtrFraming.CHUNK)broken.set(true)
                return record
            }
            override fun close()=delegate.close()
        }
        val clock={if(broken.get())throw IOException("Synthetic monotonic clock unavailable") else 0L}
        val e=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.ANALYSIS,
            RecorderLimits(maxObservationBytes=3L*1048576),backend,now=clock,sampling=FiveHertzSampling(0))
        e.admit(PeriodicTestSource.offer(m,0,0));e.stop()
        assertFalse(checkNotNull(e.awaitStop(10000)).finalized)
        assertEquals(1L,e.snapshot().acknowledged)
        assertEquals("periodic_clock_unavailable",e.periodicSnapshot()!!.failure)
        LmtrReader(f).use {val r=it.check(full=true,recover=true);assertEquals("1",r["summary"].obj()["observations"]);assertEquals(true,r["unknown_tail"])}
    }
    @Test fun sourceReceiptRegressionAndOriginalCounterOverflowCannotBeHiddenAsIntentionalDecimation() {
        val m=SimulatedRecordingModule();val clock=AtomicLong(100000000);val f=file();val e=PeriodicTestSource.engine(m,f,clock)
        e.admit(PeriodicTestSource.offer(m,0,clock.get()));clock.set(300000000)
        assertEquals(Admission.REFUSED,e.admit(PeriodicTestSource.offer(m,1,50000000)))
        assertFalse(checkNotNull(e.awaitStop(10000)).finalized)
        assertEquals("periodic_receipt_regressed",e.periodicSnapshot()!!.failure)
        val overflow=PeriodicTestSource.engine(m,file(),AtomicLong(0))
        assertEquals(Admission.REFUSED,overflow.admit(PeriodicTestSource.offer(m,Long.MAX_VALUE,0)))
        assertFalse(checkNotNull(overflow.awaitStop(10000)).finalized)
        assertEquals("periodic_counter_or_deadline_overflow",overflow.periodicSnapshot()!!.failure)
    }
    @Test fun syntheticAllocationFailureAtIntakeStopsWithoutAClosureAllocationOrARepairEnd() {
        val m=SimulatedRecordingModule();val failed=AtomicBoolean(false)
        val caller=Thread.currentThread().id
        val read={if(failed.get() && Thread.currentThread().id==caller)throw OutOfMemoryError("Synthetic allocation refusal, not actual heap exhaustion") else 0L}
        val f=file();val e=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.ANALYSIS,
            RecorderLimits(maxObservationBytes=3L*1048576),LocalRecorderBackend(f),now=read,sampling=FiveHertzSampling(0))
        failed.set(true)
        assertEquals(Admission.REFUSED,e.admit(PeriodicTestSource.offer(m,0,0)))
        val result=checkNotNull(e.awaitStop(10000));assertFalse(result.finalized)
        assertEquals("allocation_failed",e.periodicSnapshot()!!.failure)
    }
    @Test fun cumulativeSourceLossOverflowRefusesEndEvenWhenEachSignalIndividuallyFitsTheWire() {
        val m=SimulatedRecordingModule();val f=file();val e=PeriodicTestSource.engine(m,f,AtomicLong(0))
        fun signal(count:Long)=RecordingLoss(m.id,m.stream,mapOf("category" to "source_loss","reason" to "synthetic_count",
            "count" to count.toString(),"scheduled" to null,"device_first" to null,"device_last" to null,
            "observation_first" to null,"observation_last" to null,
            "evidence" to mapOf("basis" to "explicit_signal","before" to null,"after" to null)))
        assertEquals(Admission.ACCEPTED,e.admitLoss(signal(Long.MAX_VALUE)))
        assertEquals(Admission.REFUSED,e.admitLoss(signal(1)))
        assertFalse(checkNotNull(e.awaitStop(10000)).finalized)
        assertEquals("periodic_counter_or_deadline_overflow",e.periodicSnapshot()!!.failure)
        assertFalse(e.periodicSnapshot()!!.ledgerComplete)
    }
}
