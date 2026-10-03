package org.lmthermal.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301UiBindings
import org.lmthermal.core.*
import java.io.File

/** Exercise the real screen's controls with explicitly synthetic measurements; no USB driver is opened. */
@RunWith(AndroidJUnit4::class)
class LocalizationLayoutDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Test data has native geometry/provenance and no protocol/control implementation. */
    private class LayoutModule : CameraModule<Bitmap> {
        private val geometry = NativeImageGeometry(160, 120)
        override val metadata = CameraModuleMetadata(CameraModuleId("layout-test"), "synthetic-layout",
            CameraCapabilities(true, true, true, true))
        override fun probe(device: CameraDeviceIdentity) = CameraProbeResult.SUPPORTED
        override suspend fun open(device: CameraDeviceIdentity): CameraSession<Bitmap> {
            val measurement = OwnedThermalMeasurement(geometry,
                FloatArray(geometry.pixelCount) { if (it % geometry.width in 60..100) 36.5f else 24.25f },
                1, 1, MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED))
            return object : CameraSession<Bitmap> {
                override val state = MutableStateFlow(CameraSessionState(module = metadata, device = device,
                    lifecycle = CameraLifecycle.STREAMING, capabilities = metadata.capabilities, geometry = geometry,
                    status = CameraStatus(CameraStatusCode.MEASUREMENT_READY), measurement = measurement,
                    preview = CameraPreview(geometry, 1, 1, Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)),
                    actions = setOf(CameraAction.CLOSE, CameraAction.INITIALIZE_MEASUREMENT)))
                override suspend fun perform(action: CameraAction) = Unit
                override suspend fun close(reason: CameraCloseReason) { state.value = state.value.copy(preview = null, measurement = null) }
            }
        }
    }

    @Test fun englishFrenchAndPseudolocalesKeepMeasurementActionsReachableInBothOrientations() = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var original = LocaleListCompat.getEmptyLocaleList()
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            original = AppCompatDelegate.getApplicationLocales(); originalOrientation = it.requestedOrientation
        }
        try {
            for (tag in listOf("en", "fr", "en-XA", "ar-XB")) {
                for ((orientationName, orientation) in listOf("portrait" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    "landscape" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)); it.requestedOrientation = orientation }
                    instrumentation.waitForIdleSync()
                    withTimeout(5000) {
                        var configured = false
                        while (!configured) {
                            scenario.onActivity { activity ->
                                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                activity.window.attributes = activity.window.attributes.apply {
                                    rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
                                }
                                val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                                    Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                                configured = activity.resources.configuration.orientation == expected &&
                                    activity.resources.configuration.locales[0].toLanguageTag() == tag
                            }
                            if (!configured) delay(50)
                        }
                    }
                    // Accessibility/UI idleness must include completed orientation/layout changes, not just resource lookup.
                    instrumentation.uiAutomation.waitForIdle(1000, 5000)
                    val module = LayoutModule()
                    var coordinator: AndroidCameraCoordinator? = null
                    var presenter: CelsiusPresenter? = null
                    lateinit var labels: Map<Int, String>
                    scenario.onActivity { activity ->
                        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        labels = listOf(R.string.camera_connect, R.string.camera_close, R.string.camera_ht301_initialize_radiometric,
                            R.string.measurement_auto, R.string.measurement_locked, R.string.camera_diagnostics,
                            R.string.measurement_range_dialog, R.string.measurement_cancel).associateWith { activity.getString(it) }
                        val registration = AndroidModuleRegistration(module, Ht301UiBindings)
                        coordinator = AndroidCameraCoordinator(activity.applicationContext, listOf(registration), devices = {
                            listOf(object : CameraDeviceIdentity { override val key = "synthetic-layout" }) })
                        presenter = CelsiusPresenter(activity.applicationContext, coordinator!!.state)
                        coordinator!!.enterForeground(); coordinator!!.connect()
                        presenter!!.select(NativePixel(80, 60))
                        activity.setContent { ThermalScreen(coordinator!!, presenter!!, { coordinator!!.connect() }) }
                    }
                    try {
                        withTimeout(5000) { while (presenter!!.state.value.bitmap == null) delay(10) }
                        compose.waitForIdle()
                        val initialScreenshot = instrumentation.uiAutomation.takeScreenshot()
                        File(instrumentation.targetContext.cacheDir, "locale-layout-$tag-$orientationName-top.png").outputStream().use {
                            initialScreenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        initialScreenshot.recycle()
                        // Localized controls can wrap/scroll; assert visibility after bringing each into view.
                        for (id in listOf(R.string.camera_connect, R.string.camera_close,
                            R.string.camera_ht301_initialize_radiometric, R.string.camera_diagnostics)) {
                            compose.onNodeWithText(labels.getValue(id)).performScrollTo().assertIsDisplayed()
                        }
                        compose.onNodeWithText(labels.getValue(R.string.measurement_locked)).performScrollTo().performClick()
                        lateinit var rangeLabel: String
                        scenario.onActivity { rangeLabel = it.getString(R.string.measurement_set_range, 25.0, 45.0) }
                        compose.onNodeWithText(rangeLabel).performScrollTo().performClick()
                        compose.onNodeWithText(labels.getValue(R.string.measurement_range_dialog)).assertIsDisplayed()
                        compose.onNodeWithText(labels.getValue(R.string.measurement_cancel)).performClick()
                        compose.onNodeWithText(labels.getValue(R.string.measurement_auto)).performScrollTo().performClick()
                        lateinit var languageLabel: String
                        scenario.onActivity { activity -> languageLabel = activity.getString(R.string.language_label,
                            AppLanguage.selected(AppCompatDelegate.getApplicationLocales())?.let { activity.getString(it.label) }
                                ?: activity.getString(R.string.language_other, tag)) }
                        compose.onNodeWithText(languageLabel).performScrollTo().assertIsDisplayed()
                        // Numeric values and geometry are identical across locales/presentation controls.
                        assertEquals(36.5f, presenter!!.state.value.measurement!!.temperature(NativePixel(80, 60)), 0f)
                        assertEquals(160, presenter!!.state.value.measurement!!.geometry.width)
                        val screenshot = instrumentation.uiAutomation.takeScreenshot()
                        File(instrumentation.targetContext.cacheDir, "locale-layout-$tag-$orientationName.png").outputStream().use {
                            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        screenshot.recycle()
                    } finally {
                        withContext(Dispatchers.Main) { coordinator!!.leaveForeground(); presenter!!.dispose(); coordinator!!.dispose() }
                    }
                }
            }
        } finally {
            scenario.onActivity { AppCompatDelegate.setApplicationLocales(original); it.requestedOrientation = originalOrientation }
            instrumentation.waitForIdleSync()
            scenario.close()
        }
    }
}
