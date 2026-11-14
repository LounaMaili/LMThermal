package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Real recorder files with fixed authoritative clocks; diagnostic clocks never become intake.
 * This verifies isolation/integrity, not ART performance or a safety-memory bound.
 */
class MemoryRecorderIsolationTest {
    @Test fun disabledActiveAndFailedProbesProduceIdenticalDurableInterruptedFilesForBothCodecs() {
        val root=Files.createTempDirectory("memory-isolation").toFile()
        try {
            for(codec in listOf(0,1)) {
                val module=SimulatedRecordingModule();val header=SimulatedRecordingModule.header(listOf(module),codec)
                var reference:ByteArray?=null
                for(mode in listOf("off","on","failed")) {
                    val clock=AtomicLong(0);val file=File(root,"$codec-$mode.lmtr")
                    val engine=RecorderEngine(header,listOf(module.stream),RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(file),codec,now=clock::get)
                    val diagnosticClock=AtomicLong(100)
                    val memory=BracketedMemoryWindow(1,MemoryScalarProbe {d,v->
                        if(mode=="failed")throw java.io.IOException("synthetic probe failure")
                        if(d==MemoryDomain.RECORDER){val s=engine.snapshot();v[2]=s.accepted;v[3]=s.acknowledged;MemoryProbeStatus.PARTIAL}
                        else MemoryProbeStatus.UNSUPPORTED
                    },diagnosticClock::incrementAndGet)
                    repeat(3) {index->
                        assertEquals(Admission.ACCEPTED,engine.admit(module.observation(index.toLong())))
                        val until=System.nanoTime()+10000000000L
                        while(engine.snapshot().pendingBytes==0L){check(System.nanoTime()<until);Thread.sleep(1)}
                        if(mode!="off")assertTrue(memory.sample())
                    }
                    clock.set(10000000);engine.stop("test_interruption",true)
                    val result=checkNotNull(engine.awaitStop(10000));assertTrue(result.finalized);assertEquals("interrupted",result.status)
                    assertEquals(3L,engine.snapshot().acknowledged);assertEquals(0L,engine.snapshot().preparationDrops);assertEquals(0L,engine.snapshot().writerDrops)
                    memory.requestStop();val diagnostic=StringBuilder();memory.writeTo(diagnostic)
                    LmtrReader(file).use {assertEquals("valid",it.check(full=true)["status"])}
                    val bytes=file.readBytes();if(reference==null)reference=bytes else assertArrayEquals(reference,bytes)
                }
            }
        } finally {root.deleteRecursively()}
    }
    @Test fun diagnosticWorkerBlockedOutsideRecorderCannotHoldUpStopAndFinalization() {
        val root=Files.createTempDirectory("memory-blocked").toFile();val release=CountDownLatch(1);val entered=CountDownLatch(1)
        val module=SimulatedRecordingModule();val clock=AtomicLong(0);val file=File(root,"capture.lmtr")
        val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(module),1),listOf(module.stream),RecordingProfile.FULL,
            RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(file),1,now=clock::get)
        val memory=BracketedMemoryWindow(2,MemoryScalarProbe {_,_->entered.countDown();check(release.await(10,TimeUnit.SECONDS));MemoryProbeStatus.UNSUPPORTED},System::nanoTime)
        val worker=Thread {memory.sample()}
        try {
            assertEquals(Admission.ACCEPTED,engine.admit(module.observation(0)))
            worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));memory.requestStop();assertFalse(memory.quiescent())
            clock.set(10000000);engine.stop();assertTrue(checkNotNull(engine.awaitStop(5000)).finalized)
            assertEquals(1L,engine.snapshot().acknowledged);assertFalse(memory.quiescent())
            release.countDown();worker.join(10000);assertFalse(worker.isAlive);assertTrue(memory.quiescent())
            LmtrReader(file).use {assertEquals("valid",it.check(full=true)["status"])}
        } finally {release.countDown();worker.join(10000);engine.stop();engine.awaitStop(10000);root.deleteRecursively()}
    }
}
