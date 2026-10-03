package org.lmthermal.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.ht301.*
import org.lmthermal.camera.simulated.*
import org.lmthermal.core.*

/** Factories/actions are counted separately from pure probes; no test opens USB or calls HT-301 controls. */
private class StubModule(private val id: String = "test-module", private val key: String = "test",
    override val metadata: CameraModuleMetadata = CameraModuleMetadata(CameraModuleId(id), id,
        CameraCapabilities(true, false, false, false)),
    private val support: ((CameraDeviceIdentity) -> Boolean)? = null,
    private val beforeClose: suspend () -> Unit = {}) : CameraModule<ArgbImage> {
    var opens = 0
    var closes = 0
    var actions = 0
    var last: StubSession? = null
    override fun probe(device: CameraDeviceIdentity) =
        if (support?.invoke(device) ?: (device.key == key)) CameraProbeResult.SUPPORTED else CameraProbeResult.UNSUPPORTED
    override suspend fun open(device: CameraDeviceIdentity): CameraSession<ArgbImage> {
        opens++
        return StubSession(device).also { last = it }
    }
    inner class StubSession(device: CameraDeviceIdentity) : CameraSession<ArgbImage> {
        val geometry = NativeImageGeometry(160, 120)
        override val state = MutableStateFlow(CameraSessionState(module = metadata, device = device,
            lifecycle = CameraLifecycle.STREAMING, geometry = geometry, status = CameraStatus(CameraStatusCode.PREVIEW_ONLY),
            preview = CameraPreview(geometry, 1, 1, ArgbImage(geometry, IntArray(geometry.pixelCount))),
            actions = setOf(CameraAction.CLOSE)))
        override suspend fun perform(action: CameraAction) { actions++ }
        override suspend fun close(reason: CameraCloseReason) { beforeClose(); closes++ }
    }
}

private fun identity(key: String) = object : CameraDeviceIdentity { override val key = key }
private suspend fun awaitState(predicate: () -> Boolean) = withTimeout(3000) { while (!predicate()) delay(5) }

class CameraRegistryTest {
    private fun ht301() = StubModule(metadata = Ht301ModuleProfile.metadata,
        support = { Ht301ModuleProfile.probe(it) == CameraProbeResult.SUPPORTED })
    @Test fun exactHt301IdentitySelectsItsDeclaredModuleWithoutOpening() {
        val module = ht301()
        val selection = CameraModuleRegistry(listOf(module)).select(UsbCameraIdentity("usb", 0x1514, 1))
        assertSame(module, (selection as CameraSelection.Selected).candidate.module)
        assertEquals(0, module.opens); assertEquals(0, module.actions)
    }
    @Test fun unknownUsbCameraNeverFallsBackToHt301() {
        val module = ht301()
        val selection = CameraModuleRegistry(listOf(module)).select(UsbCameraIdentity("usb", 0x1514, 2))
        assertEquals(CameraErrorCode.CAMERA_UNSUPPORTED, (selection as CameraSelection.None).error.code)
        assertEquals(0, module.opens)
    }
    @Test fun emptyDiscoveryIsDistinctFromUnsupportedCamera() {
        val selection = CameraModuleRegistry(listOf(ht301())).select(emptyList())
        assertEquals(CameraErrorCode.CAMERA_NOT_FOUND, (selection as CameraSelection.None).error.code)
    }
    @Test fun ambiguousModulesAreExplicitRegardlessOfRegistryOrder() {
        val first = ht301()
        val second = StubModule("other-module", support = { Ht301ModuleProfile.probe(it) == CameraProbeResult.SUPPORTED })
        for (modules in listOf(listOf(first, second), listOf(second, first))) {
            val selection = CameraModuleRegistry(modules).select(UsbCameraIdentity("usb", 0x1514, 1))
            assertEquals(2, (selection as CameraSelection.Ambiguous).candidates.size)
        }
        assertEquals(0, first.opens); assertEquals(0, second.opens)
    }
    @Test fun twoSupportedDevicesAreNotChosenByEnumerationOrder() {
        val selection = CameraModuleRegistry(listOf(ht301())).select(listOf(
            UsbCameraIdentity("usb-b", 0x1514, 1), UsbCameraIdentity("usb-a", 0x1514, 1)))
        assertTrue(selection is CameraSelection.Ambiguous)
    }
    @Test fun duplicateStableModuleIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CameraModuleRegistry(listOf(StubModule(), StubModule())) }
    }
    @Test fun simulatedIdentityCoexistsWithRealProfileButNeverMatchesUsb() {
        val simulator = SimulatedCameraModule(previewFactory = { image: ArgbImage -> image })
        val registry = CameraModuleRegistry(listOf(ht301(), simulator))
        assertSame(simulator, (registry.select(SimulatedCameraIdentity()) as CameraSelection.Selected).candidate.module)
        assertEquals(CameraProbeResult.UNSUPPORTED, simulator.probe(UsbCameraIdentity("usb", 0x1514, 1)))
    }
    @Test fun moduleIdsRejectTranslatedOrUnstableLabels() {
        assertThrows(IllegalArgumentException::class.java) { CameraModuleId("Caméra thermique") }
        assertEquals("ht301", Ht301ModuleProfile.metadata.id.value)
    }
}

