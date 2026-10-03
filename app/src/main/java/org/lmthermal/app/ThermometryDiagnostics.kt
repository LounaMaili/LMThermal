package org.lmthermal.app

import org.lmthermal.core.*

/** Numeric-only evidence: no raw payload, scene image, serial or image hash is retained. */
object ThermometryDiagnostics {
    /** Expose original inputs and results so mobile discrepancies can be traced without private captures. */
    fun event(result: MeasurementResult, state: SessionState, sequence: Long, now: Long, elapsedNs: Long): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>("event" to "measurement", "sequence" to sequence,
            "monotonic_ms" to now, "session_state" to state.name, "available" to (result.measurement != null),
            "reason" to result.reason, "measurement_ms" to elapsedNs / 1e6,
            "warning" to NativeEquivalentThermometry.WARNING)
        result.measurement?.let { m ->
            val t = m.lookupTrace; val p = m.parameters
            fields["raw_range"] = listOf(m.rawMinimum, m.rawMaximum)
            fields["matrix_range"] = listOf(m.matrixMinimum, m.matrixMaximum)
            fun point(point: TemperaturePoint) = mapOf("index" to point.index, "celsius" to point.celsius, "x" to point.x, "y" to point.y)
            fields["trailer_center"] = point(m.trailerCenter)
            fields["literal_center"] = point(m.literalCenter)
            fields["high"] = point(m.high); fields["low"] = point(m.low)
            fields["parameters"] = mapOf("correction" to p.correction, "reflected" to p.reflected,
                "ambient" to p.ambient, "humidity" to p.humidity, "emissivity" to p.emissivity,
                "distance" to p.distance, "calibration" to listOf(p.c0, p.c1, p.c2, p.c3, p.c4))
            fields["trace"] = mapOf("host_range" to t.configuration.range, "host_lens" to t.configuration.lens,
                "host_shutter_fix" to t.configuration.shutterFix, "fpa_word" to t.fpaWord, "fpa_term" to t.fpaTerm,
                "calibration_word" to t.calibrationWord, "calibration_temperature" to t.calibrationTemperature,
                "get_fix" to t.getFix, "adjusted_lookup_base" to t.adjustedLookupBase,
                "init_a" to t.initA, "init_b" to t.initB, "linear" to t.linear, "constant" to t.constant,
                "effective_native_distance" to t.effectiveNativeDistance,
                "calc_fix_raw" to listOf(t.environment.water, t.environment.transmission, t.environment.inverse, t.environment.radiation),
                "finite_lookup_entries" to t.finiteLookupEntries)
        }
        return fields
    }
}
