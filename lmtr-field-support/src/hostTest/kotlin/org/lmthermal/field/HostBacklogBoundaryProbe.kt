package org.lmthermal.field

import org.junit.Test
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Deterministic journal boundary, not a throughput model. A blocked preparation worker
 * leaves one conservatively charged source in flight and one queued. No large source
 * is allocated: the synthetic offer deliberately reserves the normal 3 MiB maximum.
 * The unchanged 1 MiB journal can represent 32 losses; the next must poison END.
 */
object HostBacklogBoundaryProbe {
    @JvmStatic fun main(args:Array<String>) { run(File(args.single())) }

    fun run(root:File) {
        check(root.mkdirs())
        val reports=mutableListOf<Obj>()
        for(codec in 0..1)for(aged in listOf(false,true))for(losses in listOf(32,33)) {
            val directory=File(root,"codec-$codec-aged-$aged-losses-$losses");check(directory.mkdir())
            val file=File(directory,"capture.lmtr")
            val module=SimulatedRecordingModule()
            val limits=RecorderLimits(maxObservationBytes=3L*1024*1024)
            check(limits.reservedBytes==78L*1024*1024)
            val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val clock=AtomicLong(0)
            val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(module),codec),listOf(module.stream),
                RecordingProfile.FULL,limits,LocalRecorderBackend(file),codec,now=clock::get,
                prepareHook={entered.countDown();check(release.await(10,TimeUnit.SECONDS))})
            fun offer(sequence:Long)=RecordingOffer(module.id,sequence,FrozenJson(module.stream),limits.maxObservationBytes) {
                module.observation(sequence)
            }
            try {
                check(engine.admit(offer(0))==Admission.ACCEPTED)
                check(entered.await(5,TimeUnit.SECONDS))
                check(engine.admit(offer(1))==Admission.ACCEPTED)
                repeat(losses) { n ->
                    if(aged)clock.set((n+1)*40000000L)
                    val expected=if(n<32)Admission.PREPARATION_OVERLOAD else Admission.REFUSED
                    check(engine.admit(offer(n+2L))==expected)
                }
                val blocked=engine.snapshot()
                check(blocked.controlBytes==limits.controlBytes && blocked.preparationDrops==32L)
                check(blocked.acknowledged==0L)
                if(aged)clock.addAndGet(2000000000L)
                engine.stop();release.countDown()
                val result=checkNotNull(engine.awaitStop(30000))
                val snapshot=engine.snapshot()
                check(snapshot.maxPreparationQueuedBytes<=limits.preparationBytes)
                check(snapshot.maxWriterQueuedBytes<=limits.writerBytes)
                check(result.finalized==(losses==32))
                if(losses==32) {
                    check(snapshot.acknowledged==2L && snapshot.writerDrops==0L)
                    var gaps=0
                    java.io.RandomAccessFile(file,"r").use { input ->
                        var at=0L
                        val header=SimulatedRecordingModule.header(listOf(module),codec)
                        while(at<input.length()) {
                            val record=LmtrFraming.read(input,at)
                            if(record.type==LmtrFraming.CHUNK) {
                                val entries=LmtrChunk.decode(record.body,header).metadata["entries"].arr()
                                gaps+=entries.count { it.obj()["kind"]=="gap" }
                            }
                            at+=record.length
                        }
                    }
                    check(gaps==32)
                } else check(result.failure!!.contains("Cannot certify unjournaled"))
                LmtrReader(file).use { it.check(full=true,recover=losses==33) }
                reports.add(mapOf("codec" to codec,"aged" to aged,"offered_losses" to losses,
                    "blocked_control_bytes" to blocked.controlBytes.toString(),"finalized" to result.finalized,
                    "failure" to result.failure,"snapshot" to fieldSnapshot(snapshot)))
            } finally {release.countDown();engine.stop();engine.awaitStop(30000)}
        }
        File(root,"backlog.json").writeBytes(LmtrJson.encode(mapOf("cases" to reports,
            "scope" to "synthetic journal boundary; no performance, physical RSS or aged-gap causality claim")))
    }
}

class HostBacklogBoundaryTest {
    @Test fun unchangedJournalAccountsFor32LossesAndRefusesEndAfterThe33rd() {
        HostBacklogBoundaryProbe.run(Files.createTempDirectory("lmtr-host-backlog-").resolve("cases").toFile())
    }
}
