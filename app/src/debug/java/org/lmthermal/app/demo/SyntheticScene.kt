package org.lmthermal.app.demo

import org.lmthermal.core.NativeImageGeometry
import kotlin.math.sqrt

/** Analytic Celsius fixture, not a camera/thermometry model. All coordinates remain native 384 × 288.
 * The two reference patches never move; only the lower circular target changes between scene frames.
 */
object SyntheticScene {
    val geometry = NativeImageGeometry(384, 288)
    const val FRAME_INTERVAL_MS = 250L

    /** Frame zero repeats after 64 steps. Fixed patches provide independent point and ROI test oracles. */
    fun temperature(x: Int, y: Int, frame: Long): Float {
        require(x in 0 until geometry.width && y in 0 until geometry.height && frame >= 0)
        if (x in 32..159 && y in 32..111) return 10f
        if (x in 224..351 && y in 32..111) return 40f
        val centerX = 64 + (frame % 64).toInt() * 4
        val distance = sqrt(((x - centerX) * (x - centerX) + (y - 208) * (y - 208)).toDouble())
        return if (distance <= 48) (22 + 13 * (1 - distance / 48)).toFloat() else 22f
    }

    /** An owned matrix is passed to the existing generic measurement adapter, never to HT-301 algorithms. */
    fun matrix(frame: Long): FloatArray = FloatArray(geometry.pixelCount) {
        temperature(it % geometry.width, it / geometry.width, frame)
    }
}
