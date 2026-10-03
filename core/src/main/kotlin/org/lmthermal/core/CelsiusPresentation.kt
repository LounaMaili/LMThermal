package org.lmthermal.core

import org.lmthermal.camera.ThermalMeasurement
import org.lmthermal.camera.MeasurementValidity

/** Exact display bounds; these never clamp or replace measurement values. */
data class CelsiusRange(val lower: Double, val upper: Double) {
    init { require(lower.isFinite() && upper.isFinite() && lower < upper) { "Use finite minimum < maximum" } }
    /** Five legend labels use the actual rendered Celsius bounds, not raw indices. */
    fun ticks(): List<Double> = List(5) { lower + (upper - lower) * it / 4.0 }
}

/** Desktop palettes; Iron-like is OpenCV HOT, not a newly invented iron interpolation. */
enum class CelsiusPalette {
    WHITE_HOT, BLACK_HOT, INFERNO, IRON_LIKE, TURBO;
    /** Tables copied from Desktop OpenCV RGB output. Do not confuse its BGR transport with ARGB. */
    fun argb(level: Int): Int {
        require(level in 0..255)
        return when (this) {
            WHITE_HOT -> gray(level)
            BLACK_HOT -> gray(255 - level)
            INFERNO -> PaletteTables.inferno[level]
            IRON_LIKE -> PaletteTables.hot[level]
            TURBO -> PaletteTables.turbo[level]
        }
    }
    private fun gray(level: Int) = (0xff shl 24) or (level shl 16) or (level shl 8) or level
}

/** Small immutable presentation settings; live calibration remains entirely outside this layer. */
data class CelsiusPresentationSettings(val palette: CelsiusPalette = CelsiusPalette.INFERNO,
    val automatic: Boolean = true, val locked: CelsiusRange = CelsiusRange(25.0, 45.0))

/** Owned rendered output. Celsius evidence remains in the supplied measurement, not these pixels. */
class CelsiusRender internal constructor(private val pixels: IntArray, val range: CelsiusRange,
    val palette: CelsiusPalette) {
    fun argb(): IntArray = pixels.copyOf()
    /** Horizontal legend is cold-at-left, with exactly the same 256-entry palette as the image. */
    fun legend(): IntArray = IntArray(256) { palette.argb(it) }
}

/** JVM-testable port of Desktop celsius_palette.py. All normalization is display-only. */
object CelsiusRenderer {
    private const val MINIMUM_AUTO_SPAN = 1.0
    /** Reject wrong dimensions and nonfinite measurements even in locked mode. */
    private fun validate(matrix: FloatArray, geometry: NativeImageGeometry) {
        require(matrix.size == geometry.pixelCount) { "Matrix size does not match native geometry" }
        require(matrix.all { it.isFinite() }) { "Nonfinite Celsius matrix" }
    }
    /** NumPy linear 2nd/98th percentiles; a small span expands symmetrically about their midpoint. */
    fun autoRange(matrix: FloatArray, geometry: NativeImageGeometry): CelsiusRange {
        validate(matrix, geometry)
        val sorted = matrix.sortedArray()
        var lower = percentile(sorted, .02); var upper = percentile(sorted, .98)
        if (upper - lower < MINIMUM_AUTO_SPAN) {
            val middle = (lower + upper) / 2
            lower = middle - .5; upper = middle + .5
        }
        return CelsiusRange(lower, upper)
    }
    /** Preserve NumPy Float32 difference and Double interpolation, including its upper-half lerp branch. */
    private fun percentile(sorted: FloatArray, fraction: Double): Double {
        val rank = (sorted.size - 1) * fraction
        val index = rank.toInt(); val weight = rank - index
        val a = sorted[index]; val b = sorted[minOf(index + 1, sorted.lastIndex)]
        val difference = (b - a).toDouble()
        return if (weight >= .5) b.toDouble() - difference * (1 - weight)
            else a.toDouble() + difference * weight
    }
    /** Locked bounds are exact, regardless of image extrema. Matrix validity is still required. */
    fun effectiveRange(matrix: FloatArray, geometry: NativeImageGeometry, settings: CelsiusPresentationSettings): CelsiusRange {
        validate(matrix, geometry)
        return if (settings.automatic) autoRange(matrix, geometry) else settings.locked
    }
    /** Match NumPy float64 normalization/clip/rint, including round-to-even at half-integer indices. */
    fun level(celsius: Double, range: CelsiusRange): Int {
        require(celsius.isFinite())
        val scaled = (celsius - range.lower) * (255.0 / (range.upper - range.lower))
        return Math.rint(scaled.coerceIn(0.0, 255.0)).toInt()
    }
    /** A renderer owns only a matrix copy; it cannot modify source samples or measurement evidence. */
    fun render(measurement: ThermalMeasurement, settings: CelsiusPresentationSettings): CelsiusRender {
        require(measurement.validity == MeasurementValidity.VALID)
        return render(measurement.matrix(), measurement.geometry, settings)
    }
    /** Matrix overload supports deterministic Desktop presentation goldens without hardware. */
    fun render(matrix: FloatArray, geometry: NativeImageGeometry, settings: CelsiusPresentationSettings): CelsiusRender {
        val range = effectiveRange(matrix, geometry, settings)
        return CelsiusRender(IntArray(matrix.size) { settings.palette.argb(level(matrix[it].toDouble(), range)) }, range, settings.palette)
    }
}
