package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

/** Source-provided qualifications, another stream and replacement sessions stay distinct evidence. */
object QualifiedLossProbe {
    fun runCase(directory:File):Obj {
        check(directory.mkdirs());val file=File(directory,"capture.lmtr")
        val a=SimulatedRecordingModule();val b=SimulatedRecordingModule(id="secondary")
        val replacement=SimulatedRecordingModule(session="00000000-0000-4000-8000-000000000099")
        val clock=AtomicLong(0)
        val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(a,b),0),listOf(a.stream,b.stream),
            RecordingProfile.FULL,RecorderLimits(maxObservationBytes=3145728),LocalRecorderBackend(file),
            now=clock::get,experimentalLossRuns=true)
        val expected=mutableListOf<Obj>()
        fun loss(module:SimulatedRecordingModule,sequence:Long?,category:String,qualifier:String) {
            val witness=clock.addAndGet(1000)
            val facts:Obj=mapOf("category" to category,"reason" to "qualified_source_report",
                "count" to sequence?.let { "1" },"observation_first" to sequence?.toString(),"observation_last" to sequence?.toString(),
                "device_first" to null,"device_last" to null,"scheduled" to null,
                "evidence" to mapOf("basis" to if(sequence==null)"unknown" else "explicit_signal","before" to null,"after" to null),
                "extensions" to mapOf("org.example.qualification" to mapOf("schema_version" to mapOf("major" to 1,"minor" to 0),
                    "required_feature" to null,"data" to mapOf("qualifier" to qualifier))))
            assertEquals(Admission.ACCEPTED,engine.admitLoss(RecordingLoss(module.id,module.stream,facts)))
            expected.add(mapOf("stream" to module.id,"session" to module.session,"witness" to witness.toString(),"facts" to facts))
        }
        try {
            loss(a,0,"source_loss","A");loss(a,1,"writer_overload","B")
            loss(a,2,"preparation_overload","C");loss(b,5,"source_loss","other_stream")
            loss(replacement,0,"source_loss","new_session");loss(replacement,null,"interruption","unknown_count")
            engine.stop();val result=engine.awaitStop(15000) ?: error("qualified Stop timeout")
            assertTrue(result.finalized);assertEquals(0L,engine.snapshot().acknowledged)
            val report=LmtrReader(file).use { it.check(full=true) }
            assertEquals(6L,report["summary"].obj()["gap_events"].d())
            assertEquals(5L,report["summary"].obj()["known_missing_observations"].d())
            assertEquals(1L,report["summary"].obj()["unknown_gap_events"].d())
            val row:Obj=mapOf("expected" to expected,"sha256" to LmtrChunk.sha(file.readBytes()),"kotlin_report" to report)
            File(directory,"qualified.json").writeBytes(LmtrJson.encode(row));return row
        } finally {engine.stop();engine.awaitStop(15000)}
    }
    @JvmStatic fun main(args:Array<String>) { runCase(File(args.single())) }
}

class QualifiedLossTest {
    @Test fun distinctQualifiedSourceEventsSurviveEvenWhenRunPrototypeEnabled() {
        val root=Files.createTempDirectory("qualified-loss-").toFile()
        try {QualifiedLossProbe.runCase(File(root,"case"))}finally {check(root.deleteRecursively())}
    }
}
