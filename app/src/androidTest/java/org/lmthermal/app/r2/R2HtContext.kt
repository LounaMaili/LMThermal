package org.lmthermal.app.r2

import org.lmthermal.core.*

/** Disposable zero-serialization adapter of the existing HT301 metadata mapping.
 * Every existing calibration/settings/trace/observation field is retained. Tests compare
 * semantic JSON equality with Ht301ExportEvidence; no algorithm or source value changes.
 */
object R2HtContext {
    fun metadata(measurement: RadiometricMeasurement): Map<String, Any?> {
        val p = measurement.parameters; val t = measurement.lookupTrace; val c = t.configuration; val e = t.environment
        fun point(p: TemperaturePoint): Map<String, Any?> = buildMap {
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
        return metadata
    }
}
