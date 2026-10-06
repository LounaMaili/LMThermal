package org.lmthermal.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.core.NativeImageGeometry

/** Use a changing source and counted factory/actions; UI events cannot reach camera initialization. */
class CameraUiLifetimeTest {
    private class Source : CameraModule<ArgbImage> {
        override val metadata = CameraModuleMetadata(CameraModuleId("lifetime-test"), "lifetime-test",
            CameraCapabilities(true, false, false, false))
        val device = object : CameraDeviceIdentity { override val key = "test" }
        val geometry = NativeImageGeometry(2, 2)
        var opens = 0; var closes = 0; var actions = 0
        lateinit var stream: MutableStateFlow<CameraSessionState<ArgbImage>>
        override fun probe(device: CameraDeviceIdentity) = CameraProbeResult.SUPPORTED
        override suspend fun open(device: CameraDeviceIdentity): CameraSession<ArgbImage> {
            opens++
            stream = MutableStateFlow(CameraSessionState(module = metadata, device = device,
                geometry = geometry, lifecycle = CameraLifecycle.STREAMING,
                preview = CameraPreview(geometry, 1, 1, ArgbImage(geometry, IntArray(4))),
                status = CameraStatus(CameraStatusCode.PREVIEW_ONLY), actions = setOf(CameraAction.CLOSE)))
            return object : CameraSession<ArgbImage> {
                override val state = stream
                override suspend fun perform(action: CameraAction) { actions++ }
                override suspend fun close(reason: CameraCloseReason) { closes++ }
            }
        }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(3000) { while (!condition()) delay(5) }
    private fun runWithOwner(test: suspend (Source, CameraSessionOwner<ArgbImage>, CameraUiLifetime) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val source = Source(); val owner = CameraSessionOwner<ArgbImage>(scope)
        val policy = CameraUiLifetime({ owner.background() })
        try { owner.open(CameraCandidate(source.device, source)).join(); await { owner.state.value.preview != null }
            test(source, owner, policy)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun pickerCancelAndSuccessKeepChangingFramesWithoutControlsOrAnotherOwner() = runWithOwner { source, owner, policy ->
        val ui = policy.started("en")
        repeat(2) {
            assertTrue(policy.beginPicker(ui)); assertFalse(policy.beginPicker(ui))
            policy.stopped(ui, false, false)
            val previous = source.stream.value.preview!!
            source.stream.value = source.stream.value.copy(preview = previous.copy(sequence = previous.sequence + 1))
            await { owner.state.value.preview!!.sequence == previous.sequence + 1 }
            assertTrue(policy.endPicker(ui)); assertFalse(policy.endPicker(ui))
            assertEquals(1, source.opens); assertEquals(0, source.closes); assertEquals(0, source.actions)
        }
    }
    @Test fun recreationRetainsSourceAndIgnoresOldUiStopOrResult() = runWithOwner { source, owner, policy ->
        val old = policy.started("en"); assertTrue(policy.beginPicker(old))
        policy.stopped(old, true, false)
        val new = policy.started("en")
        policy.stopped(old, false, true)
        assertFalse(policy.current(old)); assertFalse(policy.endPicker(old))
        assertTrue(policy.endPicker(new)); assertSame(source.stream.value.preview, owner.state.value.preview)
        assertEquals(1, source.opens); assertEquals(0, source.closes); assertEquals(0, source.actions)
    }
    @Test fun genuineBackgroundClearsDataAndCannotReopenOnForeground() = runWithOwner { source, owner, policy ->
        val ui = policy.started("en"); policy.stopped(ui, false, false)
        assertNull(owner.state.value.preview); await { source.closes == 1 }
        policy.started("en"); delay(20)
        assertEquals(1, source.opens); assertEquals(1, source.closes); assertEquals(0, source.actions)
    }
    @Test fun localeChangeReleasesEvenDuringPickerAndCannotInitialize() = runWithOwner { source, owner, policy ->
        val ui = policy.started("en"); policy.beginPicker(ui); policy.stopped(ui, true, false)
        policy.started("fr"); await { source.closes == 1 }
        assertNull(owner.state.value.preview); assertEquals(1, source.opens); assertEquals(0, source.actions)
    }
    @Test fun screenOffOverridesPickerAndRepeatedCloseReleasesOnlyOnce() = runWithOwner { source, owner, policy ->
        val ui = policy.started("en"); policy.beginPicker(ui); policy.stopped(ui, false, false)
        policy.screenOff(); await { source.closes == 1 }
        owner.close().join(); policy.dispose(); owner.close().join()
        assertEquals(1, source.closes); assertNull(owner.state.value.preview)
        assertFalse(policy.beginPicker(ui))
    }
    @Test fun finishingOverridesPickerAndConfigurationException() = runWithOwner { source, owner, policy ->
        val ui = policy.started("en"); policy.beginPicker(ui); policy.stopped(ui, true, true)
        await { source.closes == 1 }; assertNull(owner.state.value.preview)
    }
    @Test fun matchingDetachReleasesDuringPicker() = runWithOwner { source, owner, policy ->
        val ui = policy.started("en"); policy.beginPicker(ui); policy.stopped(ui, false, false)
        owner.detached(source.device.key)!!.join()
        assertTrue(policy.endPicker(ui)); assertEquals(1, source.closes); assertNull(owner.state.value.preview)
    }
    @Test fun fatalSourceErrorReleasesAndLatePublicationCannotReviveIt() = runWithOwner { source, owner, _ ->
        val ready = source.stream.value
        source.stream.value = ready.copy(lifecycle = CameraLifecycle.ERROR, error = CameraError(CameraErrorCode.STREAM_FAILED))
        await { source.closes == 1 }
        source.stream.value = ready; delay(30)
        assertEquals(CameraLifecycle.ERROR, owner.state.value.lifecycle); assertNull(owner.state.value.preview)
        assertNull(owner.state.value.measurement); assertEquals(1, source.opens)
    }
}
