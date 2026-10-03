package org.lmthermal.core

/** Only the executed Desktop/native branch is supported; other configurations fail closed. */
data class ThermometryConfiguration(val width: Int = 384, val range: Int = 120,
    val lens: Int = 68, val shutterFix: Float = 1.5f) {
    internal fun validate() {
        require(width == 384 && range == 120 && lens == 68 && shutterFix == 1.5f) {
            "Unsupported native thermometry configuration"
        }
    }
}

/** Confirmed settings and five earlier-trailer calibration inputs, not brightness data. */
data class FrameParameters(val correction: Float, val reflected: Float, val ambient: Float,
    val humidity: Float, val emissivity: Float, val distance: Int,
    val c0: Float, val c1: Float, val c2: Float, val c3: Float, val c4: Float) {
    companion object {
        /** Verify the calibration copy byte-for-byte before trusting either copy. */
        fun decode(frame: Ht301Frame): FrameParameters {
            val raw = frame.transportBytes()
            require((0 until Ht301Layout.CALIBRATION_BYTES).all {
                raw[Ht301Layout.CALIBRATION + it] == raw[Ht301Layout.CALIBRATION_COPY + it]
            }) { "Calibration copy mismatch" }
            fun f(offset: Int): Float = Float.fromBits(frame.word(offset) or (frame.word(offset + 2) shl 16))
            val p = Ht301Layout.PARAMETERS
            val c = Ht301Layout.CALIBRATION
            val decoded = FrameParameters(f(p), f(p + 4), f(p + 8), f(p + 12), f(p + 16),
                frame.word(p + 20), f(c), f(c + 4), f(c + 8), f(c + 12), f(c + 16))
            require(listOf(decoded.correction, decoded.reflected, decoded.ambient, decoded.humidity,
                decoded.emissivity, decoded.c0, decoded.c1, decoded.c2, decoded.c3, decoded.c4).all { it.isFinite() } &&
                decoded.c0 > 0 && decoded.emissivity > 0 && decoded.humidity >= 0 && decoded.distance > 0) {
                "Nonfinite or invalid calibration/settings"
            }
            return decoded
        }
    }
}

/** CalcFixRaw outputs passed into GetTempEvn, retaining scalar float32 rounding. */
data class EnvironmentTrace(val water: Float, val transmission: Float, val inverse: Float, val radiation: Float)

/** Numerical evidence for debugging parity, independent of presentation/session state. */
data class LookupTrace(val configuration: ThermometryConfiguration, val fpaWord: Int, val fpaTerm: Float,
    val calibrationWord: Int, val calibrationTemperature: Float, val getFix: Int, val adjustedLookupBase: Int,
    val initA: Float, val initB: Float, val linear: Float, val constant: Float,
    val effectiveNativeDistance: Float, val environment: EnvironmentTrace, val finiteLookupEntries: Int)

/** Private table storage; callers can inspect a value or obtain an independent copy. */
class NativeLookup internal constructor(private val entries: FloatArray, val trace: LookupTrace) {
    fun value(index: Int): Float = entries[index]
    fun values(): FloatArray = entries.copyOf()
}

/** Immutable native coordinate and lookup result. Trailer center need not be literal center. */
data class TemperaturePoint(val index: Int, val celsius: Float, val x: Int? = null, val y: Int? = null)

/** Owns measurement evidence, with copy-only array APIs and unchanged native row-major coordinates. */
class RadiometricMeasurement internal constructor(val source: Ht301Frame,
    private val indices: IntArray, private val temperatures: FloatArray,
    val parameters: FrameParameters, val lookupTrace: LookupTrace,
    val trailerCenter: TemperaturePoint, val literalCenter: TemperaturePoint,
    val high: TemperaturePoint, val low: TemperaturePoint,
    val rawMinimum: Int, val rawMaximum: Int, val matrixMinimum: Float, val matrixMaximum: Float,
    val sequence: Long, val receivedMonotonicMs: Long) {
    fun raw14(): IntArray = indices.copyOf()
    fun matrix(): FloatArray = temperatures.copyOf()
    /** Never reverse/rotate thermometry to follow screen orientation. */
    fun temperature(x: Int, y: Int): Float {
        require(x in 0 until Ht301Layout.WIDTH && y in 0 until Ht301Layout.IMAGE_HEIGHT)
        return temperatures[y * Ht301Layout.WIDTH + x]
    }
}

