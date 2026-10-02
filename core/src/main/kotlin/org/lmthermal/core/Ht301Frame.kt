package org.lmthermal.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Native camera invariants from docs/HARDWARE.md; the trailer is never an image. */
object Ht301Layout {
    const val VID = 0x1514
    const val PID = 0x0001
    const val WIDTH = 384
    const val IMAGE_HEIGHT = 288
    const val TRANSPORT_HEIGHT = 292
    const val FPS = 25
    const val BYTES_PER_WORD = 2
    const val IMAGE_WORDS = WIDTH * IMAGE_HEIGHT
    const val IMAGE_BYTES = IMAGE_WORDS * BYTES_PER_WORD
    const val FRAME_BYTES = WIDTH * TRANSPORT_HEIGHT * BYTES_PER_WORD
    const val TRAILER_BYTES = FRAME_BYTES - IMAGE_BYTES
    const val RAW14_LIMIT = 0x4000
    const val DISPLAY_MASK = 0xff00
    const val DISPLAY_PREFIX = 0x8000
    const val PARAMETERS = 223742
    const val CALIBRATION = 223494
    const val CALIBRATION_COPY = PARAMETERS + 352
    const val CALIBRATION_BYTES = 20
    const val HIGH_XY = IMAGE_BYTES + 4
    const val HIGH_INDEX = IMAGE_BYTES + 8
    const val LOW_XY = IMAGE_BYTES + 10
    const val LOW_INDEX = IMAGE_BYTES + 14
    const val CENTER_INDEX = IMAGE_BYTES + 24
}

/** Relative float32/uint16 settings offsets documented in docs/NATIVE_CALL_CHAIN.md. */
object ParameterOffsets {
    const val CORRECTION = 0
    const val REFLECTED = 4
    const val AMBIENT = 8
    const val HUMIDITY = 12
    const val EMISSIVITY = 16
    const val DISTANCE = 20
    const val CALIBRATION_0 = 352
    const val CALIBRATION_1 = 356
    const val CALIBRATION_2 = 360
    const val CALIBRATION_3 = 364
    const val CALIBRATION_4 = 368
    val FINITE_FLOATS = listOf(CORRECTION, REFLECTED, AMBIENT, HUMIDITY, EMISSIVITY,
        CALIBRATION_0, CALIBRATION_1, CALIBRATION_2, CALIBRATION_3, CALIBRATION_4)
}

/** Pixel classification is separate from future evidence-gated radiometric readiness. */
enum class FrameMode { DISPLAY, RAW14, INVALID }

/** Desktop inspect_frame parity: raw14 may carry rejected calibration/summary evidence. */
data class FrameInspection(
    val mode: FrameMode,
    val reason: String?,
    val minimum: Int?,
    val maximum: Int?,
    val summaryValid: Boolean = false,
)

/** Owns an exact transport copy. All byte-array access returns copies, never mutable storage.
 * Native (x,y) coordinates always use 384×288; phone orientation cannot change these bytes.
 */
class Ht301Frame private constructor(private val transport: ByteArray) {
    val size: Int get() = transport.size
    /** Retain every trailer byte for the later session/thermometry port. */
    fun transportBytes(): ByteArray = transport.copyOf()
    /** Exclude all four trailer rows, not just the final parameter block. */
    fun imageBytes(): ByteArray = transport.copyOfRange(0, Ht301Layout.IMAGE_BYTES)
    /** Return independent non-image transport data. */
    fun trailerBytes(): ByteArray = transport.copyOfRange(Ht301Layout.IMAGE_BYTES, size)
    /** Decode complete little-endian words without stripping either high bit. */
    fun word(offset: Int): Int = (transport[offset].toInt() and 255) or
        ((transport[offset + 1].toInt() and 255) shl 8)
    /** Native image coordinate lookup, rejecting trailer/phone coordinate confusion. */
    fun pixel(x: Int, y: Int): Int {
        require(x in 0 until Ht301Layout.WIDTH && y in 0 until Ht301Layout.IMAGE_HEIGHT)
        return word((y * Ht301Layout.WIDTH + x) * Ht301Layout.BYTES_PER_WORD)
    }
    /** Float32 decoding preserves the stored precision; no thermometry formula is ported here. */
    private fun floatAt(offset: Int): Float = ByteBuffer.wrap(transport, offset, 4)
        .order(ByteOrder.LITTLE_ENDIAN).float
    /** Exact executable reference: ../LMThermal-Desktop/radiometric_session.py::inspect_frame. */
    fun inspect(): FrameInspection {
        var minimum = Int.MAX_VALUE
        var maximum = 0
        var display = true
        for (offset in 0 until Ht301Layout.IMAGE_BYTES step Ht301Layout.BYTES_PER_WORD) {
            val value = word(offset)
            minimum = minOf(minimum, value)
            maximum = maxOf(maximum, value)
            display = display && (value and Ht301Layout.DISPLAY_MASK) == Ht301Layout.DISPLAY_PREFIX
        }
        if (display) return FrameInspection(FrameMode.DISPLAY, null, minimum, maximum)
        if (maximum >= Ht301Layout.RAW14_LIMIT)
            return FrameInspection(FrameMode.INVALID, "mixed_or_out_of_range_words", minimum, maximum)
        val p = Ht301Layout.PARAMETERS
        val settingsOffsets = ParameterOffsets.FINITE_FLOATS
        if (settingsOffsets.any { !floatAt(p + it).isFinite() } || floatAt(p + ParameterOffsets.CALIBRATION_0) <= 0 ||
            floatAt(p + ParameterOffsets.EMISSIVITY) <= 0 || floatAt(p + ParameterOffsets.HUMIDITY) < 0 || word(p + ParameterOffsets.DISTANCE) <= 0)
            return FrameInspection(FrameMode.RAW14, "invalid_settings_or_calibration", minimum, maximum)
        if ((0 until Ht301Layout.CALIBRATION_BYTES).any {
                transport[Ht301Layout.CALIBRATION + it] != transport[Ht301Layout.CALIBRATION_COPY + it]
            }) return FrameInspection(FrameMode.RAW14, "calibration_copy_mismatch", minimum, maximum)
        val center = word(Ht301Layout.CENTER_INDEX)
        val high = word(Ht301Layout.HIGH_INDEX)
        val low = word(Ht301Layout.LOW_INDEX)
        if (maxOf(center, high, low) >= Ht301Layout.RAW14_LIMIT)
            return FrameInspection(FrameMode.RAW14, "summary_index_out_of_range", minimum, maximum)
        val hx = word(Ht301Layout.HIGH_XY)
        val hy = word(Ht301Layout.HIGH_XY + 2)
        val lx = word(Ht301Layout.LOW_XY)
        val ly = word(Ht301Layout.LOW_XY + 2)
        val coordsValid = hx < Ht301Layout.WIDTH && lx < Ht301Layout.WIDTH &&
            hy < Ht301Layout.IMAGE_HEIGHT && ly < Ht301Layout.IMAGE_HEIGHT
        val summary = coordsValid && high == maximum && low == minimum &&
            pixel(hx, hy) == high && pixel(lx, ly) == low
        return FrameInspection(FrameMode.RAW14, null, minimum, maximum, summary)
    }
    companion object {
        /** Fail closed on partial or oversized UVC payloads; never pad or crop them. */
        fun parse(bytes: ByteArray): Ht301Frame {
            require(bytes.size == Ht301Layout.FRAME_BYTES) { "transport_size: ${bytes.size}" }
            return Ht301Frame(bytes.copyOf())
        }
    }
}

