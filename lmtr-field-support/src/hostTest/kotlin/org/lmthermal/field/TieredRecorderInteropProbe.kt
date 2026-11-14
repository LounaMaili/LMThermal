package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import org.lmthermal.recording.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Eight noncanonical full-size, virtual-ten-second Analysis cases, not a physical recorder run.
 * Real adapters, periodic intake, loss ledger, two validations, backend and oracle remain active.
 * Only scalar diagnostics change; source fixtures and source/intake clocks do not. Frame pacing
 * is virtual and each selected offer drains: this proves data isolation, not sustained throughput.
 */
object TieredRecorderInteropProbe {
    private const val DOMAIN="11111111-1111-4111-8111-111111111111"
    private fun hash(file:File):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val scratch=ByteArray(65536)
            while(true){val n=input.read(scratch);if(n<0)break;digest.update(scratch,0,n)}}
        return java.util.HexFormat.of().formatHex(digest.digest())
    }
    private fun until(test:()->Boolean) {
        val deadline=System.nanoTime()+30000000000L
        while(!test()){check(System.nanoTime()<deadline);Thread.sleep(1)}
    }
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1);val root=File(args[0]);check(root.mkdirs())
        val input=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use {it.readBytes()}
        val originalHash=LmtrChunk.sha(input);val reports=mutableListOf<Obj>()
        for(codec in 0..1)for(mode in listOf("off","reference","tiered","tiered_failed")) {
            val dir=File(root,"$codec-$mode");check(dir.mkdir());val clock=AtomicLong(0)
            val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),
                "22222222-2222-4222-8222-222222222222",DOMAIN)
            val source=Ht301RecordingAdapter(identity,RecordingProfile.ANALYSIS)
            val oracle=File(dir,"oracle.jsonl").bufferedWriter()
            val adapter=FieldOracleAdapter(source,HtFieldOracle::capture,{r->oracle.write(LmtrJson.encode(r).toString(Charsets.UTF_8));oracle.newLine()},clock::get)
            val limits=FieldPreflight(RecordingProfile.ANALYSIS,10,null,codec).limits
            val file=File(dir,"capture.lmtr")
            val header=FieldClock(clock::get,DOMAIN).header()+mapOf("recording_id" to "33333333-3333-4333-8333-333333333333")
            val engine=RecorderEngine(header,listOf(source.stream.value()),RecordingProfile.ANALYSIS,limits,
                LocalRecorderBackend(file),codec,now=clock::get,sampling=FiveHertzSampling(0),stopDeadline=FieldPeriodicDuration.deadline(0,10))
            val diagnosticClock=AtomicLong(100)
            val w=BracketedMemoryWindow(7,MemoryScalarProbe {d,v->
                if(mode=="tiered_failed")throw java.io.IOException("synthetic diagnostic failure")
                if(d==MemoryDomain.RECORDER){val s=engine.snapshot();v[0]=0;v[2]=s.accepted;v[3]=s.acknowledged
                    v[4]=s.preparationDrops;v[5]=s.writerDrops;v[6]=s.preparationQueuedBytes;v[7]=s.preparationInFlightBytes
                    v[8]=s.writerQueuedBytes;v[9]=s.writerInFlightBytes;v[10]=s.pendingBytes;v[11]=s.controlBytes
                    v[12]=s.chunks;v[13]=s.checkpoints;MemoryProbeStatus.PARTIAL}
                else MemoryProbeStatus.UNSUPPORTED
            },diagnosticClock::incrementAndGet,if(mode.startsWith("tiered"))MemoryProbePlan.TIERED else MemoryProbePlan.REFERENCE)
            try {
                repeat(250) {n->
                    val tick=n*40000000L;clock.set(tick)
                    val frame=Ht301Frame.parse(input)
                    // A known unavailable selected observation must remain unavailable, not disappear.
                    val state=if(n==100)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY
                    val gated=MeasurementGate.evaluate(state,frame,n.toLong(),tick/1000000)
                    val observation=Ht301AcquiredObservation(identity,n.toLong(),n.toLong(),tick,frame,frame.inspect(),state,gated)
                    val decision=engine.admit(adapter.offer(observation))
                    check(decision==if(n%5==0)Admission.ACCEPTED else Admission.NOT_SELECTED)
                    if(n%5==0)until {engine.snapshot().let {it.preparationQueuedBytes+it.preparationInFlightBytes+it.writerQueuedBytes+it.writerInFlightBytes==0L}}
                    if(n%25==0 && mode!="off")check(w.sample())
                }
                clock.set(10000000000L);engine.wakeTimers()
                val result=checkNotNull(engine.awaitStop(30000));until {engine.workersTerminated()}
                oracle.flush();w.requestStop()
                if(mode!="off")File(dir,"memory.jsonl").bufferedWriter().use {w.writeTo(it)}
                val s=engine.snapshot();val p=engine.periodicSnapshot()!!
                check(result.finalized && p.ledgerComplete && s.acknowledged==50L && p.notSelected==200L)
                check(s.preparationDrops==0L && s.writerDrops==0L)
                val validation=LmtrReader(file).use {it.check(full=true)}
                val row=mapOf("codec" to codec,"diagnostic" to mode,"profile" to "analysis","physical" to false,
                    "source_fixture_sha256" to originalHash,"virtual_cutoff_ns" to p.cutoffNs?.toString(),
                    "snapshot" to fieldSnapshot(s),"periodic" to p.report(),"kotlin" to validation,
                    "result" to mapOf("finalized" to result.finalized,"status" to result.status,"reason" to result.reason),
                    "file_sha256" to hash(file),"reserved_bytes" to limits.reservedBytes.toString())
                File(dir,"run-result.json").writeBytes(LmtrJson.encode(row));reports.add(row)
            } finally {w.requestStop();engine.stop();engine.awaitStop(30000);oracle.close()}
        }
        check(originalHash==LmtrChunk.sha(input))
        File(root,"cases.json").writeBytes(LmtrJson.encode(mapOf("physical" to false,"canonical_fixture_generation" to false,
            "scope" to "Accelerated virtual clocks and drained selected work, not physical time/RSS/throughput",
            "source_fixture_unchanged" to true,"forced_gc" to false,"cases" to reports)))
    }
}
