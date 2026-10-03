package org.lmthermal.core

/** Native sensor coordinate, unaffected by phone layout or presentation dimensions. */
data class NativePixel(val x: Int, val y: Int) {
    init { require(x in 0 until Ht301Layout.WIDTH && y in 0 until Ht301Layout.IMAGE_HEIGHT) }
}

/** Presentation-space position and actual fitted image content bounds. */
data class DisplayPosition(val x: Double, val y: Double)
data class ImageContentRect(val left: Double, val top: Double, val width: Double, val height: Double)

/** Shared ContentScale.Fit/center mapper for touch, cursor and high/low overlays.
 * Bounds are half-open: a position on/beyond the far edge is outside, not silently clamped.
 * Markers use pixel centers; source matrices are never rotated, mirrored or resampled.
 */
class ImageCoordinateMapper(viewWidth: Double, viewHeight: Double) {
    val content: ImageContentRect
    init {
        require(viewWidth.isFinite() && viewHeight.isFinite() && viewWidth > 0 && viewHeight > 0)
        val scale = minOf(viewWidth / Ht301Layout.WIDTH, viewHeight / Ht301Layout.IMAGE_HEIGHT)
        val width = Ht301Layout.WIDTH * scale
        val height = Ht301Layout.IMAGE_HEIGHT * scale
        content = ImageContentRect((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
    }
    /** Reject letterbox/outside positions. Floor selects the pixel cell containing a touch. */
    fun toNative(position: DisplayPosition): NativePixel? {
        val x = position.x - content.left; val y = position.y - content.top
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= content.width || y >= content.height) return null
        // Floating division near the right edge can round to WIDTH; only interior points reach this clamp.
        return NativePixel((x / content.width * Ht301Layout.WIDTH).toInt().coerceAtMost(Ht301Layout.WIDTH - 1),
            (y / content.height * Ht301Layout.IMAGE_HEIGHT).toInt().coerceAtMost(Ht301Layout.IMAGE_HEIGHT - 1))
    }
    /** Map a native pixel center through precisely the same fitted rectangle as touch. */
    fun toDisplay(pixel: NativePixel): DisplayPosition = DisplayPosition(
        content.left + (pixel.x + .5) * content.width / Ht301Layout.WIDTH,
        content.top + (pixel.y + .5) * content.height / Ht301Layout.IMAGE_HEIGHT)
}

/** Selected coordinate is persistent; the reading is always derived from the supplied current measurement. */
data class CursorReading(val pixel: NativePixel, val raw14: Int, val celsius: Float)
object CursorInspection {
    /** Null measurement means unavailable, never a previous frame's Celsius. */
    fun read(pixel: NativePixel?, measurement: RadiometricMeasurement?): CursorReading? {
        if (pixel == null || measurement == null) return null
        return CursorReading(pixel, measurement.source.pixel(pixel.x, pixel.y), measurement.temperature(pixel.x, pixel.y))
    }
}
