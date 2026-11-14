package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import org.lmthermal.recording.*
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Persistent NONCANONICAL virtual-clock conformance loads; no camera/Android runtime is used.
 * Synthetic gate owners replay sanitized source bytes, before profile-aware freezing/selection.
 * All original-owner hashes and offered events stream to JSONL; no payload history is retained.
 * This is not a throughput benchmark, physical readiness, RSS or calibration experiment.
 */
object AnalysisFiveHzInteropProbe {
    private const val SECOND=1000000000L
    private const val SOURCE_PERIOD=40000000L
    private enum class Mode { TEN_SECONDS, ABSENT_LATE, PARTIAL_STOP, IDLE, PREPARATION, WRITER, DETACH, JOURNAL, CLOCK, END_FAILURE }

    private fun until(message:String,test:()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
        while(!test()){check(System.nanoTime()<end){message};Thread.sleep(2)}
    }
    private fun write(file:File,value:Obj) {check(file.createNewFile());file.writeBytes(LmtrJson.encode(value))}
    private fun hash(file:File):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val scratch=ByteArray(65536)
            while(true){val count=input.read(scratch);if(count<0)break;digest.update(scratch,0,count)}}
        return java.util.HexFormat.of().formatHex(digest.digest()) // JDK-only host source set
    }

    /** Optional second input is an existing sanitized fixture, never a new/private physical scene. */
    @JvmStatic fun main(args:Array<String>) {
        require(args.size in 1..2);val root=File(args[0]);check(root.mkdirs())
        val room=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use {it.readBytes()}
        val warm=args.getOrNull(1)?.let {File(it)}
        val originalWarmHash=warm?.let(::hash)
        val inputs=listOf(room,warm?.readBytes() ?: room)
        val reports=mutableListOf<Obj>()
        for(codec in 0..1)for(mode in Mode.entries)reports.add(runCase(root,inputs,mode,codec))
        for(codec in 0..1)for(allInvalid in listOf(true,false))reports.add(planeCase(root,codec,allInvalid))
        check(warm==null || hash(warm)==originalWarmHash)
        write(File(root,"cases.json"),mapOf("scope" to "synthetic gate replay and virtual monotonic witnesses; not physical acquisition",
            "source_fixture_sha256" to inputs.map(LmtrChunk::sha),"source_unchanged" to true,
            "canonical_fixture_generation" to false,"forced_gc" to false,"cases" to reports))
        check(reports.all {it["expectation_met"]==true}) {"Unexpected result retained; inspect cases.json, do not relabel or retry blindly"}
    }

    private fun runCase(root:File,inputs:List<ByteArray>,mode:Mode,codec:Int):Obj {
        val name="${mode.name.lowercase()}-$codec";val dir=File(root,name);check(dir.mkdir())
        val clock=AtomicLong(0);val domain=UUID.randomUUID().toString()
        val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),UUID.randomUUID().toString(),domain)
        val adapter=Ht301RecordingAdapter(identity,RecordingProfile.ANALYSIS)
        val oracle=File(dir,"oracle.jsonl").bufferedWriter();val offered=File(dir,"offered.jsonl").bufferedWriter()
        val wrapped=FieldOracleAdapter(adapter,HtFieldOracle::capture,{row->oracle.write(LmtrJson.encode(row).toString(Charsets.UTF_8));oracle.newLine()},clock::get)
        val preparationEntered=CountDownLatch(1);val preparationRelease=CountDownLatch(1)
        val writerEntered=CountDownLatch(1);val writerRelease=CountDownLatch(1)
        val backendBase=LocalRecorderBackend(File(dir,"capture.lmtr"))
        val backend=object:RecorderBackend {
            override val destination=backendBase.destination
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(mode==Mode.WRITER && type==LmtrFraming.CHUNK && writerRelease.count>0) {
                    writerEntered.countDown();check(writerRelease.await(25,TimeUnit.SECONDS))
                }
                if(mode==Mode.END_FAILURE && type==LmtrFraming.END)throw IOException("Synthetic END refusal")
                return backendBase.append(type,ordinal,previous,body)
            }
            override fun close()=backendBase.close()
        }
        val limits=FieldPreflight(RecordingProfile.ANALYSIS,10,null,codec).limits
        val engine=RecorderEngine(FieldClock(clock::get,domain).header(),listOf(adapter.stream.value()),RecordingProfile.ANALYSIS,
            limits,backend,codec,now=clock::get,prepareHook={
                if(mode in listOf(Mode.PREPARATION,Mode.JOURNAL)) {
                    preparationEntered.countDown();check(preparationRelease.await(25,TimeUnit.SECONDS))
                }
            },sampling=FiveHertzSampling(0))
        val duration=when(mode) {
            Mode.TEN_SECONDS,Mode.ABSENT_LATE->10*SECOND
            Mode.PARTIAL_STOP,Mode.END_FAILURE->1100000000L
            Mode.IDLE,Mode.DETACH->2100000000L
            Mode.PREPARATION->550000000L
            Mode.WRITER->4*SECOND
            Mode.JOURNAL->7*SECOND
            Mode.CLOCK->2*SECOND
        }
        var ordinal=0L;var lastReceipt=0L;var stoppedEarly=false
        try {
            if(mode!=Mode.IDLE) {
                var tick=0L
                while(tick*SOURCE_PERIOD<duration) {
                    val at=tick*SOURCE_PERIOD;clock.set(at)
                    val absent=(mode==Mode.ABSENT_LATE && at in 2*SECOND until 3200000000L) ||
                        (mode==Mode.PARTIAL_STOP && at>=SECOND)
                    if(absent){engine.wakeTimers();tick++;continue}
                    val receipt=if(mode==Mode.ABSENT_LATE && at==3200000000L)lastReceipt else at
                    val unavailable=at==4*SECOND || (mode==Mode.PREPARATION && at==400000000L)
                    val frame=Ht301Frame.parse(inputs[(tick/25%2).toInt()])
                    val state=if(unavailable)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY
                    val result=MeasurementGate.evaluate(state,frame,ordinal,receipt/1000000)
                    check(unavailable || result.measurement!=null) {"Input fixture is not a valid synthetic gate source"}
                    val source=Ht301AcquiredObservation(identity,ordinal,ordinal,receipt,frame,frame.inspect(),state,result)
                    val lower=clock.get();val admission=engine.admit(wrapped.offer(source));val upper=clock.get()
                    offered.write(LmtrJson.encode(mapOf("original_ordinal" to ordinal.toString(),"original_callback_sequence" to ordinal.toString(),
                        "receipt_ns" to receipt.toString(),"intake_lower_ns" to lower.toString(),"intake_upper_ns" to upper.toString(),
                        "measurement_available" to (result.measurement!=null),"unavailable_reason" to source.unavailableReason,
                        "admission" to admission.name)).toString(Charsets.UTF_8));offered.newLine()
                    lastReceipt=receipt;ordinal++;tick++
                    if(admission in listOf(Admission.STOPPED,Admission.REFUSED)){stoppedEarly=true;break}
                    if(mode in listOf(Mode.PREPARATION,Mode.JOURNAL)) {
                        if(ordinal==1L)check(preparationEntered.await(10,TimeUnit.SECONDS))
                    } else if(admission==Admission.ACCEPTED) {
                        until("source materializer did not drain") {engine.snapshot().let {
                            it.preparationQueuedBytes+it.preparationInFlightBytes==0L &&
                                (mode==Mode.WRITER || it.writerQueuedBytes+it.writerInFlightBytes==0L)}}
                        if(mode==Mode.WRITER && at>=1200000000L)check(writerEntered.await(10,TimeUnit.SECONDS))
                    }
                }
            } else {
                clock.set(2*SECOND);engine.wakeTimers()
                until("idle timer did not detect windows without a callback") {engine.periodicSnapshot()!!.missedJournaled==10L}
            }
            if(!stoppedEarly)clock.set(duration)
            if(mode==Mode.CLOCK) {
                engine.wakeTimers();until("no durable prefix before clock regression"){engine.snapshot().chunks>0}
                clock.set(SECOND);engine.wakeTimers()
                until("clock regression did not stop intake"){engine.periodicSnapshot()!!.failure!=null}
            }
            if(mode==Mode.DETACH)engine.admitLoss(adapter.loss(CameraSourceLoss(identity,"synthetic_detach",null,lastReceipt,clock.get())))
            engine.stop(if(mode==Mode.DETACH)"detached" else "user_stop",mode==Mode.DETACH)
            preparationRelease.countDown();writerRelease.countDown()
            val terminal=checkNotNull(engine.awaitStop(30000))
            until("workers retained source reservation after completion"){engine.workersTerminated()}
            oracle.flush();offered.flush()
            val recover=mode in listOf(Mode.JOURNAL,Mode.CLOCK,Mode.END_FAILURE)
            val check= LmtrReader(backend.destination).use {it.check(full=true,recover=recover)}
            val snapshot=engine.snapshot();val periodic=engine.periodicSnapshot()!!
            val expected=terminal.finalized==!recover && (recover || periodic.ledgerComplete) &&
                (mode!=Mode.WRITER || snapshot.writerDrops>0) &&
                (mode!=Mode.PREPARATION || snapshot.preparationDrops==1L) &&
                (mode!=Mode.JOURNAL || snapshot.preparationDrops==32L)
            val report=mapOf("name" to name,"codec" to codec,"recover" to recover,"expectation_met" to expected,
                "expected_scope" to "virtual-clock functional qualification, not cadence/RSS measurement",
                "requested_duration_ns" to duration.toString(),"actual_cutoff_ns" to periodic.cutoffNs?.toString(),
                "profile" to "analysis","period_ns" to FiveHertzSampling.PERIOD_NS.toString(),
                "source" to "sanitized fixture-derived simulated gate owners", "snapshot" to fieldSnapshot(snapshot),
                "periodic" to periodic.report(),"result" to mapOf("finalized" to terminal.finalized,"status" to terminal.status,
                    "reason" to terminal.reason,"failure" to terminal.failure),"kotlin" to check,
                "file_sha256" to hash(backend.destination),"reserved_bytes" to limits.reservedBytes.toString(),
                "periodic_static_metadata_bytes" to engine.periodicStaticMetadataBytes()?.toString())
            write(File(dir,"run-result.json"),report)
            println("$name finalized=${terminal.finalized} selected=${periodic.selected} ack=${snapshot.acknowledged} prep=${snapshot.preparationDrops} writer=${snapshot.writerDrops}")
            return report
        } finally {
            preparationRelease.countDown();writerRelease.countDown();engine.stop();engine.awaitStop(30000)
            oracle.close();offered.close()
        }
    }

    /** Small explicit source planes supplement full-size HT cases with mask/nonfinite refusal semantics. */
    private fun planeCase(root:File,codec:Int,allInvalid:Boolean):Obj {
        val name="${if(allInvalid)"all-invalid-mask" else "nonfinite-refused"}-$codec";val dir=File(root,name);check(dir.mkdir())
        val module=SimulatedRecordingModule();val clock=AtomicLong(0);val file=File(dir,"capture.lmtr")
        val offer=PeriodicTestSource.planeOffer(module,allInvalid);val original=offer.materialize()
        val engine=PeriodicTestSource.engine(module,file,clock,codec)
        val source=mapOf("original_ordinal" to "0","original_callback_sequence" to "0","receipt_ns" to "0",
            "intake_lower_ns" to "0","intake_upper_ns" to "0","measurement_available" to true,"unavailable_reason" to null,"admission" to "ACCEPTED")
        val oracle=mapOf("original_ordinal" to "0","original_receipt_ns" to "0","original_clock_id" to PeriodicTestSource.CLOCK,
            "original_measurement_available" to true,"adapter_context" to original.context.value(),"adapter_facts" to original.facts.value(),
            "original_payloads" to original.payloads.associate {it.role to mapOf("length" to it.size.toString(),"hash" to LmtrChunk.sha(it.bytes()))})
        File(dir,"oracle.jsonl").writeText(LmtrJson.encode(oracle).toString(Charsets.UTF_8)+"\n")
        File(dir,"offered.jsonl").writeText(LmtrJson.encode(source).toString(Charsets.UTF_8)+"\n")
        engine.admit(offer);clock.set(50000000);engine.stop();val terminal=checkNotNull(engine.awaitStop(30000))
        until("plane workers not closed"){engine.workersTerminated()}
        val checked=LmtrReader(file).use {it.check(full=true,recover=!allInvalid)}
        val report=mapOf("name" to name,"codec" to codec,"recover" to !allInvalid,"expectation_met" to (terminal.finalized==allInvalid),
            "source" to "explicit synthetic 3x2 original plane and mask; no NaN normalization", "profile" to "analysis",
            "period_ns" to FiveHertzSampling.PERIOD_NS.toString(),"snapshot" to fieldSnapshot(engine.snapshot()),"periodic" to engine.periodicSnapshot()!!.report(),
            "result" to mapOf("finalized" to terminal.finalized,"status" to terminal.status,"reason" to terminal.reason,"failure" to terminal.failure),
            "kotlin" to checked,"file_sha256" to hash(file))
        write(File(dir,"run-result.json"),report);return report
    }
}
