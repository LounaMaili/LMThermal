package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import org.lmthermal.recording.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Six NONCANONICAL full-size virtual-clock cases: 10/60 seconds and late 60-second expiry,
 * each with STORED/DEFLATE-1. Complete input decisions and original-owner hashes stay private.
 * Virtual elapsed time exercises closure/identity, not ART jitter, throughput, RSS or calibration.
 */
object AnalysisDeadlineInteropProbe {
    private fun until(test:()->Boolean) {
        val limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
        while(!test()){check(System.nanoTime()<limit);Thread.sleep(2)}
    }
    private fun hash(file:File):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val scratch=ByteArray(65536)
            while(true){val n=input.read(scratch);if(n<0)break;digest.update(scratch,0,n)}}
        return java.util.HexFormat.of().formatHex(digest.digest()) // JDK-only host source set
    }
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1);val root=File(args[0]);check(root.mkdirs())
        val input=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use {it.readBytes()}
        val inputHash=LmtrChunk.sha(input);val results=mutableListOf<Obj>()
        for(codec in 0..1)for((seconds,lag) in listOf(10 to 0L,60 to 0L,60 to 25000000L)) {
            val name="deadline-${seconds}s-${if(lag==0L)"exact" else "late"}-$codec";val dir=File(root,name);check(dir.mkdir())
            val clock=AtomicLong(0);val domain=UUID.randomUUID().toString()
            val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),UUID.randomUUID().toString(),domain)
            val source=Ht301RecordingAdapter(identity,RecordingProfile.ANALYSIS)
            val oracle=File(dir,"oracle.jsonl").bufferedWriter();val offered=File(dir,"offered.jsonl").bufferedWriter()
            val adapter=FieldOracleAdapter(source,HtFieldOracle::capture,{r->oracle.write(LmtrJson.encode(r).toString(Charsets.UTF_8));oracle.newLine()},clock::get)
            val target=FieldPeriodicDuration.deadline(0,seconds);val limits=FieldPreflight(RecordingProfile.ANALYSIS,seconds,null,codec).limits
            val file=File(dir,"capture.lmtr")
            val engine=RecorderEngine(FieldClock(clock::get,domain).header(),listOf(source.stream.value()),RecordingProfile.ANALYSIS,
                limits,LocalRecorderBackend(file),codec,now=clock::get,sampling=FiveHertzSampling(0),stopDeadline=target)
            try {
                repeat(seconds*25) {n->
                    val tick=n*40000000L;clock.set(tick);val unavailable=n==(if(seconds==10)100 else 500)
                    val state=if(unavailable)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY
                    val frame=Ht301Frame.parse(input);val gated=MeasurementGate.evaluate(state,frame,n.toLong(),tick/1000000)
                    check(unavailable || gated.measurement!=null)
                    val owner=Ht301AcquiredObservation(identity,n.toLong(),n.toLong(),tick,frame,frame.inspect(),state,gated)
                    val decision=engine.admit(adapter.offer(owner))
                    offered.write(LmtrJson.encode(mapOf("original_ordinal" to n.toString(),"original_callback_sequence" to n.toString(),
                        "receipt_ns" to tick.toString(),"intake_lower_ns" to tick.toString(),"intake_upper_ns" to tick.toString(),
                        "measurement_available" to !unavailable,"unavailable_reason" to owner.unavailableReason,"admission" to decision.name)).toString(Charsets.UTF_8));offered.newLine()
                    if(n%5==0) {
                        check(decision==Admission.ACCEPTED)
                        until {engine.snapshot().let {it.preparationQueuedBytes+it.preparationInFlightBytes+it.writerQueuedBytes+it.writerInFlightBytes==0L}}
                    } else check(decision==Admission.NOT_SELECTED)
                }
                clock.set(target.monotonicNs+lag);engine.wakeTimers()
                val result=checkNotNull(engine.awaitStop(30000));until {engine.workersTerminated()}
                oracle.flush();offered.flush()
                val p=engine.periodicSnapshot()!!;val snapshot=engine.snapshot()
                val validation=LmtrReader(file).use {it.check(full=true)}
                val expected=result.finalized && result.reason==RecordingStopDeadline.REASON &&
                    p.acquired==seconds*25L && p.selected==seconds*5L && p.notSelected==seconds*20L &&
                    snapshot.acknowledged==seconds*5L && snapshot.preparationDrops==0L && snapshot.writerDrops==0L &&
                    p.missedJournaled==(if(lag==0L)0L else 1L) && p.ledgerComplete
                val record=mapOf("name" to name,"recover" to false,"expectation_met" to expected,"codec" to codec,
                    "profile" to "analysis","period_ns" to FiveHertzSampling.PERIOD_NS.toString(),
                    "requested_duration_ns" to target.monotonicNs.toString(),"notification_lag_ns" to lag.toString(),
                    "periodic" to p.report(),"deadline" to engine.deadlineSnapshot()!!.report(),"snapshot" to fieldSnapshot(snapshot),
                    "result" to mapOf("finalized" to result.finalized,"status" to result.status,"reason" to result.reason,"failure" to result.failure),
                    "kotlin" to validation,"file_sha256" to hash(file),"reserved_bytes" to limits.reservedBytes.toString())
                File(dir,"run-result.json").writeBytes(LmtrJson.encode(record));results.add(record)
                println("$name expectation=$expected planned=${p.plannedSlots} selected=${p.selected} ack=${snapshot.acknowledged} missed=${p.missedJournaled}")
            } finally {engine.stop();engine.awaitStop(30000);oracle.close();offered.close()}
        }
        check(LmtrChunk.sha(input)==inputHash)
        File(root,"cases.json").writeBytes(LmtrJson.encode(mapOf("expected_cases" to 6,
            "scope" to "full-size synthetic gate replay, virtual monotonic domain, no camera/Android/RSS/cadence claim",
            "canonical_fixture_generation" to false,"source_fixture_sha256" to inputHash,"forced_gc" to false,"cases" to results)))
        check(results.size==6 && results.all {it["expectation_met"]==true}){"Adverse outputs retained; do not retry to hide them"}
    }
}
