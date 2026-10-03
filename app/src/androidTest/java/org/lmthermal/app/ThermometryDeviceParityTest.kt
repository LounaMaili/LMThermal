package org.lmthermal.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lmthermal.core.*

/** Execute full goldens on actual Android libm/ART without USB or camera access.
 * Android test dependencies and goldens are confined to the test APK.
 */
@RunWith(AndroidJUnit4::class)
class ThermometryDeviceParityTest {
    private fun bytes(path: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(path).use { it.readBytes() }
    private fun frame(name: String): Ht301Frame {
        val raw = bytes(if (name == "radiometric-room-settled" || name.startsWith("synthetic-"))
            "fixtures/radiometric-room-settled.raw" else "thermometry/$name.raw")
        if (name.startsWith("synthetic-")) {
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            if (name == "synthetic-long-distance") buffer.putShort(Ht301Layout.PARAMETERS + 20, 20)
            else buffer.putShort(Ht301Layout.LOOKUP_BASE_WORD, 0)
        }
        return Ht301Frame.parse(raw)
    }
    private fun golden(name: String, kind: String): FloatArray {
        val buffer = ByteBuffer.wrap(bytes("thermometry/$name.$kind.f32")).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / 4) { buffer.float }
    }
    /** Every finite bit and undefined-entry position is checked, with discrepancy statistics in logcat. */
    @Test fun testAllLookupEntriesOnAndroidRuntime() {
        for (name in listOf("radiometric-initial", "radiometric-room-first", "radiometric-room-range",
            "radiometric-room-shutter-held", "radiometric-room-settled", "warm-hand-settled",
            "synthetic-long-distance", "synthetic-base-wrap")) {
            val actual = NativeEquivalentThermometry.buildLookup(frame(name)).values()
            val expected = golden(name, "lut")
            var different = 0; var maxError = 0.0; var maxUlp = 0L
            for (i in expected.indices) {
                assertEquals("$name NaN[$i]", expected[i].isNaN(), actual[i].isNaN())
                if (expected[i].isFinite()) {
                    if (expected[i].toRawBits() != actual[i].toRawBits()) different++
                    maxError = maxOf(maxError, kotlin.math.abs(actual[i].toDouble() - expected[i].toDouble()))
                    maxUlp = maxOf(maxUlp, kotlin.math.abs(actual[i].toRawBits().toLong() - expected[i].toRawBits().toLong()))
                }
            }
            Log.i("LMThermalParity", "$name entries=16384 differing=$different maxAbs=$maxError maxULP=$maxUlp NaN-pattern=equal")
            assertEquals("$name finite bit parity", 0, different)
        }
    }
    /** Full matrix comparison also exercises summary decoding/validation on ART. */
    @Test fun testFullSettledMatricesOnAndroidRuntime() {
        for (name in listOf("radiometric-room-settled", "warm-hand-settled")) {
            val measurement = NativeEquivalentThermometry.measure(frame(name))
            val actual = measurement.matrix(); val expected = golden(name, "matrix")
            for (i in expected.indices) assertEquals("$name pixel[$i]", expected[i].toRawBits(), actual[i].toRawBits())
            Log.i("LMThermalParity", "$name pixels=110592 differing=0 trailer-center=${measurement.trailerCenter.celsius} literal-center=${measurement.literalCenter.celsius}")
        }
    }
}
