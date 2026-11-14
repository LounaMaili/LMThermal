package org.lmthermal.field

import com.sun.management.ThreadMXBean
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import org.lmthermal.recording.*
import java.io.File
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Fresh-process HotSpot load comparable to the pinned Full stress probe: 50 producer-only
 * callbacks, then 200 callbacks at requested 25 Hz per codec. Only the explicit profile/policy
 * changes; this does not isolate those two effects, emulate Compose/USB, or measure Pixel RSS.
 * Natural GC and occupied heap are sampled. No forced GC, favorable retry or enlarged queue.
 */
object AnalysisFiveHzCadenceProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1);val root=File(args[0]);check(root.mkdirs())
        val input=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use {it.readBytes()}
        val inputHash=LmtrChunk.sha(input)
        val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),
            UUID.randomUUID().toString(),UUID.randomUUID().toString())
        val allocated=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(allocated.isThreadAllocatedMemorySupported);allocated.isThreadAllocatedMemoryEnabled=true
        fun gc()=ManagementFactory.getGarbageCollectorMXBeans().associate {it.name to
            mapOf("collections" to it.collectionCount.toString(),"time_ms" to it.collectionTime.toString())}
        fun until(deadline:Long) {while(true){val delay=deadline-System.nanoTime();if(delay<=0)return;TimeUnit.NANOSECONDS.sleep(delay)}}
        fun source(n:Long):Ht301AcquiredObservation {
            val frame=Ht301Frame.parse(input);val receipt=System.nanoTime()
            val result=MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY,frame,n,receipt/1000000)
            check(result.measurement!=null)
            return Ht301AcquiredObservation(identity,n,n,receipt,frame,frame.inspect(),SessionState.RADIOMETRIC_READY,result)
        }
        val baseIntervals=FieldDistribution();val baseStart=System.nanoTime();var last=baseStart
        repeat(50) {n->until(baseStart+n*40000000L);source(n.toLong());val at=System.nanoTime()
            if(n>0)baseIntervals.add(at-last);last=at}
        val baseline=mapOf("observations" to 50,"elapsed_ns" to (System.nanoTime()-baseStart).toString(),"intervals" to baseIntervals.report())
        val reports=mutableListOf<Obj>()
        for(codec in 0..1) {
            val dir=File(root,"analysis-$codec");check(dir.mkdir())
            val clock=FieldClock(System::nanoTime,identity.receiptClockId)
            val original=Ht301RecordingAdapter(identity,RecordingProfile.ANALYSIS)
            val oracle=File(dir,"oracle.jsonl").bufferedWriter()
            val adapter=FieldOracleAdapter(original,HtFieldOracle::capture,{row->
                oracle.write(LmtrJson.encode(row).toString(Charsets.UTF_8));oracle.newLine()},System::nanoTime)
            val codecTimes=FieldDistribution();val encodeTimes=FieldDistribution();val appendTimes=FieldDistribution()
            val intervals=FieldDistribution();val late=FieldDistribution();val admissionTimes=FieldDistribution()
            val limits=FieldPreflight(RecordingProfile.ANALYSIS,10,null,codec).limits
            val beforeGc=gc();val start=System.nanoTime()
            val engine=RecorderEngine(clock.header(),listOf(original.stream.value()),RecordingProfile.ANALYSIS,limits,
                LocalRecorderBackend(File(dir,"capture.lmtr")),codec,now=System::nanoTime,
                codecMetric={ns,_,_->codecTimes.add(ns)},sealMetric={encode,append,_,_->encodeTimes.add(encode);appendTimes.add(append)},
                sampling=FiveHertzSampling(start))
            var count=0;var selected=0;var nonselected=0;var refused:String?=null;var memoryStopped=false
            var peakOccupied=0L;var minHeadroom=Long.MAX_VALUE;last=start
            val allocationBefore=allocated.getThreadAllocatedBytes(Thread.currentThread().id)
            try {
                repeat(200) {n->
                    if(refused!=null || memoryStopped)return@repeat
                    val deadline=start+n*40000000L;until(deadline)
                    val value=source(n.toLong());val at=System.nanoTime()
                    if(n>0)intervals.add(at-last);last=at;late.add(maxOf(0,at-deadline))
                    val begin=System.nanoTime();val decision=engine.admit(adapter.offer(value));admissionTimes.add(System.nanoTime()-begin)
                    count++
                    when(decision) {
                        Admission.ACCEPTED,Admission.PREPARATION_OVERLOAD -> selected++
                        Admission.NOT_SELECTED -> nonselected++
                        else -> refused=decision.name
                    }
                    val runtime=Runtime.getRuntime();val occupied=runtime.totalMemory()-runtime.freeMemory()
                    peakOccupied=maxOf(peakOccupied,occupied);minHeadroom=minOf(minHeadroom,runtime.maxMemory()-occupied)
                    if(minHeadroom<FieldSafety.HEAP_MARGIN_BYTES)memoryStopped=true
                }
                val intakeEnd=System.nanoTime();val stop=System.nanoTime();engine.stop()
                val result=checkNotNull(engine.awaitStop(30000));val stopNs=System.nanoTime()-stop
                val exitDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(!engine.workersTerminated()){check(System.nanoTime()<exitDeadline);Thread.sleep(2)}
                val snapshot=engine.snapshot();val periodic=engine.periodicSnapshot()!!
                File(dir,"preflight.json").writeBytes(LmtrJson.encode(FieldPreflight(RecordingProfile.ANALYSIS,10,null,codec).report()+mapOf("physical" to false)))
                File(dir,"run-result.json").writeBytes(LmtrJson.encode(mapOf("finalized" to result.finalized,"status" to result.status,
                    "reason" to result.reason,"failure" to result.failure,"snapshot" to fieldSnapshot(snapshot),"periodic" to periodic.report())))
                reports.add(mapOf("codec" to codec,"finalized" to result.finalized,"status" to result.status,"failure" to result.failure,
                    "requested_source_fps" to 25,"requested_recording_hz" to 5,"period_ns" to FiveHertzSampling.PERIOD_NS.toString(),
                    "observations_offered" to count,"selected_admissions" to selected,"not_selected_admissions" to nonselected,
                    "refused_admission" to refused,"memory_guard_stopped" to memoryStopped,"intake_elapsed_ns" to (intakeEnd-start).toString(),
                    "producer_intervals" to intervals.report(),"schedule_lateness" to late.report(),"admission" to admissionTimes.report(),
                    "codec_wall" to codecTimes.report(),"seal_encode" to encodeTimes.report(),"append" to appendTimes.report(),
                    "stop_ns" to stopNs.toString(),"source_thread_allocated_bytes" to
                        (allocated.getThreadAllocatedBytes(Thread.currentThread().id)-allocationBefore).toString(),
                    "java_occupied_sampled_peak_bytes" to peakOccupied.toString(),"minimum_headroom_bytes" to minHeadroom.toString(),
                    "gc_before" to beforeGc,"gc_after" to gc(),"snapshot" to fieldSnapshot(snapshot),"periodic" to periodic.report()))
            } finally {engine.stop();engine.awaitStop(30000);oracle.close()}
            File(root,"stress.json").writeBytes(LmtrJson.encode(mapOf("baseline" to baseline,"cases" to reports,
                "scope" to "finite saved fixture HotSpot replay; profile and policy both change versus old Full; no ART/USB/Compose/RSS claim",
                "heap_sampling" to "occupied, not live retained; each callback, natural GC; peaks may be missed",
                "java" to System.getProperty("java.runtime.version"),"java_max_bytes" to Runtime.getRuntime().maxMemory().toString(),
                "forced_gc" to false,"source_fixture_sha256" to inputHash,"source_unchanged" to (LmtrChunk.sha(input)==inputHash))))
            // Preserve an adverse result and stop, rather than hiding it by continuing/repeating.
            check(refused==null && !memoryStopped && reports.last()["finalized"]==true){"Failure retained in stress.json"}
        }
    }
}
