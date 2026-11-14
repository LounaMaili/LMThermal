package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import org.lmthermal.camera.ThermalMeasurement
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.core.*
import org.lmthermal.lmtr.*

/** Fixed existing-fixture caller-thread allocation experiment, outside Android source sets.
 * The source/LUT is built before measurement. A volatile sink retains at most one output,
 * not a history. This excludes Bitmap/Compose/native work and cannot establish ART RSS.
 * The same class uses only pre-existing APIs on baseline and candidate runtimes.
 */
object RenderOwnershipProbe {
    @Volatile private var pixels:IntArray?=null
    @Volatile private var matrix:FloatArray?=null

    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1)
        val destination=File(args[0]);check(!destination.exists())
        val raw=javaClass.getResourceAsStream("/thermometry/warm-hand-settled.raw")!!.use {it.readBytes()}
        val source=Ht301ThermalMeasurement(NativeEquivalentThermometry.measure(Ht301Frame.parse(raw)))
        // Delegation does not acquire another owner's optional computation capability.
        val fallback=object:ThermalMeasurement by source {override fun matrix()=source.matrix()}
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported);bean.isThreadAllocatedMemoryEnabled=true
        val thread=Thread.currentThread().id
        val results=mutableListOf<Obj>()
        fun measured(name:String,operation:()->Unit) {
            repeat(4){repeat(8){operation()}}
            val values=(0 until 8).map {
                val before=bean.getThreadAllocatedBytes(thread);val start=System.nanoTime()
                repeat(8){operation()}
                val wall=System.nanoTime()-start;val bytes=bean.getThreadAllocatedBytes(thread)-before
                mapOf("wall_ns" to wall.toString(),"allocated_bytes" to bytes.toString(),"operations" to 8)
            }
            results.add(mapOf("case" to name,"samples" to values))
        }
        measured("public_matrix_copy"){matrix=source.matrix()}
        val digests=mutableMapOf<String,String>()
        for(automatic in listOf(true,false)) {
            val settings=CelsiusPresentationSettings(automatic=automatic)
            for((name,value) in listOf("direct" to source,"fallback" to fallback)) {
                val label="${if(automatic)"auto" else "locked"}-$name"
                measured(label){pixels=CelsiusRenderer.render(value,settings).takeArgb()}
                val buffer=java.nio.ByteBuffer.allocate(pixels!!.size*4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                pixels!!.forEach {buffer.putInt(it)}
                digests[label]=LmtrChunk.sha(buffer.array())
            }
            check(digests["${if(automatic)"auto" else "locked"}-direct"]==digests["${if(automatic)"auto" else "locked"}-fallback"])
        }
        check(matrix!!.contentEquals(source.matrix()))
        destination.writeBytes(LmtrJson.encode(mapOf("scope" to "HotSpot caller cumulative allocation; no Bitmap, ART, retained-live or Pixel RSS claim",
            "source_fixture_sha256" to LmtrChunk.sha(raw),"java" to System.getProperty("java.runtime.version"),
            "warmup_samples" to 4,"measured_samples" to 8,"operations_per_sample" to 8,"forced_gc" to false,
            "source_unchanged" to true,"argb_sha256" to digests,"cases" to results)))
        println("Five allocation cases and exact direct/fallback ARGB hashes retained")
    }
}
