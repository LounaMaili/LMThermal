package org.lmthermal.app.r2

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.core.Ht301Frame
import org.lmthermal.core.NativeEquivalentThermometry
import org.lmthermal.r2.R2Json as LmtxJson
import org.lmthermal.r2.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in isolated CPU attribution after the live runs. This is a diagnostic replay,
 * never a recording path or recomputation of the stored authoritative Celsius.
 */
@RunWith(AndroidJUnit4::class)
class R2ThermometryCostDeviceTest {
    @Test fun unchangedThermometryCpuOnOwnedRecordedSources() {
        val args=InstrumentationRegistry.getArguments(); assumeTrue(args.getString("r2Cost")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.filesDir,"recording-r2/"+args.getString("r2Run"))
        val source=File(directory,"full-deflate-1.r2proto")
        val rows=mutableListOf<Map<String,Any?>>()
        PrototypeReader(source,mapOf(0 to Stored,1 to Deflate1,2 to NativeZstd)).use { reader ->
            val last=reader.records.last(); val metadata=reader.records.json(last)
            val root=metadata.getValue("root") as Map<*,*>
            val chunk=reader.seek(requireNotNull(root["first"]).toString().toLong())!!
            val valid=chunk.entries.zip(chunk.bytes).filter { it.first["reason"]==null }.take(10)
            require(valid.isNotEmpty())
            valid.forEach { (entry,payloads) ->
                val frame=Ht301Frame.parse(payloads.getValue("acquisition"))
                NativeEquivalentThermometry.measure(frame)
                repeat(5) { pass ->
                    val wall=SystemClock.elapsedRealtimeNanos(); val cpu=Debug.threadCpuTimeNanos()
                    val measured=NativeEquivalentThermometry.measure(frame)
                    val cpuNs=Debug.threadCpuTimeNanos()-cpu; val wallNs=SystemClock.elapsedRealtimeNanos()-wall
                    val original=ByteBuffer.wrap(payloads.getValue("temperature")).order(ByteOrder.LITTLE_ENDIAN)
                    measured.matrix().forEach { require(it.toRawBits()==original.int) }
                    rows+=mapOf("pass" to pass,"sequence" to entry["sequence"],"wall_ns" to wallNs,"cpu_ns" to cpuNs,"exact_original_float32" to true)
                }
            }
        }
        File(directory,"thermometry-cost.json").writeBytes(LmtxJson.encode(mapOf("method" to "unchanged NativeEquivalentThermometry.measure",
            "source" to "owned Full recorded frames","rows" to rows,"isolated_replay_not_live_worker_attribution" to true,
            "stored_celsius_authoritative" to true,"warning" to NativeEquivalentThermometry.WARNING)))
    }
}
