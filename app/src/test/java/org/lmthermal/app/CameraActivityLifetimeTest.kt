package org.lmthermal.app

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.lmthermal.exchange.LmtxChecker
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Exercise real Activity/ViewModel recreation with an injected generic source, without USB or native code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class CameraActivityLifetimeTest {
    @Before fun receiverPermission() {
        // AndroidX declares this signature permission in its merged manifest; grant it in the JVM shadow too.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("org.lmthermal.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }
    class HarnessActivity : MainActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory get() = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val source = CountedModule()
                val camera = AndroidCameraCoordinator(application, listOf(AndroidModuleRegistration(source, object : CameraUiBindings {
                    override val modelLabel = R.string.app_name
                }, false)), devices = { listOf(source.device) })
                @Suppress("UNCHECKED_CAST")
                return CameraViewModel(application as Application, camera) as T
            }
        }
        override fun onCreate(savedInstanceState: Bundle?) { setTheme(R.style.AppTheme); super.onCreate(savedInstanceState) }
    }
    class CountedModule : CameraModule<Bitmap> {
        override val metadata = CameraModuleMetadata(CameraModuleId("activity-test"), "activity-test", CameraCapabilities(true, false, false, false))
        val device = object : CameraDeviceIdentity { override val key = "activity-test" }
        override fun probe(device: CameraDeviceIdentity) = CameraProbeResult.SUPPORTED
        override suspend fun open(device: CameraDeviceIdentity): CameraSession<Bitmap> {
            val geometry = NativeImageGeometry(160, 120)
            val snapshot = CameraSessionState(module = metadata, device = device, geometry = geometry,
                lifecycle = CameraLifecycle.STREAMING, status = CameraStatus(CameraStatusCode.PREVIEW_ONLY),
                actions = setOf(CameraAction.CLOSE), preview = CameraPreview(geometry, 1, 1, Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)))
            return object : CameraSession<Bitmap> {
                override val state = MutableStateFlow(snapshot)
                override suspend fun perform(action: CameraAction) { error("Lifecycle must not initialize") }
                override suspend fun close(reason: CameraCloseReason) {}
            }
        }
    }
    private fun model(activity: HarnessActivity) = ViewModelProvider(activity)[CameraViewModel::class.java]
    private fun await(predicate: () -> Boolean) {
        repeat(500) { shadowOf(Looper.getMainLooper()).idle(); if (predicate()) return; Thread.sleep(10) }
        assertTrue("Asynchronous worker did not complete", predicate())
    }
    @Test fun configurationRecreatesUiButRetainsLiveOwnerChoicesAndFinalizedCapture() {
        val controller = Robolectric.buildActivity(HarnessActivity::class.java).setup()
        try {
            val first = controller.get(); val retained = model(first)
            retained.camera.connect(); await { retained.camera.state.value.preview != null }
            val source = retained.camera.state.value
            retained.presentation.select(NativePixel(20, 30))
            retained.presentation.setPalette(CelsiusPalette.TURBO)
            retained.presentation.setLocked(CelsiusRange(25.0, 45.0))
            retained.exporter.prepare(source, NativeRoiSelection(), retained.presentation.settings.value, NativePixel(20, 30))
            await { retained.exporter.state.value.phase == ExportPhase.READY }
            val file = File(first.cacheDir, "lmtx-export/" + retained.exporter.state.value.name)
            val bytes = file.readBytes()
            controller.configurationChange(Configuration(first.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE })
            val replacement = controller.get()
            assertNotSame(first, replacement); assertSame(retained, model(replacement))
            assertSame(source.preview, retained.camera.state.value.preview)
            assertEquals(CameraLifecycle.STREAMING, retained.camera.state.value.lifecycle)
            assertEquals(NativePixel(20, 30), retained.presentation.pixel.value)
            assertEquals(CelsiusPalette.TURBO, retained.presentation.settings.value.palette)
            assertEquals(CelsiusRange(25.0, 45.0), retained.presentation.settings.value.locked)
            assertEquals(ExportPhase.READY, retained.exporter.state.value.phase)
            assertArrayEquals(bytes, file.readBytes()); LmtxChecker.check(file)
            retained.exporter.cancel()
        } finally { controller.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle() }
    }
    @Test fun ordinaryStopReleasesAndStartDoesNotReopen() {
        val controller = Robolectric.buildActivity(HarnessActivity::class.java).setup()
        try {
            val retained = model(controller.get()); retained.camera.connect()
            await { retained.camera.state.value.preview != null }
            controller.pause().stop(); await { retained.camera.state.value.preview == null }
            controller.start().resume(); shadowOf(Looper.getMainLooper()).idle()
            assertNull(retained.camera.state.value.preview); assertNull(retained.camera.state.value.measurement)
            assertNotEquals(CameraLifecycle.STREAMING, retained.camera.state.value.lifecycle)
        } finally { controller.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle() }
    }
}
