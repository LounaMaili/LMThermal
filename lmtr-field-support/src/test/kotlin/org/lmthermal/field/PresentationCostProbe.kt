package org.lmthermal.field

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import java.io.File
import java.util.Random

/** Paired same-process benchmark: warm both algorithms, alternate ordering, check outputs outside timing.
 * Thread counters are exact only when supported. ART process counters include instrumentation overhead.
 * No GC, USB, recorder or corpus generation; timings are not concurrent live preview FPS.
 */
object PresentationCostProbe {
    @JvmStatic fun main(args:Array<String>) {
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val type=Class.forName("com.sun.management.ThreadMXBean")
        val allocation=type.getMethod("getThreadAllocatedBytes",Long::class.javaPrimitiveType)
        val cpu=Class.forName("java.lang.management.ThreadMXBean").getMethod("getCurrentThreadCpuTime")
        run(File(args[0]),{(cpu.invoke(bean) as Number).toLong()},
            {(allocation.invoke(bean,Thread.currentThread().id) as Number).toLong()},{emptyMap()})
    }
    fun run(root:File,cpu:()->Long,allocated:()->Long?,platform:()->Obj) {
        check(root.mkdirs());val rows=mutableListOf<Obj>()
        val source=Ht301ThermalMeasurement(NativeEquivalentThermometry.measure(Ht301Frame.parse(
            javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes())))
        val random=Random(19);val g=source.geometry
        val planes=linkedMapOf("room" to source.matrix(),"random" to FloatArray(g.pixelCount){(random.nextDouble()*100).toFloat()},
            "ordered" to FloatArray(g.pixelCount){it.toFloat()},"plateau" to FloatArray(g.pixelCount){if(it%2==0)-0f else 0f})
        for((name,plane)in planes) {
            val m=if(name=="room")source else OwnedThermalMeasurement(g,plane,0,0,
                MeasurementProvenance(CameraModuleId("benchmark"),"synthetic",TemperatureProvenanceKind.SIMULATED))
            val whole=NativeRect(0,0,g.width,g.height)
            compare(rows,"$name/range",{PresentationReference.range(plane,g)}, {CelsiusRenderer.autoRange(plane,g)},cpu,allocated,platform)
            compare(rows,"$name/render",{PresentationReference.pixels(m)},
                {CelsiusRenderer.render(m,CelsiusPresentationSettings()).takeArgb()},cpu,allocated,platform)
            compare(rows,"$name/roi",{RoiStatistics.calculate(g,m.matrix(),whole,m.validityMask())},
                {m.roiStatistics(whole)},cpu,allocated,platform)
        }
        File(root,"presentation.json").writeBytes(LmtrJson.encode(mapOf("baseline_commit" to PresentationReference.BASE,
            "warmup_per_version" to 8,"samples_per_version" to 32,"order" to "alternating_old_new",
            "physical" to false,"forced_gc" to false,"cases" to rows,
            "scope" to "synchronous_same_data_not_USB_Compose_preview_or_live_heap_bound")))
    }
    private fun compare(rows:MutableList<Obj>,name:String,old:()->Any,new:()->Any,cpu:()->Long,
        allocated:()->Long?,platform:()->Obj) {
        fun equal(a:Any,b:Any) {
            when(a) {
                is IntArray -> check(a.contentEquals(b as IntArray))
                is CelsiusRange -> { b as CelsiusRange;check(a.lower.toRawBits()==b.lower.toRawBits() && a.upper.toRawBits()==b.upper.toRawBits()) }
                else -> check(a==b)
            }
        }
        repeat(8){equal(old(),new())}
        val wall=Array(2){LongArray(32)};val cpuTimes=Array(2){LongArray(32)};val bytes=Array(2){arrayOfNulls<Long>(32)}
        val before=platform()
        fun timed(which:Int,index:Int):Any {
            val b=allocated();val c=cpu();val start=System.nanoTime()
            val result=if(which==0)old() else new()
            wall[which][index]=System.nanoTime()-start;cpuTimes[which][index]=cpu()-c
            val end=allocated();bytes[which][index]=if(b!=null && end!=null)end-b else null
            return result
        }
        repeat(32){i -> val first=i%2;val a=timed(first,i);val b=timed(1-first,i);equal(a,b)}
        fun distribution(values:LongArray):Obj {val sorted=values.sortedArray();return mapOf("p50" to sorted[15],"p95" to sorted[30],"max" to sorted.last())}
        rows.add(mapOf("case" to name,"parity" to true,"platform_before" to before,"platform_after" to platform(),
            "versions" to (0..1).map { v -> mapOf("version" to if(v==0)"baseline" else "candidate",
                "wall_ns" to distribution(wall[v]),"thread_cpu_ns" to distribution(cpuTimes[v]),
                "allocated_bytes" to if(bytes[v].all{it!=null})distribution(bytes[v].map{it!!}.toLongArray())else null) }))
    }
}
