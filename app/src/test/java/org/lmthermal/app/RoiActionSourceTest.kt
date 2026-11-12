package org.lmthermal.app

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.lmthermal.camera.*
import org.lmthermal.core.*

/** A full-image action shares the drag guard: an old source cannot select its successor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class RoiActionSourceTest {
    private val metadata = CameraModuleMetadata(CameraModuleId("roi-action-test"), "synthetic",
        CameraCapabilities(true, true, false, true))
    private fun source(key: String, grid: NativeImageGeometry) = CameraSessionState<Bitmap>(
        module = metadata, device = object : CameraDeviceIdentity { override val key = key },
        geometry = grid, lifecycle = CameraLifecycle.STREAMING,
        measurement = OwnedThermalMeasurement(grid, FloatArray(grid.pixelCount) { 25f }, 1, 1,
            MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED)))

    @Test fun capturedSourceAndGeometryMustMatchCurrentOwnerForFullImageSelection() {
        val geometry = NativeImageGeometry(4, 3)
        val original = source("first", geometry)
        val rectangle = NativeRect(0, 0, geometry.width, geometry.height)
        for (replacement in listOf(source("second", geometry), source("first", NativeImageGeometry(8, 6)))) {
            val current = MutableStateFlow(replacement)
            val presenter = RoiPresenter(RuntimeEnvironment.getApplication(), current,
                MutableStateFlow(CelsiusPresentationSnapshot()))
            try {
                presenter.select(rectangle, roiSourceKey(original), geometry)
                assertNull(presenter.selection.value.rect)
                presenter.select(rectangle, roiSourceKey(replacement), replacement.geometry)
                assertEquals(rectangle, presenter.selection.value.rect)
            } finally { presenter.dispose() }
        }
    }
}
