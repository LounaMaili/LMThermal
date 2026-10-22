package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException

/** Generate disposable R4 engineering outputs through real admission, not the fixture-plan writer.
 * Sources and expected hashes are retained independently before admission. Not canonical R3 fixtures.
 */
object RecordingInteropProbe {
    @JvmStatic fun main(args:Array<String>) {
        val root=File(args.single());require(root.mkdirs())
        val cases=mutableListOf<Obj>()
        fun expected(o:PreparedObservation):Obj=mapOf("stream_id" to o.streamId,"sequence" to o.sequence.toString(),
            "session_id" to o.stream.value()["session_id"],"context_hash" to LmtrChunk.sha(LmtrJson.encode(o.context.value())),
            "payloads" to o.payloads.associate { it.role to mapOf("hash" to LmtrChunk.sha(it.bytes()),"length" to it.size.toString()) })
        fun run(name:String,profile:RecordingProfile,codec:Int,m:SimulatedRecordingModule,view:Boolean=false,
            second:SimulatedRecordingModule?=null,geometryChange:Boolean=false) {
            val modules=listOfNotNull(m,second);val file=File(root,"$name.lmtr")
            val clock=java.util.concurrent.atomic.AtomicLong(0)
            val engine=RecorderEngine(SimulatedRecordingModule.header(modules,codec,view),modules.map { it.stream },profile,RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(file),codec,now=clock::get)
            val originals=mutableListOf<PreparedObservation>()
            val observations=buildList {
                add(m.observation(0,1,masked=m.thermal,view=view));if(second!=null)add(second.observation(0))
                add(m.observation(1,2,view=view))
                if(geometryChange)add(SimulatedRecordingModule(m.id,7,3,session="00000000-0000-4000-8000-000000000066").observation(0,3))
            }
            for(o in observations){clock.addAndGet(1000000);originals.add(RecordingProfiles.select(o,profile));check(engine.admit(o)==Admission.ACCEPTED)}
            clock.set(10000000);engine.stop();val result=engine.awaitStop(10000)!!;check(result.finalized){result.toString()}
            val expected=originals.map { o -> mapOf("stream_id" to o.streamId,"sequence" to o.sequence.toString(),"session_id" to o.stream.value()["session_id"],
                "context_hash" to LmtrChunk.sha(LmtrJson.encode(o.context.value())),
                "payloads" to o.payloads.associate { it.role to mapOf("hash" to LmtrChunk.sha(it.bytes()),"length" to it.size.toString()) }) }
            cases.add(mapOf("name" to name,"file" to file.name,"sha256" to LmtrChunk.sha(file.readBytes()),"kotlin" to LmtrReader(file).use { it.check(full=true) },"engine_reopen" to result.report,"expected" to expected))
        }
        for(codec in 0..1)for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL)) {
            run("${profile.wire}-$codec",profile,codec,SimulatedRecordingModule(),view=profile==RecordingProfile.FULL)
        }
        run("independent-native-acquisition",RecordingProfile.FULL,1,SimulatedRecordingModule())
        run("temperature-only",RecordingProfile.ANALYSIS,0,SimulatedRecordingModule(width=13,height=7,native=false,acquisition=false))
        run("preview-only",RecordingProfile.VISUAL,1,SimulatedRecordingModule(thermal=false,native=false,acquisition=false,preview=true))
        run("multistream-geometry",RecordingProfile.FULL,1,SimulatedRecordingModule(),second=SimulatedRecordingModule("secondary",13,7),geometryChange=true)
        // Truncated tail after a genuine acknowledged idle chunk: both readers must recover the prefix.
        val m=SimulatedRecordingModule();val file=File(root,"torn-tail.lmtr");val base=LocalRecorderBackend(file);var chunk=0
        val b=object:RecorderBackend { override val destination=file
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.CHUNK && ++chunk==2){RandomAccessFile(file,"rw").use { it.seek(it.length());it.write(byteArrayOf(1,2,3));it.fd.sync() };throw IOException("injected torn append")}
                return base.append(type,ordinal,previous,body)
            };override fun close()=base.close() }
        val time=java.util.concurrent.atomic.AtomicLong(0)
        val e=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.FULL,RecorderLimits(maxObservationBytes=1048576),b,now=time::get)
        e.admit(m.observation(0));val deadline=System.nanoTime()+10000000000
        while(e.snapshot().pendingBytes==0L){check(System.nanoTime()<deadline);Thread.sleep(2)}
        time.set(1000000000);e.wakeTimers();while(e.snapshot().acknowledged==0L){check(System.nanoTime()<deadline);Thread.sleep(2)}
        e.admit(m.observation(1));e.stop();check(!e.awaitStop(10000)!!.finalized)
        val recovery=LmtrReader(file).use { it.check(full=true,recover=true) }
        val prefixExpected=listOf(expected(RecordingProfiles.select(m.observation(0),RecordingProfile.FULL)))
        cases.add(mapOf("name" to "torn-tail","file" to file.name,"sha256" to LmtrChunk.sha(file.readBytes()),
            "kotlin" to recovery,"verify_prefix_payloads" to true,"expected" to prefixExpected))
        val navFile=File(root,"navigation-failure.lmtr");val navBase=LocalRecorderBackend(navFile)
        val navBackend=object:RecorderBackend {
            override val destination=navFile
            override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                if(type==LmtrFraming.INDEX)throw IOException("injected navigation failure after CHUNK acknowledgment")
                return navBase.append(type,ordinal,previous,body)
            }
            override fun close()=navBase.close()
        }
        val navClock=java.util.concurrent.atomic.AtomicLong(0)
        val nav=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.FULL,
            RecorderLimits(maxObservationBytes=1048576),navBackend,now=navClock::get)
        nav.admit(m.observation(0));navClock.set(10000000);nav.stop();check(!nav.awaitStop(10000)!!.finalized)
        check(nav.snapshot().acknowledged==1L)
        cases.add(mapOf("name" to "navigation-failure","file" to navFile.name,"sha256" to LmtrChunk.sha(navFile.readBytes()),
            "kotlin" to LmtrReader(navFile).use { it.check(full=true,recover=true) },"verify_prefix_payloads" to true,
            "expected" to prefixExpected))
        // Mixed unrepresented loss must preserve an actual acknowledged source prefix, never END.
        val lostFile=File(root,"unjournaled-loss.lmtr")
        // Negotiate one complete prepared observation, including resolved policy/retention JSON.
        val cap=maxOf(m.observation(0).ownedBytes,RecordingProfiles.select(m.observation(0),RecordingProfile.FULL).ownedBytes)
        val lostClock=java.util.concurrent.atomic.AtomicLong(0);val block=java.util.concurrent.atomic.AtomicBoolean(false)
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val lost=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.FULL,
            RecorderLimits(preparationBytes=cap,writerBytes=cap,maxObservationBytes=cap,preferredChunkBytes=cap*4,controlBytes=32768),
            LocalRecorderBackend(lostFile),now=lostClock::get,prepareHook={
                if(block.get()){entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS));throw IOException("later preparation error")}
            })
        fun waitFor(predicate:()->Boolean){val end=System.nanoTime()+10000000000;while(!predicate()){check(System.nanoTime()<end);Thread.sleep(2)}}
        try {
            lost.admit(m.observation(0));waitFor{lost.snapshot().pendingBytes>0};lostClock.set(1000000000);lost.wakeTimers()
            waitFor{lost.snapshot().acknowledged==1L};block.set(true)
            lost.admit(m.observation(1));check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));lost.admit(m.observation(2))
            check(lost.admit(m.observation(3))==Admission.PREPARATION_OVERLOAD)
            check(lost.admit(m.observation(4))==Admission.REFUSED);lost.failSource("acquisition_error");val stopped=lost.stop()
            release.countDown();check(!lost.awaitStop(10000)!!.finalized);check(lost.snapshot().acknowledged==1L)
            val hash=LmtrChunk.sha(lostFile.readBytes());val prefix=LmtrReader(lostFile).use { it.check(full=true,recover=true) }
            check(prefix["unknown_tail"]==true && prefix["declared_end_status"]==null)
            check(prefix["summary"].obj()["observations"]=="1");check(stopped===lost.stop())
            check(hash==LmtrChunk.sha(lostFile.readBytes()))
            cases.add(mapOf("name" to "unjournaled-loss","file" to lostFile.name,"sha256" to hash,"recover" to true,
                "verify_prefix_payloads" to true,"expected_gaps" to emptyList<Any>(),"kotlin" to prefix,
                "expected" to prefixExpected))
        } finally {release.countDown();lost.stop()}
        // A represented failure is different: its known-count gap can honestly finalize failed.
        val failedFile=File(root,"represented-source-failure.lmtr")
        val failed=RecorderEngine(SimulatedRecordingModule.header(listOf(m)),listOf(m.stream),RecordingProfile.FULL,
            RecorderLimits(maxObservationBytes=1048576),LocalRecorderBackend(failedFile),now={0L})
        failed.admit(RecordingOffer(m.id,0,FrozenJson(m.stream),cap){throw IllegalArgumentException("bad current source")})
        val failedResult=failed.awaitStop(10000)!!;check(failedResult.finalized && failedResult.status=="failed")
        cases.add(mapOf("name" to "represented-source-failure","file" to failedFile.name,"sha256" to LmtrChunk.sha(failedFile.readBytes()),
            "kotlin" to LmtrReader(failedFile).use { it.check(full=true) },"expected" to emptyList<Any>(),
            "expected_gaps" to listOf(mapOf("category" to "source_loss","reason" to "source_loss","count" to "1",
                "observation_first" to "0","observation_last" to "0"))))
        File(root,"source-manifest.json").writeBytes(LmtrJson.encode(mapOf("cases" to cases)))
    }
}
