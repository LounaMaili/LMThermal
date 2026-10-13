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
        cases.add(mapOf("name" to "torn-tail","file" to file.name,"sha256" to LmtrChunk.sha(file.readBytes()),"kotlin" to recovery,"expected" to emptyList<Any>()))
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
            "kotlin" to LmtrReader(navFile).use { it.check(full=true,recover=true) },"expected" to emptyList<Any>()))
        File(root,"source-manifest.json").writeBytes(LmtrJson.encode(mapOf("cases" to cases)))
    }
}
