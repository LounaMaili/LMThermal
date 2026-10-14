package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.recording.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Six full-size existing fixture observations fill near-preferred CHUNKs under both codecs.
 * Fixed engineering identities/ticks allow baseline/candidate exact file comparison; these are
 * simulated witnesses, never sensor time. Sampling includes uncollected garbage, misses brief
 * peaks and does not replace logical admission or prove reachable live heap. No forced GC.
 */
object HtMemoryProbe {
    private const val DOMAIN="11111111-1111-4111-8111-111111111111"
    @JvmStatic fun main(args:Array<String>) { run(File(args[0])) }
    fun run(root:File,platformSample:()->Obj={emptyMap()}) {
        check(root.mkdirs())
        val input=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
        val reports=mutableListOf<Obj>()
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL))for(codec in 0..1) {
            val directory=File(root,"${profile.wire}-$codec");check(directory.mkdir())
            val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),
                "22222222-2222-4222-8222-222222222222",DOMAIN)
            val clock=AtomicLong(0)
            val header=FieldClock(clock::get,DOMAIN).header()+mapOf("recording_id" to "33333333-3333-4333-8333-333333333333")
            val limits=FieldPreflight(profile,20,null,codec).limits
            val peaks=mutableMapOf<String,Long>();var samples=0
            fun sample() {
                val runtime=Runtime.getRuntime()
                val row=mapOf("java_occupied_bytes" to (runtime.totalMemory()-runtime.freeMemory()),
                    "java_max_bytes" to runtime.maxMemory())+platformSample()
                synchronized(peaks) {samples++;for((k,v)in row)if(v is Number)peaks[k]=maxOf(peaks[k]?:0,v.toLong())}
            }
            val active=AtomicBoolean(true)
            val sampler=Thread({while(active.get()){sample();Thread.sleep(5)}},"field-memory-sample")
            sampler.start()
            val original=Ht301RecordingAdapter(identity)
            val oracle=File(directory,"oracle.jsonl").bufferedWriter()
            val adapter=FieldOracleAdapter(original,HtFieldOracle::capture,{r->oracle.write(LmtrJson.encode(r).toString(Charsets.UTF_8));oracle.newLine()},clock::get)
            val engine=RecorderEngine(header,listOf(original.stream.value()),profile,limits,
                LocalRecorderBackend(File(directory,"capture.lmtr")),codec,now=clock::get,
                codecMetric={_,_,_->sample()},sealMetric={_,_,_,_->sample()})
            var maxSource=0L
            try {
                repeat(6) {index->
                    clock.set(index*1000000L)
                    val frame=Ht301Frame.parse(input)
                    val state=if(index==0)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY
                    val result=MeasurementGate.evaluate(state,frame,index.toLong(),clock.get()/1000000)
                    val source=Ht301AcquiredObservation(identity,index.toLong(),index.toLong(),clock.get(),frame,frame.inspect(),state,result)
                    maxSource=maxOf(maxSource,source.retainedBytes)
                    check(engine.admit(adapter.offer(source))==Admission.ACCEPTED)
                    val deadline=System.nanoTime()+10000000000L
                    while(engine.snapshot().let {it.preparationQueuedBytes>0 || it.preparationInFlightBytes>0 || it.writerQueuedBytes>0 || it.writerInFlightBytes>0}) {
                        check(System.nanoTime()<deadline);Thread.sleep(2)
                    }
                }
                clock.set(10000000);engine.stop();check(engine.awaitStop(30000)?.finalized==true)
                val snapshot=engine.snapshot();check(snapshot.acknowledged==6L && snapshot.preparationDrops==0L && snapshot.writerDrops==0L)
                check(snapshot.maxPreparationQueuedBytes<=limits.preparationBytes && snapshot.maxWriterQueuedBytes<=limits.writerBytes)
                sample()
                reports.add(mapOf("profile" to profile.wire,"codec" to codec,"observations" to 6,
                    "source_owner_array_bytes" to maxSource.toString(),"reserved_bytes" to limits.reservedBytes.toString(),
                    "snapshot" to fieldSnapshot(snapshot),"sampled_peaks" to synchronized(peaks){peaks.mapValues {it.value.toString()}},
                    "samples" to samples,"scope" to "saved_fixture_occupied_samples_not_current_camera_or_reachable_heap"))
            } finally {engine.stop();engine.awaitStop(30000);oracle.close();active.set(false);sampler.join(10000);check(!sampler.isAlive)}
            File(directory,"preflight.json").writeBytes(LmtrJson.encode(FieldPreflight(profile,20,null,codec).report()+mapOf("physical" to false)))
        }
        File(root,"memory.json").writeBytes(LmtrJson.encode(mapOf("cases" to reports,"forced_gc" to false)))
    }
}
