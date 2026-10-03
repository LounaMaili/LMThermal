package org.lmthermal.exchange

import org.lmthermal.camera.*
import org.lmthermal.core.*

/** Freezes one retained immutable source state and UI choices before destination interaction.
 * Published module data are immutable by contract. New frames/close cannot change a retained request.
 * No previous presentation/measurement fallback and no module-specific persistence cast exists here.
 */
object CaptureFreeze {
    fun canCapture(source: CameraSessionState<*>): Boolean = source.lifecycle == CameraLifecycle.STREAMING &&
        source.module != null && source.geometry != null && (source.preview != null || source.measurement?.validity == MeasurementValidity.VALID)

    fun <P> freeze(source: CameraSessionState<P>, selection: NativeRoiSelection, settings: CelsiusPresentationSettings,
        point: NativePixel?, producer: CaptureProducer, creation: CaptureClock, domain: String,
        png: (NativeImageGeometry, IntArray) -> ByteArray, sourcePreviewPng: (P) -> ByteArray): LmtxCapture {
        demand(canCapture(source), "invalid_payload", "No current capture")
        val module = source.module!!; val geometry = source.geometry!!
        demand(geometry.pixelCount <= 4_194_304, "resource_limit", "Local 4M pixel capture limit")
        val measurement = source.measurement?.takeIf { it.validity == MeasurementValidity.VALID &&
            it.geometry == geometry && it.provenance.moduleId == module.id && it.provenance.modelId == module.modelId }
        val evidence = (measurement as? ExportEvidenceProvider)?.exportEvidence() ?: SourceExportEvidence()
        val temperature = measurement?.let { m -> CapturedTemperature(geometry, m.matrix(), m.validityMask(),
            evidence.temperatureProvenance() ?: mapOf("kind" to m.provenance.kind.name.lowercase(java.util.Locale.ROOT),
                "physical_accuracy" to if (m.provenance.kind == TemperatureProvenanceKind.SIMULATED) "not_applicable" else "unknown")) }
        val rendered = temperature?.render(settings)
        val preview = if (temperature != null) rendered?.let { png(geometry, it.argb()) } else source.preview?.takeIf { it.geometry == geometry }?.let { sourcePreviewPng(it.image) }
        val presentation = buildMap<String, Any?> {
            put("palette_id", settings.palette.name.lowercase(java.util.Locale.ROOT))
            rendered?.let { put("range_mode", if (settings.automatic) "auto" else "manual")
                put("effective_bounds", mapOf("min" to it.range.lower, "max" to it.range.upper, "unit" to "Cel")) }
        }
        val previewPayload = preview?.let { EvidencePayload("preview", "preview/thermal.png", "preview", "image/png", OwnedMember(it),
            mapOf("image" to mapOf("width_px" to geometry.width, "height_px" to geometry.height,
                "coordinate_space" to "native", "orientation" to "stored_pixels"),
                "origin" to if (temperature != null) "frozen_temperature_presentation_without_overlays" else "source_preview_without_overlays")) }
        val key = RoiSourceKey(module.id, module.modelId, source.device?.key)
        val roi = selection.rect?.takeIf { selection.source == key && selection.geometry == geometry }
        val sequence = measurement?.sequence ?: source.preview?.sequence
        val receipt = measurement?.receivedMonotonicMs ?: source.preview?.receivedMonotonicMs
        return LmtxCapture(geometry, CaptureSource(module.id.value, module.modelId, module.sourceOrigin), producer, creation,
            sequence = sequence, receiptMonotonicNs = receipt?.let { Math.multiplyExact(it, 1_000_000) }, clockDomainId = receipt?.let { domain },
            temperature = temperature, preview = previewPayload, evidence = evidence, roi = roi,
            point = point?.takeIf { selection.source == key && geometry.contains(it) }, presentation = presentation,
            capabilities = mapOf("preview" to if (source.capabilities?.preview == true) "supported" else "unknown",
                "temperature" to if (source.capabilities?.temperatureMeasurement == true) "supported" else if (source.capabilities != null) "unsupported" else "unknown"),
            unavailableReason = "not_available_for_frame")
    }
}
