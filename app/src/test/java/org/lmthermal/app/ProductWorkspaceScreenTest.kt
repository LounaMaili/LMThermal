package org.lmthermal.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.lmthermal.camera.*
import org.lmthermal.core.*

/** Actual product screen and coordinator with a synthetic, counted module. Never uses USB or ADB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32,36])
open class ProductWorkspaceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    /** Subclasses qualify constrained real-screen controls rather than just the layout shell. */
    protected open val viewport: Pair<Int, Int> get() =
        if (context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            800 to 360 else 400 to 760
    protected open val fontScale = 1f
    @Before fun permission() {
        shadowOf(context).grantPermissions("org.lmthermal.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }
    private class Module : CameraModule<Bitmap> {
        override val metadata = CameraModuleMetadata(CameraModuleId("product-screen"), "simulated",
            CameraCapabilities(true, true, false, true))
        val device = object : CameraDeviceIdentity { override val key = "synthetic-only" }
        var opens = 0; var closes = 0; var actions = 0
        lateinit var stream: MutableStateFlow<CameraSessionState<Bitmap>>
        override fun probe(device: CameraDeviceIdentity) = CameraProbeResult.SUPPORTED
        override suspend fun open(device: CameraDeviceIdentity): CameraSession<Bitmap> {
            opens++
            val grid = NativeImageGeometry(32, 24)
            val measurement = OwnedThermalMeasurement(grid, FloatArray(grid.pixelCount) { 20f + it % 32 },
                1, 1, MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED))
            stream = MutableStateFlow(CameraSessionState(module = metadata, device = device, geometry = grid,
                lifecycle = CameraLifecycle.STREAMING, status = CameraStatus(CameraStatusCode.MEASUREMENT_READY),
                measurement = measurement, actions = setOf(CameraAction.CLOSE)))
            return object : CameraSession<Bitmap> {
                override val state = stream
                override suspend fun perform(action: CameraAction) { actions++;error("Navigation cannot perform camera actions") }
                override suspend fun close(reason: CameraCloseReason) { closes++ }
            }
        }
    }
    private fun screen(module: Module, available: Boolean = true, check: (AndroidCameraCoordinator, CelsiusPresenter) -> Unit) {
        val registration = AndroidModuleRegistration(module, object : CameraUiBindings { override val modelLabel = R.string.app_name }, false)
        val camera = AndroidCameraCoordinator(context, listOf(registration), devices = { if (available) listOf(module.device) else emptyList() })
        val presenter = CelsiusPresenter(context, camera.state, sourceGeneration = camera::presentationGeneration,
            publishInGeneration = camera::publishPresentation)
        try {
            camera.enterForeground()
            compose.setContent {
              val density = LocalDensity.current.density
              CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
               MaterialTheme { Box(Modifier.requiredSize(viewport.first.dp, viewport.second.dp)) {
                ThermalScreen(camera, presenter, { camera.connect() })
               } }
              }
            }
            check(camera,presenter)
        } finally { presenter.dispose();camera.dispose() }
    }
    private fun section(tag: String) = compose.onNodeWithTag("workspace-tab-$tag").performScrollTo().performClick()
    @Test fun noCameraNavigationNeverOpensOrInitializesAndStageRemainsVisible() {
        val module = Module()
        screen(module, false) { _, _ ->
            val original = compose.onNodeWithTag("thermal-image").fetchSemanticsNode().boundsInRoot
            assertTrue(original.width > 0 && original.height > 0)
            for (section in WorkspaceSection.entries) {
                section(section.tag)
                compose.onNodeWithTag("thermal-image").assertIsDisplayed()
                assertEquals(original, compose.onNodeWithTag("thermal-image").fetchSemanticsNode().boundsInRoot)
            }
            section("live")
            compose.onNodeWithText(context.getString(R.string.camera_connect)).assertIsNotEnabled()
            assertEquals(0,module.opens);assertEquals(0,module.actions)
        }
    }
    @Test fun sourceFailureClearsMeasurementsAndExposesLocalizedErrorWithoutReopening() {
        val module = Module()
        screen(module) { _, presenter ->
            compose.onNodeWithText(context.getString(R.string.camera_connect)).performScrollTo().performClick()
            compose.waitUntil(5000) { presenter.state.value.measurement != null }
            section("measure")
            module.stream.value = module.stream.value.copy(lifecycle = CameraLifecycle.ERROR,
                error = CameraError(CameraErrorCode.STREAM_FAILED))
            compose.waitUntil(5000) { presenter.state.value.measurement == null }
            section("live")
            compose.onNodeWithText(context.getString(R.string.camera_error_stream_failed)).assertExists()
            compose.onNodeWithTag("thermal-image").assertIsDisplayed()
            assertEquals(1,module.opens);assertEquals(0,module.actions)
        }
    }
    @Test fun measurementShortcutsPaletteAndTransientUseCurrentSourceWithoutControls() {
        val module = Module()
        screen(module) { camera, presenter ->
            section("measure")
            compose.onNodeWithText(context.getString(R.string.app_workspace_full_roi)).performScrollTo().assertIsNotEnabled()
            section("live")
            compose.onNodeWithText(context.getString(R.string.camera_connect)).performScrollTo().performClick()
            compose.waitUntil(5000) { presenter.state.value.measurement != null }
            val measurement = presenter.state.value.measurement!!
            val original = measurement.matrix()
            section("measure")
            compose.onNodeWithText(context.getString(R.string.app_workspace_literal_center)).performScrollTo().performClick()
            assertEquals(NativePixel(16,12),presenter.pixel.value)
            compose.onNodeWithText(context.getString(R.string.app_workspace_full_roi)).performScrollTo().performClick()
            compose.waitUntil(5000) { presenter.roi.state.value.statistics != null }
            assertEquals(NativeRect(0,0,32,24),presenter.roi.selection.value.rect)
            val statistics = presenter.roi.state.value.statistics
            presenter.setPalette(CelsiusPalette.TURBO);presenter.setLocked(CelsiusRange(25.0,45.0))
            compose.waitUntil(5000) { presenter.state.value.palette == CelsiusPalette.TURBO }
            assertEquals(statistics,presenter.roi.state.value.statistics)
            assertArrayEquals(original,measurement.matrix(),0f)
            module.stream.value = module.stream.value.copy(measurement = null)
            compose.waitUntil(5000) { presenter.state.value.measurement == null && presenter.roi.state.value.statistics == null }
            compose.onNodeWithText(context.getString(R.string.app_workspace_full_roi)).assertIsNotEnabled()
            section("live")
            compose.onNodeWithText(context.getString(R.string.camera_close)).performScrollTo().performClick()
            compose.waitUntil(5000) { module.closes == 1 }
            assertNull(camera.state.value.measurement)
            assertEquals(1,module.opens);assertEquals(0,module.actions)
        }
    }
}
