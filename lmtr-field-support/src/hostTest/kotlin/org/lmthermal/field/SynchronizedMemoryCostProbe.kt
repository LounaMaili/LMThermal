package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.io.FileInputStream
import java.lang.management.ManagementFactory
import org.lmthermal.lmtr.*

/** Host-only active/off experiment. Linux RSS/HotSpot counters do not stand in for Android Debug,
 * graphics, ART allocation or the field weak-model adapter. No forced GC, device or timing policy.
 */
object SynchronizedMemoryCostProbe {
    @Volatile private var sink=0L
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2 && args[0] in listOf("off","on"));val active=args[0]=="on"
        val destination=File(args[1]);check(!destination.exists());check(destination.mkdirs())
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported);bean.isThreadAllocatedMemoryEnabled=true
        val id=Thread.currentThread().id;val runtime=Runtime.getRuntime();val collectors=ManagementFactory.getGarbageCollectorMXBeans()
        val rss=BoundedProcRss()
        val probe=MemoryScalarProbe {d,v ->
            when(d) {
                MemoryDomain.JAVA->{v[0]=runtime.maxMemory();v[1]=runtime.totalMemory()-runtime.freeMemory();v[2]=v[0]-v[1]}
                MemoryDomain.RSS->FileInputStream("/proc/self/status").use {v[0]=rss.read(it)}
                MemoryDomain.GC_BEFORE,MemoryDomain.GC_AFTER->{v[0]=bean.getThreadAllocatedBytes(id);v[2]=collectors.sumOf {it.collectionCount};v[3]=collectors.sumOf {it.collectionTime}}
                else->return@MemoryScalarProbe MemoryProbeStatus.UNSUPPORTED
            };MemoryProbeStatus.OK
        }
        val samples=mutableListOf<Obj>();var last:BracketedMemoryWindow?=null
        repeat(12) {iteration ->
            val beforeCreate=bean.getThreadAllocatedBytes(id);val createStart=System.nanoTime()
            val window=if(active)BracketedMemoryWindow(17,probe,System::nanoTime) else null
            val constructNs=System.nanoTime()-createStart;val constructBytes=bean.getThreadAllocatedBytes(id)-beforeCreate
            val before=bean.getThreadAllocatedBytes(id);val begin=System.nanoTime()
            repeat(16) {operation->
                var value=sink+operation;repeat(20000){value=(value*1664525+1013904223) xor (value ushr 7)};sink=value
                if(window!=null)check(window.sample())
            }
            val elapsed=System.nanoTime()-begin;val allocated=bean.getThreadAllocatedBytes(id)-before
            window?.requestStop();last=window
            if(iteration>=4)samples.add(mapOf("operations" to 16,"wall_ns" to elapsed.toString(),"allocated_bytes" to allocated.toString(),
                "constructor_wall_ns" to constructNs.toString(),"constructor_allocated_bytes" to constructBytes.toString()))
        }
        last?.let {w->File(destination,"memory.jsonl").bufferedWriter().use {w.writeTo(it)}}
        File(destination,"cost.json").writeBytes(LmtrJson.encode(mapOf("mode" to args[0],"scope" to "Accelerated HotSpot/Linux caller allocation, not one-Hz Android/graphics/RSS or physical cadence",
            "java" to System.getProperty("java.runtime.version"),"samples" to samples,"forced_gc" to false,
            "warmups" to 4,"primitive_window_bytes" to BracketedMemoryWindow.PRIMITIVE_BYTES,
            "proc_scratch_bytes" to BoundedProcRss.BUFFER_BYTES+BoundedProcRss.LINE_BYTES,
            "missing_platforms" to listOf("Android Debug.MemoryInfo","ART GC counters","native allocator","graphics","real published roles","real recorder"))))
        println("Finite active/off host probe completed; no Android qualification")
    }
}
