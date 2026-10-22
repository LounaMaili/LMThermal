package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.recording.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.io.File

/** Existing sanitized room input; noncanonical engineering output, not physical acquisition or new corpus. */
object FieldOracleProbe {
    @JvmStatic fun main(args:Array<String>) {
        val root=File(args[0]);check(root.mkdirs())
        val bytes=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL)) {
            val directory=File(root,profile.wire);check(directory.mkdir())
            val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"))
            val clock=FieldClock(System::nanoTime)
            val source=Ht301RecordingAdapter(identity)
            val oracle=File(directory,"oracle.jsonl").bufferedWriter()
            val adapter=FieldOracleAdapter(source,HtFieldOracle::capture,{ row ->oracle.write(LmtrJson.encode(row).toString(Charsets.UTF_8));oracle.newLine()},clock::now)
            val engine=RecorderEngine(clock.header(),listOf(source.stream.value()),profile,FieldPreflight(profile,10,null).limits,
                LocalRecorderBackend(File(directory,"capture.lmtr")),1,now=clock::now)
            // Unready then ready, with genuine common-domain intake witnesses. No controls or sensor clock.
            repeat(3) { ordinal ->
                val frame=Ht301Frame.parse(bytes)
                val state=if(ordinal==0)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY
                val received=clock.now()
                val result=MeasurementGate.evaluate(state,frame,ordinal.toLong(),received/1000000)
                val observation=Ht301AcquiredObservation(identity,ordinal.toLong(),ordinal.toLong(),received,frame,frame.inspect(),state,result)
                check(engine.admit(adapter.offer(observation))==Admission.ACCEPTED)
                // Avoid engineering producer outrunning bounded 3 MiB charges; not a throughput benchmark.
                val deadline=System.nanoTime()+10000000000
                while(engine.snapshot().preparationQueuedBytes>0 || engine.snapshot().preparationInFlightBytes>0 || engine.snapshot().writerQueuedBytes>0){check(System.nanoTime()<deadline);Thread.sleep(2)}
            }
            engine.stop();check(engine.awaitStop(10000)!!.finalized)
            oracle.close()
            File(directory,"preflight.json").writeBytes(LmtrJson.encode(FieldPreflight(profile,10,null).report()+mapOf("physical" to false,"input" to "existing_sanitized_room_fixture")))
        }
    }
}
