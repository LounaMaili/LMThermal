package org.lmthermal.field

import org.lmthermal.lmtr.*
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest

/** Exact same saved full-size CHUNKs on the retained baseline and candidate classpaths.
 * Reflection allows this one probe to run against the old library (decode) and new library
 * (validate), rather than reconstructing an alleged baseline implementation. No GC is forced.
 * Host per-thread allocation is optional; ART process counters include harness/runtime work.
 */
object RecorderAllocationProbe {
    @JvmStatic fun main(args:Array<String>) = run(File(args[0]), File(args[1]))

    fun run(root:File, output:File, platform:()->Obj={emptyMap()}) {
        val method = LmtrChunk::class.java.methods.firstOrNull { it.name=="validate" && it.parameterCount==3 }
            ?: LmtrChunk::class.java.methods.single { it.name=="decode" && it.parameterCount==3 }
        val allocation = hostAllocationCounter()
        val reports = mutableListOf<Obj>()
        for (profile in listOf("analysis","native","full")) for (codec in 0..1) {
            val file=File(root,"$profile-$codec/capture.lmtr")
            val sourceHash=hashFile(file)
            var header:Obj=emptyMap();var body=ByteArray(0)
            RandomAccessFile(file,"r").use { input ->
                var at=0L
                while(at<input.length()) {
                    val record=LmtrFraming.read(input,at);at+=record.length
                    if(record.type==LmtrFraming.HEADER)header=LmtrJson.parse(record.body)
                    if(record.type==LmtrFraming.CHUNK && record.body.size>body.size)body=record.body
                }
            }
            check(body.isNotEmpty())
            fun verify() { try { method.invoke(LmtrChunk,body,header,134217728L) }
                catch(error:InvocationTargetException) { throw error.targetException } }
            repeat(4){verify()}
            val before=platform();val wall=mutableListOf<Long>();val allocated=mutableListOf<Long>()
            repeat(8) {
                val bytes=allocation?.invoke();val begin=System.nanoTime();verify();wall.add(System.nanoTime()-begin)
                if(bytes!=null)allocated.add(allocation!!.invoke()-bytes)
            }
            val after=platform();check(sourceHash==hashFile(file))
            val meta=LmtrChunk.metadata(body).first
            val blocks=meta["blocks"].arr().map {it.obj()}
            val decoded=blocks.sumOf {it["decoded"].d()};val stored=blocks.sumOf {it["stored"].d()}
            val logical=meta["entries"].arr().map {it.obj()}.filter {it["kind"]=="observation"}
                .sumOf {it["payloads"].arr().sumOf {p->p.obj()["length"].d()}}
            val deflated=blocks.filter {it["codec"].i()==1}.sumOf {it["decoded"].d()}
            reports.add(mapOf("profile" to profile,"codec" to codec,"mode" to method.name,
                "body_sha256" to LmtrChunk.sha(body),"source_sha256" to sourceHash,
                "body_bytes" to body.size.toString(),"stored_bytes" to stored.toString(),
                "decoded_bytes" to decoded.toString(),"logical_bytes_including_views" to logical.toString(),
                "old_two_boundaries_payload_array_allocation" to (2*(stored+decoded+logical)).toString(),
                "new_two_boundaries_payload_array_allocation" to (2*deflated).toString(),
                "wall_ns" to distribution(wall),"thread_allocated_bytes" to distribution(allocated),
                "platform_before" to before,"platform_after" to after))
        }
        output.writeBytes(LmtrJson.encode(mapOf("cases" to reports,"samples" to 8,"warmups" to 4,
            "forced_gc" to false,"scope" to "saved_fixture_validation_cost_not_Java_live_heap_or_physical_recording")))
    }

    /** Streaming file hashes exclude a duration-sized copy from the measured work. */
    private fun hashFile(file:File):String {
        val hash=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536)
        file.inputStream().use { input -> while(true){val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)} }
        return hash.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    private fun distribution(values:List<Long>):Obj {
        val sorted=values.sorted()
        fun at(fraction:Double)=if(sorted.isEmpty())null else sorted[((sorted.size-1)*fraction).toInt()].toString()
        return mapOf("count" to sorted.size,"p50" to at(.5),"p95" to at(.95),"max" to at(1.0))
    }
    /** No java.management linkage in ART. Unavailable counters stay absent, never zero. */
    private fun hostAllocationCounter():(()->Long)? = try {
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val api=Class.forName("com.sun.management.ThreadMXBean")
        check(api.getMethod("isThreadAllocatedMemorySupported").invoke(bean)==true)
        api.getMethod("setThreadAllocatedMemoryEnabled",Boolean::class.javaPrimitiveType).invoke(bean,true)
        val read=api.getMethod("getThreadAllocatedBytes",Long::class.javaPrimitiveType)
        val id=Thread.currentThread().id
        val sample:()->Long={read.invoke(bean,id) as Long};sample
    } catch(_:Exception) {null}
}
