package org.lmthermal.app.r2

import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.r2.*

/** The unchanged pre-optimization intake algorithm, retained for controlled ablations. */
fun freezeLegacy(measurement: Ht301ThermalMeasurement, profile: Profile, originMs: Long): Observation {
        val matrix = StageCosts.timed("matrix_copy", 442368, 442368) { measurement.matrix() }; val temperature = little(matrix.size * 4)
        StageCosts.timed("f32_encode", matrix.size.toLong()*4, matrix.size.toLong()*4) { matrix.forEach(temperature::putFloat) }
        val metadata = StageCosts.timed("context_build") { R2HtContext.metadata(measurement.evidence) }
        return Observation(measurement.sequence, measurement.receivedMonotonicMs * 1000000,
            (measurement.receivedMonotonicMs - originMs).coerceAtLeast(0) * 1000000, measurement.geometry.width, measurement.geometry.height,
            temperature.array(), measurement.validityMask(), if (profile != Profile.ANALYSIS) StageCosts.timed("native_copy", 221184, 221184) { measurement.evidence.source.imageBytes() } else null,
            if (profile == Profile.FULL) StageCosts.timed("transport_copy", 224256, 224256) { measurement.evidence.source.transportBytes() } else null,
            mapOf("module" to "ht301", "provenance" to "real-ht301-native-equivalent", "metadata" to metadata,
                "warning" to "Native-equivalent temperatures; absolute physical accuracy not yet independently validated.")).also { StageCosts.timed("context_encode") { it.contextBytes } }
    }
