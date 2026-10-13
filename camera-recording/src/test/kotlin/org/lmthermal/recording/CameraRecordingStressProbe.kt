package org.lmthermal.recording

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import java.io.File
import java.util.concurrent.TimeUnit

/** Finite real-time host simulation. Cadence/source/UI are synthetic, never HT-301/Pixel8 throughput. */
object CameraRecordingStressProbe {
    @JvmStatic fun main(args:Array<String>) {
        val output=File(args[0]);check(output.mkdirs());val seconds=args[1].toInt();require(seconds in 2..180)
        val producer=SimulatedRecordingModule(width=13,height=7);val identity=testIdentity(producer)
        val session=RecordingTestSession(CameraObservationHub(identity),object:CameraDeviceIdentity{override val key="synthetic_device"})
        val adapter=RecordingTestAdapter(identity,producer);val binding=CameraRecorderBinding<Unit>{adapter}
        binding.opened(session);val file=File(output,"source-25hz.lmtr")
        val acknowledgments=mutableListOf<Obj>();val local=LocalRecorderBackend(file)
        val backend=object:RecorderBackend { override val destination=file
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                val before=System.nanoTime();val r=local.append(type,ordinal,previous,body)
                acknowledgments.add(mapOf("type" to type,"ordinal" to ordinal.toString(),"ack_ns" to System.nanoTime().toString(),
                    "sync_duration_ns" to (System.nanoTime()-before).toString()));return r
            }
            override fun close()=local.close()
        }
        val engine=testEngine(producer,file,backend=backend);check(binding.start(engine))
        val expected=mutableListOf<Obj>();val admissions=mutableListOf<Long>();var preview=0;var paletteChanges=0;var roiChanges=0
        val count=seconds*25;val start=System.nanoTime()
        for(n in 0 until count) {
            val deadline=start+n*40000000L
            while(System.nanoTime()<deadline) { val delay=deadline-System.nanoTime();if(delay>0)TimeUnit.NANOSECONDS.sleep(delay) }
            var original=producer.observation(n.toLong(),calibration=1+n/250,masked=n%11==0)
            if(n%80==25) {
                val facts=original.facts.value()+mapOf("measurement" to mapOf("status" to "unavailable","reason" to "simulated_transient"),
                    "availability" to mapOf("temperature" to mapOf("status" to "unavailable","reason" to "simulated_transient")))
                original=RecordingObservation(producer.id,n.toLong(),producer.stream,original.context.value(),facts,
                    original.payloads.filter { it.role !in listOf("temperature","validity") })
            }
            val receipt=System.nanoTime();val begin=receipt
            session.hub.publish { ordinal -> RecordingTestObservation(identity,original,ordinal,receipt) }
            admissions.add(System.nanoTime()-begin)
            val prepared=RecordingProfiles.select(original,RecordingProfile.FULL)
            expected.add(mapOf("stream_id" to producer.id,"sequence" to n.toString(),"session_id" to identity.sessionId,
                "context_hash" to LmtrChunk.sha(LmtrJson.encode(original.context.value())),
                "facts" to (original.facts.value()+mapOf("receipt" to RecordingSourceMetadata.receipt(identity,receipt),
                    "source_evidence" to mapOf("module_source_sequence" to (n*2L).toString()))).filterKeys {
                        it in listOf("receipt","acquisition","device_sequence","scheduled","source_evidence") },
                "intake_lower_monotonic_ns" to receipt.toString(),
                "intake_upper_monotonic_ns" to (receipt+admissions.last()).toString(),
                "payloads" to prepared.payloads.associate { it.role to mapOf("hash" to LmtrChunk.sha(it.bytes()),"length" to it.size.toString()) }))
            if(n%2==0){session.presentation(n.toLong());preview++}
            if(n%17==0)paletteChanges++ // Separate test presentation state: never used by source/recording.
            if(n%13==0)roiChanges++
        }
        val beforeStop=System.nanoTime();binding.stop();val result=binding.pendingStop()!!.get(30,TimeUnit.SECONDS);val stopNs=System.nanoTime()-beforeStop
        check(result.finalized){result.toString()};val elapsed=System.nanoTime()-start;val snapshot=engine.snapshot()
        val full=LmtrReader(file).use { it.check(full=true) }
        // Engineering expectations are JSONL, outside the bounded wire-JSON grammar. Do not
        // weaken that grammar to serialize duration-sized diagnostic hash inventories.
        File(output,"source-expected.jsonl").bufferedWriter().use { writer ->
            for(row in expected){writer.write(String(LmtrJson.encode(row)));writer.newLine()}
        }
        val cases=listOf(mapOf("name" to "source-25hz","file" to file.name,"sha256" to LmtrChunk.sha(file.readBytes()),
            "kotlin" to full.filterKeys { it in listOf("status","integrity_scope","summary","declared_end_status") },
            "expected_jsonl" to "source-expected.jsonl",
            "expected_jsonl_sha256" to LmtrChunk.sha(File(output,"source-expected.jsonl").readBytes())))
        File(output,"source-manifest.json").writeBytes(LmtrJson.encode(mapOf("cases" to cases)))
        fun percentile(values:List<Long>,fraction:Double)=values.sorted()[(values.size-1).times(fraction).toInt()].toString()
        val report=mapOf("scope" to "finite 13x7 non-USB host source simulation, not hardware throughput",
            "duration_seconds" to seconds,"source_observations" to count,"source_nominal_fps" to 25,
            "elapsed_ns" to elapsed.toString(),"presentation_emissions" to preview,"palette_changes" to paletteChanges,"roi_changes" to roiChanges,
            "accepted" to snapshot.accepted.toString(),"acknowledged" to snapshot.acknowledged.toString(),"preparation_drops" to snapshot.preparationDrops.toString(),
            "writer_drops" to snapshot.writerDrops.toString(),"chunks" to snapshot.chunks.toString(),"checkpoints" to snapshot.checkpoints.toString(),
            "max_preparation_queued_bytes" to snapshot.maxPreparationQueuedBytes.toString(),"max_writer_queued_bytes" to snapshot.maxWriterQueuedBytes.toString(),
            "max_preparation_in_flight_bytes" to snapshot.maxPreparationInFlightBytes.toString(),
            "max_writer_in_flight_bytes" to snapshot.maxWriterInFlightBytes.toString(),"max_pending_bytes" to snapshot.maxPendingBytes.toString(),
            "admission_p50_ns" to percentile(admissions,.5),"admission_p95_ns" to percentile(admissions,.95),"admission_max_ns" to admissions.max().toString(),
            "stop_ns" to stopNs.toString(),"file_bytes" to file.length().toString(),"acknowledgments" to acknowledgments,
            "summary" to full["summary"],"accuracy_warning" to NativeEquivalentThermometry.WARNING)
        File(output,"stress-summary.json").writeBytes(LmtrJson.encode(report))
        // Separate copy-cost observation, using an existing sanitized fixture evaluated once before timing.
        val f=Ht301Frame.parse(CameraRecordingStressProbe::class.java.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes())
        val m=MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY,f,99,42)
        val source=Ht301AcquiredObservation(CameraSourceIdentity(Ht301ModuleProfile.metadata),0,99,42000001,f,f.inspect(),SessionState.RADIOMETRIC_READY,m)
        val ht=Ht301RecordingAdapter(source.identity);repeat(5){ht.offer(source).materialize()}
        val offerTimes=mutableListOf<Long>();val freezeTimes=mutableListOf<Long>()
        repeat(50){val at=System.nanoTime();val offer=ht.offer(source);offerTimes.add(System.nanoTime()-at)
            val begin=System.nanoTime();offer.materialize();freezeTimes.add(System.nanoTime()-begin)}
        File(output,"copy-cost.json").writeBytes(LmtrJson.encode(mapOf("samples" to 50,"fixture" to "existing sanitized room frame; not exported",
            "source_retained_bytes" to source.retainedBytes.toString(),"charged_bytes" to ht.maximumCharge.toString(),
            "offer_p50_ns" to percentile(offerTimes,.5),"offer_p95_ns" to percentile(offerTimes,.95),"offer_max_ns" to offerTimes.max().toString(),
            "freeze_p50_ns" to percentile(freezeTimes,.5),"freeze_p95_ns" to percentile(freezeTimes,.95),"freeze_max_ns" to freezeTimes.max().toString(),
            "scope" to "host synthetic copy/metadata cost; thermometry evaluated once outside timing, no SHA/compression/I/O")))
        check(snapshot.preparationDrops==0L && snapshot.writerDrops==0L && snapshot.acknowledged==count.toLong()) { "Simulation retained failure evidence" }
    }
}
