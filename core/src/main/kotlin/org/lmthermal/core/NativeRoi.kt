package org.lmthermal.core

import org.lmthermal.camera.CameraModuleId
import org.lmthermal.camera.MeasurementValidity
import org.lmthermal.camera.ThermalMeasurement

/** Native pixel-cell rectangle, [x1,x2) × [y1,y2), matching LMTX v1 §10.
 * Construction rejects empty/negative/overflowing bounds; validate against the source geometry before use.
 * Persisted/model coordinates are never clamped. UI drag conversion is a separate operation.
 */
data class NativeRect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
    init {
        require(x1 >= 0 && y1 >= 0 && x1 < x2 && y1 < y2)
        require((x2 - x1).toLong() * (y2 - y1) <= Int.MAX_VALUE)
    }
    val width: Int get() = x2 - x1
    val height: Int get() = y2 - y1
    val pixelCount: Int get() = width * height
    fun contains(pixel: NativePixel): Boolean = pixel.x in x1 until x2 && pixel.y in y1 until y2
    fun validate(geometry: NativeImageGeometry): NativeRect = also {
        require(x2 <= geometry.width && y2 <= geometry.height) { "ROI exceeds native geometry" }
    }
    companion object {
        /** Include both touched pixel cells, even for a tap or a reversed drag, then use half-open bounds. */
        fun fromDrag(first: NativePixel, last: NativePixel, geometry: NativeImageGeometry): NativeRect {
            require(geometry.contains(first) && geometry.contains(last))
            return NativeRect(minOf(first.x, last.x), minOf(first.y, last.y),
                maxOf(first.x, last.x) + 1, maxOf(first.y, last.y) + 1).validate(geometry)
        }
    }
}

/** Unrounded matrix-derived statistics. Zero-valid regions have counts only, never filler/stale readings.
 * Float extrema retain source Float32 values; the mean is accumulated in Float64 in native row-major order.
 * The algorithm is LMTX v1 §7's finite-valid-row-major-f64-v1; presentation is not an input.
 */
data class RoiStatistics(val pixelCount: Int, val validPixelCount: Int,
    val minC: Float?, val maxC: Float?, val meanC: Double?,
    val minPixel: NativePixel?, val maxPixel: NativePixel?) {
    companion object {
        const val METHOD = "finite-valid-row-major-f64-v1"

        /** Borrowed arrays must stay immutable during this call. A null mask means all valid.
         * Validate the entire supplied plane/mask, including cells outside the rectangle, before analysis.
         * Invalid cells are never inspected as temperatures; filler serialization belongs to future export.
         */
        fun calculate(geometry: NativeImageGeometry, temperature: FloatArray,
            roi: NativeRect, validity: ByteArray? = null): RoiStatistics {
            roi.validate(geometry)
            require(temperature.size == geometry.pixelCount) { "Matrix size does not match native geometry" }
            require(validity == null || validity.size == temperature.size) { "Mask size does not match native geometry" }
            for (index in temperature.indices) {
                val valid = validity?.get(index)?.toInt() ?: 1
                require(valid == 0 || valid == 1) { "Validity bytes must be 0 or 1" }
                require(valid == 0 || temperature[index].isFinite()) { "Valid Celsius samples must be finite" }
            }
            var count = 0
            var sum = 0.0
            var minimum = Float.POSITIVE_INFINITY
            var maximum = Float.NEGATIVE_INFINITY
            var minimumIndex = -1
            var maximumIndex = -1
            for (y in roi.y1 until roi.y2) {
                for (x in roi.x1 until roi.x2) {
                    val index = y * geometry.width + x
                    if (validity != null && validity[index].toInt() == 0) continue
                    val value = temperature[index]
                    // Strict comparisons retain the first row-major tie, including numeric signed-zero ties.
                    if (minimumIndex < 0 || value < minimum) { minimum = value; minimumIndex = index }
                    if (maximumIndex < 0 || value > maximum) { maximum = value; maximumIndex = index }
                    sum += value.toDouble()
                    count++
                }
            }
            return RoiStatistics(roi.pixelCount, count, if (count == 0) null else minimum,
                if (count == 0) null else maximum, if (count == 0) null else sum / count,
                if (count == 0) null else geometry.pixel(minimumIndex % geometry.width, minimumIndex / geometry.width),
                if (count == 0) null else geometry.pixel(maximumIndex % geometry.width, maximumIndex / geometry.width))
        }
    }
}

/** Analysis belongs to a specific module/source; identical dimensions alone cannot establish compatibility. */
data class RoiSourceKey(val moduleId: CameraModuleId, val modelId: String, val deviceKey: String?)

/** Pure selection lifecycle, separate from measurements and transient gesture state.
 * A missing source/geometry during close may retain selection, but a known replacement clears it.
 * This model contains no temperature cache: only a current valid measurement may supply statistics.
 */
data class NativeRoiSelection(val source: RoiSourceKey? = null, val geometry: NativeImageGeometry? = null,
    val rect: NativeRect? = null) {
    init { if (rect != null) { require(source != null && geometry != null); rect.validate(geometry) } }
    fun bind(next: RoiSourceKey?, nextGeometry: NativeImageGeometry?): NativeRoiSelection {
        if (next == null) return this
        return if (next != source || (nextGeometry != null && nextGeometry != geometry))
            NativeRoiSelection(next, nextGeometry) else this
    }
    fun select(rect: NativeRect): NativeRoiSelection {
        require(source != null && geometry != null)
        return copy(rect = rect.validate(geometry))
    }
    fun clear(): NativeRoiSelection = copy(rect = null)
    /** Evaluate only the supplied current measurement; unavailable/foreign data never reuse old statistics. */
    fun statistics(measurement: ThermalMeasurement?): RoiStatistics? {
        val selected = rect ?: return null
        if (measurement == null || measurement.validity != MeasurementValidity.VALID ||
            measurement.geometry != geometry || measurement.provenance.moduleId != source?.moduleId ||
            measurement.provenance.modelId != source.modelId) return null
        return RoiStatistics.calculate(measurement.geometry, measurement.matrix(), selected, measurement.validityMask())
    }
}
