package org.lmthermal.field

import java.io.File
import java.util.concurrent.locks.LockSupport
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recording.*
import org.lmthermal.recorder.*

/** HOST-ONLY selected-source adapter experiment; not wired into camera/product code.
 * Every synthetic acquisition callback is counted before selection. Only callbacks actually
 * selected for recording advance the new, explicitly named selected counter. Original module
 * ordinal, callback counter, receipt and complete evidence remain unchanged in source_evidence.
 * Idle deadline detection and selected-slot overload retention still need a production design;
 * this finite experiment cannot qualify those paths or the physical frame-liveness gate.
 */
object OfflineCadenceStudyProbe {
    private const val SECOND=1000000000L
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1);val root=File(args[0]);check(root.mkdirs())
        val reports=mutableListOf<Obj>()
        for((profile,hz) in listOf(RecordingProfile.ANALYSIS to 5,RecordingProfile.ANALYSIS to 10,
            RecordingProfile.ANALYSIS to 25,RecordingProfile.FULL to 25)) {
            val dir=File(root,"${profile.wire}-$hz");check(dir.mkdir())
            val identity=OfflineProfileSource.identity
            val adapter=OfflineProfileAdapter(identity,profile)
            val period=SECOND/hz;check(SECOND%hz==0L)
            val clock=FieldClock(System::nanoTime,identity.receiptClockId)
            val stream=adapter.stream.value()+mapOf("sampling" to mapOf("mode" to "periodic",
                "period_ns" to period.toString(),"origin_timeline_ns" to "0"),
                "extensions" to mapOf("org.lmthermal.study.selected-counter-v1" to mapOf(
                    "schema_version" to mapOf("major" to 1,"minor" to 0),"required_feature" to null,
                    "data" to mapOf("counter_domain" to "selected_callback_count","source_origin" to "fixture_replay",
                        "original_ordinal" to "source_evidence.original_acquisition_ordinal","requested_hz" to hz))))
            val limits=FieldPreflight(profile,10,null,1).limits
            val seals=mutableListOf<Obj>();val oracle=File(dir,"oracle.jsonl").bufferedWriter()
            val engine=RecorderEngine(clock.header(),listOf(stream),profile,limits,LocalRecorderBackend(File(dir,"capture.lmtr")),1,
                sealMetric={encode,append,decoded,stored->synchronized(seals){check(seals.size<1024);seals.add(mapOf(
                    "encode_ns" to encode.toString(),"append_ns" to append.toString(),"decoded_bytes" to decoded.toString(),"stored_bytes" to stored.toString()))}})
            var sourceCount=0L;var selected=0L;var notSelected=0L;var nextSlot=1L;var missed=0L
            var minHeadroom=Long.MAX_VALUE
            try {
                val sourceEnd=clock.origin+10*SECOND
                while(System.nanoTime()<sourceEnd && engine.snapshot().state=="recording") {
                    val sourceDeadline=clock.origin+(sourceCount+1)*(SECOND/25)
                    while(true) {val left=sourceDeadline-System.nanoTime();if(left<=0)break;LockSupport.parkNanos(left)}
                    val receipt=System.nanoTime();sourceCount++
                    val frame=Ht301Frame.parse(OfflineProfileSource.fixture)
                    val state=if(sourceCount==100L)SessionState.RAW14_UNSETTLED else SessionState.RADIOMETRIC_READY
                    val value=Ht301AcquiredObservation(identity,sourceCount-1,sourceCount-1,receipt,frame,frame.inspect(),state,
                        MeasurementGate.evaluate(state,frame,sourceCount-1,receipt/1000000))
                    val due=(System.nanoTime()-clock.origin)/period
                    if(due<nextSlot) {notSelected++;continue}
                    fun schedule(first:Long,last:Long=first)=mapOf("first_slot" to first.toString(),"last_slot" to last.toString(),
                        "first_deadline_ns" to (first*period).toString(),"last_deadline_ns" to (last*period).toString())
                    if(due>nextSlot) {
                        val count=due-nextSlot;missed+=count
                        check(engine.admitLoss(RecordingLoss("primary",stream,mapOf("category" to "missed_schedule","reason" to "synthetic_host_deadline_missed",
                            "count" to count.toString(),"scheduled" to schedule(nextSlot,due-1),"device_first" to null,"device_last" to null,
                            "observation_first" to null,"observation_last" to null,
                            "evidence" to mapOf("basis" to "declared_schedule","before" to null,"after" to null))))==Admission.ACCEPTED)
                    }
                    val sequence=selected++;val slot=due;nextSlot=due+1
                    val offered=adapter.offer(value);val lower=System.nanoTime()
                    val wrapped=RecordingOffer("primary",sequence,FrozenJson(stream),offered.chargedBytes) {
                        val upper=System.nanoTime();val original=offered.materialize()
                        val evidence=original.facts.value()["source_evidence"].obj()+mapOf(
                            "original_acquisition_ordinal" to value.ordinal.toString(),"selected_callback_counter" to sequence.toString())
                        val facts=original.facts.value()+mapOf("scheduled" to schedule(slot),"source_evidence" to evidence)
                        val independent=HtFieldOracle.capture(value)+mapOf("selected_sequence" to sequence.toString(),
                            "selected_slot" to slot.toString(),"intake_lower_ns" to lower.toString(),"intake_upper_ns" to upper.toString())
                        oracle.write(LmtrJson.encode(independent).toString(Charsets.UTF_8));oracle.newLine()
                        RecordingObservation("primary",sequence,stream,original.context.value(),facts,original.payloads)
                    }
                    val admission=engine.admit(wrapped)
                    check(admission in listOf(Admission.ACCEPTED,Admission.PREPARATION_OVERLOAD))
                    val runtime=Runtime.getRuntime();val headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
                    minHeadroom=minOf(minHeadroom,headroom)
                    if(headroom<32L*1024*1024){engine.memoryPressure();break}
                }
                val stop=System.nanoTime();engine.stop();val result=engine.awaitStop(30000)
                reports.add(mapOf("profile" to profile.wire,"requested_hz" to hz,"source_callbacks" to sourceCount.toString(),
                    "selected" to selected.toString(),"intentional_not_selected" to notSelected.toString(),"missed_slots" to missed.toString(),
                    "offering_elapsed_ns" to (stop-clock.origin).toString(),"stop_elapsed_ns" to (System.nanoTime()-stop).toString(),
                    "minimum_sampled_headroom_bytes" to minHeadroom.toString(),"reserved_bytes" to limits.reservedBytes.toString(),
                    "finalized" to result?.finalized,"result_status" to result?.status,"snapshot" to fieldSnapshot(engine.snapshot()),"seals" to seals.toList()))
                check(result?.finalized==true)
            } finally {engine.stop();engine.awaitStop(30000);oracle.close()}
        }
        File(root,"report.json").writeBytes(LmtrJson.encode(mapOf("cases" to reports,"physical" to false,
            "scope" to "finite HotSpot synthetic gate replay; no USB, Compose, Android RSS or physical liveness proof",
            "production_sampler_implemented" to false,"forced_gc" to false)))
    }
}
