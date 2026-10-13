package org.lmthermal.field

import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.util.concurrent.atomic.AtomicLong

/** Actual helpers used by the field APK, not a documentation-only estimate. */
class FieldProtocolTest {
    @Test fun knownInsufficientCapacityRefusesWithoutLoweringRoles() {
        for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL)) {
            val plan=FieldPreflight(profile,146,0);assertFalse(plan.permitted)
            assertTrue(FieldPreflight(profile,146,plan.requiredFreeBytes).permitted)
            assertFalse(FieldPreflight(profile,146,plan.requiredFreeBytes-1).permitted)
        }
    }
    @Test fun unknownCapacityIsReportedNotInvented() {
        val plan=FieldPreflight(RecordingProfile.FULL,146,null)
        assertTrue(plan.permitted);assertEquals("unknown",plan.report()["capacity_status"])
        assertNull(plan.report()["free_bytes"])
    }
    @Test fun fullRateCountsViewOnceAndKeepsBoundedReservations() {
        val plan=FieldPreflight(RecordingProfile.FULL,146,null)
        assertEquals(17484800,plan.bytesPerSecond.toInt())
        assertEquals(81788928,plan.limits.reservedBytes.toInt())
        assertEquals(4194304,plan.limits.writerBytes.toInt());assertEquals(4194304,plan.limits.preparationBytes.toInt())
        assertEquals(4194304,plan.limits.preferredChunkBytes.toInt())
        assertEquals(3145728,plan.limits.maxObservationBytes.toInt())
    }
    @Test fun finiteDurationsAndCodecsCannotBeExpandedByCaller() {
        for(seconds in listOf(0,147,Int.MAX_VALUE))assertThrows(IllegalArgumentException::class.java){FieldPreflight(RecordingProfile.FULL,seconds,null)}
        assertThrows(IllegalArgumentException::class.java){FieldPreflight(RecordingProfile.FULL,10,null,2)}
    }
    @Test fun regressionsRefuseRatherThanClampClockWitnesses() {
        val read=AtomicLong(100);val clock=FieldClock(read::get)
        read.set(101);assertEquals(101L,clock.now());read.set(100)
        assertThrows(IllegalStateException::class.java){clock.now()}
    }
    @Test fun sourceDomainChangesAndSourceRegressionsCannotReuseRun() {
        val clock=FieldClock({100})
        clock.source("first",90);clock.source("first",100)
        assertThrows(IllegalStateException::class.java){clock.source("other",101)}
        assertThrows(IllegalStateException::class.java){clock.source("first",99)}
    }
    @Test fun suspendAdvanceIsRealElapsedTimeNotInventedFramesOrUtc() {
        val read=AtomicLong(100);val clock=FieldClock(read::get)
        read.addAndGet(60000000000);assertEquals(60000000100L,clock.now())
        assertNull(clock.header()["timeline"].obj()["resolution_ns"])
        assertEquals("unknown",clock.header()["creation_time"].obj()["status"])
        LmtrMetadata.header(clock.header())
    }
    @Test fun processRecreationGetsNewDomainAndRecordingIdentity() {
        val first=FieldClock({100});val second=FieldClock({100})
        assertNotEquals(first.domainId,second.domainId)
        assertNotEquals(first.header()["recording_id"],second.header()["recording_id"])
    }
    @Test fun lateReceiptMayPrecedeOriginWithoutBecomingTimeline() {
        val clock=FieldClock({100});clock.source("original-source",90);assertEquals(100L,clock.now())
    }
    @Test fun distributionsAreBoundedAndDeclareOmittedSamples() {
        val sample=FieldDistribution(3);for(n in 1..8)sample.add(n.toLong())
        assertEquals(3,sample.report()["count"]);assertEquals("5",sample.report()["omitted"])
        assertEquals("3",sample.report()["max_ns"])
    }
    @Test fun metricHooksCannotChangeStoredBytesEvenWhenObserverThrows() {
        val directory=java.nio.file.Files.createTempDirectory("field-metric-test").toFile()
        try {
            val module=SimulatedRecordingModule();val header=SimulatedRecordingModule.header(listOf(module),1)
            var calls=0
            for(name in listOf("plain","observed")) {
                val clock=AtomicLong(0)
                val engine=RecorderEngine(header,listOf(module.stream),RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576),
                    LocalRecorderBackend(java.io.File(directory,"$name.lmtr")),1,now=clock::get,
                    sealMetric=if(name=="observed") { _,_,_,_ -> calls++;throw IllegalStateException("broken diagnostic") } else null,
                    codecMetric=if(name=="observed") { _,_,_ -> calls++;throw IllegalStateException("broken diagnostic") } else null)
                assertEquals(Admission.ACCEPTED,engine.admit(module.observation(0)))
                clock.set(10000000);engine.stop();assertTrue(engine.awaitStop(10000)!!.finalized)
            }
            assertTrue(calls>0)
            assertArrayEquals(java.io.File(directory,"plain.lmtr").readBytes(),java.io.File(directory,"observed.lmtr").readBytes())
        } finally { directory.deleteRecursively() }
    }
    @Test fun fieldFactoryMatchesActualHtRegistryIdNotPayloadNamespace() {
        val identity=org.lmthermal.camera.CameraSourceIdentity(org.lmthermal.camera.ht301.Ht301ModuleProfile.metadata)
        assertNotNull(HtFieldRoute.adapter(identity,{error("no recording before Start")},{0L}))
        val other=identity.copy(module=identity.module.copy(id=org.lmthermal.camera.CameraModuleId("other")))
        assertNull(HtFieldRoute.adapter(other,{error("unsupported module must not record")},{0L}))
    }
    @Test fun writerFailureResultDoesNotImplyDeferredOwnerReleased() {
        val directory=java.nio.file.Files.createTempDirectory("field-worker-quiescence").toFile()
        val release=java.util.concurrent.CountDownLatch(1);val entered=java.util.concurrent.CountDownLatch(1)
        val clock=AtomicLong(0);val blocked=java.util.concurrent.atomic.AtomicBoolean(false)
        val module=SimulatedRecordingModule();val file=java.io.File(directory,"partial.lmtr");val local=LocalRecorderBackend(file)
        val backend=object:RecorderBackend {
            override val destination=file
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.INDEX)throw java.io.IOException("field deterministic checkpoint failure")
                return local.append(type,ordinal,previous,body)
            }
            override fun close()=local.close()
        }
        val engine=RecorderEngine(SimulatedRecordingModule.header(listOf(module)),listOf(module.stream),RecordingProfile.FULL,
            RecorderLimits(maxObservationBytes=1048576),backend,now=clock::get,prepareHook={
                if(blocked.get()){entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))}
            })
        fun waitFor(test:()->Boolean){val until=System.nanoTime()+10000000000;while(!test()){check(System.nanoTime()<until);Thread.sleep(2)}}
        try {
            engine.admit(module.observation(0));waitFor{engine.snapshot().pendingBytes>0};clock.set(1000000000);engine.wakeTimers()
            waitFor{engine.snapshot().acknowledged==1L}
            blocked.set(true);engine.admit(module.observation(1));assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            clock.set(6000000000);engine.wakeTimers();assertFalse(engine.awaitStop(10000)!!.finalized)
            assertFalse(engine.workersTerminated()) // Oracle/ownership reservation cannot be reused yet.
            release.countDown();waitFor{engine.workersTerminated()}
            val before=LmtrChunk.sha(file.readBytes());LmtrReader(file).use { assertTrue(it.check(full=true,recover=true)["unknown_tail"]==true) }
            assertEquals(before,LmtrChunk.sha(file.readBytes()))
        } finally {release.countDown();engine.stop();engine.awaitStop(10000);directory.deleteRecursively()}
    }
}
