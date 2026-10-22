package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.recording.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Finite full-size saved-HT replay, independent frame owners, actual thermometry/adapter/codec/
 * validation paths at a requested25 Hz. No Compose/transport/device is instantiated. Measured
 * cadence and deadline lateness, not nominal rate, describe the result. No GC or capacity tuning.
 */
object HtRecorderStressProbe {
    @JvmStatic fun main(args:Array<String>) = run(File(args[0]))
    fun run(root:File,now:()->Long=System::nanoTime,platform:()->Obj={emptyMap()}) {
        check(root.mkdirs())
        val input=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
        val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),
            UUID.randomUUID().toString(),UUID.randomUUID().toString())
        fun source(n:Long):Ht301AcquiredObservation {
            val frame=Ht301Frame.parse(input);val receipt=now()
            val result=MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY,frame,n,receipt/1000000)
            check(result.measurement!=null)
            return Ht301AcquiredObservation(identity,n,n,receipt,frame,frame.inspect(),SessionState.RADIOMETRIC_READY,result)
        }
        fun until(deadline:Long) {while(now()<deadline){val delay=deadline-now();if(delay>0)TimeUnit.NANOSECONDS.sleep(delay)}}
        // Matched producer-only window establishes the emulator/host's ability to supply the request.
        val baseIntervals=FieldDistribution();var previous=now();val baseStart=previous
        repeat(50) {n->until(baseStart+n*40000000L);source(n.toLong());val at=now();if(n>0)baseIntervals.add(at-previous);previous=at}
        val baseline=mapOf("observations" to 50,"elapsed_ns" to (now()-baseStart).toString(),"intervals" to baseIntervals.report())
        val reports=mutableListOf<Obj>()
        fun persist() = File(root,"stress.json").writeBytes(LmtrJson.encode(mapOf("baseline" to baseline,"cases" to reports,
            "scope" to "finite_saved_HT_fixture_replay_no_Compose_USB_or_physical_admission",
            "sampling" to "occupied heap each observation; native/RSS/PSS at ~500ms; natural GC only; peaks may be missed",
            "forced_gc" to false,"warning" to NativeEquivalentThermometry.WARNING)))
        for(codec in 0..1) {
            val directory=File(root,"full-$codec");check(directory.mkdir())
            val clock=FieldClock(now,identity.receiptClockId);val original=Ht301RecordingAdapter(identity)
            val oracle=File(directory,"oracle.jsonl").bufferedWriter()
            val adapter=FieldOracleAdapter(original,HtFieldOracle::capture,{row->oracle.write(String(LmtrJson.encode(row)));oracle.newLine()},now)
            val codecTimes=FieldDistribution();val encodeTimes=FieldDistribution();val appendTimes=FieldDistribution()
            val engine=RecorderEngine(clock.header(),listOf(original.stream.value()),RecordingProfile.FULL,
                FieldPreflight(RecordingProfile.FULL,20,null,codec).limits,LocalRecorderBackend(File(directory,"capture.lmtr")),
                codec,now=now,codecMetric={ns,_,_->codecTimes.add(ns)},sealMetric={encode,append,_,_->encodeTimes.add(encode);appendTimes.add(append)})
            val intervals=FieldDistribution();val lateness=FieldDistribution();val admission=FieldDistribution()
            val peaks=mutableMapOf<String,Long>();var minimumHeadroom=Long.MAX_VALUE;var count=0;var stoppedForMemory=false;var refused:String?=null
            val before=platform();val start=now();var last=start;var lastPlatform=start
            fun sample(full:Boolean) {
                val runtime=Runtime.getRuntime();val occupied=runtime.totalMemory()-runtime.freeMemory()
                minimumHeadroom=minOf(minimumHeadroom,runtime.maxMemory()-occupied)
                peaks["java_occupied_bytes"]=maxOf(peaks["java_occupied_bytes"]?:0,occupied)
                if(full)for((key,value)in platform())if(value is Number)peaks[key]=maxOf(peaks[key]?:0,value.toLong())
            }
            try {
                for(n in 0 until 200) {
                    val deadline=start+n*40000000L;until(deadline)
                    val value=source(n.toLong());val at=now();lateness.add(maxOf(0,at-deadline))
                    if(n>0)intervals.add(at-last);last=at
                    val begin=now();val accepted=engine.admit(adapter.offer(value));admission.add(now()-begin)
                    count++
                    if(accepted !in listOf(Admission.ACCEPTED,Admission.PREPARATION_OVERLOAD)){refused=accepted.name;break}
                    sample(at-lastPlatform>=500000000L);if(at-lastPlatform>=500000000L)lastPlatform=at
                    if(minimumHeadroom<FieldSafety.HEAP_MARGIN_BYTES){stoppedForMemory=true;break}
                }
                val intakeEnd=now();val beforeStop=platform();val stop=now();engine.stop()
                val result=engine.awaitStop(30000) ?: error("bounded fixture Stop timed out")
                val stopNs=now()-stop
                // Persist the exact resource/refusal boundary before any assertion, not only a generic
                // instrumentation Check failed. An incomplete file is a recovery candidate, never success.
                File(directory,"run-result.json").writeBytes(LmtrJson.encode(mapOf("finalized" to result.finalized,
                    "status" to result.status,"reason" to result.reason,"failure" to result.failure,
                    "refused_admission" to refused,"snapshot" to fieldSnapshot(engine.snapshot()))))
                val exitDeadline=now()+5000000000L;while(!engine.workersTerminated()){check(now()<exitDeadline);Thread.sleep(5)}
                val snapshot=engine.snapshot();sample(true)
                check(snapshot.maxPreparationQueuedBytes<=4194304 && snapshot.maxWriterQueuedBytes<=4194304)
                File(directory,"preflight.json").writeBytes(LmtrJson.encode(FieldPreflight(RecordingProfile.FULL,20,null,codec).report()+mapOf("physical" to false)))
                val verdict=if(result.finalized && refused==null && !stoppedForMemory && count==200 && snapshot.acknowledged==200L && snapshot.preparationDrops==0L && snapshot.writerDrops==0L)"completed_no_drops" else "retained_failure"
                reports.add(mapOf("codec" to codec,"verdict" to verdict,"finalized" to result.finalized,
                    "reason" to result.reason,"failure" to result.failure,"refused_admission" to refused,"requested_fps" to 25,
                    "observations_offered" to count,"intake_elapsed_ns" to (intakeEnd-start).toString(),
                    "producer_intervals" to intervals.report(),"schedule_lateness" to lateness.report(),
                    "admission" to admission.report(),"codec_wall" to codecTimes.report(),
                    "seal_encode" to encodeTimes.report(),"append" to appendTimes.report(),"stop_ns" to stopNs.toString(),
                    "minimum_headroom_bytes" to minimumHeadroom.toString(),"java_max_bytes" to Runtime.getRuntime().maxMemory().toString(),
                    "occupied_native_rss_pss_peaks" to peaks.mapValues {it.value.toString()},
                    "platform_before" to before,"platform_before_stop" to beforeStop,"platform_after" to platform(),
                    "snapshot" to fieldSnapshot(snapshot),"memory_guard_stopped" to stoppedForMemory,
                    "original_input_not_mutated" to input.contentEquals(javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes())))
                persist()
            } finally {engine.stop();engine.awaitStop(30000);oracle.close()}
            // Do not launch a new configuration after an unresolved memory or engine failure.
            if(stoppedForMemory || reports.last()["finalized"]!=true)break
        }
        persist()
        check(reports.all {it["finalized"]==true}) {"Recorder failure retained in stress.json and run-result.json"}
    }
}
