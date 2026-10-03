package org.lmthermal.camera

/** Selection retains the exact device/factory pair, never a translated model string. */
data class CameraCandidate<P>(val device: CameraDeviceIdentity, val module: CameraModule<P>)
sealed interface CameraSelection<P> {
    data class None<P>(val error: CameraError) : CameraSelection<P>
    data class Selected<P>(val candidate: CameraCandidate<P>) : CameraSelection<P>
    data class Ambiguous<P>(val candidates: List<CameraCandidate<P>>) : CameraSelection<P>
}

/** Enumerate every support result before choosing. Multiple devices or matching modules are explicit ambiguity. */
class CameraModuleRegistry<P>(modules: List<CameraModule<P>>) {
    private val modules = modules.toList()
    init { require(this.modules.map { it.metadata.id }.distinct().size == this.modules.size) }
    fun select(devices: List<CameraDeviceIdentity>): CameraSelection<P> {
        val candidates = devices.flatMap { device ->
            modules.filter { it.probe(device) == CameraProbeResult.SUPPORTED }.map { CameraCandidate(device, it) }
        }
        return when (candidates.size) {
            0 -> CameraSelection.None(CameraError(if (devices.isEmpty()) CameraErrorCode.CAMERA_NOT_FOUND else CameraErrorCode.CAMERA_UNSUPPORTED))
            1 -> CameraSelection.Selected(candidates.single())
            else -> CameraSelection.Ambiguous(candidates)
        }
    }
    fun select(device: CameraDeviceIdentity): CameraSelection<P> = select(listOf(device))
}
