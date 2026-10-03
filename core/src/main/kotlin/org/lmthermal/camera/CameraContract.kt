package org.lmthermal.camera

import kotlinx.coroutines.flow.StateFlow
import org.lmthermal.core.NativeImageGeometry
import org.lmthermal.core.NativePixel

/** Stable untranslated identity, independent of any resource/display label. */
@JvmInline
value class CameraModuleId(val value: String) {
    init { require(value.matches(Regex("[a-z0-9][a-z0-9.-]*"))) }
}

/** Discovery evidence only. A future network/SDK module can supply another identity implementation. */
interface CameraDeviceIdentity { val key: String }

/** USB identification is optional transport evidence, not a requirement of a camera/session. */
data class UsbCameraIdentity(override val key: String, val vendorId: Int, val productId: Int) : CameraDeviceIdentity {
    init { require(key.isNotBlank() && vendorId in 0..65535 && productId in 0..65535) }
}

/** Deliberately small capability set: do not advertise settings/NUC APIs that are not implemented. */
data class CameraCapabilities(val preview: Boolean, val temperatureMeasurement: Boolean,
    val explicitMeasurementInitialization: Boolean, val touchInspection: Boolean) {
    init {
        require(!touchInspection || temperatureMeasurement)
        require(!explicitMeasurementInitialization || temperatureMeasurement)
    }
}

/** Model IDs describe hardware/data provenance; translated product labels belong to Android resources. */
data class CameraModuleMetadata(val id: CameraModuleId, val modelId: String,
    val capabilities: CameraCapabilities) {
    init { require(modelId.isNotBlank()) }
}

enum class CameraProbeResult { UNSUPPORTED, SUPPORTED }

/** Probe is pure identification: it must never open a device, start acquisition or send controls. */
interface CameraModuleDefinition {
    val metadata: CameraModuleMetadata
    fun probe(device: CameraDeviceIdentity): CameraProbeResult
}

/** Integrated module factory. Opening follows explicit Connect; it does not initialize measurement. */
interface CameraModule<P> : CameraModuleDefinition {
    suspend fun open(device: CameraDeviceIdentity): CameraSession<P>
}

enum class CameraAction { CONNECT, CLOSE, INITIALIZE_MEASUREMENT }
enum class CameraLifecycle { DISCONNECTED, DETECTED, PERMISSION_PENDING, OPENING, STREAMING, CLOSED, ERROR }
enum class CameraStatusCode { NO_CAMERA, DETECTED, PERMISSION_REQUIRED, OPENING, PREVIEW_ONLY,
    INITIALIZING, MEASUREMENT_READY, MEASUREMENT_UNAVAILABLE, CLOSED, ERROR }
enum class CameraErrorCode { CAMERA_NOT_FOUND, CAMERA_UNSUPPORTED, CAMERA_MATCH_AMBIGUOUS,
    PERMISSION_REQUIRED, PERMISSION_DENIED, STREAM_OPEN_FAILED, STREAM_FAILED, MODULE_DATA_INVALID, ACTION_UNAVAILABLE }
enum class CameraCloseReason { USER, REPLACED, DETACHED, BACKGROUND, DISPOSED }

/** Module-specific machine status remains typed/namespaced; common consumers do not interpret protocol states. */
interface ModuleStatusIdentifier {
    val moduleId: CameraModuleId
    val machineCode: String
}

/** Parameters are machine facts/IDs, not sentences or exception messages. Android resolves localized resources. */
data class CameraStatus(val code: CameraStatusCode, val detail: ModuleStatusIdentifier? = null,
    val parameters: Map<String, String> = emptyMap())
data class CameraError(val code: CameraErrorCode, val parameters: Map<String, String> = emptyMap())

/** A preview carries its own geometry and receipt metadata; P can be an Android bitmap or a test pixel plane. */
data class CameraPreview<P>(val geometry: NativeImageGeometry, val sequence: Long,
    val receivedMonotonicMs: Long, val image: P)

/** Acquisition diagnostics, independent of rendered FPS and transport format. */
data class CameraStatistics(val callbackFps: Double = 0.0, val received: Long = 0,
    val replaced: Long = 0, val malformed: Long = 0)

