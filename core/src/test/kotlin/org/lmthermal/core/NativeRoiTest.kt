package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*

class NativeRoiTest {
    private val geometry = NativeImageGeometry(3, 2)
    private val whole = NativeRect(0, 0, 3, 2)
    private val source = RoiSourceKey(CameraModuleId("test.camera"), "synthetic", "device-a")
    private fun statistics(matrix: FloatArray, rect: NativeRect = whole, mask: ByteArray? = null) =
        RoiStatistics.calculate(geometry, matrix, rect, mask)
    private fun rejects(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }

    @Test fun singlePixelHasOneCellAndContainsOnlyThatPixel() {
        val rect = NativeRect(1, 1, 2, 2).validate(geometry)
        assertEquals(1, rect.width); assertEquals(1, rect.height); assertEquals(1, rect.pixelCount)
        assertTrue(rect.contains(NativePixel(1, 1))); assertFalse(rect.contains(NativePixel(2, 1)))
    }
    @Test fun fullImageAndCornersUseModuleGeometry() {
        for (grid in listOf(NativeImageGeometry(384, 288), NativeImageGeometry(160, 120), NativeImageGeometry(7, 19))) {
            val full = NativeRect.fromDrag(grid.pixel(0, 0), grid.pixel(grid.width - 1, grid.height - 1), grid)
            assertEquals(grid.pixelCount, full.pixelCount)
            assertEquals(NativeRect(0, 0, grid.width, grid.height), full)
            for (x in listOf(0, grid.width - 1)) for (y in listOf(0, grid.height - 1))
                assertEquals(1, NativeRect.fromDrag(grid.pixel(x, y), grid.pixel(x, y), grid).pixelCount)
        }
    }
    @Test fun reversedDragIncludesBothTouchedCells() {
        assertEquals(NativeRect(0, 0, 3, 2), NativeRect.fromDrag(NativePixel(2, 1), NativePixel(0, 0), geometry))
        assertEquals(NativeRect(1, 0, 3, 2), NativeRect.fromDrag(NativePixel(2, 0), NativePixel(1, 1), geometry))
    }
    @Test fun zeroWidthRejected() { rejects { NativeRect(1, 0, 1, 1) } }
    @Test fun zeroHeightRejected() { rejects { NativeRect(0, 1, 1, 1) } }
    @Test fun negativeBoundsRejected() { rejects { NativeRect(-1, 0, 2, 1) } }
    @Test fun reversedModelBoundsRejected() { rejects { NativeRect(2, 0, 1, 1) } }
    @Test fun persistedOutOfBoundsRejectedWithoutClamping() { rejects { NativeRect(0, 0, 4, 2).validate(geometry) } }
    @Test fun outOfBoundsDragEndpointsRejected() { rejects { NativeRect.fromDrag(NativePixel(3, 0), NativePixel(1, 1), geometry) } }
    @Test fun overflowingPixelCountRejected() { rejects { NativeRect(0, 0, Int.MAX_VALUE, 2) } }
    @Test fun fittedRectangleEdgesMatchCellsInWideAndTallViewports() {
        for ((w, h) in listOf(600.0 to 200.0, 100.0 to 500.0, 300.0 to 200.0)) {
            val mapper = ImageCoordinateMapper(geometry, w, h)
            val full = mapper.toDisplay(whole)
            assertEquals(mapper.content, full)
            val cell = mapper.toDisplay(NativeRect(2, 1, 3, 2))
            val center = mapper.toDisplay(NativePixel(2, 1))
            assertEquals(cell.left + cell.width / 2, center.x, 1e-10)
            assertEquals(cell.top + cell.height / 2, center.y, 1e-10)
        }
    }
    @Test fun initialLetterboxTouchRejectedButLiveDragLimitsToEdge() {
        val mapper = ImageCoordinateMapper(geometry, 600.0, 200.0)
        assertNull(mapper.toNative(DisplayPosition(0.0, 0.0)))
        assertEquals(NativePixel(0, 0), mapper.toNativeClamped(DisplayPosition(-100.0, -100.0)))
        assertEquals(NativePixel(2, 1), mapper.toNativeClamped(DisplayPosition(900.0, 400.0)))
        assertNull(mapper.toNativeClamped(DisplayPosition(Double.NaN, 0.0)))
    }
    @Test fun knownMatrixStatisticsAreNativeAndUnrounded() {
        val result = statistics(floatArrayOf(10f, 20f, 30f, 4f, 50f, 60f), NativeRect(1, 0, 3, 2))
        assertEquals(4, result.pixelCount); assertEquals(4, result.validPixelCount)
        assertEquals(20f, result.minC); assertEquals(60f, result.maxC); assertEquals(40.0, result.meanC!!, 0.0)
        assertEquals(NativePixel(1, 0), result.minPixel); assertEquals(NativePixel(2, 1), result.maxPixel)
    }
    @Test fun tiesChooseFirstValidNativeRowMajorCell() {
        val result = statistics(floatArrayOf(0f, 5f, 5f, 0f, 0f, 5f))
        assertEquals(NativePixel(0, 0), result.minPixel); assertEquals(NativePixel(1, 0), result.maxPixel)
    }
    @Test fun signedZeroNumericTieRetainsFirstBitsAndCoordinate() {
        val result = statistics(floatArrayOf(-0.0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals((-0.0f).toRawBits(), result.minC!!.toRawBits())
        assertEquals(NativePixel(0, 0), result.maxPixel)
    }
    @Test fun meanUsesDoubleAccumulationRatherThanFloat() {
        val result = statistics(floatArrayOf(100_000_000f, 1f, -100_000_000f, 0f, 0f, 0f), NativeRect(0, 0, 3, 1))
        assertEquals(1.0 / 3.0, result.meanC!!, 0.0)
    }
    @Test fun finiteLargeFloatInputsDoNotOverflowMeanAccumulator() {
        val result = statistics(FloatArray(6) { Float.MAX_VALUE })
        assertEquals(Float.MAX_VALUE.toDouble(), result.meanC!!, 0.0)
    }
    @Test fun explicitAllValidMaskEqualsNoMask() {
        val matrix = FloatArray(6) { it.toFloat() }
        assertEquals(statistics(matrix), statistics(matrix, mask = ByteArray(6) { 1 }))
    }
    @Test fun partialMaskIgnoresFillerAndExcludedExtrema() {
        val result = statistics(floatArrayOf(20f, -999f, 31f, 24f, Float.NaN, Float.POSITIVE_INFINITY),
            mask = byteArrayOf(1, 0, 1, 1, 0, 0))
        assertEquals(3, result.validPixelCount); assertEquals(25.0, result.meanC!!, 0.0)
        assertEquals(20f, result.minC); assertEquals(31f, result.maxC)
    }
    @Test fun canonicalLmtxMaskedExampleProducesAcceptedStatistics() {
        val result = RoiStatistics.calculate(NativeImageGeometry(2, 2), floatArrayOf(20f, 0f, 31f, 24f),
            NativeRect(0, 0, 2, 2), byteArrayOf(1, 0, 1, 1))
        assertEquals(4, result.pixelCount); assertEquals(3, result.validPixelCount)
        assertEquals(NativePixel(0, 0), result.minPixel); assertEquals(NativePixel(0, 1), result.maxPixel)
        assertEquals(25.0, result.meanC!!, 0.0)
    }
    @Test fun singleValidPixelControlsAllResults() {
        val result = statistics(floatArrayOf(0f, 0f, 0f, 0f, 42f, 0f), mask = byteArrayOf(0, 0, 0, 0, 1, 0))
        assertEquals(1, result.validPixelCount); assertEquals(42.0, result.meanC!!, 0.0)
        assertEquals(result.minPixel, result.maxPixel); assertEquals(NativePixel(1, 1), result.minPixel)
    }
    @Test fun zeroValidPixelsHaveNoNumbersOrCoordinates() {
        val result = statistics(FloatArray(6), mask = ByteArray(6))
        assertEquals(0, result.validPixelCount)
        assertNull(result.minC); assertNull(result.maxC); assertNull(result.meanC)
        assertNull(result.minPixel); assertNull(result.maxPixel)
    }
    @Test fun validZeroIsAReading() { assertEquals(0f, statistics(FloatArray(6)).minC) }
    @Test fun invalidMaskValueOutsideRoiStillRejected() {
        rejects { statistics(FloatArray(6), NativeRect(0, 0, 1, 1), byteArrayOf(1, 1, 1, 1, 1, 2)) }
    }
    @Test fun unsignedInvalidMaskByteRejected() { rejects { statistics(FloatArray(6), mask = byteArrayOf(1, 1, 1, 1, 1, -1)) } }
    @Test fun wrongMaskLengthRejected() { rejects { statistics(FloatArray(6), mask = ByteArray(5)) } }
    @Test fun wrongMatrixLengthRejected() { rejects { statistics(FloatArray(5)) } }
    @Test fun nonfiniteValidTemperatureRejected() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
            rejects { statistics(floatArrayOf(1f, 1f, 1f, 1f, 1f, value)) }
    }
    @Test fun selectionPersistsForSameSourceAndAcrossUnavailableGeometry() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertEquals(selected, selected.bind(source, geometry))
        assertEquals(selected, selected.bind(source, null)); assertEquals(selected, selected.bind(null, null))
    }
    @Test fun moduleChangeClearsRectangleEvenWithoutNewGeometry() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertNull(selected.bind(source.copy(moduleId = CameraModuleId("other.camera")), null).rect)
    }
    @Test fun geometryChangeClearsRatherThanReinterpretsRectangle() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertNull(selected.bind(source, NativeImageGeometry(2, 3)).rect)
    }
    @Test fun deviceReplacementClearsSameSizeSelection() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertNull(selected.bind(source.copy(deviceKey = "device-b"), geometry).rect)
    }
    @Test fun clearRemovesSelectionWithoutChangingSource() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertEquals(selected.copy(rect = null), selected.clear())
    }
    @Test fun newFrameChangesStatisticsWithoutChangingSelection() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertEquals(10.0, statistics(FloatArray(6) { 10f }, selected.rect!!).meanC!!, 0.0)
        assertEquals(20.0, statistics(FloatArray(6) { 20f }, selected.rect).meanC!!, 0.0)
        assertEquals(whole, selected.rect)
    }
    @Test fun palettesAndAutoLockedRangesCannotMutateRoiInputsOrResults() {
        val matrix = FloatArray(6) { it + 20f }
        val before = matrix.copyOf(); val expected = statistics(matrix)
        for (palette in CelsiusPalette.entries) for (automatic in listOf(true, false)) {
            CelsiusRenderer.render(matrix, geometry, CelsiusPresentationSettings(palette, automatic))
            assertArrayEquals(before, matrix, 0f); assertEquals(expected, statistics(matrix))
        }
    }
    @Test fun statisticsRespectAlternateGeometryAndRowOffsets() {
        for (grid in listOf(NativeImageGeometry(384, 288), NativeImageGeometry(160, 120), NativeImageGeometry(7, 19))) {
            val matrix = FloatArray(grid.pixelCount) { it.toFloat() }
            val bottomRight = NativeRect(grid.width - 1, grid.height - 1, grid.width, grid.height)
            val result = RoiStatistics.calculate(grid, matrix, bottomRight)
            assertEquals((grid.pixelCount - 1).toDouble(), result.meanC!!, 0.0)
        }
    }
    private fun measurement(value: Float, sequence: Long = 1, grid: NativeImageGeometry = geometry) =
        OwnedThermalMeasurement(grid, FloatArray(grid.pixelCount) { value }, sequence, sequence,
            MeasurementProvenance(source.moduleId, source.modelId, TemperatureProvenanceKind.SIMULATED))
    @Test fun currentFrameRefreshesAndUnavailableFrameHasNoCachedStatistics() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertEquals(10.0, selected.statistics(measurement(10f))!!.meanC!!, 0.0)
        assertEquals(20.0, selected.statistics(measurement(20f, 2))!!.meanC!!, 0.0)
        // Close/detach/unsettled sources supply no measurement, while geometry may remain selected.
        assertNull(selected.statistics(null)); assertEquals(whole, selected.rect)
    }
    @Test fun invalidCurrentMeasurementCannotRevivePriorStatistics() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        val valid = measurement(30f)
        val invalid = object : ThermalMeasurement by valid { override val validity = MeasurementValidity.INVALID }
        assertNotNull(selected.statistics(valid)); assertNull(selected.statistics(invalid))
    }
    @Test fun foreignGeometryOrProvenanceCannotSupplySelectionReadings() {
        val selected = NativeRoiSelection().bind(source, geometry).select(whole)
        assertNull(selected.statistics(measurement(20f, grid = NativeImageGeometry(2, 3))))
        val valid = measurement(20f)
        val foreign = object : ThermalMeasurement by valid {
            override val provenance = valid.provenance.copy(moduleId = CameraModuleId("foreign"))
        }
        assertNull(selected.statistics(foreign))
    }
}
