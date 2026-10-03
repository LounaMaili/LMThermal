package org.lmthermal.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.core.*

/** Camera-free Android runtime regressions for color/coordinate presentation, not brittle window automation. */
@RunWith(AndroidJUnit4::class)
class CelsiusPresentationDeviceTest {
    private fun bytes(path: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(path).use { it.readBytes() }
    @Test fun fullDesktopColorParityForRoomAndHandInAutoAndLockedModes() {
        val colors = ByteBuffer.wrap(bytes("presentation/palettes.argb")).order(ByteOrder.LITTLE_ENDIAN)
        val tables = List(5) { IntArray(256) { colors.int } }
        for (name in listOf("radiometric-room-settled", "warm-hand-settled")) {
            val buffer = ByteBuffer.wrap(bytes("thermometry/$name.matrix.f32")).order(ByteOrder.LITTLE_ENDIAN)
            val matrix = FloatArray(Ht301Layout.IMAGE_WORDS) { buffer.float }
            for (automatic in listOf(true, false)) {
                val levels = bytes("presentation/$name.${if (automatic) "auto" else "locked"}.levels")
                for ((paletteIndex, palette) in CelsiusPalette.entries.withIndex()) {
                    val pixels = CelsiusRenderer.render(matrix, CelsiusPresentationSettings(palette, automatic)).argb()
                    for (i in pixels.indices) assertEquals(tables[paletteIndex][levels[i].toInt() and 255], pixels[i])
                    Log.i("LMThermalPresentationParity", "$name auto=$automatic palette=${palette.label} pixels=110592 differences=0")
                }
            }
        }
    }
    @Test fun coordinateMappingRoundTripsPortraitLandscapeAndLetterboxedImages() {
        for ((width, height) in listOf(1080.0 to 2000.0, 2400.0 to 1080.0, 413.0 to 277.0)) {
            val mapper = ImageCoordinateMapper(width, height)
            for (pixel in listOf(NativePixel(0,0), NativePixel(383,287), NativePixel(192,144), NativePixel(191,211)))
                assertEquals(pixel, mapper.toNative(mapper.toDisplay(pixel)))
            assertNull(mapper.toNative(DisplayPosition(-1.0, 0.0)))
        }
    }
}