/** Common snapshot excludes protocol bytes, control values, trailer/calibration layouts and raw encoding. */
data class CameraSessionState<P>(
    val module: CameraModuleMetadata? = null,
    val device: CameraDeviceIdentity? = null,
    val lifecycle: CameraLifecycle = CameraLifecycle.DISCONNECTED,
    val capabilities: CameraCapabilities? = module?.capabilities,
    val geometry: NativeImageGeometry? = null,
    val status: CameraStatus = CameraStatus(CameraStatusCode.NO_CAMERA),
    val error: CameraError? = null,
    val preview: CameraPreview<P>? = null,
    val measurement: ThermalMeasurement? = null,
    val actions: Set<CameraAction> = emptySet(),
    val statistics: CameraStatistics = CameraStatistics(),
)

/** One owned source. close must await acquisition release before another source may open. */
interface CameraSession<P> {
    val state: StateFlow<CameraSessionState<P>>
    suspend fun perform(action: CameraAction)
    suspend fun close(reason: CameraCloseReason)
}

/** Single shared UI policy; unsupported controls are absent rather than model-name special cases. */
object CameraUiPolicy {
    fun showTemperatureControls(state: CameraSessionState<*>): Boolean =
        state.capabilities?.temperatureMeasurement == true
    fun showInitialize(state: CameraSessionState<*>): Boolean =
        state.capabilities?.explicitMeasurementInitialization == true
    fun canInspect(state: CameraSessionState<*>): Boolean =
        state.capabilities?.touchInspection == true && state.measurement?.validity == MeasurementValidity.VALID
    fun canPerform(state: CameraSessionState<*>, action: CameraAction): Boolean =
        action in state.actions && (action != CameraAction.INITIALIZE_MEASUREMENT || showInitialize(state))
}

enum class NativeCoordinateSystem { SENSOR_ROW_MAJOR }
enum class MeasurementValidity { VALID, INVALID }
enum class TemperatureProvenanceKind { NATIVE_EQUIVALENT, SIMULATED, DEVICE_REPORTED }

/** No assumed raw encoding or physical calibration claim is imposed on another module. */
data class MeasurementProvenance(val moduleId: CameraModuleId, val modelId: String,
    val kind: TemperatureProvenanceKind)
data class MeasurementPoint(val pixel: NativePixel, val celsius: Float)
/** Optional opaque/native sample evidence; encoding IDs are namespaced and untranslated. */
data class NativeSample(val encodingId: String, val value: Int)

/** Validity-qualified native Celsius data with copy-only matrix access.
 * A module may retain richer immutable evidence in its own implementation. The common UI needs no raw plane.
 */
interface ThermalMeasurement {
    val geometry: NativeImageGeometry
    val sequence: Long
    val receivedMonotonicMs: Long
    val coordinateSystem: NativeCoordinateSystem get() = NativeCoordinateSystem.SENSOR_ROW_MAJOR
    val validity: MeasurementValidity
    val provenance: MeasurementProvenance
    val high: MeasurementPoint
    val low: MeasurementPoint
    val matrixMinimum: Float
    val matrixMaximum: Float
    fun matrix(): FloatArray
    fun temperature(pixel: NativePixel): Float
    fun sample(pixel: NativePixel): NativeSample? = null
}

/** Owned generic finite matrix for modules/tests; validates extrema against the actual native pixel plane. */
class OwnedThermalMeasurement(override val geometry: NativeImageGeometry, matrix: FloatArray,
    override val sequence: Long, override val receivedMonotonicMs: Long,
    override val provenance: MeasurementProvenance) : ThermalMeasurement {
    private val values = matrix.copyOf()
    override val validity = MeasurementValidity.VALID
    override val matrixMinimum: Float
    override val matrixMaximum: Float
    override val high: MeasurementPoint
    override val low: MeasurementPoint
    init {
        require(values.size == geometry.pixelCount && values.all { it.isFinite() })
        val highIndex = values.indices.maxBy { values[it] }; val lowIndex = values.indices.minBy { values[it] }
        matrixMaximum = values[highIndex]; matrixMinimum = values[lowIndex]
        high = MeasurementPoint(geometry.pixel(highIndex % geometry.width, highIndex / geometry.width), matrixMaximum)
        low = MeasurementPoint(geometry.pixel(lowIndex % geometry.width, lowIndex / geometry.width), matrixMinimum)
    }
    override fun matrix(): FloatArray = values.copyOf()
    override fun temperature(pixel: NativePixel): Float = values[geometry.offset(pixel)]
}

/** Immutable preview pixel storage for the simulator/reference adapters; no thermal meaning is assigned. */
class ArgbImage(val geometry: NativeImageGeometry, pixels: IntArray) {
    private val values = pixels.copyOf()
    init { require(values.size == geometry.pixelCount) }
    fun pixels(): IntArray = values.copyOf()
}
