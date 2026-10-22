package org.lmthermal.art

import android.graphics.Bitmap
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.lmthermal.field.PipelineHeapProbe
import java.io.File
import java.util.UUID

/** No permissions, USB, recorder or physical scene. GC requests only characterize fixture reclaimability. */
class PipelineHeapRuntimeTest {
    @Test fun separateAcquisitionPresentationAndReclaimableOwners() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "r4/pipeline-${UUID.randomUUID()}")
        PipelineHeapProbe.run(root, paced = true, platformSample = {
            val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info)
            mapOf("art_gc" to Debug.getRuntimeStats(), "native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),
                "pss_bytes" to info.totalPss * 1024L)
        }, bitmap = { colors -> Bitmap.createBitmap(colors, 384, 288, Bitmap.Config.ARGB_8888) },
            diagnosticGc = { System.gc(); Thread.sleep(250) })
    }
}