class GenericGeometryMeasurementTest {
    @Test fun positiveGeometryAndPixelCountAreValidatedWithoutAnyCameraDefault() {
        assertEquals(19200, NativeImageGeometry(160, 120).pixelCount)
        assertThrows(IllegalArgumentException::class.java) { NativeImageGeometry(0, 120) }
        assertThrows(IllegalArgumentException::class.java) { NativeImageGeometry(160, -1) }
        assertThrows(IllegalArgumentException::class.java) { NativeImageGeometry(Int.MAX_VALUE, 2) }
        assertThrows(IllegalArgumentException::class.java) { NativeImageGeometry(160, 120).pixel(160, 0) }
        // The coordinate itself has no hidden 384-wide upper bound.
        assertEquals(NativePixel(800, 500), NativeImageGeometry(1024, 768).pixel(800, 500))
    }
    @Test fun allPixelsRoundTripForDifferentResolutionsAndAspectRatios() {
        for (geometry in listOf(NativeImageGeometry(384, 288), NativeImageGeometry(160, 120),
            NativeImageGeometry(200, 50), NativeImageGeometry(96, 128))) {
            for ((width, height) in listOf(900.0 to 400.0, 317.0 to 811.0)) {
                val mapper = ImageCoordinateMapper(geometry, width, height)
                assertEquals(geometry.pixel(0,0), mapper.toNative(DisplayPosition(mapper.content.left, mapper.content.top)))
                for (y in 0 until geometry.height) for (x in 0 until geometry.width) {
                    val pixel = geometry.pixel(x, y); assertEquals(pixel, mapper.toNative(mapper.toDisplay(pixel)))
                }
                assertNull(mapper.toNative(DisplayPosition(mapper.content.left + mapper.content.width, mapper.content.top)))
                assertNull(mapper.toNative(DisplayPosition(mapper.content.left, mapper.content.top - .01)))
            }
        }
    }
    @Test fun mapperRejectsMarkerFromAnotherGeometry() {
        val mapper = ImageCoordinateMapper(NativeImageGeometry(160, 120), 800.0, 600.0)
        assertThrows(IllegalArgumentException::class.java) { mapper.toDisplay(NativePixel(383, 287)) }
    }
    @Test fun genericMeasurementOwnsCelsiusButRequiresNoRawPlane() {
        val geometry = NativeImageGeometry(160, 120)
        val matrix = FloatArray(geometry.pixelCount) { it.toFloat() / 1000 }
        val measurement = OwnedThermalMeasurement(geometry, matrix, 4, 25,
            MeasurementProvenance(CameraModuleId("test-temperature"), "synthetic", TemperatureProvenanceKind.SIMULATED))
        matrix.fill(-100f); measurement.matrix().fill(-200f)
        assertEquals(0f, measurement.temperature(geometry.pixel(0,0)), 0f)
        assertEquals(geometry.pixel(159,119), measurement.high.pixel)
        assertEquals(geometry.pixel(0,0), measurement.low.pixel)
        assertNull(measurement.sample(geometry.pixel(80,60)))
        assertEquals(4L, measurement.sequence); assertEquals(25L, measurement.receivedMonotonicMs)
    }
    @Test fun genericRendererUsesSuppliedGeometryAndDoesNotClampMeasurement() {
        val geometry = NativeImageGeometry(160, 120)
        val values = FloatArray(geometry.pixelCount) { if (it == 0) -10f else if (it == geometry.pixelCount - 1) 100f else 25f }
        val measurement = OwnedThermalMeasurement(geometry, values, 1, 1,
            MeasurementProvenance(CameraModuleId("test-temperature"), "synthetic", TemperatureProvenanceKind.SIMULATED))
        val colors = CelsiusRenderer.render(measurement, CelsiusPresentationSettings(CelsiusPalette.WHITE_HOT, false))
        assertEquals(geometry.pixelCount, colors.argb().size)
        assertEquals(CelsiusPalette.WHITE_HOT.argb(0), colors.argb().first())
        assertEquals(CelsiusPalette.WHITE_HOT.argb(255), colors.argb().last())
        assertEquals(-10f, measurement.matrixMinimum, 0f); assertEquals(100f, measurement.matrixMaximum, 0f)
        assertEquals(24.5, CelsiusRenderer.autoRange(values, geometry).lower, 0.0)
        assertEquals(25.5, CelsiusRenderer.autoRange(values, geometry).upper, 0.0)
    }
    @Test fun mismatchedGeometryAndNonfiniteGenericMatrixAreRejected() {
        val geometry = NativeImageGeometry(160, 120)
        assertThrows(IllegalArgumentException::class.java) {
            CelsiusRenderer.render(FloatArray(384 * 288), geometry, CelsiusPresentationSettings())
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnedThermalMeasurement(geometry, FloatArray(geometry.pixelCount) { Float.NaN }, 0, 0,
                MeasurementProvenance(CameraModuleId("test"), "synthetic", TemperatureProvenanceKind.SIMULATED))
        }
    }
    @Test fun aSinglePixelStillHasDefinedAutoBoundsAndOptionalCursorEvidence() {
        val geometry = NativeImageGeometry(1,1)
        val measurement = OwnedThermalMeasurement(geometry, floatArrayOf(25f), 1, 1,
            MeasurementProvenance(CameraModuleId("test"), "single", TemperatureProvenanceKind.SIMULATED))
        assertEquals(CelsiusRange(24.5,25.5), CelsiusRenderer.autoRange(measurement.matrix(), geometry))
        assertEquals(25f, CursorInspection.read(geometry.pixel(0,0), measurement)!!.celsius, 0f)
        assertNull(CursorInspection.read(NativePixel(1,0), measurement))
    }
    @Test fun ht301AdapterPreservesEveryArrayAndRicherNativeEvidence() {
        val bytes = javaClass.getResourceAsStream("/thermometry/warm-hand-settled.raw")!!.use { it.readBytes() }
        val original = NativeEquivalentThermometry.measure(Ht301Frame.parse(bytes), 10, 15)
        val generic = Ht301ThermalMeasurement(original)
        assertSame(original, generic.evidence)
        assertArrayEquals(original.matrix(), generic.matrix(), 0f)
        assertEquals(original.high.celsius, generic.high.celsius, 0f)
        assertEquals(original.low.celsius, generic.low.celsius, 0f)
        assertEquals("ht301.raw14", generic.sample(NativePixel(192,144)).encodingId)
        assertEquals(original.source.pixel(192,144), generic.sample(NativePixel(192,144)).value)
        assertEquals(10L, generic.sequence); assertEquals(15L, generic.receivedMonotonicMs)
        generic.matrix().fill(0f)
        assertArrayEquals(original.source.transportBytes(), bytes)
    }
}

