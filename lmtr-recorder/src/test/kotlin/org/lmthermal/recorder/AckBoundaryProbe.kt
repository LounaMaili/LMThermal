package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** An uncertain backend return can leave either no CHUNK or a complete unacknowledged CHUNK. */
object AckBoundaryProbe {
    fun runCase(directory:File,codec:Int,afterDurable:Boolean):Obj {
        check(directory.mkdirs());val path=File(directory,"capture.lmtr");val base=LocalRecorderBackend(path)
        val backend=object:RecorderBackend {
            override val destination=path
            override fun close()=base.close()
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK && !afterDurable)throw IOException("injected before durable append")
                val value=base.append(type,ordinal,previous,body)
                if(type==LmtrFraming.CHUNK)throw IOException("injected after footer sync before ACK return")
                return value
            }
        }
        val module=SimulatedRecordingModule(width=32,height=16);val clock=AtomicLong(0)
        val source=module.observation(0);val expected=RecordingProfiles.select(source,RecordingProfile.FULL)
        val recorder=RecorderEngine(SimulatedRecordingModule.header(listOf(module),codec),listOf(module.stream),
            RecordingProfile.FULL,RecorderLimits(maxObservationBytes=3145728),backend,codec,now=clock::get,
            experimentalLossRuns=true)
        try {
            assertEquals(Admission.ACCEPTED,recorder.admit(source))
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(recorder.snapshot().pendingBytes==0L && System.nanoTime()<until)Thread.sleep(1)
            assertTrue(recorder.snapshot().pendingBytes>0L)
            clock.set(2000000000);recorder.wakeTimers()
            val result=recorder.awaitStop(15000) ?: error("boundary test timeout")
            while(!recorder.workersTerminated() && System.nanoTime()<until)Thread.sleep(1)
            assertTrue(recorder.workersTerminated());assertFalse(result.finalized)
            assertEquals(0L,recorder.snapshot().acknowledged)
            assertEquals(RecorderFailureStage.BACKEND_APPEND,result.firstFailure!!.stage)
            val before=LmtrChunk.sha(path.readBytes())
            val report=LmtrReader(path).use { it.check(full=true,recover=true) }
            assertEquals(if(afterDurable)1L else 0L,report["summary"].obj()["observations"].d())
            assertEquals(true,report["unknown_tail"]);assertEquals(before,LmtrChunk.sha(path.readBytes()))
            val row:Obj=mapOf("after_durable" to afterDurable,"codec" to codec,"source_sha256" to before,
                "acknowledged" to "0","kotlin_report" to report,"payloads" to expected.payloads.associate { p ->
                    p.role to mapOf("length" to p.size.toString(),"hash" to LmtrChunk.sha(p.bytes())) })
            File(directory,"boundary.json").writeBytes(LmtrJson.encode(row));return row
        } finally {recorder.stop();recorder.awaitStop(15000)}
    }
    @JvmStatic fun main(args:Array<String>) {
        val root=File(args.single());check(root.mkdirs())
        for(codec in 0..1)for(after in listOf(false,true))runCase(File(root,"$codec-$after"),codec,after)
    }
}

class AckBoundaryTest {
    @Test fun interruptionBeforeAndAfterFooterSyncCannotInventLiveAcknowledgment() {
        val root=Files.createTempDirectory("ack-boundaries-").toFile()
        try {for(codec in 0..1)for(after in listOf(false,true))AckBoundaryProbe.runCase(File(root,"$codec-$after"),codec,after)}
        finally {check(root.deleteRecursively())}
    }
    @Test fun throwingObserverCannotChangeRecorderBytesOrSuccess() {
        val root=Files.createTempDirectory("flow-failure-").toFile()
        val broken=RecorderFlowObserver { _,_,_,_,_,_,_,_,_,_,_,_,_,_,_->throw IllegalStateException("diagnostic") }
        try {for(codec in 0..1) {
            val reference=RecorderBacklogProbe.runCase(File(root,"ref-$codec"),codec,true)
            val tested=RecorderBacklogProbe.runCase(File(root,"broken-$codec"),codec,true,observer=broken)
            assertEquals(reference["file_sha256"],tested["file_sha256"])
            assertTrue(tested["flow_observer_failures"].d()>0);assertNull(tested["first_failure"])
        }} finally {check(root.deleteRecursively())}
    }
    @Test fun diagnosticTraceDoesNotChangeDefaultReferenceBytes() {
        val root=Files.createTempDirectory("trace-parity-").toFile()
        try {
            for(codec in 0..1) {
                val off=RecorderBacklogProbe.runCase(File(root,"off-$codec"),codec,true)
                val on=RecorderBacklogProbe.runCase(File(root,"on-$codec"),codec,true,withTrace=true)
                assertEquals(off["file_sha256"],on["file_sha256"])
                val report=on["flow_trace"].obj();assertEquals("0",report["omitted"])
                assertEquals("0",report["clock_failures"]);assertEquals("0",on["flow_observer_failures"])
                for(row in report["rows"].arr().map { it.arr() })if(row[0]=="PENDING_ADD")assertTrue(row[10].d()>0)
            }
        } finally {check(root.deleteRecursively())}
    }
}
