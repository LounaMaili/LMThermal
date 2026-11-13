package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import org.lmthermal.recording.*

/** Fixed host-only attribution on an immutable fixture and one preserved physical CHUNK.
 * Main-thread allocated bytes are cumulative allocation, never reachable heap or Pixel RSS.
 * No GC request, camera, Android process, retained result history or payload output is involved.
 */
object HostWriterCostProbe {
    @Volatile private var sink=0L
    private const val WARMUP=4
    private const val SAMPLES=8
    private val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean

    /** Bounded scalar samples; only the private input body survives between invocations. */
    private fun measure(name:String,repetitions:Int,action:()->Long):Obj {
        repeat(WARMUP){repeat(repetitions){sink=sink xor action()}}
        val rows=ArrayList<Obj>(SAMPLES)
        repeat(SAMPLES) {
            val runtime=Runtime.getRuntime()
            val occupiedBefore=runtime.totalMemory()-runtime.freeMemory()
            val allocation=bean.getThreadAllocatedBytes(Thread.currentThread().id)
            val cpu=bean.currentThreadCpuTime
            val start=System.nanoTime()
            repeat(repetitions){sink=sink xor action()}
            val elapsed=System.nanoTime()-start
            val cpuElapsed=bean.currentThreadCpuTime-cpu
            val bytes=bean.getThreadAllocatedBytes(Thread.currentThread().id)-allocation
            rows.add(mapOf("wall_ns" to elapsed.toString(),"cpu_ns" to cpuElapsed.toString(),
                "allocated_bytes" to bytes.toString(),"occupied_before_bytes" to occupiedBefore.toString(),
                "occupied_after_bytes" to (runtime.totalMemory()-runtime.freeMemory()).toString()))
        }
        return mapOf("name" to name,"repetitions_per_sample" to repetitions,"samples" to rows)
    }

    /** Arguments name a read-only preserved file and a NEW numerical report; never publish its body. */
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val input=File(args[0]);val output=File(args[1]);check(!output.exists())
        require(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled=true
        val originalHash=RecordingFileHashes.sha256(input)
        var chosen:ByteArray?=null;var chosenOffset=0L;var header:Obj?=null;var maximum=0L
        RandomAccessFile(input,"r").use { file ->
            var offset=0L
            while(offset<file.length()) {
                val record=LmtrFraming.read(file,offset)
                if(record.type==LmtrFraming.HEADER)header=LmtrJson.parse(record.body)
                if(record.type==LmtrFraming.CHUNK) {
                    val metadata=LmtrChunk.metadata(record.body).first
                    val size=metadata["blocks"].arr().sumOf { it.obj()["decoded"].d() }
                    if(size>maximum){maximum=size;chosen=record.body;chosenOffset=offset}
                }
                offset+=record.length
            }
        }
        val body=checkNotNull(chosen);val h=checkNotNull(header)
        val metadata=LmtrChunk.metadata(body).first
        val fixture=javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use { it.readBytes() }
        val frame=Ht301Frame.parse(fixture)
        val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated"),
            "22222222-2222-4222-8222-222222222222","11111111-1111-4111-8111-111111111111")
        val source=Ht301AcquiredObservation(identity,0,0,1000000000L,frame,
            frame.inspect(),SessionState.RADIOMETRIC_READY,
            MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY,frame,0,1000))
        val adapter=Ht301RecordingAdapter(identity)
        val cases=listOf(
            measure("fixture_freeze_and_full_select",6) {
                val prepared=RecordingProfiles.select(adapter.offer(source).materialize(),RecordingProfile.FULL)
                prepared.ownedBytes+prepared.payloads.sumOf { it.role.length }
            },
            measure("physical_metadata_complete_closure",4) {
                LmtrMetadata.chunk(metadata,h);metadata["entries"].arr().size.toLong()
            },
            measure("physical_chunk_two_independent_full_validations",2) {
                val first=LmtrChunk.validate(body,h)
                val second=LmtrChunk.validate(body,h)
                first["entries"].arr().size.toLong()+second["entries"].arr().size
            })
        check(RecordingFileHashes.sha256(input)==originalHash)
        val result=mapOf("host_only" to true,"forced_gc" to false,"java" to System.getProperty("java.runtime.version"),
            "vm" to System.getProperty("java.vm.name"),"max_heap_bytes" to Runtime.getRuntime().maxMemory().toString(),
            "available_processors" to Runtime.getRuntime().availableProcessors(),"warmup_samples" to WARMUP,
            "input_sha256" to originalHash,"physical_chunk_offset" to chosenOffset.toString(),
            "physical_chunk_decoded_bytes" to maximum.toString(),"physical_chunk_metadata_bytes" to LmtrJson.encode(metadata).size,
            "cases" to cases,"gc" to ManagementFactory.getGarbageCollectorMXBeans().map {
                mapOf("name" to it.name,"count" to it.collectionCount.toString(),"time_ms" to it.collectionTime.toString()) },
            "source_unchanged" to true,"scope" to "current-thread HotSpot allocation and inclusive wall/CPU; not ART or live-heap/RSS proof")
        output.writeBytes(LmtrJson.encode(result))
        println("Host attribution completed; no payload written")
    }
}

/** Streaming source identity, outside every measured interval. */
private object RecordingFileHashes {
    fun sha256(file:File):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val scratch=ByteArray(65536)
            while(true){val count=input.read(scratch);if(count<0)break;digest.update(scratch,0,count)}
        }
        val alphabet="0123456789abcdef";val bytes=digest.digest();val chars=CharArray(64)
        for(i in bytes.indices){val v=bytes[i].toInt() and 255;chars[i*2]=alphabet[v ushr 4];chars[i*2+1]=alphabet[v and 15]}
        return String(chars)
    }
}
