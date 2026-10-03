package org.lmthermal.core

import org.lmthermal.camera.MeasurementValidity
import org.lmthermal.camera.NativeSample
import org.lmthermal.camera.ThermalMeasurement

/** Immutable native dimensions supplied by data, never inferred from a camera family or bitmap scaling. */
data class NativeImageGeometry(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE) }
    val pixelCount: Int get() = width * height
    fun contains(pixel: NativePixel): Boolean = pixel.x in 0 until width && pixel.y in 0 until height
    /** Geometry-bound construction protects module-independent coordinate consumers. */
    fun pixel(x: Int, y: Int): NativePixel = NativePixel(x, y).also { require(contains(it)) }
    fun offset(pixel: NativePixel): Int { require(contains(pixel)); return pixel.y * width + pixel.x }
}

/** A nonnegative native coordinate; its upper bounds must be validated against its accompanying geometry. */
data class NativePixel(val x: Int, val y: Int) {
    init { require(x >= 0 && y >= 0) }
}

data class DisplayPosition(val x: Double, val y: Double)
data class ImageContentRect(val left: Double, val top: Double, val width: Double, val height: Double)

/** Shared centered ContentScale.Fit mapper for any native geometry.
 * Bounds are half-open; markers use pixel centers and source data is never rotated or resampled.
 */
class ImageCoordinateMapper(val geometry: NativeImageGeometry, viewWidth: Double, viewHeight: Double) {
    val content: ImageContentRect
    init {
        require(viewWidth.isFinite() && viewHeight.isFinite() && viewWidth > 0 && viewHeight > 0)
        val scale = minOf(viewWidth / geometry.width, viewHeight / geometry.height)
        val width = geometry.width * scale; val height = geometry.height * scale
        content = ImageContentRect((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
    }
    /** Reject letterbox/outside touches; only validated interior roundoff can clamp to the last pixel cell. */
    fun toNative(position: DisplayPosition): NativePixel? {
        val x = position.x - content.left; val y = position.y - content.top
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= content.width || y >= content.height) return null
        return geometry.pixel((x / content.width * geometry.width).toInt().coerceAtMost(geometry.width - 1),
            (y / content.height * geometry.height).toInt().coerceAtMost(geometry.height - 1))
    }
    /** Validate against this image and map the native pixel center through exactly the touch rectangle. */
    fun toDisplay(pixel: NativePixel): DisplayPosition {
        require(geometry.contains(pixel))
        return DisplayPosition(content.left + (pixel.x + .5) * content.width / geometry.width,
            content.top + (pixel.y + .5) * content.height / geometry.height)
    }
}

/** Native sample evidence is optional; Celsius never comes from the rendered pixels. */
data class CursorReading(val pixel: NativePixel, val sample: NativeSample?, val celsius: Float)
object CursorInspection {
    /** A retained selection outside a replacement module's geometry has no reading. */
    fun read(pixel: NativePixel?, measurement: ThermalMeasurement?): CursorReading? {
        if (pixel == null || measurement == null || measurement.validity != MeasurementValidity.VALID ||
            !measurement.geometry.contains(pixel)) return null
        return CursorReading(pixel, measurement.sample(pixel), measurement.temperature(pixel))
    }
}
