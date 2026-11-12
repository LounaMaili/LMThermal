package org.lmthermal.art

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.lmthermal.field.HtMemoryProbe
import org.lmthermal.field.RecorderAllocationProbe
import org.lmthermal.field.HtRecorderStressProbe
import org.lmthermal.recorder.RecorderCodecProbe
import java.io.File
import java.util.UUID

/** Existing full-size sanitized fixture, no UVC/permissions/camera or global device changes. */
class HtMemoryRuntimeTest {
    @Test fun fullSizeFixtureMemoryAndSourceParityKeepInitialBudgets() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"r4/memory-${UUID.randomUUID()}")
        HtMemoryProbe.run(root, platformSample = {
            val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
            mapOf("native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),"pss_bytes" to memory.totalPss*1024L)
        })
        RecorderAllocationProbe.run(root,File(root,"validation-cost.json")) {
            mapOf("runtime" to Debug.getRuntimeStats(),"native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),
                "java_occupied_bytes" to Runtime.getRuntime().let {it.totalMemory()-it.freeMemory()})
        }
        RecorderCodecProbe.run(File(root,"codec-workspace.json"))
    }
    @Test fun fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"r4/recorder-stress-${UUID.randomUUID()}")
        // Only the fine observer/tables differ. Common coarse metrics and all workload/guards stay on.
        val mode = InstrumentationRegistry.getArguments().getString("lmtrWriterMetrics") ?: "on"
        require(mode == "on" || mode == "off") { "Unsupported fine-metrics mode" }
        HtRecorderStressProbe.run(root,android.os.SystemClock::elapsedRealtimeNanos,instrumentWriter=mode == "on") {
            val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
            mapOf("runtime" to Debug.getRuntimeStats(),"native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),
                "pss_bytes" to info.totalPss*1024L)
        }
    }

}
