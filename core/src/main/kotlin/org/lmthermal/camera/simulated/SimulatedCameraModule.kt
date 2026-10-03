package org.lmthermal.camera.simulated

import kotlinx.coroutines.*
import org.lmthermal.camera.*
import org.lmthermal.core.LatestFrameState
import org.lmthermal.core.NativeImageGeometry

/** Test-only identity: this module must never match a real USB device or advertise a commercial camera. */
data class SimulatedCameraIdentity(override val key: String = "test-preview", val profile: String = "preview-only") : CameraDeviceIdentity

/** Integrated preview-only simulator deliberately differs in dimensions/capabilities and imports no HT-301 code.
 * The factory is generic so JVM uses owned pixels and Android uses a bitmap without protocol dependencies.
 */
class SimulatedCameraModule<P>(private val previewFactory: (ArgbImage) -> P,
    private val frameIntervalMs: Long = 100, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : CameraModule<P> {
    val geometry = NativeImageGeometry(160, 120)
    override val metadata = CameraModuleMetadata(CameraModuleId("simulated-preview"), "simulated-160x120",
        CameraCapabilities(preview = true, temperatureMeasurement = false,
            explicitMeasurementInitialization = false, touchInspection = false))
    init { require(frameIntervalMs > 0) }
    override fun probe(device: CameraDeviceIdentity) = if (device is SimulatedCameraIdentity && device.profile == "preview-only")
        CameraProbeResult.SUPPORTED else CameraProbeResult.UNSUPPORTED
    override suspend fun open(device: CameraDeviceIdentity): CameraSession<P> {
        require(probe(device) == CameraProbeResult.SUPPORTED)
        return Session(device)
    }
    private inner class Session(private val device: CameraDeviceIdentity) : CameraSession<P> {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        private val latest = LatestFrameState(CameraSessionState<P>(module = metadata, device = device,
            lifecycle = CameraLifecycle.OPENING, geometry = geometry, status = CameraStatus(CameraStatusCode.OPENING)))
        override val state = latest.asStateFlow()
        private val job = scope.launch {
            var sequence = 0L
            while (isActive) {
                val pixels = IntArray(geometry.pixelCount) { i ->
                    val value = ((i % geometry.width) + sequence.toInt()) % 256
                    (0xff shl 24) or (value shl 16) or (value shl 8) or value
                }
                val preview = CameraPreview(geometry, ++sequence, clock(), previewFactory(ArgbImage(geometry, pixels)))
                latest.updateIf({ isActive }) { it.copy(lifecycle = CameraLifecycle.STREAMING,
                    status = CameraStatus(CameraStatusCode.PREVIEW_ONLY), preview = preview,
                    actions = setOf(CameraAction.CLOSE), statistics = CameraStatistics(received = sequence)) }
                delay(frameIntervalMs)
            }
        }
        override suspend fun perform(action: CameraAction) { require(action == CameraAction.CLOSE); close(CameraCloseReason.USER) }
        override suspend fun close(reason: CameraCloseReason) {
            scope.cancel(); job.join()
            latest.value = CameraSessionState(module = metadata, device = device, geometry = geometry,
                lifecycle = CameraLifecycle.CLOSED, status = CameraStatus(CameraStatusCode.CLOSED))
        }
    }
}
