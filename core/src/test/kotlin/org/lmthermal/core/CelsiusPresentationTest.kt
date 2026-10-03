package org.lmthermal.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

/** Desktop exports are independent expected values, including every normalized pixel and palette entry. */
class CelsiusPresentationTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
    private fun matrix(name: String): FloatArray {
        val buffer = ByteBuffer.wrap(resource("/thermometry/$name.matrix.f32")).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / 4) { buffer.float }
    }
    private fun measurement() = NativeEquivalentThermometry.measure(Ht301Frame.parse(resource("/thermometry/warm-hand-settled.raw")))
    private val ranges get() = Properties().apply { load(resource("/presentation/ranges.properties").inputStream()) }
    private fun rangeParity(name: String, matrix: FloatArray) {
        val range = CelsiusRenderer.autoRange(matrix)
        assertEquals(name, ranges.getProperty("$name.lower").toDouble().toRawBits(), range.lower.toRawBits())
        assertEquals(name, ranges.getProperty("$name.upper").toDouble().toRawBits(), range.upper.toRawBits())
    }
    private fun linear(start: Double = 10.0, end: Double = 40.0) =
        FloatArray(Ht301Layout.IMAGE_WORDS) { (start + (it % 384) * ((end - start) / 383)).toFloat() }
    @Test fun settledRoomAutoRangeMatchesExactDesktopPercentiles() = rangeParity("radiometric-room-settled", matrix("radiometric-room-settled"))
    @Test fun handAutoRangeMatchesExactDesktopPercentiles() = rangeParity("warm-hand-settled", matrix("warm-hand-settled"))
    @Test fun linearAutoRangeMatchesDesktopInterpolation() = rangeParity("linear", linear())
    @Test fun constantMatrixExpandsSymmetricallyToOneDegree() = rangeParity("constant", FloatArray(Ht301Layout.IMAGE_WORDS) { 25f })
    @Test fun nearlyConstantMatrixHasCenteredMinimumSpan() = rangeParity("near-constant", linear(25.0, 25.01))
    @Test fun isolatedOutliersDoNotDetermineAutoBounds() {
        val values = linear().apply { this[0] = -1000f; this[1] = 1000f }
        rangeParity("outliers", values)
        val bounds = CelsiusRenderer.autoRange(values)
        assertTrue(bounds.lower > 10); assertTrue(bounds.upper < 40)
    }
    @Test fun nonfiniteOrWrongSizeMatrixRejectedEvenWithLockedScale() {
        val settings = CelsiusPresentationSettings(automatic = false)
        for (bad in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            val values = linear().apply { this[0] = bad }
            assertThrows(IllegalArgumentException::class.java) { CelsiusRenderer.render(values, settings) }
            assertThrows(IllegalArgumentException::class.java) { CelsiusRenderer.autoRange(values) }
        }
        assertThrows(IllegalArgumentException::class.java) { CelsiusRenderer.render(FloatArray(384 * 292), settings) }
    }
    @Test fun lockedBoundsAreExactAndIgnoreSceneExtrema() {
        val bounds = CelsiusRange(-5.5, 35.25)
        assertEquals(bounds, CelsiusRenderer.effectiveRange(linear(), CelsiusPresentationSettings(automatic = false, locked = bounds)))
        assertEquals(0, CelsiusRenderer.level(-100.0, bounds)); assertEquals(255, CelsiusRenderer.level(100.0, bounds))
        assertEquals(0, CelsiusRenderer.level(bounds.lower, bounds)); assertEquals(255, CelsiusRenderer.level(bounds.upper, bounds))
    }
    @Test fun reversedEqualAndNonfiniteBoundsRejected() {
        for ((low, high) in listOf(20.0 to 20.0, 21.0 to 20.0, Double.NaN to 30.0, 20.0 to Double.POSITIVE_INFINITY))
            assertThrows(IllegalArgumentException::class.java) { CelsiusRange(low, high) }
    }
    @Test fun normalizedQuarterLevelsAndTiesUseDesktopRoundToEven() {
        val bounds = CelsiusRange(0.0, 1.0)
        assertEquals(listOf(0, 64, 128, 191, 255), listOf(0.0, .25, .5, .75, 1.0).map { CelsiusRenderer.level(it, bounds) })
        assertEquals(0, CelsiusRenderer.level(.5 / 255, bounds))
        assertEquals(2, CelsiusRenderer.level(1.5 / 255, bounds))
    }
    @Test fun everyColorOfAllFivePalettesMatchesDesktopRgbReference() {
        val buffer = ByteBuffer.wrap(resource("/presentation/palettes.argb")).order(ByteOrder.LITTLE_ENDIAN)
        for (palette in CelsiusPalette.entries) for (level in 0..255) assertEquals("${palette.label}[$level]", buffer.int, palette.argb(level))
    }
    @Test fun allRenderedPixelsMatchDesktopNormalizationAndPaletteForBothFixturesAndModes() {
        val colors = ByteBuffer.wrap(resource("/presentation/palettes.argb")).order(ByteOrder.LITTLE_ENDIAN)
        val expectedPalettes = List(5) { IntArray(256) { colors.int } }
        for (name in listOf("radiometric-room-settled", "warm-hand-settled")) for (automatic in listOf(true, false)) {
            val values = matrix(name)
            val levels = resource("/presentation/$name.${if (automatic) "auto" else "locked"}.levels")
            for ((paletteIndex, palette) in CelsiusPalette.entries.withIndex()) {
                val actual = CelsiusRenderer.render(values, CelsiusPresentationSettings(palette, automatic)).argb()
                for (index in actual.indices) assertEquals("$name auto=$automatic $palette pixel=$index",
                    expectedPalettes[paletteIndex][levels[index].toInt() and 255], actual[index])
            }
        }
    }
    @Test fun legendEndpointsAndFiveLabelsMatchRenderedRange() {
        val result = CelsiusRenderer.render(linear(), CelsiusPresentationSettings(automatic = false, locked = CelsiusRange(25.0, 45.0)))
        val legend = result.legend()
        assertEquals(CelsiusPalette.INFERNO.argb(0), legend.first()); assertEquals(CelsiusPalette.INFERNO.argb(255), legend.last())
        assertEquals(listOf(25.0, 30.0, 35.0, 40.0, 45.0), result.range.ticks())
    }
    @Test fun renderingCannotMutateMatrixRawTransportOrExtrema() {
        val m = measurement(); val raw = m.raw14(); val temperatures = m.matrix(); val source = m.source.transportBytes()
        val high = m.high; val low = m.low; val trailer = m.trailerCenter
        for (palette in CelsiusPalette.entries) {
            val result = CelsiusRenderer.render(m, CelsiusPresentationSettings(palette))
            result.argb().fill(0); result.legend().fill(0)
        }
        assertArrayEquals(raw, m.raw14()); assertArrayEquals(temperatures, m.matrix(), 0f); assertArrayEquals(source, m.source.transportBytes())
        assertEquals(high, m.high); assertEquals(low, m.low); assertEquals(trailer, m.trailerCenter)
    }
    @Test fun cursorReadsCurrentMeasurementAtPersistentNativeCoordinate() {
        val first = NativeEquivalentThermometry.measure(Ht301Frame.parse(resource("/fixtures/radiometric-room-settled.raw")), 1)
        val raw = first.source.transportBytes()
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putShort((100 * 384 + 100) * 2, 5328)
        val next = NativeEquivalentThermometry.measure(Ht301Frame.parse(raw), 2)
        val pixel = NativePixel(100, 100)
        assertEquals(first.source.pixel(100, 100), CursorInspection.read(pixel, first)!!.raw14)
        assertEquals(5328, CursorInspection.read(pixel, next)!!.raw14)
        assertEquals(next.temperature(100, 100), CursorInspection.read(pixel, next)!!.celsius, 0f)
        assertNull(CursorInspection.read(pixel, null)); assertNull(CursorInspection.read(null, next))
    }
}

