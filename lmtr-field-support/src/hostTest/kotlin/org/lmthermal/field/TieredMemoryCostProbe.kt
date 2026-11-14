package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.io.FileInputStream
import java.lang.management.ManagementFactory
import org.lmthermal.lmtr.*

/** Matched host-only reference/tiered costs. "linux" has no Android MemoryInfo implementation;
 * "synthetic" adds an explicit fixed CPU/8 KiB scratch workload in that domain. Neither is ART,
 * a fit to Android timings, a whole-pipeline rate nor an Android RSS/retention bound. No GC calls.
 */
object TieredMemoryCostProbe {
    @Volatile private var sink=0L
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==3 && args[0] in listOf("off","reference","tiered") && args[1] in listOf("linux","synthetic"))
        val root=File(args[2]);check(!root.exists());check(root.mkdirs())
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported);bean.isThreadAllocatedMemoryEnabled=true
        val id=Thread.currentThread().id;val runtime=Runtime.getRuntime()
        val gc=ManagementFactory.getGarbageCollectorMXBeans();val rss=BoundedProcRss()
        val calls=LongArray(MemoryDomain.entries.size)
        val probe=MemoryScalarProbe {d,v->
            calls[d.ordinal]++
            when(d) {
                MemoryDomain.JAVA->{v[0]=runtime.maxMemory();v[1]=runtime.totalMemory()-runtime.freeMemory();v[2]=v[0]-v[1]}
                MemoryDomain.RSS->FileInputStream("/proc/self/status").use {v[0]=rss.read(it)}
                MemoryDomain.GC_BEFORE,MemoryDomain.GC_AFTER->{v[0]=bean.getThreadAllocatedBytes(id);v[2]=gc.sumOf {it.collectionCount};v[3]=gc.sumOf {it.collectionTime}}
                MemoryDomain.ANDROID_MEMORY-> {
                    if(args[1]=="linux")return@MemoryScalarProbe MemoryProbeStatus.UNSUPPORTED
                    // Deliberately artificial, bounded caller work whose removed calls/allocations
                    // can be counted exactly. It does not model Android Debug or graphics storage.
                    val scratch=ByteArray(8192);var x=sink
                    repeat(200000) {n->x=(x*1664525+1013904223) xor (x ushr 7);scratch[n and 8191]=x.toByte()}
                    sink=x+scratch[4095];d.fields.indices.forEach {v[it]=0}
                }
                else->return@MemoryScalarProbe MemoryProbeStatus.UNSUPPORTED
            };MemoryProbeStatus.OK
        }
        val plan=if(args[0]=="tiered")MemoryProbePlan.TIERED else MemoryProbePlan.REFERENCE
        val samples=mutableListOf<Obj>();var last:BracketedMemoryWindow?=null
        repeat(12) {iteration->
            val allocated=bean.getThreadAllocatedBytes(id);val constructBegin=System.nanoTime()
            val w=if(args[0]=="off")null else BracketedMemoryWindow(17,probe,System::nanoTime,plan)
            val constructNs=System.nanoTime()-constructBegin;val constructBytes=bean.getThreadAllocatedBytes(id)-allocated
            val before=bean.getThreadAllocatedBytes(id);val begin=System.nanoTime()
            repeat(20) {index->var x=sink+index;repeat(20000){x=(x*1664525+1013904223) xor (x ushr 7)};sink=x
                if(w!=null)check(w.sample())}
            val wall=System.nanoTime()-begin;val bytes=bean.getThreadAllocatedBytes(id)-before
            w?.requestStop();last=w
            if(iteration>=4)samples.add(mapOf("operations" to 20,"wall_ns" to wall.toString(),"allocated_bytes" to bytes.toString(),
                "constructor_wall_ns" to constructNs.toString(),"constructor_allocated_bytes" to constructBytes.toString()))
        }
        last?.let {w->File(root,"memory.jsonl").bufferedWriter().use {w.writeTo(it)}}
        File(root,"cost.json").writeBytes(LmtrJson.encode(mapOf("mode" to args[0],"platform" to args[1],
            "scope" to "Accelerated HotSpot/Linux or explicit synthetic domain; not Android observer allocation, RSS or speedup",
            "java" to System.getProperty("java.runtime.version"),"warmups" to 4,"measured_batches" to 8,"operations_per_batch" to 20,
            "samples" to samples,"domain_calls_including_warmups" to MemoryDomain.entries.associate {it.name to calls[it.ordinal].toString()},
            "primitive_window_bytes" to BracketedMemoryWindow.PRIMITIVE_BYTES,"forced_gc" to false)))
    }
}
