package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*

/** Deliberate high-contention host stress, not the actual one-Hz field frequency or ART cost.
 * Existing callbacks are identical in both arms; only the scalar reader is active/inactive.
 */
object MemoryEventContentionProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2 && args[0] in listOf("on","off"));val active=args[0]=="on";val destination=File(args[1]);check(!destination.exists())
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean;bean.isThreadAllocatedMemoryEnabled=true
        val m=OwnedThermalMeasurement(NativeImageGeometry(1,1),floatArrayOf(20f),1,1,
            MeasurementProvenance(CameraModuleId("test"),"synthetic",TemperatureProvenanceKind.SIMULATED))
        val counters=PresentationCounters();val samples=mutableListOf<Obj>()
        repeat(12) {iteration->
            val gate=CountDownLatch(1);val errors=AtomicReference<Throwable?>();val allocated=LongArray(2);val scratch=LongArray(2)
            val begin=System.nanoTime()
            val producer=Thread {
                try {gate.await();val id=Thread.currentThread().id;val before=bean.getThreadAllocatedBytes(id)
                    repeat(10000){counters.event("request",m,0);counters.event("started",m,0);counters.event("completed",m,1)}
                    allocated[0]=bean.getThreadAllocatedBytes(id)-before
                }catch(t:Throwable){errors.compareAndSet(null,t)}
            }
            val observer=Thread {
                try {gate.await();val id=Thread.currentThread().id;val before=bean.getThreadAllocatedBytes(id)
                    repeat(10000){if(active)counters.memoryEventSequences(scratch,0)else scratch[0]=it.toLong()}
                    allocated[1]=bean.getThreadAllocatedBytes(id)-before
                }catch(t:Throwable){errors.compareAndSet(null,t)}
            }
            producer.start();observer.start();gate.countDown();producer.join(30000);observer.join(30000)
            check(!producer.isAlive && !observer.isAlive);errors.get()?.let {throw it}
            if(iteration>=4)samples.add(mapOf("wall_ns" to (System.nanoTime()-begin).toString(),"producer_events" to 30000,
                "reader_iterations" to 10000,"producer_allocated_bytes" to allocated[0].toString(),"reader_allocated_bytes" to allocated[1].toString()))
        }
        destination.writeBytes(LmtrJson.encode(mapOf("mode" to args[0],"samples" to samples,"forced_gc" to false,
            "scope" to "Escaped two-worker HotSpot high-contention stress; startup/join included, not an ART or one-Hz overhead bound")))
    }
}
