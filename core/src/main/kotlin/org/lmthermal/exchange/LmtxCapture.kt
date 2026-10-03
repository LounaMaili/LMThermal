package org.lmthermal.exchange

import java.io.OutputStream
import java.util.UUID
import org.lmthermal.core.*

/** Immutable repeatable member bytes; owned once, streamed through hashing/compression without a ZIP-sized buffer. */
interface MemberBytes { val size: Long; fun writeTo(output: OutputStream) }
class OwnedMember(bytes: ByteArray) : MemberBytes {
    private val value = bytes.copyOf()
    override val size get() = value.size.toLong()
    override fun writeTo(output: OutputStream) { output.write(value) }
}

/** Module-owned optional evidence descriptors use generic wire roles and finite declarative metadata. */
class EvidencePayload(val id: String, val member: String, val role: String, val mediaType: String,
    val bytes: MemberBytes, attributes: JsonObject = emptyMap()) {
    private val metadata = LmtxJson.encode(attributes)
    fun attributes(): JsonObject = LmtxJson.decode(metadata)
}
class SourceExportEvidence(payloads: List<EvidencePayload> = emptyList(), extensions: List<JsonObject> = emptyList(),
    settingsExtensionIds: List<String> = emptyList(), capabilities: Map<String, String> = emptyMap(),
    temperatureProvenance: JsonObject? = null) {
    val payloads = payloads.toList()
    private val extensionJson = extensions.map(LmtxJson::encode)
    val settingsExtensionIds = settingsExtensionIds.toList()
    val capabilities = capabilities.toMap()
    private val provenanceBytes = temperatureProvenance?.let(LmtxJson::encode)
    fun temperatureProvenance(): JsonObject? = provenanceBytes?.let(LmtxJson::decode)
    fun extensions() = extensionJson.map(LmtxJson::decode)
}

/** Implemented by a module's immutable measurement adapter, never by a common camera-name switch.
 * It must return coherent owned evidence from that measurement, without USB I/O or thermometry recomputation.
 */
interface ExportEvidenceProvider { fun exportEvidence(): SourceExportEvidence }

/** Clock quality is explicit. Host creation/receipt clocks never stand in for sensor acquisition UTC. */
data class CaptureClock(val status: String = "unknown", val source: String = "unknown",
    val utc: String? = null, val reason: String? = "not_exposed") {
    fun json(): JsonObject = buildMap {
        put("status", status); put("clock_source", source)
        utc?.let { put("utc", it) }; reason?.let { put("reason", it) }
    }
}
data class CaptureProducer(val applicationId: String, val version: String) {
    fun json(): JsonObject = mapOf("application_id" to applicationId, "version" to version)
}
data class CaptureSource(val moduleId: String, val modelId: String, val origin: String = "unknown") {
    fun json(): JsonObject = mapOf("module_id" to moduleId, "model_id" to modelId, "origin" to origin)
}

/** Owns exact Float32 values and optional mask. Invalid filler is canonicalized only on original serialization.
 * Statistics are computed against the same owned plane and valid-cell authority, never device summaries.
 */
