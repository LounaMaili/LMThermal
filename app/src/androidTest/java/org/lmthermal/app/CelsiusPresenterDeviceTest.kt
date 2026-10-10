package org.lmthermal.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.core.*
import android.graphics.Bitmap
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*

/** Exercise the actual bounded Android renderer and close/cancellation races without opening USB or a window. */
@RunWith(AndroidJUnit4::class)
class CelsiusPresenterDeviceTest {
    private fun camera(): MutableStateFlow<CameraSessionState<Bitmap>> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val raw = context.assets.open("thermometry/warm-hand-settled.raw").use { it.readBytes() }
        return MutableStateFlow(CameraSessionState(module = Ht301ModuleProfile.metadata,
            device = UsbCameraIdentity("test", Ht301Layout.VID, Ht301Layout.PID),
            lifecycle = CameraLifecycle.STREAMING, geometry = Ht301ModuleProfile.GEOMETRY,
            measurement = Ht301ThermalMeasurement(NativeEquivalentThermometry.measure(Ht301Frame.parse(raw))),
            status = CameraStatus(CameraStatusCode.MEASUREMENT_READY, Ht301SessionStatus.RADIOMETRIC_READY)))
    }
    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(5000) {
        while (!condition()) delay(10)
    }
    /** A controlled render slower than source cadence must still publish fresh completed frames.
     * No USB or thermometry change: the delay is in a test measurement's copy accessor only.
     */
    @Test fun continuousValidSourceDoesNotStarveSlowRendering() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("r2SlowRender") == "true")
        val source=camera(); val base=source.value; val measurement=base.measurement!!
        val presenter=CelsiusPresenter(InstrumentationRegistry.getInstrumentation().targetContext,source)
        var completed=0
        val observer=launch { presenter.state.collect { if ((it.measurement?.sequence ?: 0)>0) completed++ } }
        try {
            waitUntil { presenter.state.value.measurement != null }
            repeat(60) { sequence ->
                source.value=base.copy(measurement=object: ThermalMeasurement by measurement {
                    override val sequence=sequence.toLong()+1
                    override fun matrix():FloatArray { Thread.sleep(80); return measurement.matrix() }
                })
                delay(20)
            }
            // Read while the source is still changing, before the final request can catch up.
            assertTrue("Slow valid rendering starved: only $completed published",completed>=4)
        } finally { observer.cancelAndJoin(); presenter.dispose() }
    }
    @Test fun repeatedSettingsReplaceObsoletePendingRenders() = runBlocking {
        val source = camera()
        val presenter = CelsiusPresenter(InstrumentationRegistry.getInstrumentation().targetContext, source)
        try {
            repeat(100) { presenter.setPalette(CelsiusPalette.entries[it % 5]); presenter.setAutomatic(it % 2 == 0) }
            presenter.setPalette(CelsiusPalette.TURBO); presenter.setLocked(CelsiusRange(25.0, 45.0))
            waitUntil { presenter.state.value.palette == CelsiusPalette.TURBO && presenter.state.value.range == CelsiusRange(25.0, 45.0) }
            assertSame(source.value.measurement, presenter.state.value.measurement)
            assertNotNull(presenter.state.value.bitmap)
        } finally { presenter.dispose() }
    }
    @Test fun unavailableAndDisposedSourcesCannotRestoreCompletedCelsius() = runBlocking {
        val source = camera()
        val presenter = CelsiusPresenter(InstrumentationRegistry.getInstrumentation().targetContext, source)
        try {
            waitUntil { presenter.state.value.measurement != null }
            presenter.setPalette(CelsiusPalette.BLACK_HOT)
            source.value = CameraSessionState(lifecycle = CameraLifecycle.CLOSED, status = CameraStatus(CameraStatusCode.CLOSED))
            waitUntil { presenter.state.value.measurement == null }
            delay(100)
            assertNull(presenter.state.value.bitmap); assertNull(presenter.state.value.legend); assertNull(presenter.state.value.range)
            source.value = camera().value
            presenter.dispose()
            delay(100)
            assertNull(presenter.state.value.measurement)
        } finally { presenter.dispose() }
    }
}
