package org.lmthermal.recorder

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*

/** A gap-only CHUNK is valid evidence, not a lost payload or proof of the historic fatal.
 * Fixed witnesses and one-item-at-a-time draining make these control paths deterministic.
 */
class RecorderGapOnlyTest {
    @Test fun gapOnlyChunksCommitWithoutInventingAcknowledgedMeasurementsInEitherCodec() {
        for (codec in 0..1) {
            val module=SimulatedRecordingModule()
            val file=Files.createTempDirectory("lmtr-gap-only-").resolve("capture.lmtr").toFile()
            val clock=AtomicLong(0)
            val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(module),codec),listOf(module.stream),
                RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576,preferredChunkBytes=1),
                LocalRecorderBackend(file),codec,now=clock::get)
            repeat(4) { i ->
                val gap=mapOf("category" to "source_loss","reason" to "module_reported_loss","count" to "1",
                    "observation_first" to i.toString(),"observation_last" to i.toString(),
                    "device_first" to null,"device_last" to null,"scheduled" to null,
                    "evidence" to mapOf("basis" to "host_observation","before" to null,"after" to null))
                assertEquals(Admission.ACCEPTED,engine.admitLoss(RecordingLoss(module.id,module.stream,gap)))
                // Source reserve must actually drain, not merely be accepted by intake.
                val limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(engine.snapshot().controlBytes!=0L) {
                    check(System.nanoTime()<limit);Thread.sleep(1)
                }
                clock.addAndGet(1000000)
            }
            engine.stop();val result=engine.awaitStop(10000)!!
            assertTrue(result.toString(),result.finalized)
            assertEquals("completed",result.status);assertNull(result.firstFailure)
            assertFalse(engine.snapshot().unrepresentedLoss);assertEquals(0L,engine.snapshot().acknowledged)
            val report=LmtrReader(file).use { it.check(full=true) }
            val summary=report["summary"].obj()
            assertEquals("0",summary["observations"]);assertEquals("4",summary["gap_events"])
            assertEquals("4",summary["known_missing_observations"])
            assertEquals("0",summary["physical_payload_bytes"])
            assertTrue(summary["chunks"].d()>=4)
        }
    }
}
