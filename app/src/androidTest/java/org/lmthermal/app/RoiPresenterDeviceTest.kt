package org.lmthermal.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.core.*

/** Real ART/worker tests with synthetic generic measurements; no USB source/control is opened. */
@RunWith(AndroidJUnit4::class)
class RoiPresenterDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val geometry = NativeImageGeometry(160, 120)
    private val metadata = CameraModuleMetadata(CameraModuleId("roi-test"), "synthetic",
        CameraCapabilities(true, true, false, true))
    private val roi = NativeRect(10, 10, 30, 25)
    private fun source(value: Float = 25f, sequence: Long = 1, grid: NativeImageGeometry = geometry): CameraSessionState<Bitmap> =
        CameraSessionState(module = metadata, device = object : CameraDeviceIdentity { override val key = "roi-source" },
            geometry = grid, lifecycle = CameraLifecycle.STREAMING,
            measurement = OwnedThermalMeasurement(grid, FloatArray(grid.pixelCount) { value }, sequence, sequence,
                MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED)))
    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(10) }

    @Test fun newFramesRefreshAndCloseDetachClearReadingsWithoutErasingCompatibleGeometry() = runBlocking {
        val camera = MutableStateFlow(source())
        val presenter = CelsiusPresenter(context, camera)
        try {
            waitUntil { presenter.state.value.measurement != null }
            presenter.roi.select(roi)
            waitUntil { presenter.roi.state.value.statistics?.meanC == 25.0 }
            camera.value = source(35f, 2)
            waitUntil { presenter.roi.state.value.statistics?.meanC == 35.0 }
            for (lifecycle in listOf(CameraLifecycle.CLOSED, CameraLifecycle.DISCONNECTED, CameraLifecycle.STREAMING)) {
                camera.value = camera.value.copy(lifecycle = lifecycle, measurement = null)
                waitUntil { presenter.roi.state.value.statistics == null }
                assertEquals(roi, presenter.roi.selection.value.rect)
                delay(50); assertNull(presenter.roi.state.value.statistics)
            }
            camera.value = source(40f, 3)
            waitUntil { presenter.roi.state.value.statistics?.meanC == 40.0 }
        } finally { presenter.dispose() }
    }
    @Test fun paletteAndRangeChangesReuseExactSameFrameStatistics() = runBlocking {
        val camera = MutableStateFlow(source())
        val presenter = CelsiusPresenter(context, camera)
        try {
            waitUntil { presenter.state.value.measurement != null }; presenter.roi.select(roi)
            waitUntil { presenter.roi.state.value.statistics != null }
            val expected = presenter.roi.state.value.statistics
            for (palette in CelsiusPalette.entries) {
                presenter.setPalette(palette); presenter.setLocked(CelsiusRange(20.0, 40.0))
                waitUntil { presenter.state.value.palette == palette && presenter.state.value.range == CelsiusRange(20.0, 40.0) }
                assertEquals(expected, presenter.roi.state.value.statistics)
                presenter.setAutomatic(true)
                waitUntil { presenter.state.value.range == CelsiusRange(24.5, 25.5) }
                assertEquals(expected, presenter.roi.state.value.statistics)
            }
        } finally { presenter.dispose() }
    }
    @Test fun geometryModuleAndDeviceReplacementClearSelections() = runBlocking {
        val camera = MutableStateFlow(source())
        val presenter = CelsiusPresenter(context, camera)
        try {
            waitUntil { presenter.state.value.measurement != null }; presenter.roi.select(roi)
            waitUntil { presenter.roi.state.value.statistics != null }
            camera.value = source(grid = NativeImageGeometry(80, 60))
            waitUntil { presenter.roi.selection.value.rect == null }
            waitUntil { presenter.state.value.measurement?.geometry == NativeImageGeometry(80, 60) }
            presenter.roi.select(roi)
            camera.value = camera.value.copy(module = metadata.copy(id = CameraModuleId("other-source")), measurement = null)
            waitUntil { presenter.roi.selection.value.rect == null }
            camera.value = source()
            waitUntil { presenter.state.value.measurement?.geometry == geometry }; presenter.roi.select(roi)
            camera.value = camera.value.copy(device = object : CameraDeviceIdentity { override val key = "replacement" }, measurement = null)
            waitUntil { presenter.roi.selection.value.rect == null }
            assertNull(presenter.roi.state.value.statistics)
        } finally { presenter.dispose() }
    }
    @Test fun clearAndDisposeCannotRestoreSupersededResultsAndPointSelectionPersists() = runBlocking {
        val camera = MutableStateFlow(source())
        val presenter = CelsiusPresenter(context, camera)
        try {
            waitUntil { presenter.state.value.measurement != null }
            presenter.select(NativePixel(12, 13))
            presenter.roi.setMode(InspectionMode.ROI)
            repeat(50) { presenter.roi.select(NativeRect(0, 0, 1 + it, 1 + it)) }
            presenter.roi.clear()
            waitUntil { presenter.roi.state.value.statistics == null }
            assertNull(presenter.roi.selection.value.rect)
            presenter.roi.setMode(InspectionMode.POINT)
            assertEquals(NativePixel(12, 13), presenter.pixel.value)
            presenter.roi.select(roi); presenter.dispose(); delay(100)
            assertNull(presenter.roi.state.value.statistics)
        } finally { presenter.dispose() }
    }
    @Test fun previewOnlySourceCannotSelectOrPublishCelsiusRoi() = runBlocking {
        val camera = MutableStateFlow(source().copy(measurement = null,
            module = metadata.copy(capabilities = CameraCapabilities(true, false, false, false)),
            capabilities = CameraCapabilities(true, false, false, false)))
        val presenter = CelsiusPresenter(context, camera)
        try {
            presenter.roi.select(roi); delay(100)
            assertNull(presenter.roi.selection.value.rect); assertNull(presenter.roi.state.value.statistics)
        } finally { presenter.dispose() }
    }
    @Test fun gestureFromSupersededSourceOrGeometryCannotSelectReplacement() = runBlocking {
        val camera = MutableStateFlow(source())
        val presenter = CelsiusPresenter(context, camera)
        try {
            val originalSource = roiSourceKey(camera.value)
            camera.value = source(grid = NativeImageGeometry(80, 60))
            waitUntil { presenter.state.value.measurement != null }
            presenter.roi.select(roi, originalSource, geometry)
            assertNull(presenter.roi.selection.value.rect)
            camera.value = source().copy(device = object : CameraDeviceIdentity { override val key = "replacement" })
            presenter.roi.select(roi, originalSource, geometry)
            assertNull(presenter.roi.selection.value.rect)
        } finally { presenter.dispose() }
    }
    @Test fun optionalMaskIsAppliedByActualWorkerAndZeroValidHasNoReadings() = runBlocking {
        val base = source()
        val measurement = object : ThermalMeasurement by base.measurement!! {
            override fun validityMask() = ByteArray(geometry.pixelCount)
        }
        val camera = MutableStateFlow(base.copy(measurement = measurement))
        val presenter = CelsiusPresenter(context, camera)
        try {
            waitUntil { presenter.state.value.measurement != null }; presenter.roi.select(roi)
            waitUntil { presenter.roi.state.value.statistics != null }
            val result = presenter.roi.state.value.statistics!!
            assertEquals(0, result.validPixelCount); assertNull(result.meanC); assertNull(result.minPixel)
        } finally { presenter.dispose() }
    }
    @Test fun representativeSmallAndFullFrameCostsOnPixelArt() {
        val grid = NativeImageGeometry(384, 288)
        val matrix = FloatArray(grid.pixelCount) { 20f + (it % 100) / 10f }
        for ((name, rect) in listOf("small_64x48" to NativeRect(20, 20, 84, 68),
            "full_384x288" to NativeRect(0, 0, grid.width, grid.height))) {
            repeat(100) { RoiStatistics.calculate(grid, matrix, rect) }
            var checksum = 0.0
            val samples = DoubleArray(500) {
                val start = SystemClock.elapsedRealtimeNanos()
                checksum += RoiStatistics.calculate(grid, matrix, rect).meanC!!
                (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            }.sorted()
            assertTrue(checksum > 0)
            Log.i("LMThermalRoiBenchmark", "$name median_ms=${samples[250]} p95_ms=${samples[475]} samples=500")
        }
    }
}
