package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Deterministic software-only backlog experiments. Clock time is SIMULATED latency,
 * never measured throughput. The existing 4MiB queues, 4MiB preferred CHUNK, 3MiB
 * negotiated owner maximum and 1MiB control reserve remain unchanged (78MiB formula).
 */
object RecorderBacklogProbe {
    private const val CONTROL = 1048576L
    private const val LOSS_CHARGE = 32768L
    private const val SECOND = 1000000000L
    private const val SYNC_LATENCY = 50000000L

    private fun until(label: String, predicate: () -> Boolean) {
        val limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
        while(!predicate()) { check(System.nanoTime()<limit) { label };Thread.sleep(1) }
    }

    /** A bounded trace stores scalars only; no CHUNK, metadata AST or payload is retained. */
    private class DelayedBackend(val clock:AtomicLong, file:File):RecorderBackend {
        override val destination=file
        private val base=LocalRecorderBackend(file)
        val engine=AtomicReference<RecorderEngine>()
        val blocked=CountDownLatch(1)
        val release=CountDownLatch(1)
        val rows=mutableListOf<Obj>()
        var chunkAttempts=0
        var failLastGapSequence:Long?=null
        override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
            var observations=0;var gaps=0;var first:Long?=null;var last:Long?=null
            var firstId:String?=null;var lastId:String?=null;var lastGap:Long?=null
            if(type==LmtrFraming.CHUNK) {
                chunkAttempts++
                val metadata=LmtrChunk.metadata(body).first
                val entries=metadata["entries"].arr().map { it.obj() }
                observations=entries.count { it["kind"]=="observation" }
                gaps=entries.count { it["kind"]=="gap" }
                first=entries.first()["timeline_ns"].d();last=entries.last()["timeline_ns"].d()
                firstId=entries.first()["entry_id"].text();lastId=entries.last()["entry_id"].text()
                lastGap=entries.lastOrNull { it["kind"]=="gap" }?.get("observation_last")?.d()
                if(chunkAttempts==2) { blocked.countDown();check(release.await(15,TimeUnit.SECONDS)) }
            }
            val at=clock.get();val snapshot=engine.get()?.snapshot()
            check(rows.size<256) { "Diagnostic trace capacity exceeded" }
            rows.add(mapOf("record_type" to type,"ordinal" to ordinal.toString(),
                "simulated_append_begin_ns" to at.toString(),"first_timeline_ns" to first?.toString(),
                "last_timeline_ns" to last?.toString(),"oldest_age_ns" to first?.let { (at-it).toString() },
                "observations" to observations,"gap_entries" to gaps,"first_entry" to firstId,"last_entry" to lastId,
                "control_before_sync" to snapshot?.controlBytes?.toString(),
                "acknowledged_before_sync" to snapshot?.acknowledged?.toString()))
            if(observations==0 && gaps>0 && lastGap==failLastGapSequence) throw IOException("injected final gap append failure before base backend")
            // Moving the test clock makes subsequent policy decisions see latency;
            // no actual sleep, GC, weakened deadline or recorder callback is introduced.
            clock.addAndGet(SYNC_LATENCY)
            return base.append(type,ordinal,previous,body)
        }
        override fun close()=base.close()
    }

    /** Prime a durable prefix, block the second CHUNK, reserve exactly32 losses,
     * then compare fresh/aged witnesses or attempt the unrepresentable33rd loss.
     */
    fun runCase(directory:File,codec:Int,aged:Boolean,overflow:Boolean=false,failLastGap:Boolean=false,coalescing:Boolean=false,flowTrace:RecorderFlowTrace?=null,concurrentStop:Boolean=false,withTrace:Boolean=false,observer:RecorderFlowObserver?=null):Obj {
        check(directory.mkdirs())
        val module=SimulatedRecordingModule(width=256,height=128)
        val clock=AtomicLong(0)
        val actualTrace=flowTrace ?: if(withTrace)RecorderFlowTrace(2048,RecorderMetricClock(clock::get)) else null
        val file=File(directory,"capture.lmtr")
        val backend=DelayedBackend(clock,file)
        val firstPass=AtomicLong();val secondPass=AtomicLong()
        val instrumentation=RecorderInstrumentation(RecorderMetricObserver { phase,_,_,_,_,_,_ ->
            if(phase==RecorderPhase.CHUNK_FIRST_VALIDATION)firstPass.incrementAndGet()
            if(phase==RecorderPhase.APPEND_CHUNK)secondPass.incrementAndGet()
        },RecorderMetricClock { 0L })
        val limits=RecorderLimits(maxObservationBytes=3145728)
        check(limits.reservedBytes==78L*1048576 && limits.controlBytes==CONTROL)
        val recorder=RecorderEngine(SimulatedRecordingModule.header(listOf(module),codec),listOf(module.stream),
            RecordingProfile.FULL,limits,backend,codec,now=clock::get,instrumentation=instrumentation,experimentalLossRuns=coalescing,flowObserver=observer ?: actualTrace)
        backend.engine.set(recorder)
        val originals=mutableMapOf<Long,PreparedObservation>()
        val intakeWitnesses=mutableMapOf<Long,Long>()
        fun offer(sequence:Long):Admission {
            // Vary original loss witnesses without advancing the clock by a sealing interval.
            if(coalescing && sequence>=2)clock.incrementAndGet()
            intakeWitnesses[sequence]=clock.get()
            val value=module.observation(sequence)
            originals[sequence]=RecordingProfiles.select(value,RecordingProfile.FULL)
            return recorder.admit(value)
        }
        var lastSequence=1L
        var expectedLosses=0L
        var snapshotAtLimit:RecorderSnapshot?=null
        try {
            assertEquals(Admission.ACCEPTED,offer(0))
            until("prime pending"){recorder.snapshot().pendingBytes>0}
            clock.addAndGet(SECOND);recorder.wakeTimers()
            until("prime durable acknowledgment"){recorder.snapshot().acknowledged==1L}
            assertEquals(Admission.ACCEPTED,offer(1))
            until("second pending"){recorder.snapshot().pendingBytes>0}
            clock.addAndGet(SECOND);recorder.wakeTimers()
            check(backend.blocked.await(15,TimeUnit.SECONDS))
            assertEquals("Blocked sync cannot acknowledge the second observation",1L,recorder.snapshot().acknowledged)
            // Admit one at a time while preparation is unblocked: any overload here
            // is writer-owned, not an accidentally scheduler-dependent preparation drop.
            while(recorder.snapshot().writerDrops<CONTROL/LOSS_CHARGE) {
                lastSequence++
                val before=recorder.snapshot()
                assertEquals(Admission.ACCEPTED,offer(lastSequence))
                until("preparation resolved"){val s=recorder.snapshot();s.preparationInFlightBytes==0L && s.preparationQueuedBytes==0L &&
                    (s.writerQueuedBytes>before.writerQueuedBytes || s.writerDrops>before.writerDrops)}
                assertEquals(0L,recorder.snapshot().preparationDrops)
                check(lastSequence<100)
            }
            expectedLosses=recorder.snapshot().writerDrops
            snapshotAtLimit=recorder.snapshot()
            assertEquals(CONTROL,snapshotAtLimit.controlBytes)
            assertNull(snapshotAtLimit.firstFailure);assertFalse(snapshotAtLimit.unrepresentedLoss)
            if(overflow) {
                lastSequence++
                assertEquals(Admission.ACCEPTED,offer(lastSequence))
                until("33rd writer-loss reserve failure"){recorder.snapshot().unrepresentedLoss}
                val first=recorder.snapshot().firstFailure!!
                assertEquals(RecorderFailureStage.WRITER_LOSS_RESERVE,first.stage)
                assertEquals(RecorderFailureCode.LOSS_JOURNAL_EXHAUSTED,first.code)
                assertEquals(CONTROL,first.controlBytes);assertEquals(lastSequence,first.observationSequence)
            }
            if(failLastGap)backend.failLastGapSequence=lastSequence
            if(concurrentStop) {
                val stoppers=(0 until 4).map { Thread { repeat(25){recorder.stop()} }.also { it.start() } }
                stoppers.forEach { it.join(5000);check(!it.isAlive) }
                assertEquals(Admission.STOPPED,recorder.admit(module.observation(lastSequence+1)))
                assertNull("Stop cannot acknowledge a blocked backend",recorder.awaitStop(10))
            } else recorder.stop()
            if(aged)clock.addAndGet(2*SECOND)
            backend.release.countDown()
            val result=recorder.awaitStop(30000) ?: error("Stop timed out")
            until("all workers terminated"){recorder.workersTerminated()}
            val snapshot=recorder.snapshot()
            assertEquals(0L,snapshot.controlBytes)
            assertEquals(0L,snapshot.preparationQueuedBytes);assertEquals(0L,snapshot.writerQueuedBytes)
            assertEquals(0L,snapshot.preparationInFlightBytes);assertEquals(0L,snapshot.writerInFlightBytes)
            assertEquals("first failure=${result.firstFailure?.detailCode}, terminal=${result.failure}",firstPass.get(),secondPass.get())
            assertEquals(backend.chunkAttempts.toLong(),firstPass.get())
            assertEquals(0L,instrumentation.failures().first);assertEquals(0L,instrumentation.failures().second)
            assertEquals(!(overflow || failLastGap),result.finalized)
            assertEquals(overflow,snapshot.unrepresentedLoss)
            if(failLastGap) {
                assertEquals(RecorderFailureStage.BACKEND_APPEND,result.firstFailure!!.stage)
                assertEquals(0L,result.firstFailure!!.controlBytes)
            }
            val beforeHash=LmtrChunk.sha(file.readBytes())
            val report=LmtrReader(file).use { it.check(full=true,recover=!result.finalized) }
            assertEquals(if(result.finalized)"full" else "full_verified_prefix",report["integrity_scope"])
            val summary=report["summary"].obj()
            assertEquals(snapshot.acknowledged,summary["observations"].d())
            assertEquals(expectedLosses-(if(failLastGap) { if(coalescing)expectedLosses else 1 } else 0),summary["known_missing_observations"].d())
            if(!result.finalized)assertEquals(true,report["unknown_tail"])
            val expected=mutableListOf<Obj>()
            val gapSequences=mutableListOf<Long>()
            java.io.RandomAccessFile(file,"r").use { input ->
                val header=LmtrFraming.read(input,0);val head=LmtrJson.parse(header.body)
                val end=if(result.finalized)file.length() else report["verified_prefix_bytes"].d()
                var at=header.length;var nextEntry=0L;var previousTimeline=-1L
                while(at<end) {
                    val record=LmtrFraming.read(input,at)
                    assertFalse("No END in incomplete source",!result.finalized && record.type==LmtrFraming.END)
                    if(record.type==LmtrFraming.CHUNK) {
                        val chunk=LmtrChunk.decode(record.body,head)
                        for(e in chunk.metadata["entries"].arr().map { it.obj() }) {
                            assertEquals(nextEntry++,e["entry_id"].d())
                            assertTrue(e["timeline_ns"].d()>=previousTimeline);previousTimeline=e["timeline_ns"].d()
                            if(e["kind"]=="gap") {
                                val first=e["observation_first"].d();val last=e["observation_last"].d()
                                gapSequences.addAll(first..last)
                                if(coalescing && last>first) {
                                    val evidence=e["extensions"].obj()["org.lmthermal.diagnostic.loss-witnesses"].obj()["data"].obj()
                                    assertEquals((first..last).map { intakeWitnesses.getValue(it).toString() },evidence["intake_monotonic_ns"].arr())
                                    assertEquals(intakeWitnesses.getValue(first),e["timeline_ns"].d())
                                }
                            }
                            if(e["kind"]=="observation") {
                                val sequence=e["observation_sequence"].d();val source=originals.getValue(sequence)
                                val roles=source.payloads.associate { p ->
                                    val bytes=chunk.payloads.getValue("${e["entry_id"]}:${p.descriptor.value()["id"]}")
                                    assertEquals(LmtrChunk.sha(p.bytes()),LmtrChunk.sha(bytes))
                                    p.role to mapOf("length" to p.size.toString(),"hash" to LmtrChunk.sha(p.bytes()))
                                }
                                expected.add(mapOf("sequence" to sequence.toString(),"payloads" to roles))
                            }
                        }
                    }
                    at+=record.length
                }
            }
            assertEquals(gapSequences.sorted(),gapSequences)
            assertEquals(gapSequences.distinct(),gapSequences)
            assertEquals(beforeHash,LmtrChunk.sha(file.readBytes()))
            val summaryRow=mapOf("codec" to codec,"aged" to aged,"overflow" to overflow,"last_gap_append_failure" to failLastGap,
                "simulated_latency_ns_per_record" to SYNC_LATENCY.toString(),"journal_peak_bytes" to snapshotAtLimit.controlBytes.toString(),
                "source_offers" to (lastSequence+1).toString(),"expected_loss_records" to expectedLosses.toString(),
                "finalized" to result.finalized,"status" to result.status,"terminal_reason" to result.reason,
                "unrepresented_loss" to snapshot.unrepresentedLoss,"first_failure" to result.firstFailure?.let { mapOf(
                    "stage" to it.stage.name,"code" to it.code.name,"control_bytes" to it.controlBytes.toString()) },
                "chunks" to summary["chunks"],"first_validation_calls" to firstPass.get().toString(),
                "second_validation_calls" to secondPass.get().toString(),"trace" to backend.rows,
                "file_sha256" to beforeHash,"kotlin_report" to report,"original_payloads" to expected,
                "coalescing" to coalescing,"flow_trace" to actualTrace?.report(),
                "flow_observer_failures" to recorder.flowDiagnosticFailures().toString(),
                "offered_intake_witnesses" to intakeWitnesses.mapKeys { it.key.toString() }.mapValues { it.value.toString() },"scope" to "controlled synthetic software clock; not measured ART throughput or physical recording")
            File(directory,"probe.json").writeBytes(LmtrJson.encode(summaryRow))
            return summaryRow
        } finally {backend.release.countDown();recorder.stop();recorder.awaitStop(30000)}
    }

    @JvmStatic fun main(args:Array<String>) {
        val root=File(args.single());check(root.mkdirs())
        val reports=mutableListOf<Obj>()
        for(codec in 0..1) {
            reports.add(runCase(File(root,"fresh-$codec"),codec,false))
            reports.add(runCase(File(root,"aged-$codec"),codec,true))
            reports.add(runCase(File(root,"overflow-$codec"),codec,true,overflow=true))
            reports.add(runCase(File(root,"gap-sync-failure-$codec"),codec,true,failLastGap=true))
        }
        for(codec in 0..1) {
            val fresh=reports.single { it["codec"]==codec && it["aged"]==false }
            val aged=reports.single { it["codec"]==codec && it["aged"]==true && it["overflow"]==false && it["last_gap_append_failure"]==false }
            assertTrue("Expired witnesses amplify CHUNKs",aged["chunks"].d()>fresh["chunks"].d())
        }
        File(root,"summary.json").writeBytes(LmtrJson.encode(mapOf("cases" to reports)))
    }
}

/** Exercise the same bounded deterministic scenarios in the standard host suite. */
class RecorderBacklogTest {
    /** Persistent probe evidence uses main(); ordinary JUnit owns disposable temp files. */
    private fun scenario(aged:Boolean,overflow:Boolean=false,failLastGap:Boolean=false) {
        for(codec in 0..1) {
            val parent=Files.createTempDirectory("backlog-test-").toFile()
            try { RecorderBacklogProbe.runCase(File(parent,"case"),codec,aged,overflow,failLastGap) }
            finally { check(parent.deleteRecursively()) { "Failed to release disposable test files" } }
        }
    }
    @Test fun fullJournalCanFinalizeWhenItsEveryLossIsRepresented()=scenario(false)
    @Test fun agedLossesSealIndividuallyWithoutInventedObservations()=scenario(true)
    @Test fun nextUnrepresentableLossPoisonsEndAndPreservesPrefix()=scenario(true,overflow=true)
    @Test fun releasedControlChargeDoesNotCertifyFailedGapAppend()=scenario(true,failLastGap=true)
}
