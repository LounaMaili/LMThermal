package org.lmthermal.app.r2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.ht301.Ht301ExportEvidence
import org.lmthermal.core.*
import org.lmthermal.exchange.LmtxJson
import org.lmthermal.r2.R2Json
import java.io.ByteArrayOutputStream

/** Protect the test-only direct mapping from accidentally simplifying still evidence. */
@RunWith(AndroidJUnit4::class)
class R2ContextDeviceTest {
    @Test fun directMetadataRetainsEveryStillEvidenceValue() {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        for (path in listOf("fixtures/radiometric-room-settled.raw", "thermometry/warm-hand-settled.raw")) {
            val frame=Ht301Frame.parse(assets.open(path).use { it.readBytes() })
            val measurement=NativeEquivalentThermometry.measure(frame)
            val old=Ht301ExportEvidence.freeze(measurement).payloads.single { it.id=="ht301-metadata" }
            val bytes=ByteArrayOutputStream().also { old.bytes.writeTo(it) }.toByteArray()
            assertEquals(path,LmtxJson.decode(bytes),R2Json.decode(R2Json.encode(R2HtContext.metadata(measurement))))
        }
    }
}
