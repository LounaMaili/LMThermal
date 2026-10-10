package org.lmthermal.r2

import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.core.Ht301Layout
import org.lmthermal.core.NativeEquivalentThermometry
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Private producer boundary, specific to the proven immutable HT-301 measurement.
 * No caller can obtain/mutate its arrays or context. Reader validation is unaffected.
 * The core already establishes finite temperatures and the complete raw14 grid.
 */
class OwnedObservation private constructor(internal val frame: Observation, internal val nativeViewLength: Int = 0) {
    val bytes get() = frame.bytes
    val sequence get() = frame.sequence
    internal fun gap(reason: String, end: Long) = OwnedObservation(frame.copy(temperature = null, mask = null,
        native = null, acquisition = null, receiptNs = null, reason = reason, gapEnd = end))
    companion object {
        /** Serialize on the dedicated preparation thread, never in the camera/UI callback. */
        fun ht301(measurement: Ht301ThermalMeasurement, profile: Profile, originMs: Long): OwnedObservation {
            val matrix = StageCosts.timed("matrix_copy", Ht301Layout.IMAGE_BYTES.toLong() * 2, Ht301Layout.IMAGE_BYTES.toLong() * 2) { measurement.matrix() }
            val temperature = StageCosts.timed("f32_encode", matrix.size.toLong() * 4, matrix.size.toLong() * 4) {
                ByteBuffer.allocate(matrix.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { it.asFloatBuffer().put(matrix) }.array()
            }
            val context = StageCosts.timed("context_build") { mapOf("module" to "ht301", "provenance" to "real-ht301-native-equivalent",
                "metadata" to R2HtContext.metadata(measurement.evidence), "warning" to NativeEquivalentThermometry.WARNING) }
            val frame = Observation(measurement.sequence, measurement.receivedMonotonicMs * 1000000,
                (measurement.receivedMonotonicMs - originMs).coerceAtLeast(0) * 1000000,
                measurement.geometry.width, measurement.geometry.height, temperature,
                native = if (profile == Profile.NATIVE) StageCosts.timed("native_copy", Ht301Layout.IMAGE_BYTES.toLong(), Ht301Layout.IMAGE_BYTES.toLong()) { measurement.evidence.source.imageBytes() } else null,
                acquisition = if (profile == Profile.FULL) StageCosts.timed("transport_copy", Ht301Layout.FRAME_BYTES.toLong(), Ht301Layout.FRAME_BYTES.toLong()) { measurement.evidence.source.transportBytes() } else null,
                context = context)
            require(frame.width == Ht301Layout.WIDTH && frame.height == Ht301Layout.IMAGE_HEIGHT && temperature.size == matrix.size * 4)
            require(frame.sequence >= 0 && frame.relativeNs >= 0 && frame.receiptNs!! >= 0)
            // HT source owns exact transport bytes; imageBytes is precisely this prefix.
            // Full therefore needs no redundant extracted native plane/equality scan.
            if (profile == Profile.FULL) require(frame.acquisition!!.size == Ht301Layout.FRAME_BYTES)
            StageCosts.timed("context_encode") { frame.contextBytes }
            return OwnedObservation(frame, if (profile == Profile.FULL) Ht301Layout.IMAGE_BYTES else 0)
        }
        /** Source gaps contain no inherited temperature/native/transport plane. */
        fun gap(sequence: Long, end: Long, relativeNs: Long, reason: String): OwnedObservation {
            val frame = Observation(sequence, null, relativeNs, Ht301Layout.WIDTH, Ht301Layout.IMAGE_HEIGHT,
                null, reason = reason, gapEnd = end)
            frame.validate(); frame.contextBytes
            return OwnedObservation(frame)
        }
    }
}

/** Internal provenance travels with the private token; generic mutable callers stay untrusted. */
internal data class ProducerEntry(val frame: Observation, val owned: OwnedObservation? = null) {
    fun gap(reason: String, end: Long): ProducerEntry = owned?.gap(reason, end)?.let { ProducerEntry(it.frame, it) }
        ?: ProducerEntry(frame.copy(temperature = null, mask = null, native = null, acquisition = null,
            receiptNs = null, reason = reason, gapEnd = end))
}