/** Operation-for-operation port of Desktop native_equivalent_thermometry.py.
 * Float operations round at the SSE/NumPy boundaries. Math exp/pow/sqrt are Double;
 * their results are narrowed at the same points as the inspected x86_64 path.
 * Do not simplify, fuse or reassociate these expressions. Undefined entries stay NaN.
 */
object NativeEquivalentThermometry {
    const val LOOKUP_SIZE = 16384
    const val WARNING = "Native-equivalent temperatures; absolute physical accuracy not yet independently validated."
    private const val KELVIN = 273.15f

    /** Normal-path CalcFixRaw; the caller supplies lens-68 effective distance. */
    fun calcFixRaw(ambient: Float, humidity: Float, distance: Float, emissivity: Float,
        reflected: Float): EnvironmentTrace {
        var polynomial = ((6.8455e-7f * ambient) * ambient) * ambient
        polynomial += (1.5587f + 0.06939f * ambient) - (0.00027816f * ambient) * ambient
        val water = (Math.exp(polynomial.toDouble()) * humidity.toDouble()).toFloat()
        val rootDistance = Math.sqrt(distance.toDouble()).toFloat()
        val rootWater = Math.sqrt(water.toDouble()).toFloat()
        val exponent1 = (rootWater * -0.002276f + 0.006569f) * -rootDistance
        val exponent2 = (rootWater * -0.006670f + 0.012620f) * -rootDistance
        val transmission = (Math.exp(exponent1.toDouble()) * 1.9f.toDouble() +
            Math.exp(exponent2.toDouble()) * (-0.9f).toDouble()).toFloat()
        val inverse = 1f / (transmission * emissivity)
        val reflectedTerm = ((1f - emissivity) * Math.pow((reflected + KELVIN).toDouble(), 4.0).toFloat()) * transmission
        val radiation = Math.pow((ambient + KELVIN).toDouble(), 4.0).toFloat() * (1f - transmission) + reflectedTerm
        return EnvironmentTrace(water, transmission, inverse, radiation)
    }

    /** LUT generation does not require a settled image; fixtures include transient stage evidence. */
    fun buildLookup(frame: Ht301Frame, configuration: ThermometryConfiguration = ThermometryConfiguration()): NativeLookup {
        configuration.validate()
        val p = FrameParameters.decode(frame)
        val fpaWord = frame.word(Ht301Layout.FPA_WORD)
        val calibrationWord = frame.word(Ht301Layout.CALIBRATION_TEMPERATURE_WORD)
        val fpa = 20f - (fpaWord - 7800).toFloat() / 36f
        val calibrationTemperature = calibrationWord.toFloat() / 10f - KELVIN
        val fix = maxOf(0, (390f - 7.05f * fpa).toInt())
        require(fix <= 32767) { "FPA outside supported GetFix branch" }
        val base = (frame.word(Ht301Layout.LOOKUP_BASE_WORD) - fix) and 0xffff
        val initA = p.c1 / (p.c0 + p.c0)
        val initB = (p.c1 * p.c1) / (p.c0 * (4f * p.c0))
        val shifted = calibrationTemperature + configuration.shutterFix
        val constant = (p.c0 * shifted) * shifted + shifted * p.c1
        val linear = (fpa * p.c3 + (p.c2 * fpa) * fpa) + p.c4
        val distance = (3 * p.distance).toFloat()
        val env = calcFixRaw(p.ambient, p.humidity, distance, p.emissivity, p.reflected)
        val entries = FloatArray(LOOKUP_SIZE) { index ->
            val radicand = (((index - base).toFloat() * linear + constant) / p.c0) + initB
            if (radicand < 0) Float.NaN else {
                // sqrt subtraction is Double before narrowing; fourth-power input was rounded Float.
                val calibrated = (Math.sqrt(radicand.toDouble()) - initA.toDouble()).toFloat()
                val powered = Math.pow((calibrated + KELVIN).toDouble(), 4.0).toFloat()
                val correctedPower = env.inverse * (powered - env.radiation)
                if (correctedPower < 0) Float.NaN else {
                    val corrected = Math.pow(correctedPower.toDouble(), .25).toFloat() - KELVIN
                    val factor = if (distance < 60f) (distance * .85f).toDouble() + 1.125 else 52.125
                    (corrected.toDouble() + (corrected - p.ambient).toDouble() * factor / 100.0).toFloat()
                }
            }
        }
        return NativeLookup(entries, LookupTrace(configuration, fpaWord, fpa, calibrationWord,
            calibrationTemperature, fix, base, initA, initB, linear, constant, distance, env,
            entries.count { it.isFinite() }))
    }

