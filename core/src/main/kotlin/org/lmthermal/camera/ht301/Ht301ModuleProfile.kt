package org.lmthermal.camera.ht301

import org.lmthermal.camera.*
import org.lmthermal.core.*

/** HT-301 identification and capability declaration; probing does not instantiate the validated engine/USB source. */
object Ht301ModuleProfile : CameraModuleDefinition {
    val ID = CameraModuleId("ht301")
    val GEOMETRY = NativeImageGeometry(Ht301Layout.WIDTH, Ht301Layout.IMAGE_HEIGHT)
    override val metadata = CameraModuleMetadata(ID, "HT-301/T3-317-13",
        CameraCapabilities(preview = true, temperatureMeasurement = true,
            explicitMeasurementInitialization = true, touchInspection = true))
    override fun probe(device: CameraDeviceIdentity): CameraProbeResult =
        if (device is UsbCameraIdentity && device.vendorId == Ht301Layout.VID && device.productId == Ht301Layout.PID)
            CameraProbeResult.SUPPORTED else CameraProbeResult.UNSUPPORTED
}

/** Zero-reinterpretation adapter: the original raw/calibration/trailer/LUT evidence remains owned and accessible.
 * Shared consumers see only native geometry/Celsius/extrema and optional namespaced sample evidence.
 */
class Ht301ThermalMeasurement(val evidence: RadiometricMeasurement) : ThermalMeasurement {
    override val geometry = Ht301ModuleProfile.GEOMETRY
    override val sequence get() = evidence.sequence
    override val receivedMonotonicMs get() = evidence.receivedMonotonicMs
    override val validity = MeasurementValidity.VALID
    override val provenance = MeasurementProvenance(Ht301ModuleProfile.ID, Ht301ModuleProfile.metadata.modelId,
        TemperatureProvenanceKind.NATIVE_EQUIVALENT)
    override val high = MeasurementPoint(geometry.pixel(evidence.high.x!!, evidence.high.y!!), evidence.high.celsius)
    override val low = MeasurementPoint(geometry.pixel(evidence.low.x!!, evidence.low.y!!), evidence.low.celsius)
    override val matrixMinimum get() = evidence.matrixMinimum
    override val matrixMaximum get() = evidence.matrixMaximum
    override fun matrix(): FloatArray = evidence.matrix()
    override fun temperature(pixel: NativePixel): Float {
        geometry.offset(pixel)
        return evidence.temperature(pixel.x, pixel.y)
    }
    override fun sample(pixel: NativePixel): NativeSample {
        geometry.offset(pixel)
        return NativeSample("ht301.raw14", evidence.source.pixel(pixel.x, pixel.y))
    }
}