/** All presentation overlays and inspection use the same fitted rectangle and native pixel centers. */
class ImageCoordinateMapperTest {
    @Test fun cornersCenterAndFarInteriorEdgesSelectCorrectPixels() {
        val mapper = ImageCoordinateMapper(384.0, 288.0)
        assertEquals(NativePixel(0, 0), mapper.toNative(DisplayPosition(0.0, 0.0)))
        assertEquals(NativePixel(383, 287), mapper.toNative(DisplayPosition(Math.nextDown(384.0), Math.nextDown(288.0))))
        assertEquals(NativePixel(192, 144), mapper.toNative(DisplayPosition(192.0, 144.0)))
        assertNull(mapper.toNative(DisplayPosition(384.0, 288.0)))
    }
    @Test fun boundaryRoundingUsesPixelCellsNotNearestPixel() {
        val mapper = ImageCoordinateMapper(768.0, 576.0)
        assertEquals(NativePixel(0, 0), mapper.toNative(DisplayPosition(1.999, 1.999)))
        assertEquals(NativePixel(1, 1), mapper.toNative(DisplayPosition(2.0, 2.0)))
        assertEquals(NativePixel(383, 287), mapper.toNative(DisplayPosition(767.999, 575.999)))
    }
    @Test fun portraitLetterboxRejectsTouchesAboveAndBelowContent() {
        val mapper = ImageCoordinateMapper(384.0, 1000.0)
        assertEquals(356.0, mapper.content.top, 0.0)
        assertNull(mapper.toNative(DisplayPosition(100.0, 355.0)))
        assertNull(mapper.toNative(DisplayPosition(100.0, 644.0)))
        assertEquals(NativePixel(0, 0), mapper.toNative(DisplayPosition(0.0, 356.0)))
        assertEquals(NativePixel(192, 144), mapper.toNative(DisplayPosition(192.0, 500.0)))
    }
    @Test fun landscapeLetterboxRejectsSideTouches() {
        val mapper = ImageCoordinateMapper(1000.0, 288.0)
        assertEquals(308.0, mapper.content.left, 0.0)
        assertNull(mapper.toNative(DisplayPosition(307.0, 100.0)))
        assertNull(mapper.toNative(DisplayPosition(692.0, 100.0)))
        assertEquals(NativePixel(192, 144), mapper.toNative(DisplayPosition(500.0, 144.0)))
    }
    @Test fun everyNativePixelRoundTripsThroughMarkersAcrossResizedViewports() {
        for ((width, height) in listOf(384.0 to 288.0, 1080.0 to 2000.0, 2400.0 to 1080.0, 413.0 to 277.0)) {
            val mapper = ImageCoordinateMapper(width, height)
            for (y in 0..287) for (x in 0..383) {
                val pixel = NativePixel(x, y)
                assertEquals(pixel, mapper.toNative(mapper.toDisplay(pixel)))
            }
        }
    }
    @Test fun invalidAndOutsidePositionsNeverSilentlyClampToEdges() {
        val mapper = ImageCoordinateMapper(384.0, 288.0)
        for (position in listOf(DisplayPosition(-.001, 0.0), DisplayPosition(0.0, -.001),
            DisplayPosition(384.0, 1.0), DisplayPosition(1.0, 288.0), DisplayPosition(Double.NaN, 1.0))) assertNull(mapper.toNative(position))
        assertThrows(IllegalArgumentException::class.java) { ImageCoordinateMapper(0.0, 10.0) }
        assertThrows(IllegalArgumentException::class.java) { NativePixel(384, 0) }
    }
}
