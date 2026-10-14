package org.lmthermal.art

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.lmthermal.field.HtMemoryProbe
import java.io.File
import java.util.UUID

/** Existing full-size sanitized fixture, no UVC/permissions/camera or global device changes. */
class HtMemoryRuntimeTest {
    @Test fun fullSizeFixtureMemoryAndSourceParityKeepInitialBudgets() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"r4/memory-${UUID.randomUUID()}")
        HtMemoryProbe.run(root) {
            val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
            mapOf("native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),"pss_bytes" to memory.totalPss*1024L)
        }
    }
}