class CameraOwnershipTest {
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Test fun detectionAndAmbiguityOnlyPublishStructuredStateWithoutFactories() = runBlocking {
        val scope = scope(); val owner = CameraSessionOwner<ArgbImage>(scope); val module = StubModule()
        try {
            owner.detected(CameraModuleRegistry(listOf(module)).select(identity("test")))
            assertEquals(CameraLifecycle.DETECTED, owner.state.value.lifecycle)
            owner.detected(CameraSelection.None(CameraError(CameraErrorCode.CAMERA_UNSUPPORTED)))
            assertEquals(CameraErrorCode.CAMERA_UNSUPPORTED, owner.state.value.error!!.code)
            owner.detected(CameraSelection.Ambiguous(listOf(CameraCandidate(identity("test"), module))))
            assertEquals(CameraErrorCode.CAMERA_MATCH_AMBIGUOUS, owner.state.value.error!!.code)
            assertEquals(0, module.opens); assertEquals(0, module.actions)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun replacementAwaitsPreviousReleaseBeforeOpeningAnotherSession() = runBlocking {
        val scope = scope(); val release = CompletableDeferred<Unit>()
        val old = StubModule("old", beforeClose = { release.await() }); val next = StubModule("next")
        val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("test"), old)).join()
            awaitState { owner.state.value.lifecycle == CameraLifecycle.STREAMING }
            val replacing = owner.open(CameraCandidate(identity("test"), next))
            delay(30); assertEquals(0, next.opens)
            assertNull(owner.state.value.preview); assertNull(owner.state.value.measurement)
            release.complete(Unit); replacing.join()
            awaitState { owner.state.value.module == next.metadata && owner.state.value.lifecycle == CameraLifecycle.STREAMING }
            assertEquals(1, old.closes); assertEquals(1, next.opens)
        } finally { release.complete(Unit); owner.close().join(); scope.cancel() }
    }
    @Test fun unrelatedDetachDoesNotReleaseButMatchingDetachDoes() = runBlocking {
        val scope = scope(); val module = StubModule(); val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("attached"), module)).join()
            awaitState { owner.state.value.lifecycle == CameraLifecycle.STREAMING }
            assertNull(owner.detached("other")); assertEquals(0, module.closes)
            owner.detached("attached")!!.join()
            assertEquals(1, module.closes); assertNull(owner.state.value.preview)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun backgroundClearsNowAndLateSourcePublicationCannotRevivePreview() = runBlocking {
        val scope = scope(); val module = StubModule(); val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("test"), module)).join()
            awaitState { owner.state.value.preview != null }
            val previous = module.last!!.state.value
            owner.background().join()
            module.last!!.state.value = previous.copy(statistics = CameraStatistics(received = 2))
            delay(30)
            assertEquals(CameraLifecycle.CLOSED, owner.state.value.lifecycle); assertNull(owner.state.value.preview)
            assertEquals(1, module.closes)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun cancelledOpeningIsReleasedWithoutPublishingAnActiveSession() = runBlocking {
        val scope = scope(); val allow = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        val module = StubModule()
        val delayed = object : CameraModule<ArgbImage> by module {
            override suspend fun open(device: CameraDeviceIdentity): CameraSession<ArgbImage> {
                started.complete(Unit); allow.await(); return module.open(device)
            }
        }
        val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            val opening = owner.open(CameraCandidate(identity("test"), delayed))
            started.await()
            val closing = owner.close()
            allow.complete(Unit); opening.join(); closing.join()
            assertEquals(1, module.closes); assertEquals(CameraLifecycle.CLOSED, owner.state.value.lifecycle)
            assertNull(owner.state.value.preview)
        } finally { allow.complete(Unit); owner.close().join(); scope.cancel() }
    }
    @Test fun queuedStaleOpenDoesNotReleaseANewerActiveSession() = runBlocking {
        val scope = scope(); val owner = CameraSessionOwner<ArgbImage>(scope); val first = StubModule("first"); val second = StubModule("second")
        try {
            val old = owner.open(CameraCandidate(identity("test"), first))
            val newer = owner.open(CameraCandidate(identity("test"), second))
            old.join(); newer.join()
            awaitState { owner.state.value.module == second.metadata && owner.state.value.lifecycle == CameraLifecycle.STREAMING }
            assertEquals(0, second.closes)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun releaseFailureBlocksReplacementAndPublishesStructuredError() = runBlocking {
        val scope = scope(); val old = StubModule("old", beforeClose = { error("Technical close failure") })
        val next = StubModule("next"); val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("test"), old)).join()
            awaitState { owner.state.value.lifecycle == CameraLifecycle.STREAMING }
            owner.open(CameraCandidate(identity("test"), next)).join()
            assertEquals(0, next.opens)
            assertEquals(CameraErrorCode.STREAM_OPEN_FAILED, owner.state.value.error!!.code)
            assertNull(owner.state.value.preview)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun moduleCannotPublishAPreviewWithAnotherGeometry() = runBlocking {
        val scope = scope(); val module = StubModule(); val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("test"), module)).join()
            awaitState { owner.state.value.preview != null }
            module.last!!.state.value = module.last!!.state.value.copy(geometry = NativeImageGeometry(384, 288))
            awaitState { owner.state.value.error?.code == CameraErrorCode.MODULE_DATA_INVALID }
            assertNull(owner.state.value.preview); assertNull(owner.state.value.measurement)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun moduleCannotPublishAnotherModulesMeasurementProvenance() = runBlocking {
        val scope = scope()
        val metadata = CameraModuleMetadata(CameraModuleId("thermal-test"), "thermal-test",
            CameraCapabilities(true, true, false, true))
        val module = StubModule(metadata = metadata); val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("test"), module)).join()
            awaitState { owner.state.value.preview != null }
            val geometry = module.last!!.geometry
            for (provenance in listOf(
                MeasurementProvenance(CameraModuleId("other-module"), metadata.modelId, TemperatureProvenanceKind.SIMULATED),
                MeasurementProvenance(metadata.id, "other-model", TemperatureProvenanceKind.SIMULATED))) {
                module.last!!.state.value = module.last!!.state.value.copy(measurement = null)
                awaitState { owner.state.value.preview != null }
                module.last!!.state.value = module.last!!.state.value.copy(measurement =
                    OwnedThermalMeasurement(geometry, FloatArray(geometry.pixelCount) { 25f }, 1, 1, provenance))
                awaitState { owner.state.value.error?.code == CameraErrorCode.MODULE_DATA_INVALID }
                assertNull(owner.state.value.preview); assertNull(owner.state.value.measurement)
            }
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun failedModuleActionClearsMeasurementsAndPublishesAStructuredError() = runBlocking {
        val scope = scope(); val stub = StubModule()
        val module = object : CameraModule<ArgbImage> by stub {
            override suspend fun open(device: CameraDeviceIdentity): CameraSession<ArgbImage> {
                val session = stub.open(device)
                return object : CameraSession<ArgbImage> by session {
                    override suspend fun perform(action: CameraAction) { error("Technical driver failure") }
                }
            }
        }
        val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(identity("test"), module)).join()
            awaitState { owner.state.value.preview != null }
            stub.last!!.state.value = stub.last!!.state.value.copy(actions = setOf(CameraAction.CONNECT, CameraAction.CLOSE))
            awaitState { CameraAction.CONNECT in owner.state.value.actions }
            owner.perform(CameraAction.CONNECT).join()
            assertEquals(CameraErrorCode.STREAM_FAILED, owner.state.value.error!!.code)
            assertNull(owner.state.value.preview); assertNull(owner.state.value.measurement)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun permissionStateIsStructuredAndDoesNotOpenOrInitialize() = runBlocking {
        val scope = scope(); val owner = CameraSessionOwner<ArgbImage>(scope); val module = StubModule()
        try {
            val candidate = CameraCandidate(identity("test"), module)
            owner.permissionRequired(candidate)
            assertEquals(CameraErrorCode.PERMISSION_REQUIRED, owner.state.value.error!!.code)
            owner.permissionDenied(candidate)
            assertEquals(CameraErrorCode.PERMISSION_DENIED, owner.state.value.error!!.code)
            assertEquals(0, module.opens); assertEquals(0, module.actions)
        } finally { owner.close().join(); scope.cancel() }
    }
}

