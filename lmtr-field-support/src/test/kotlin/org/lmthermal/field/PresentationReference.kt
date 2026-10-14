package org.lmthermal.field

import org.lmthermal.camera.ThermalMeasurement
import org.lmthermal.core.*

/** Exact e98ce49 presentation algorithm, test-only baseline; never a second thermometry implementation. */
object PresentationReference {
    const val BASE = "e98ce49cd8e899e718a48b45b17092edf1fc99b7"
    fun range(matrix: FloatArray, geometry: NativeImageGeometry): CelsiusRange {
        require(matrix.size == geometry.pixelCount && matrix.all { it.isFinite() })
        val sorted = matrix.sortedArray()
        fun percentile(fraction: Double): Double {
            val rank = (sorted.size - 1) * fraction; val index = rank.toInt(); val weight = rank - index
            val a = sorted[index]; val b = sorted[minOf(index + 1, sorted.lastIndex)]
            val difference = (b - a).toDouble()
            return if (weight >= .5) b.toDouble() - difference * (1 - weight) else a.toDouble() + difference * weight
        }
        var lower = percentile(.02); var upper = percentile(.98)
        if (upper - lower < 1.0) { val middle = (lower + upper) / 2; lower = middle - .5; upper = middle + .5 }
        return CelsiusRange(lower, upper)
    }
    /** Preserve the former two validation scans, color ownership and public defensive-copy overhead. */
    fun pixels(measurement: ThermalMeasurement): IntArray {
        val matrix = measurement.matrix()
        require(matrix.size == measurement.geometry.pixelCount && matrix.all { it.isFinite() })
        val bounds = range(matrix, measurement.geometry)
        val pixels = IntArray(matrix.size) { CelsiusPalette.INFERNO.argb(CelsiusRenderer.level(matrix[it].toDouble(), bounds)) }
        return pixels.copyOf()
    }
}
