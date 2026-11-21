package org.lmthermal.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.app.demo.SyntheticDemoActivity
import org.lmthermal.app.demo.SyntheticScene
import org.lmthermal.camera.CameraLifecycle
import org.lmthermal.camera.TemperatureProvenanceKind
import org.lmthermal.core.*
import org.lmthermal.exchange.LmtxChecker
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/** Actual debug entry point and inherited production screen/SAF, without replacing content or camera owners.
 * The host recipe restricts execution to emulator-5554; these tests reject physical devices independently.
 */
@RunWith(AndroidJUnit4::class)
class SyntheticDemoDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val locale get() = InstrumentationRegistry.getArguments().getString("demoLocale", "en")
    private lateinit var model: CameraViewModel
    private lateinit var scenario: ActivityScenario<SyntheticDemoActivity>
    private val output get() = File(instrumentation.targetContext.filesDir, "ux-synthetic").apply { mkdirs() }

    /** Preserve the operator's locale/orientation; never clear app/AVD data or request hardware permissions. */
    private fun withDemo(block: () -> Unit) {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        check(android.os.Build.VERSION.SDK_INT == 36)
        var originalLocale = LocaleListCompat.getEmptyLocaleList()
        scenario = ActivityScenario.launch(SyntheticDemoActivity::class.java)
        scenario.onActivity {
            originalLocale = AppCompatDelegate.getApplicationLocales()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        try {
            awaitConfiguration(Configuration.ORIENTATION_PORTRAIT)
            scenario.onActivity {
                model = ViewModelProvider(it)[CameraViewModel::class.java]
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            compose.onNodeWithTag("connection").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(10_000) { model.presentation.state.value.bitmap != null }
            assertEquals(TemperatureProvenanceKind.SIMULATED, model.camera.state.value.measurement!!.provenance.kind)
            block()
        } finally {
            scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                AppCompatDelegate.setApplicationLocales(originalLocale)
            }
            scenario.close()
        }
    }

    /** Wait for real recreation, then make assertions against the screen's retained ViewModel. */
    private fun awaitConfiguration(orientation: Int) {
        compose.waitUntil(10_000) {
            var matches = false
            scenario.onActivity {
                matches = it.resources.configuration.orientation == orientation &&
                    it.resources.configuration.locales[0].language == locale
            }
            matches
        }
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
    }

    private fun label(id: Int, vararg arguments: Any): String {
        var result = ""
        scenario.onActivity { result = it.getString(id, *arguments) }
        return result
    }

    /** Pause via the real diagnostics button; reset makes the next screenshot and matrix deterministic. */
    private fun resetScene() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("demo-reset").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            model.camera.state.value.statistics.callbackFps == 0.0 &&
                model.presentation.state.value.measurement === model.camera.state.value.measurement
        }
        compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
    }

    /** Coordinates deliberately go through the viewport's centered Fit mapping and real pointer events. */
    private fun location(pixel: NativePixel): Offset {
        val bounds = compose.onNodeWithTag("thermal-image").fetchSemanticsNode().boundsInRoot
        val position = ImageCoordinateMapper(SyntheticScene.geometry, bounds.width.toDouble(), bounds.height.toDouble()).toDisplay(pixel)
        return Offset(position.x.toFloat(), position.y.toFloat())
    }

    private fun selectPoint(pixel: NativePixel, expected: Double) {
        compose.onNode(hasText(label(R.string.measurement_mode_point)) and hasClickAction()).performScrollTo().performClick()
        val point = location(pixel)
        compose.onNodeWithTag("thermal-image").performTouchInput { click(point) }
        compose.waitUntil(5_000) { model.presentation.pixel.value == pixel }
        compose.onNodeWithTag("selected-temperature").assertTextEquals(label(R.string.measurement_value_celsius, expected))
    }

    /** The 128 × 48 reference rectangle contains 32 cold, 64 background and 32 hot columns. */
    private fun selectReferenceRoi() {
        compose.onNode(hasText(label(R.string.measurement_mode_roi)) and hasClickAction()).performScrollTo().performClick()
        val image = compose.onNodeWithTag("thermal-image")
        val before = image.fetchSemanticsNode().boundsInRoot
        val first = location(NativePixel(128, 48))
        val last = location(NativePixel(255, 95))
        image.performTouchInput { down(first) }
        compose.waitUntil(5_000) { model.presentation.roi.selection.value.rect == NativeRect(128, 48, 129, 49) }
        if (model.camera.state.value.statistics.callbackFps > 0) {
            val sequence = model.camera.state.value.measurement!!.sequence
            compose.waitUntil(5_000) { model.presentation.state.value.measurement!!.sequence >= sequence + 2 }
        }
        assertEquals("Readouts must not move the image under the held finger", before, image.fetchSemanticsNode().boundsInRoot)
        image.performTouchInput { moveTo(last); up() }
        compose.waitUntil(5_000) {
            model.presentation.roi.state.value.selection.rect == NativeRect(128, 48, 256, 96) &&
                model.presentation.roi.state.value.statistics?.pixelCount == 6144
        }
        assertReferenceRoi()
        assertEquals(before, image.fetchSemanticsNode().boundsInRoot)
    }

    private fun assertReferenceRoi() {
        val statistics = model.presentation.roi.state.value.statistics!!
        assertEquals(23.5, statistics.meanC!!, 0.0)
        assertEquals(10f, statistics.minC!!, 0f)
        assertEquals(40f, statistics.maxC!!, 0f)
        assertEquals(6144, statistics.validPixelCount)
        compose.onNodeWithTag("selected-temperature").assertTextEquals(label(R.string.measurement_value_celsius, 23.5))
        compose.onNodeWithTag("minimum-temperature").assertTextEquals(label(R.string.measurement_value_celsius, 10.0))
        compose.onNodeWithTag("maximum-temperature").assertTextEquals(label(R.string.measurement_value_celsius, 40.0))
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "$locale-$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun evolvingScenePointRoiAndRotationKeepNativeMeasurementsAndSession() = withDemo {
        val first = model.camera.state.value.measurement!!
        compose.waitUntil(5_000) { model.camera.state.value.measurement!!.sequence > first.sequence + 2 }
        assertFalse(first.matrix().contentEquals(model.camera.state.value.measurement!!.matrix()))
        selectPoint(NativePixel(64, 64), 10.0)
        selectPoint(NativePixel(288, 64), 40.0)
        selectPoint(NativePixel(192, 64), 22.0)
        selectReferenceRoi()
        resetScene()
        selectPoint(NativePixel(64, 208), 35.0)
        screenshot("point-portrait")
        selectReferenceRoi()
        screenshot("roi-portrait")
        val measurement = model.camera.state.value.measurement
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitConfiguration(Configuration.ORIENTATION_LANDSCAPE)
        scenario.onActivity { assertSame(model, ViewModelProvider(it)[CameraViewModel::class.java]) }
        assertSame(measurement, model.camera.state.value.measurement)
        assertReferenceRoi()
        selectReferenceRoi()
        screenshot("roi-landscape")
        // One UI step changes the lower target while retaining fixed patches and ROI coordinates.
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("demo-step").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.camera.state.value.measurement!!.sequence > measurement!!.sequence }
        compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.presentation.roi.state.value.measurement === model.camera.state.value.measurement }
        assertReferenceRoi()
        assertEquals(35f, model.camera.state.value.measurement!!.temperature(NativePixel(68, 208)), 0f)
        File(output, "$locale-measurements.json").writeText(JSONObject(mapOf(
            "geometry" to listOf(384, 288), "point_c" to listOf(10, 40, 22, 35),
            "roi_bounds_exclusive" to listOf(128, 48, 256, 96), "roi_mean_c" to 23.5,
            "roi_min_c" to 10, "roi_max_c" to 40, "roi_pixels" to 6144,
            "evolution_verified" to true, "rotation_same_view_model_and_measurement" to true)).toString(2))
    }

    @Test fun palettesManualRangeAndSafCapturePreserveTheFrozenSyntheticMatrix() = withDemo {
        resetScene()
        selectReferenceRoi()
        val frozen = model.camera.state.value.measurement!!
        val colors = mutableSetOf<Int>()
        for (palette in CelsiusPalette.entries) {
            compose.onNodeWithTag("appearance").performScrollTo().performClick()
            compose.onNode(hasText(label(PaletteResources.label(palette))) and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(5_000) { model.presentation.state.value.palette == palette }
            colors += model.presentation.state.value.bitmap!!.getPixel(192, 64)
            compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
            assertSame(frozen, model.presentation.state.value.measurement)
            assertReferenceRoi()
            screenshot("palette-${palette.name.lowercase()}")
        }
        assertTrue("Palette changes must affect colors", colors.size > 1)
        compose.onNodeWithTag("appearance").performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.measurement_set_range, 25.0, 45.0)).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("20,5")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("40,5")
        compose.onNodeWithText(label(R.string.measurement_apply)).performClick()
        compose.waitUntil(5_000) { model.presentation.state.value.range == CelsiusRange(20.5, 40.5) }
        compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
        assertReferenceRoi()
        screenshot("manual-range")
        compose.onNodeWithTag("appearance").performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.measurement_auto)).performScrollTo().performClick()
        compose.waitUntil(5_000) { model.presentation.state.value.range == CelsiusRange(10.0, 40.0) }
        compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
        compose.onNodeWithTag("capture").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.exporter.state.value.phase == ExportPhase.READY }
        assertTrue(model.exporter.state.value.radiometric)
        screenshot("capture-ready")
        scenario.recreate()
        awaitConfiguration(Configuration.ORIENTATION_PORTRAIT)
        scenario.onActivity { assertSame(model, ViewModelProvider(it)[CameraViewModel::class.java]) }
        compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("demo-step").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.camera.state.value.measurement!!.sequence > frozen.sequence }
        compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
        compose.onNodeWithTag("capture").performScrollTo().performClick()
        // The Android document picker is genuinely launched; use platform accessibility, no mock URI/provider.
        compose.onNodeWithTag("capture-destination").performScrollTo().performClick()
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var saved = false
        while (!saved && SystemClock.elapsedRealtime() < deadline) {
            saved = clickDocumentSave(instrumentation.uiAutomation.rootInActiveWindow)
            if (!saved) SystemClock.sleep(100)
        }
        assertTrue("DocumentsUI must expose an enabled Save action", saved)
        compose.waitUntil(15_000) { model.exporter.state.value.phase == ExportPhase.SUCCESS }
        assertFalse(frozen.matrix().contentEquals(model.camera.state.value.measurement!!.matrix()))
        val archive = checkNotNull(model.exporter.shareFile())
        val manifest = LmtxChecker.check(archive)
        assertEquals("simulated", (manifest["source"] as Map<*, *>)["origin"])
        ZipFile(archive).use { zip ->
            val payload = (manifest["payloads"] as List<*>).map { it as Map<*, *> }.single { it["role"] == "temperature" }
            val bytes = zip.getInputStream(zip.getEntry(payload["member"] as String)).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val values = frozen.matrix()
            assertEquals(values.size * 4, bytes.size)
            for (value in values) assertEquals(value.toRawBits(), buffer.int)
        }
        archive.copyTo(File(output, "$locale-synthetic-capture.lmtx"), overwrite = true)
        screenshot("capture-saved")
        File(output, "$locale-capture.json").writeText(JSONObject(mapOf("saf_published" to true,
            "lmtx_conformance" to true, "native_float32_bits_equal_after_new_live_frame" to true,
            "prepared_capture_survives_recreation" to true, "source_origin" to "simulated",
            "palettes" to CelsiusPalette.entries.map { it.name }, "manual_range" to listOf(20.5, 40.5),
            "automatic_range" to listOf(10, 40))).toString(2))
    }

    /** Limit clicks to the system picker Save control, never another application's matching label. */
    private fun clickDocumentSave(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.packageName?.toString()?.contains("documentsui") == true && node.isEnabled && node.isClickable &&
            (node.viewIdResourceName?.endsWith(":id/button1") == true || node.text?.toString()?.lowercase() in setOf("save", "enregistrer")))
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        for (index in 0 until node.childCount) if (clickDocumentSave(node.getChild(index))) return true
        return false
    }

    @Test fun closeAndBackgroundRequireExplicitReconnectWithNoStaleTemperatures() = withDemo {
        resetScene()
        compose.onNodeWithTag("connection").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.presentation.state.value.measurement == null }
        compose.onNodeWithTag("capture").assertDoesNotExist()
        compose.onNodeWithTag("connection").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.presentation.state.value.measurement != null }
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitUntil(5_000) { model.camera.state.value.lifecycle != CameraLifecycle.STREAMING }
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertNull(model.camera.state.value.measurement)
        compose.onNodeWithTag("connection").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("capture").assertDoesNotExist()
        screenshot("explicit-reconnect")
    }
}
