package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import java.nio.file.Files

/** Diagnostic JSON is local evidence, not an extension of the frozen file contract. */
class FieldFailureDiagnosticTest {
    @Test fun localFailureSnapshotSeparatesFirstWitnessFromTerminalResult() {
        val module=SimulatedRecordingModule()
        val file=Files.createTempDirectory("r4-failure-diagnostic-").resolve("capture.lmtr").toFile()
        val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(module)),listOf(module.stream),
            RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(file),now={0L})
        engine.failSource("initial_source_error")
        val result=engine.awaitStop(10000)!!
        assertTrue(result.toString(),result.finalized);assertEquals("failed",result.status)
        val snapshot=engine.snapshot();val diagnostic=fieldSnapshot(snapshot)
        val json=LmtrJson.parse(LmtrJson.encode(diagnostic))
        val first=json["first_failure"].obj()
        assertEquals("source",first["stage"]);assertEquals("source_failure",first["code"])
        assertEquals("none",first["exception_kind"]);assertEquals("initial_source_error",first["detail_code"])
        assertEquals("0",first["accepted"])
        assertEquals("0",first["last_intake_witness_ns"])
        for(key in listOf("stream_id","observation_sequence","record_ordinal","record_type"))assertNull(first[key])
        assertEquals(false,json["unrepresented_loss"]);assertEquals(false,first["unrepresented_loss"])
        assertSame(result.firstFailure,snapshot.firstFailure)
        assertEquals(fieldFirstFailure(result.firstFailure!!),diagnostic["first_failure"])
        assertFalse(first.keys.any {it in listOf("exception","message","stack_trace","payload")})
    }
    @Test fun firstFailureSlotContainsOnlyFixedScalarsAndBoundedIdentity() {
        val fields=RecorderFirstFailure::class.java.declaredFields.filterNot {java.lang.reflect.Modifier.isStatic(it.modifiers)}
        assertTrue(fields.isNotEmpty())
        assertTrue(fields.all {it.type.isPrimitive || it.type.isEnum || it.type==String::class.java})
    }
}