class CapturedTemperature(val geometry: NativeImageGeometry, values: FloatArray, mask: ByteArray? = null,
    provenance: JsonObject) : MemberBytes {
    private val values = values.copyOf()
    private val mask = mask?.copyOf()
    private val provenanceBytes = LmtxJson.encode(provenance)
    val fullStatistics = statistics(NativeRect(0, 0, geometry.width, geometry.height))
    val validity: String get() = when (fullStatistics.validPixelCount) {
        0 -> "no_valid_pixels"; geometry.pixelCount -> "all_valid"; else -> "partially_valid"
    }
    override val size get() = geometry.pixelCount.toLong() * 4
    fun provenance() = LmtxJson.decode(provenanceBytes)
    fun validityMember(): MemberBytes? = mask?.let(::OwnedMember)
    fun statistics(rect: NativeRect) = RoiStatistics.calculate(geometry, values, rect, mask)
    fun temperature(pixel: NativePixel): Float? = geometry.offset(pixel).let {
        if (mask != null && mask[it].toInt() == 0) null else values[it]
    }
    /** Optional preview comes only from this frozen plane. Invalid pixels get a distinct diagnostic checker pattern. */
    fun render(settings: CelsiusPresentationSettings): CelsiusRender? {
        if (fullStatistics.validPixelCount == 0) return null
        val valid = if (mask == null) values else values.filterIndexed { i, _ -> mask[i].toInt() == 1 }.toFloatArray()
        val range = if (settings.automatic) CelsiusRenderer.autoRange(valid, NativeImageGeometry(valid.size, 1)) else settings.locked
        return CelsiusRender(IntArray(values.size) { i ->
            if (mask != null && mask[i].toInt() == 0) if ((i % geometry.width + i / geometry.width) % 2 == 0) 0xffff00ff.toInt() else 0xff808080.toInt()
            else settings.palette.argb(CelsiusRenderer.level(values[i].toDouble(), range))
        }, range, settings.palette)
    }
    override fun writeTo(output: OutputStream) {
        val buffer = ByteArray(8192); var used = 0
        values.forEachIndexed { index, value ->
            val bits = if (mask != null && mask[index].toInt() == 0) 0 else value.toRawBits()
            for (shift in 0..24 step 8) buffer[used++] = (bits ushr shift).toByte()
            if (used == buffer.size) { output.write(buffer); used = 0 }
        }
        if (used != 0) output.write(buffer, 0, used)
    }
}

/** Immutable acquisition capture. No live controller, Bitmap, array alias or previous-ready result survives here. */
class LmtxCapture(val geometry: NativeImageGeometry, val source: CaptureSource, val producer: CaptureProducer,
    val creation: CaptureClock, val acquisition: CaptureClock = CaptureClock(),
    val captureId: String = UUID.randomUUID().toString(), val sequence: Long? = null,
    val receiptMonotonicNs: Long? = null, val clockDomainId: String? = null,
    val temperature: CapturedTemperature? = null, val preview: EvidencePayload? = null,
    val evidence: SourceExportEvidence = SourceExportEvidence(), val roi: NativeRect? = null,
    val point: NativePixel? = null, presentation: JsonObject = emptyMap(),
    capabilities: Map<String, String> = emptyMap(), val unavailableReason: String = "not_available_for_frame") {
    private val presentationBytes = LmtxJson.encode(presentation)
    val capabilities = capabilities.toMap()
    fun presentation() = LmtxJson.decode(presentationBytes)
    init {
        demand(geometry.pixelCount <= 33_554_432, "resource_limit")
        demand(temperature == null || temperature.geometry == geometry)
        roi?.validate(geometry); point?.let { require(geometry.contains(it)) }
        demand(temperature != null || preview != null || evidence.payloads.any { it.role in setOf("native_samples", "acquisition", "visible_image") },
            "invalid_payload", "No current source payload")
        demand(sequence == null || sequence >= 0); demand(receiptMonotonicNs == null || receiptMonotonicNs >= 0)
        demand((receiptMonotonicNs == null) == (clockDomainId == null))
    }
}

/** Canonical statistics mapping shares the ROI engine; zero-valid results intentionally omit all five numbers/locations. */
fun RoiStatistics.exchangeJson(): JsonObject = buildMap {
    put("temperature_payload_id", "temperature"); put("pixel_count", pixelCount); put("valid_pixel_count", validPixelCount)
    put("method", RoiStatistics.METHOD); put("unit", "Cel")
    if (validPixelCount > 0) {
        put("min", minC); put("max", maxC); put("mean", meanC)
        put("min_xy", listOf(minPixel!!.x, minPixel.y)); put("max_xy", listOf(maxPixel!!.x, maxPixel.y))
    }
}
