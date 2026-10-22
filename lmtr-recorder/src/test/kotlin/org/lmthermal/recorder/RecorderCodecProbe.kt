package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import java.io.File
import java.util.Random

/** Paired codec-only allocation/cost attribution. These synthetic byte patterns are not camera
 * captures, process peak-live bounds, source timing or a recorder admission experiment. No GC.
 */
object RecorderCodecProbe {
    @JvmStatic fun main(args:Array<String>) = run(File(args[0]))

    fun run(output:File) {
        val allocated=allocationCounter()
        val rows=ArrayList<Obj>()
        for(size in listOf(224256,442368,1769472))for(random in listOf(false,true)) {
            val raw=ByteArray(size)
            if(random)Random(size.toLong()).nextBytes(raw) else for(i in raw.indices)raw[i]=(i%127).toByte()
            val original=LmtrChunk.sha(raw)
            val reference=LmtrCodec.encode(raw,1)
            val variants=listOf<Pair<String,()->ByteArray>>(
                "original_growing_stream" to {LmtrCodec.encode(raw,1)},
                "bounded_segments" to {RecordingDeflate.encode(raw,reference.size+65536)})
            val times=variants.map { ArrayList<Long>() }
            val bytes=variants.map { ArrayList<Long>() }
            // Alternate order to avoid consistently charging just one candidate for runtime warmup.
            repeat(12) {round->
                for(index in if(round%2==0)listOf(0,1) else listOf(1,0)) {
                    val beginBytes=allocated?.invoke();val begin=System.nanoTime()
                    val result=variants[index].second()
                    val elapsed=System.nanoTime()-begin
                    val cost=beginBytes?.let {allocated!!.invoke()-it}
                    if(round>=4){times[index].add(elapsed);cost?.let {bytes[index].add(it)}}
                    check(result.contentEquals(reference))
                }
            }
            check(LmtrChunk.sha(raw)==original)
            fun distribution(values:List<Long>):Obj? {
                if(values.isEmpty())return null
                val sorted=values.sorted()
                return mapOf("p50" to sorted[sorted.size/2].toString(),"p95" to sorted.last().toString(),"max" to sorted.last().toString())
            }
            rows.add(mapOf("source_bytes" to size,"pattern" to if(random)"seeded_incompressible" else "repeated_127",
                "stored_bytes" to reference.size,"exact_byte_parity" to true,"source_unchanged" to true,
                "segment_capacity_upper_bound_bytes" to (reference.size.toLong()+65535).toString(),
                "variants" to variants.indices.map {index->mapOf("name" to variants[index].first,
                    "wall_ns" to distribution(times[index]),"thread_allocated_bytes" to distribution(bytes[index]))}))
        }
        check(!output.exists())
        output.writeBytes(LmtrJson.encode(mapOf("scope" to "paired_synthetic_codec_not_physical_or_peak_live",
            "forced_gc" to false,"warmups" to 4,"samples" to 8,"cases" to rows)))
    }

    /** ART may not expose host ThreadMXBean; absent allocation counters stay explicitly unknown. */
    private fun allocationCounter():(()->Long)? = try {
        val api=Class.forName("com.sun.management.ThreadMXBean")
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        api.getMethod("setThreadAllocatedMemoryEnabled",Boolean::class.javaPrimitiveType).invoke(bean,true)
        val read=api.getMethod("getThreadAllocatedBytes",Long::class.javaPrimitiveType)
        val id=Thread.currentThread().id
        val counter:()->Long={read.invoke(bean,id) as Long}
        counter
    } catch(_:Throwable){null}
}
