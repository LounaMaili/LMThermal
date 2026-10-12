package org.lmthermal.app

import android.graphics.Bitmap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Latches control rendering, rather than relying on sleeps to hit a lifecycle race.
 * The source can deliberately conflate intermediate states, as real StateFlow permits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class CelsiusReplacementTest {
    private val geometry = NativeImageGeometry(16, 12)
    private val id = CameraModuleId("presentation-test")
    private val metadata = CameraModuleMetadata(id, "test", CameraCapabilities(true, true, false, true))
    private val device = UsbCameraIdentity("same-device", 1, 1)

    /** Each wrapper owns its source values; no raw samples, protocol or camera is needed. */
    private fun measurement(sequence: Long, size: NativeImageGeometry = geometry,
        module: CameraModuleMetadata = metadata) = OwnedThermalMeasurement(size,
        FloatArray(size.pixelCount) { 20f + it / 100f }, sequence, sequence,
        MeasurementProvenance(module.id, module.modelId, TemperatureProvenanceKind.SIMULATED))

    private fun ready(value: ThermalMeasurement = measurement(0)) = CameraSessionState<Bitmap>(
        module = metadata, device = device, geometry = value.geometry, lifecycle = CameraLifecycle.STREAMING,
        measurement = value, status = CameraStatus(CameraStatusCode.MEASUREMENT_READY))

    private class Blocked(val original: ThermalMeasurement) : ThermalMeasurement by original {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun matrix(): FloatArray {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Test render was not released" }
            return original.matrix()
        }
    }

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(5000) {
        while (!condition()) delay(10)
    }

    /** Suppressed emissions reproduce conflation deterministically while .value remains current.
     * The epoch represents the owner's synchronous invalidation, not a guessed device/sequence ID.
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class Source(initial: CameraSessionState<Bitmap>) : StateFlow<CameraSessionState<Bitmap>> by MutableStateFlow(initial) {
        private val emitted = MutableStateFlow(initial)
        @Volatile private var current = initial
        @Volatile private var epoch = 0L
        override val value get() = current
        override val replayCache get() = listOf(value)
        @OptIn(InternalCoroutinesApi::class)
        override suspend fun collect(collector: FlowCollector<CameraSessionState<Bitmap>>): Nothing = emitted.collect(collector)
        @Synchronized fun token(snapshot: CameraSessionState<Bitmap>): Long? = epoch.takeIf { current === snapshot }
        @Synchronized fun publish(expectedEpoch: Long, publication: () -> Unit) {
            if (epoch == expectedEpoch) publication()
        }
        @Synchronized fun set(snapshot: CameraSessionState<Bitmap>, invalidate: Boolean = false, emit: Boolean = true) {
            if (invalidate) epoch++
            current = snapshot
            if (emit) emitted.value = snapshot
        }
    }

    @Test fun newerValidFrameDoesNotCancelUsefulInFlightRender() = runBlocking {
        val old = Blocked(measurement(1)); val next = Blocked(measurement(2))
        val source = Source(ready())
        val presenter = CelsiusPresenter(RuntimeEnvironment.getApplication(), source, sourceGeneration = source::token, publishInGeneration = source::publish)
        try {
            waitUntil { presenter.state.value.measurement != null }
            source.set(ready(old)); assertTrue(old.entered.await(5, TimeUnit.SECONDS))
            source.set(ready(next)); old.release.countDown()
            assertTrue(next.entered.await(5, TimeUnit.SECONDS))
            assertSame("Useful completed frame must publish during ongoing arrivals", old, presenter.state.value.measurement)
            next.release.countDown(); waitUntil { presenter.state.value.measurement === next }
        } finally { old.release.countDown(); next.release.countDown(); presenter.dispose() }
    }

    /** The successor is blocked too, so any stale publication remains observable. */
    private suspend fun rejectsReplacement(replacement: (ThermalMeasurement) -> CameraSessionState<Bitmap>,
        conflateClose: Boolean = false) {
        val old = Blocked(measurement(1)); val successor = replacement(measurement(2))
        val next = Blocked(successor.measurement!!)
        val source = Source(ready())
        val presenter = CelsiusPresenter(RuntimeEnvironment.getApplication(), source, sourceGeneration = source::token, publishInGeneration = source::publish)
        try {
            waitUntil { presenter.state.value.measurement != null }
            source.set(ready(old)); assertTrue(old.entered.await(5, TimeUnit.SECONDS))
            if (conflateClose) source.set(ready().copy(lifecycle = CameraLifecycle.CLOSED, measurement = null),
                invalidate = true, emit = false)
            source.set(successor.copy(measurement = next), invalidate = conflateClose)
            old.release.countDown(); assertTrue(next.entered.await(5, TimeUnit.SECONDS))
            assertNotSame("Earlier source/session render leaked into successor", old, presenter.state.value.measurement)
            next.release.countDown(); waitUntil { presenter.state.value.measurement === next }
        } finally { old.release.countDown(); next.release.countDown(); presenter.dispose() }
    }

    @Test fun rapidSameDeviceCloseReopenRejectsEarlierSessionEvenWithConflatedClose() = runBlocking {
        rejectsReplacement({ ready(it) }, conflateClose = true)
    }
    @Test fun ownerGenerationPairsExactSnapshotAndAdvancesSynchronouslyOnClose() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = CameraSessionOwner<Bitmap>(ownerScope)
        try {
            val original = owner.state.value
            val first = owner.presentationGeneration(original)!!
            owner.close().join()
            val closed = owner.state.value
            assertNull(owner.presentationGeneration(original))
            assertTrue(owner.presentationGeneration(closed)!! > first)
            val second = owner.presentationGeneration(closed)!!
            owner.close().join()
            // StateFlow retains an equal CLOSED snapshot; ownership invalidation still advances.
            val third = owner.presentationGeneration(owner.state.value)!!
            assertTrue(third > second)
            var publications = 0
            owner.publishPresentation(first) { publications++ }
            assertEquals(0, publications)
            owner.publishPresentation(third) { publications++ }
            assertEquals(1, publications)
        } finally { ownerScope.cancel() }
    }
    @Test fun deviceReplacementRejectsEarlierRender() = runBlocking {
        rejectsReplacement({ ready(it).copy(device = UsbCameraIdentity("replacement", 1, 1)) })
    }
    @Test fun moduleReplacementRejectsEarlierRender() = runBlocking {
        val other = CameraModuleMetadata(CameraModuleId("other-module"), "other", metadata.capabilities)
        rejectsReplacement({ value -> ready(measurement(value.sequence, module = other)).copy(module = other) })
    }
    @Test fun geometryReplacementRejectsEarlierRender() = runBlocking {
        val other = NativeImageGeometry(13, 7)
        rejectsReplacement({ value -> ready(measurement(value.sequence, size = other)) })
    }

    @Test fun settingsReplacementRejectsEarlierRenderAndUsesFinalSettings() = runBlocking {
        val old = Blocked(measurement(1)); val source = Source(ready())
        val presenter = CelsiusPresenter(RuntimeEnvironment.getApplication(), source, sourceGeneration = source::token, publishInGeneration = source::publish)
        try {
            waitUntil { presenter.state.value.measurement != null }
            source.set(ready(old)); assertTrue(old.entered.await(5, TimeUnit.SECONDS))
            presenter.setPalette(CelsiusPalette.TURBO); presenter.setLocked(CelsiusRange(25.0, 45.0))
            old.release.countDown()
            waitUntil { presenter.state.value.palette == CelsiusPalette.TURBO && presenter.state.value.range == CelsiusRange(25.0, 45.0) }
            assertSame(old, presenter.state.value.measurement)
        } finally { old.release.countDown(); presenter.dispose() }
    }

    @Test fun unavailableErrorAndDisposeCannotRestoreBlockedCelsius() = runBlocking {
        for (terminal in listOf("unavailable", "error", "dispose")) {
            val old = Blocked(measurement(1)); val source = Source(ready())
            val presenter = CelsiusPresenter(RuntimeEnvironment.getApplication(), source, sourceGeneration = source::token, publishInGeneration = source::publish)
            try {
                waitUntil { presenter.state.value.measurement != null }
                source.set(ready(old)); assertTrue(old.entered.await(5, TimeUnit.SECONDS))
                if (terminal == "dispose") presenter.dispose()
                else source.set(ready().copy(measurement = null, error = if (terminal == "error") CameraError(CameraErrorCode.STREAM_FAILED) else null), invalidate = true)
                waitUntil { presenter.state.value.measurement == null }
                old.release.countDown(); delay(100)
                assertNull(terminal, presenter.state.value.measurement)
                assertNull(terminal, presenter.state.value.bitmap); assertNull(terminal, presenter.state.value.legend)
            } finally { old.release.countDown(); presenter.dispose() }
        }
    }

    @Test fun genericTemperatureOnlyGeometryAndOwnedMatrixRemainUnchanged() = runBlocking {
        val value = measurement(1, NativeImageGeometry(160, 120)); val before = value.matrix()
        val source = Source(ready(value))
        val presenter = CelsiusPresenter(RuntimeEnvironment.getApplication(), source, sourceGeneration = source::token, publishInGeneration = source::publish)
        try {
            waitUntil { presenter.state.value.measurement === value }
            presenter.select(NativePixel(159, 119)); presenter.setPalette(CelsiusPalette.TURBO)
            waitUntil { presenter.state.value.palette == CelsiusPalette.TURBO }
            assertNull(value.sample(NativePixel(159, 119)))
            assertEquals(160, presenter.state.value.bitmap!!.width); assertEquals(120, presenter.state.value.bitmap!!.height)
            assertArrayEquals(before, value.matrix(), 0f)
        } finally { presenter.dispose() }
    }
}
