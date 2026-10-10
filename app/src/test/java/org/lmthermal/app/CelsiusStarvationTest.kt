package org.lmthermal.app

import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Controlled publication-starvation reproduction on the actual Android presenter.
 * The artificial delay is test-only; it models a render slower than source arrival.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class CelsiusStarvationTest {
    @Test fun continuingValidSourceMustNotStarveCompletedSlowRenders() = runBlocking {
        val geometry=NativeImageGeometry(16,12)
        val id=CameraModuleId("slow-render-test")
        val metadata=CameraModuleMetadata(id,"test",CameraCapabilities(true,true,false,true))
        val measurement=OwnedThermalMeasurement(geometry,FloatArray(geometry.pixelCount) { 20f+it/100f },0,0,
            MeasurementProvenance(id,"test",TemperatureProvenanceKind.SIMULATED))
        val initial=CameraSessionState<Bitmap>(module=metadata,geometry=geometry,lifecycle=CameraLifecycle.STREAMING,
            measurement=measurement,status=CameraStatus(CameraStatusCode.MEASUREMENT_READY))
        val source=MutableStateFlow(initial)
        val presenter=CelsiusPresenter(RuntimeEnvironment.getApplication(),source)
        var completed=0
        val observer=launch { presenter.state.collect { if ((it.measurement?.sequence ?: 0)>0) completed++ } }
        try {
            withTimeout(5000) { while(presenter.state.value.measurement==null) delay(10) }
            repeat(100) { sequence ->
                source.value=initial.copy(measurement=object:ThermalMeasurement by measurement {
                    override val sequence=sequence.toLong()+1
                    override fun matrix():FloatArray { Thread.sleep(100); return measurement.matrix() }
                })
                delay(10)
            }
            println("slow-render completed during continuous source: $completed")
            assertTrue("Continuously valid source starved publication: $completed completed",completed>=3)
        } finally { observer.cancelAndJoin(); presenter.dispose() }
    }
    /** A blocked old render cannot resurrect Celsius after a source/session invalidation. */
    @Test fun closeDuringInFlightRenderCannotRestoreCelsius() = runBlocking {
        val geometry=NativeImageGeometry(16,12); val id=CameraModuleId("slow-close-test")
        val measurement=OwnedThermalMeasurement(geometry,FloatArray(geometry.pixelCount) { 20f },0,0,
            MeasurementProvenance(id,"test",TemperatureProvenanceKind.SIMULATED))
        val metadata=CameraModuleMetadata(id,"test",CameraCapabilities(true,true,false,true))
        val source=MutableStateFlow(CameraSessionState<Bitmap>(module=metadata,geometry=geometry,
            lifecycle=CameraLifecycle.STREAMING,measurement=measurement,status=CameraStatus(CameraStatusCode.MEASUREMENT_READY)))
        val presenter=CelsiusPresenter(RuntimeEnvironment.getApplication(),source)
        val entered=java.util.concurrent.CountDownLatch(1); val release=java.util.concurrent.CountDownLatch(1)
        try {
            withTimeout(5000) { while(presenter.state.value.measurement==null) delay(10) }
            source.value=source.value.copy(measurement=object:ThermalMeasurement by measurement {
                override val sequence=1L
                override fun matrix():FloatArray { entered.countDown(); check(release.await(5,java.util.concurrent.TimeUnit.SECONDS)); return measurement.matrix() }
            })
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            source.value=CameraSessionState(lifecycle=CameraLifecycle.CLOSED,status=CameraStatus(CameraStatusCode.CLOSED))
            withTimeout(5000) { while(presenter.state.value.measurement!=null) delay(10) }
            release.countDown(); delay(200)
            assertNull(presenter.state.value.bitmap); assertNull(presenter.state.value.legend); assertNull(presenter.state.value.measurement)
        } finally { release.countDown(); presenter.dispose() }
    }

}
