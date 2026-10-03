package org.lmthermal.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.camera.simulated.*
import org.lmthermal.core.*
import org.lmthermal.exchange.*
import java.io.*

/** ART export/storage/lifetime checks, with injected new-document streams; never opens a real USB source. */
@RunWith(AndroidJUnit4::class)
class CaptureExporterDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val geometry = NativeImageGeometry(160, 120)
    private val metadata = CameraModuleMetadata(CameraModuleId("export-test"), "synthetic-160x120", CameraCapabilities(true, true, false, true), "simulated")
    private val device = object : CameraDeviceIdentity { override val key = "export-test" }
    private val roi = NativeRect(10, 20, 40, 60)
    private fun source() = CameraSessionState<Bitmap>(module = metadata, device = device, geometry = geometry, lifecycle = CameraLifecycle.STREAMING,
        measurement = OwnedThermalMeasurement(geometry, FloatArray(geometry.pixelCount) { 20f + it / 1000f }, 55, 1000,
            MeasurementProvenance(metadata.id, metadata.modelId, TemperatureProvenanceKind.SIMULATED)))
    private fun selection() = NativeRoiSelection().bind(RoiSourceKey(metadata.id, metadata.modelId, device.key), geometry).select(roi)
    private suspend fun await(exporter: CaptureExporter, phase: ExportPhase) = withTimeout(10000) { while (exporter.state.value.phase != phase) {
        check(exporter.state.value.phase != ExportPhase.ERROR) { exporter.state.value.toString() }; delay(10) } }
    private fun prepared(exporter: CaptureExporter) = File(context.cacheDir, "lmtx-export/" + exporter.state.value.name)
    @Test fun frozenCurrentFrameRoiAndSettingsSurviveNewFramesAndClose() = runBlocking {
        val exporter = CaptureExporter(context)
        try {
            val source = source(); exporter.prepare(source, selection(), CelsiusPresentationSettings(CelsiusPalette.TURBO, false, CelsiusRange(22.0, 44.0)), NativePixel(25, 30))
            // Neither a new live source nor closing it can change the already submitted immutable request.
            source.copy(measurement = null, lifecycle = CameraLifecycle.CLOSED)
            await(exporter, ExportPhase.READY)
            val file = prepared(exporter); val m = LmtxChecker.check(file)
            assertEquals("turbo", (m["presentation"] as Map<*, *>)["palette_id"])
            assertEquals("manual", (m["presentation"] as Map<*, *>)["range_mode"])
            java.util.zip.ZipFile(file).use { zip -> val input = java.io.DataInputStream(zip.getInputStream(zip.getEntry("data/temperature.f32le")))
                val original = source.measurement!!.matrix()
                for (value in original) assertEquals(value.toRawBits(), java.lang.Integer.reverseBytes(input.readInt()))
            }
            assertEquals("55", (m["acquisition"] as Map<*, *>)["sequence"])
            assertNotNull((m["analysis"] as Map<*, *>)["shapes"])
        } finally { exporter.cancel(); exporter.dispose() }
    }
    @Test fun actualPreviewOnlySimulatorExportsVisualWithoutFabricatedThermometry() = runBlocking {
        val module = SimulatedCameraModule({ image -> Bitmap.createBitmap(image.pixels(), image.geometry.width, image.geometry.height, Bitmap.Config.ARGB_8888) }, 100)
        val session = module.open(SimulatedCameraIdentity())
        val exporter = CaptureExporter(context)
        try {
            withTimeout(5000) { while (session.state.value.preview == null) delay(10) }
            exporter.prepare(session.state.value, NativeRoiSelection(), CelsiusPresentationSettings(), null)
            session.close(CameraCloseReason.USER); await(exporter, ExportPhase.READY)
            val m = LmtxChecker.check(prepared(exporter))
            assertEquals("visual", m["content_class"]); assertFalse(m.containsKey("extensions"))
            assertEquals("unavailable", (m["measurement"] as Map<*, *>)["status"])
        } finally { exporter.cancel(); exporter.dispose(); session.close(CameraCloseReason.DISPOSED) }
    }
    @Test fun unavailableCurrentFrameRetainsRoiButNeverLastReadyTemperatures() = runBlocking {
        val exporter = CaptureExporter(context)
        try {
            val preview = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
            val unsettled = source().copy(measurement = null, preview = CameraPreview(geometry, 56, 1010, preview))
            exporter.prepare(unsettled, selection(), CelsiusPresentationSettings(), null); await(exporter, ExportPhase.READY)
            val m = LmtxChecker.check(prepared(exporter))
            assertEquals("unavailable", (m["measurement"] as Map<*, *>)["status"])
            val shapes = (m["analysis"] as Map<*, *>)["shapes"] as List<*>
            assertFalse((shapes.single() as Map<*, *>).containsKey("statistics"))
            assertFalse((m["presentation"] as Map<*, *>).containsKey("effective_bounds"))
        } finally { exporter.cancel(); exporter.dispose() }
    }
    @Test fun pickerCancellationRemovesStageAndCannotShare() = runBlocking {
        val exporter = CaptureExporter(context)
        try { exporter.prepare(source(), selection(), CelsiusPresentationSettings(), null); await(exporter, ExportPhase.READY)
            val file = prepared(exporter); exporter.publish(null)
            assertEquals(ExportPhase.CANCELLED, exporter.state.value.phase); assertFalse(file.exists()); assertNull(exporter.shareFile())
        } finally { exporter.dispose() }
    }
    @Test fun successRequiresDestinationCloseAndNarrowFinalArtifact() = runBlocking {
        val output = object : ByteArrayOutputStream() { var closed = false; override fun close() { closed = true } }
        val exporter = CaptureExporter(context, { output }, { error("Success must not remove document") })
        try { exporter.prepare(source(), selection(), CelsiusPresentationSettings(), null); await(exporter, ExportPhase.READY)
            val expected = prepared(exporter).readBytes(); exporter.publish(Uri.parse("content://test/new-document")); await(exporter, ExportPhase.SUCCESS)
            assertTrue(output.closed); assertArrayEquals(expected, output.toByteArray()); assertNotNull(exporter.shareFile())
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".captures", exporter.shareFile()!!)
            assertEquals("content", uri.scheme)
        } finally { exporter.cancel(); exporter.dispose() }
    }
    @Test fun failedCloseDeletesPartialAndCannotShare() = runBlocking {
        var deleted = false
        val exporter = CaptureExporter(context, { object : ByteArrayOutputStream() { override fun close() { throw IOException("Full") } } }, { deleted = true; true })
        try { exporter.prepare(source(), selection(), CelsiusPresentationSettings(), null); await(exporter, ExportPhase.READY)
            exporter.publish(Uri.parse("content://test/new-document"))
            withTimeout(10000) { while (exporter.state.value.phase != ExportPhase.ERROR) delay(10) }
            assertTrue(deleted); assertNull(exporter.shareFile())
        } finally { exporter.dispose() }
    }
    @Test fun generationCancellationCannotPublishSupersededCaptureOrGrowQueue() = runBlocking {
        val exporter = CaptureExporter(context)
        try { exporter.prepare(source(), selection(), CelsiusPresentationSettings(), null); repeat(20) { exporter.prepare(source(), selection(), CelsiusPresentationSettings(), null) }
            exporter.cancel(); exporter.prepare(source(), selection(), CelsiusPresentationSettings(CelsiusPalette.BLACK_HOT), null)
            await(exporter, ExportPhase.READY); delay(100)
            val m = LmtxChecker.check(prepared(exporter)); assertEquals("black_hot", (m["presentation"] as Map<*, *>)["palette_id"])
        } finally { exporter.cancel(); exporter.dispose() }
    }
    @Test fun activityRecreationKeepsFrozenArtifactButCannotReopenCamera() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: CameraViewModel
            scenario.onActivity { activity -> activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                model = ViewModelProvider(activity)[CameraViewModel::class.java]
                model.exporter.prepare(source(), selection(), CelsiusPresentationSettings(), null) }
            await(model.exporter, ExportPhase.READY); val file = prepared(model.exporter)
            scenario.recreate()
            scenario.onActivity { activity -> assertSame(model, ViewModelProvider(activity)[CameraViewModel::class.java]) }
            assertEquals(ExportPhase.READY, model.exporter.state.value.phase); LmtxChecker.check(file)
            assertNotEquals(CameraLifecycle.STREAMING, model.camera.state.value.lifecycle)
            model.exporter.cancel()
        }
    }
}