/** Display-only grayscale output. Never a source of raw indices or future temperature values. */
object PreviewRenderer {
    /** Mirror Desktop 1st/99th percentile contrast, retaining native image orientation. */
    fun grayscale(frame: Ht301Frame, inspection: FrameInspection): IntArray {
        require(inspection.mode != FrameMode.INVALID)
        val words = IntArray(Ht301Layout.IMAGE_WORDS) { frame.word(it * Ht301Layout.BYTES_PER_WORD) }
        val gray = if (inspection.mode == FrameMode.DISPLAY) {
            IntArray(words.size) { words[it] and 255 }
        } else {
            require(words.all { it < Ht301Layout.RAW14_LIMIT })
            val sorted = words.sortedArray()
            val low = percentile(sorted, .01)
            val high = percentile(sorted, .99)
            IntArray(words.size) { if (high <= low) 127 else
                ((words[it] - low) * (255.0 / (high - low))).roundToInt().coerceIn(0, 255) }
        }
        return IntArray(gray.size) { val v = gray[it]; (0xff shl 24) or (v shl 16) or (v shl 8) or v }
    }
    /** Linear percentile interpolation uses the same rank convention as NumPy's default. */
    private fun percentile(sorted: IntArray, fraction: Double): Double {
        val rank = (sorted.size - 1) * fraction
        val lower = rank.toInt()
        return sorted[lower] + (sorted[minOf(lower + 1, sorted.lastIndex)] - sorted[lower]) * (rank - lower)
    }
}

/** A conflated latest snapshot, used by the Android controller: never queues presentation frames. */
class LatestFrameState<T>(initial: T) {
    private val latest = MutableStateFlow(initial)
    private val publicationLock = Any()
    /** Publishing replaces the current value; slow consumers observe the newest available snapshot. */
    var value: T
        get() = latest.value
        set(value) { synchronized(publicationLock) { latest.value = value } }
    /** Serialize the ownership check and publication with close/reset, preventing a stale final frame. */
    fun updateIf(ownsSource: () -> Boolean, transform: (T) -> T) {
        synchronized(publicationLock) {
            if (ownsSource()) latest.value = transform(latest.value)
        }
    }
    /** Consumers cannot mutate the producer's state or ask it to replay an obsolete queue. */
    fun asStateFlow(): StateFlow<T> = latest.asStateFlow()
}

/** Platform-independent USB status. A raw14 candidate never implies session readiness. */
enum class UsbPhase { ABSENT, ATTACHED, PERMISSION_PENDING, PERMISSION_DENIED, OPENING, STREAMING, CLOSED, ERROR }

/** Fresh state on attach/disconnect prevents stale stream statistics and presentation. */
data class UsbState(val phase: UsbPhase = UsbPhase.ABSENT, val message: String = "No HT-301") {
    /** Called on each discovery, including devices already attached at launch. */
    fun attached() = UsbState(UsbPhase.ATTACHED, "HT-301 attached")
    /** A normal unplug/close has no surviving streaming state. */
    fun disconnected() = UsbState()
}