    /** Match Desktop make_measurement: consistent extrema plus finite observed LUT values are mandatory. */
    fun measure(frame: Ht301Frame, sequence: Long = 0, receivedMonotonicMs: Long = 0,
        configuration: ThermometryConfiguration = ThermometryConfiguration()): RadiometricMeasurement {
        val inspection = frame.inspect()
        require(inspection.mode == FrameMode.RAW14 && inspection.reason == null && inspection.summaryValid) {
            "Frame not measurement-valid: ${inspection.reason ?: "summary_mismatch_or_display"}"
        }
        val parameters = FrameParameters.decode(frame)
        val lookup = buildLookup(frame, configuration)
        fun selected(index: Int): Float {
            require(index in 0 until LOOKUP_SIZE) { "Summary/pixel index outside raw14 range" }
            val value = lookup.value(index) + parameters.correction
            require(value.isFinite()) { "Observed index selects undefined native lookup entry" }
            return value
        }
        val indices = IntArray(Ht301Layout.IMAGE_WORDS) { frame.word(it * 2) }
        val matrix = FloatArray(indices.size) { selected(indices[it]) }
        val literalIndex = frame.pixel(192, 144)
        val trailerCenter = TemperaturePoint(frame.word(Ht301Layout.CENTER_INDEX), selected(frame.word(Ht301Layout.CENTER_INDEX)))
        val high = TemperaturePoint(frame.word(Ht301Layout.HIGH_INDEX), selected(frame.word(Ht301Layout.HIGH_INDEX)),
            frame.word(Ht301Layout.HIGH_XY), frame.word(Ht301Layout.HIGH_XY + 2))
        val low = TemperaturePoint(frame.word(Ht301Layout.LOW_INDEX), selected(frame.word(Ht301Layout.LOW_INDEX)),
            frame.word(Ht301Layout.LOW_XY), frame.word(Ht301Layout.LOW_XY + 2))
        return RadiometricMeasurement(frame, indices, matrix, parameters, lookup.trace, trailerCenter,
            TemperaturePoint(literalIndex, selected(literalIndex), 192, 144), high, low,
            inspection.minimum!!, inspection.maximum!!, matrix.min(), matrix.max(), sequence, receivedMonotonicMs)
    }
}

/** Stateless current-frame gate: no last-good Celsius survives an invalid/held/disconnected frame.
 * Session readiness remains structural/liveness; thermometry failure only removes measurement.
 */
object MeasurementGate {
    fun evaluate(state: SessionState, frame: Ht301Frame?, sequence: Long, receivedMonotonicMs: Long): MeasurementResult {
        if (state != SessionState.RADIOMETRIC_READY || frame == null) return MeasurementResult(null, "Session not ready")
        return try { MeasurementResult(NativeEquivalentThermometry.measure(frame, sequence, receivedMonotonicMs), null) }
        catch (failure: IllegalArgumentException) { MeasurementResult(null, failure.message) }
    }
}

/** Failure reason is numerical evidence, never a plausible fallback temperature. */
data class MeasurementResult(val measurement: RadiometricMeasurement?, val reason: String?)
