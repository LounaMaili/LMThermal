package org.lmthermal.app

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.camera.simulated.SimulatedCameraIdentity
import org.lmthermal.core.*

/** Exercise the Android composition root with non-USB identities; these tests never open the HT-301. */
@RunWith(AndroidJUnit4::class)
class CameraModuleDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(5000) {
        while (!condition()) delay(10)
    }

    @Test fun simulatedPreviewUsesAlternateGeometryAndReleasesBeforeReopen() = runBlocking {
        val coordinator = AndroidCameraCoordinator(context, devices = { listOf(SimulatedCameraIdentity()) })
        try {
            withContext(Dispatchers.Main) {
                coordinator.enterForeground()
                assertEquals(CameraLifecycle.DETECTED, coordinator.state.value.lifecycle)
                assertNull(coordinator.state.value.preview)
                // A preview-only module neither requests CAMERA permission nor advertises measurement controls.
                assertFalse(coordinator.connect())
            }
            waitUntil { coordinator.state.value.preview != null }
            val first = coordinator.state.value
            val preview = first.preview!!
            assertEquals(NativeImageGeometry(160, 120), first.geometry)
            assertEquals(160, preview.image.width); assertEquals(120, preview.image.height)
            assertFalse(CameraUiPolicy.showInitialize(first)); assertFalse(CameraUiPolicy.showTemperatureControls(first))
            assertFalse(CameraUiPolicy.canInspect(first)); assertNull(first.measurement)
            assertEquals(setOf(CameraAction.CLOSE), first.actions)
            val oldBitmap = preview.image
            val pixels = IntArray(160 * 120).also { oldBitmap.getPixels(it, 0, 160, 0, 0, 160, 120) }
            waitUntil { coordinator.state.value.preview!!.sequence > preview.sequence }
            assertArrayEquals(pixels, IntArray(160 * 120).also { oldBitmap.getPixels(it, 0, 160, 0, 0, 160, 120) })
            withContext(Dispatchers.Main) { coordinator.close() }
            assertNull(coordinator.state.value.preview); assertNull(coordinator.state.value.measurement)
            withContext(Dispatchers.Main) { coordinator.connect() }
            waitUntil { coordinator.state.value.preview != null }
            assertEquals("simulated-preview", coordinator.state.value.module!!.id.value)
            withContext(Dispatchers.Main) { coordinator.leaveForeground() }
            assertNull(coordinator.state.value.preview)
            delay(150)
            assertNull(coordinator.state.value.preview)
        } finally { withContext(Dispatchers.Main) { coordinator.dispose() } }
    }

    @Test fun unknownIdentityRemainsStructuredWithoutOpeningARegisteredDriver() = runBlocking {
        val unknown = object : CameraDeviceIdentity { override val key = "unsupported-test-identity" }
        val coordinator = AndroidCameraCoordinator(context, devices = { listOf(unknown) })
        try {
            withContext(Dispatchers.Main) { coordinator.enterForeground(); assertFalse(coordinator.connect()) }
            assertEquals(CameraErrorCode.CAMERA_UNSUPPORTED, coordinator.state.value.error!!.code)
            assertNull(coordinator.state.value.module); assertNull(coordinator.state.value.preview)
            assertTrue(coordinator.state.value.actions.isEmpty())
            // Machine codes remain independent of resource labels and the current Android locale.
            assertEquals("CAMERA_UNSUPPORTED", coordinator.state.value.error!!.code.name)
            assertEquals(context.getString(R.string.camera_error_unsupported),
                context.getString(cameraErrorResource(CameraErrorCode.CAMERA_UNSUPPORTED)))
        } finally { withContext(Dispatchers.Main) { coordinator.dispose() } }
    }

    @Test fun genericCelsiusPresenterNeedsNeitherRawSamplesNorHt301Geometry() = runBlocking {
        val geometry = NativeImageGeometry(160, 120)
        val metadata = CameraModuleMetadata(CameraModuleId("synthetic-temperature"), "synthetic-test",
            CameraCapabilities(true, true, false, true))
        val measurement = OwnedThermalMeasurement(geometry, FloatArray(geometry.pixelCount) { it / 1000f }, 7, 9,
            MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED))
        val source = MutableStateFlow(CameraSessionState<Bitmap>(module = metadata,
            geometry = geometry, lifecycle = CameraLifecycle.STREAMING, measurement = measurement,
            status = CameraStatus(CameraStatusCode.MEASUREMENT_READY)))
        val presenter = CelsiusPresenter(context, source)
        try {
            waitUntil { presenter.state.value.bitmap != null }
            assertEquals(160, presenter.state.value.bitmap!!.width); assertEquals(120, presenter.state.value.bitmap!!.height)
            presenter.select(geometry.pixel(159, 119))
            assertEquals(measurement.matrixMaximum, CursorInspection.read(presenter.pixel.value, presenter.state.value.measurement)!!.celsius, 0f)
            assertNull(CursorInspection.read(presenter.pixel.value, presenter.state.value.measurement)!!.sample)
            presenter.setLocked(CelsiusRange(10.0, 12.0)); presenter.setPalette(CelsiusPalette.WHITE_HOT)
            waitUntil { presenter.state.value.range == CelsiusRange(10.0, 12.0) && presenter.state.value.palette == CelsiusPalette.WHITE_HOT }
            assertSame(measurement, presenter.state.value.measurement)
            assertEquals(measurement.matrixMaximum, CursorInspection.read(presenter.pixel.value, presenter.state.value.measurement)!!.celsius, 0f)
            source.value = source.value.copy(measurement = null, status = CameraStatus(CameraStatusCode.MEASUREMENT_UNAVAILABLE))
            waitUntil { presenter.state.value.bitmap == null }
            assertNull(CursorInspection.read(presenter.pixel.value, presenter.state.value.measurement)); assertNull(presenter.state.value.legend)
        } finally { presenter.dispose() }
    }
}