class CameraCapabilitiesSimulationTest {
    @Test fun ht301CapabilitiesDescribeOnlyImplementedOperations() {
        val capabilities = Ht301ModuleProfile.metadata.capabilities
        assertTrue(capabilities.preview); assertTrue(capabilities.temperatureMeasurement)
        assertTrue(capabilities.explicitMeasurementInitialization); assertTrue(capabilities.touchInspection)
    }
    @Test fun simulatedPreviewStreamsDifferentGeometryAndCleansUp() = runBlocking {
        val module = SimulatedCameraModule(previewFactory = { image: ArgbImage -> image }, frameIntervalMs = 10)
        val session = module.open(SimulatedCameraIdentity())
        try {
            awaitState { (session.state.value.preview?.sequence ?: 0) >= 2 }
            val state = session.state.value
            assertEquals(NativeImageGeometry(160,120), state.geometry)
            assertEquals(19200, state.preview!!.image.pixels().size)
            assertFalse(CameraUiPolicy.showTemperatureControls(state)); assertFalse(CameraUiPolicy.showInitialize(state))
            assertFalse(CameraUiPolicy.canInspect(state)); assertNull(state.measurement)
            assertEquals(setOf(CameraAction.CLOSE), state.actions)
            assertFalse(CameraUiPolicy.canPerform(state, CameraAction.INITIALIZE_MEASUREMENT))
        } finally { session.close(CameraCloseReason.USER) }
        val last = session.state.value
        delay(30); assertSame(last, session.state.value)
        assertEquals(CameraLifecycle.CLOSED, last.lifecycle); assertNull(last.preview)
    }
    @Test fun unsupportedActionsCannotReachASimulatedModule() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val module = SimulatedCameraModule(previewFactory = { image: ArgbImage -> image }, frameIntervalMs = 10)
        val owner = CameraSessionOwner<ArgbImage>(scope)
        try {
            owner.open(CameraCandidate(SimulatedCameraIdentity(), module)).join()
            awaitState { owner.state.value.preview != null }
            owner.perform(CameraAction.INITIALIZE_MEASUREMENT).join()
            assertEquals(CameraErrorCode.ACTION_UNAVAILABLE, owner.state.value.error!!.code)
            assertNull(owner.state.value.measurement)
        } finally { owner.close().join(); scope.cancel() }
    }
    @Test fun capabilityPolicyRejectsAnErroneouslyAdvertisedUnsupportedAction() {
        val state = CameraSessionState<ArgbImage>(capabilities = CameraCapabilities(true, false, false, false),
            actions = setOf(CameraAction.INITIALIZE_MEASUREMENT))
        assertFalse(CameraUiPolicy.canPerform(state, CameraAction.INITIALIZE_MEASUREMENT))
    }
}
