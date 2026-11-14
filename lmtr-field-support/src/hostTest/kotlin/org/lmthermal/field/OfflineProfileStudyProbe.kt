package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import org.lmthermal.core.SessionState
import org.lmthermal.lmtr.*
import org.lmthermal.recording.*
import org.lmthermal.recorder.*

/** Finite HotSpot comparison, not an ART/RSS admission or 25 Hz product qualification.
 * Six fixture-derived synthetic offers per file include one unavailable measurement.
 * File clocks/identities are fixed simulated witnesses for exact baseline/candidate comparison.
 * Allocation scopes include freeze+selection only. Engine work retains both validations.
 */
object OfflineProfileStudyProbe {
    @Volatile private var sink=0L
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2 && args[0]=="profile") {
            "Use the pinned 4229456 runtime for the original eager baseline; do not bypass profile checks"
        }
        val root=File(args[1]);check(root.mkdirs())
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported);bean.isThreadAllocatedMemoryEnabled=true
        val reports=mutableListOf<Obj>()
        val source=OfflineProfileSource.observation(1)
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL)) {
            val adapter:CameraRecordingAdapter=Ht301RecordingAdapter(source.identity,profile)
            fun prepare():PreparedObservation=RecordingProfiles.select(adapter.offer(source).materialize(),profile)
            repeat(4) { repeat(6){sink=prepare().ownedBytes} }
            val samples=(0 until 8).map {
                val allocation=bean.getThreadAllocatedBytes(Thread.currentThread().id)
                val start=System.nanoTime()
                repeat(6) {sink=prepare().ownedBytes}
                mapOf("wall_ns" to (System.nanoTime()-start).toString(),
                    "allocated_bytes" to (bean.getThreadAllocatedBytes(Thread.currentThread().id)-allocation).toString())
            }
            val prepared=prepare()
            val files=mutableListOf<Obj>()
            for(codec in 0..1) {
                val dir=File(root,"${profile.wire}-$codec");check(dir.mkdir())
                val clock=AtomicLong(0)
                val header=FieldClock(clock::get,source.identity.receiptClockId).header()+mapOf("recording_id" to "33333333-3333-4333-8333-333333333333")
                val oracle=File(dir,"oracle.jsonl").bufferedWriter()
                val wrapped=FieldOracleAdapter(adapter,HtFieldOracle::capture,{r->oracle.write(LmtrJson.encode(r).toString(Charsets.UTF_8));oracle.newLine()},clock::get)
                val limits=FieldPreflight(profile,20,null,codec).limits
                val seals=mutableListOf<Obj>()
                val engine=RecorderEngine(header,listOf(adapter.stream.value()),profile,limits,
                    LocalRecorderBackend(File(dir,"capture.lmtr")),codec,now=clock::get,
                    sealMetric={encode,append,decoded,stored->synchronized(seals){
                        check(seals.size<16);seals.add(mapOf("encode_including_first_validation_ns" to encode.toString(),
                            "append_including_second_validation_ns" to append.toString(),"decoded_bytes" to decoded.toString(),"stored_bytes" to stored.toString()))}})
                try {
                    repeat(6) {index->
                        clock.set(index*1000000L)
                        val value=OfflineProfileSource.observation(index.toLong(),if(index==0)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY)
                        check(engine.admit(wrapped.offer(value))==Admission.ACCEPTED)
                        val timeout=System.nanoTime()+10000000000
                        while(engine.snapshot().let {it.preparationQueuedBytes+it.preparationInFlightBytes+it.writerQueuedBytes+it.writerInFlightBytes>0}) {
                            check(System.nanoTime()<timeout);Thread.sleep(2)
                        }
                    }
                    clock.set(10000000)
                    val start=System.nanoTime();engine.stop();check(engine.awaitStop(30000)?.finalized==true)
                    val stop=System.nanoTime()-start;val snapshot=engine.snapshot()
                    check(snapshot.acknowledged==6L && snapshot.preparationDrops==0L && snapshot.writerDrops==0L)
                    files.add(mapOf("codec" to codec,"file_bytes" to File(dir,"capture.lmtr").length().toString(),
                        "stop_ns" to stop.toString(),"reserved_bytes" to limits.reservedBytes.toString(),
                        "snapshot" to fieldSnapshot(snapshot),"seals" to seals.toList()))
                } finally {engine.stop();engine.awaitStop(30000);oracle.close()}
                File(dir,"preflight.json").writeBytes(LmtrJson.encode(FieldPreflight(profile,20,null,codec).report()+mapOf("physical" to false)))
            }
            reports.add(mapOf("profile" to profile.wire,"prepared_owned_bytes" to prepared.ownedBytes.toString(),
                "source_array_bytes" to source.retainedBytes.toString(),"samples" to samples,"files" to files))
        }
        File(root,"report.json").writeBytes(LmtrJson.encode(mapOf("arm" to args[0],"source" to "fixture-derived simulated owners; no new acquisition",
            "warmup" to 4,"samples" to 8,"repetitions" to 6,"forced_gc" to false,"cases" to reports,
            "java" to System.getProperty("java.runtime.version"),"max_heap_bytes" to Runtime.getRuntime().maxMemory().toString())))
    }
}
