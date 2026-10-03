package org.lmthermal.camera.ht301

import org.lmthermal.core.*
import org.lmthermal.exchange.*

/** Schema 1.0 of module evidence, not a universal camera contract. Copies only already-authoritative data.
 * Device summaries remain named observations, separate from the exchange's matrix-derived extrema.
 */
object Ht301ExportEvidence {
    const val NAMESPACE = "org.lmthermal.camera.ht301"
    fun freeze(measurement: RadiometricMeasurement): SourceExportEvidence {
        val raw = measurement.source.imageBytes()
        require(raw.indices.step(2).all { i -> ((raw[i].toInt() and 255) or ((raw[i + 1].toInt() and 255) shl 8)) < Ht301Layout.RAW14_LIMIT })
        val p = measurement.parameters; val t = measurement.lookupTrace; val c = t.configuration; val e = t.environment
        fun point(p: TemperaturePoint): JsonObject = buildMap {
            put("raw_index", p.index); put("native_equivalent_c", p.celsius)
            if (p.x != null && p.y != null) put("xy", listOf(p.x, p.y))
        }
        val metadata = mapOf(
            "transport" to mapOf("payload_id" to "acquisition", "image_bytes" to Ht301Layout.IMAGE_BYTES,
                "trailer_bytes" to Ht301Layout.TRAILER_BYTES, "rows" to Ht301Layout.TRANSPORT_HEIGHT),
            "frame_relationship" to mapOf("sequence" to measurement.sequence.toString(), "native_payload_id" to "native"),
            "settings" to mapOf("correction_c" to p.correction, "reflected_c" to p.reflected, "ambient_c" to p.ambient,
                "humidity" to p.humidity, "emissivity" to p.emissivity, "distance" to p.distance),
            "calibration" to listOf(p.c0, p.c1, p.c2, p.c3, p.c4),
            "host_configuration" to mapOf("range" to c.range, "lens" to c.lens, "shutter_fix" to c.shutterFix),
            "lookup_trace" to mapOf("fpa_word" to t.fpaWord, "fpa_term" to t.fpaTerm,
                "calibration_word" to t.calibrationWord, "calibration_temperature_c" to t.calibrationTemperature,
                "get_fix" to t.getFix, "adjusted_lookup_base" to t.adjustedLookupBase, "init_a" to t.initA,
                "init_b" to t.initB, "linear" to t.linear, "constant" to t.constant,
                "effective_native_distance" to t.effectiveNativeDistance, "finite_entries" to t.finiteLookupEntries,
                "environment" to mapOf("water" to e.water, "transmission" to e.transmission, "inverse" to e.inverse, "radiation" to e.radiation)),
            "observations" to mapOf("trailer_center" to point(measurement.trailerCenter),
                "literal_center" to point(measurement.literalCenter), "trailer_high" to point(measurement.high), "trailer_low" to point(measurement.low)),
            "preview_origin" to "frozen_temperature_presentation_without_overlays",
            "algorithm_id" to "org.lmthermal.ht301.native-equivalent-normal",
            "algorithm_version_status" to "not_exposed",
            "physical_accuracy" to "not_independently_validated", "warning" to NativeEquivalentThermometry.WARNING)
        val path = "extensions/" + NAMESPACE + "/metadata.json"
        return SourceExportEvidence(listOf(
            EvidencePayload("native", "data/native.u16le", "native_samples", "application/octet-stream", OwnedMember(raw),
                mapOf("encoding" to "ht301.raw14", "dtype" to "u16", "byte_order" to "little",
                    "shape" to listOf(Ht301Layout.IMAGE_HEIGHT, Ht301Layout.WIDTH), "order" to "row_major", "coordinate_space" to "native")),
            EvidencePayload("acquisition", "data/acquisition.bin", "acquisition", "application/octet-stream",
                OwnedMember(measurement.source.transportBytes()), mapOf("encoding" to "ht301.uvc-yuyv-transport")),
            EvidencePayload("ht301-metadata", path, "extension_json", "application/json", OwnedMember(LmtxJson.encode(metadata)))),
            listOf(mapOf("id" to NAMESPACE, "schema_version" to mapOf("major" to 1, "minor" to 0), "metadata_payload_id" to "ht301-metadata")),
            listOf(NAMESPACE), mapOf("native_samples" to "supported", "acquisition_payload" to "supported", "calibration_settings" to "supported"),
            mapOf("kind" to "native_equivalent", "physical_accuracy" to "not_independently_validated",
                "warning" to NativeEquivalentThermometry.WARNING))
    }
}
