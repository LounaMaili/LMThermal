package org.lmthermal.art

import android.graphics.Bitmap
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.field.PresentationCostProbe
import org.lmthermal.core.*
import java.io.File
import java.util.UUID

/** Same-size synthetic/fixture comparison on ART; no camera or deliberate GC. */
class PresentationCostRuntimeTest {
    @Test fun pairedOldNewPresentationBitsAndCosts() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"r4/presentation-${UUID.randomUUID()}")
        PresentationCostProbe.run(root,Debug::threadCpuTimeNanos,{null}) {
            mapOf("gc" to Debug.getRuntimeStats(),"native_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),
                "java_occupied_bytes" to Runtime.getRuntime().let{it.totalMemory()-it.freeMemory()})
        }
    }
    @Test fun androidBitmapDoesNotAliasTransferredPixelArray() {
        val render=CelsiusRenderer.render(floatArrayOf(1f,2f),NativeImageGeometry(2,1),CelsiusPresentationSettings())
        val transferred=render.takeArgb();val expected=transferred.copyOf()
        val bitmap=Bitmap.createBitmap(transferred,2,1,Bitmap.Config.ARGB_8888)
        transferred.fill(0)
        assertEquals(expected[0],bitmap.getPixel(0,0));assertEquals(expected[1],bitmap.getPixel(1,0))
        assertThrows(IllegalStateException::class.java){render.takeArgb()}
    }
}
