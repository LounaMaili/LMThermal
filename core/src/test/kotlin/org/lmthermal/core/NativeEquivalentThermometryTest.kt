package org.lmthermal.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

/** Full-entry/pixel goldens are exported by Desktop, not computed by Kotlin under test. */
class NativeEquivalentThermometryTest {
    private val names = listOf("radiometric-initial", "radiometric-room-first", "radiometric-room-range",
        "radiometric-room-shutter-held", "radiometric-room-settled", "warm-hand-settled",
        "synthetic-long-distance", "synthetic-base-wrap")
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
    private fun bytes(name: String = "radiometric-room-settled"): ByteArray {
        if (name.startsWith("synthetic-")) return bytes().apply {
            if (name == "synthetic-long-distance") putWord(this, Ht301Layout.PARAMETERS + 20, 20)
            else putWord(this, Ht301Layout.LOOKUP_BASE_WORD, 0)
        }
        return resource(if (name == "radiometric-room-settled") "/fixtures/$name.raw" else "/thermometry/$name.raw")
    }
    private fun frame(name: String = "radiometric-room-settled") = Ht301Frame.parse(bytes(name))
    private fun golden(name: String, kind: String): FloatArray {
        val buffer = ByteBuffer.wrap(resource("/thermometry/$name.$kind.f32")).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / 4) { buffer.float }
    }
    private fun properties(name: String): Properties = Properties().apply {
        load(resource("/thermometry/$name.properties").inputStream())
    }
    private fun bits(p: Properties, name: String, actual: Float) =
        assertEquals(name, p.getProperty(name).toLong().toInt(), actual.toRawBits())
    private fun integer(p: Properties, name: String, actual: Int) =
        assertEquals(name, p.getProperty(name).toInt(), actual)
    private fun putWord(raw: ByteArray, offset: Int, value: Int) {
        raw[offset] = value.toByte(); raw[offset + 1] = (value shr 8).toByte()
    }
    private fun putFloat(raw: ByteArray, offset: Int, value: Float) {
        ByteBuffer.wrap(raw, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
    }
    private fun reject(raw: ByteArray) {
        assertThrows(IllegalArgumentException::class.java) { NativeEquivalentThermometry.measure(Ht301Frame.parse(raw)) }
    }
    private fun fullParity(name: String) {
        val expected = golden(name, "lut")
        val actual = NativeEquivalentThermometry.buildLookup(frame(name)).values()
        assertEquals(16384, actual.size)
        var different = 0
        var maxError = 0.0
        var maxUlp = 0L
        for (i in expected.indices) {
            assertEquals("$name NaN[$i]", expected[i].isNaN(), actual[i].isNaN())
            if (expected[i].isFinite()) {
                if (expected[i].toRawBits() != actual[i].toRawBits()) different++
                maxError = maxOf(maxError, kotlin.math.abs(expected[i].toDouble() - actual[i].toDouble()))
                maxUlp = maxOf(maxUlp, kotlin.math.abs(expected[i].toRawBits().toLong() - actual[i].toRawBits().toLong()))
            }
        }
        println("$name: LUT entries=${actual.size} differing=$different maxAbs=$maxError maxULP=$maxUlp NaN pattern equal")
        assertEquals("$name full LUT bit parity", 0, different)
    }
    @Test fun goldenArtifactsMatchProvenanceManifestHashes() {
        val manifest = String(resource("/thermometry/manifest.json"))
        val artifacts = manifest.substringAfter("\"artifacts\": {").substringBefore("},")
        val pattern = Regex("\"([^\"]+)\": \"([0-9a-f]{64})\"")
        for (match in pattern.findAll(artifacts)) {
            val name = match.groupValues[1]
            val data = resource(if (name.startsWith("../")) "/" + name.removePrefix("../") else "/thermometry/$name")
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            assertEquals(name, match.groupValues[2], actual)
        }
    }
    @Test fun initialFullLookupMatchesNativeReference() = fullParity(names[0])
    @Test fun firstRaw14FullLookupMatchesNativeReference() = fullParity(names[1])
    @Test fun normalRangeFullLookupMatchesNativeReference() = fullParity(names[2])
    @Test fun shutterHeldFullLookupMatchesNativeReferenceWithoutClaimingReadiness() = fullParity(names[3])
    @Test fun settledRoomFullLookupMatchesNativeReference() = fullParity(names[4])
    @Test fun settledHandFullLookupMatchesDesktopReference() = fullParity(names[5])
    @Test fun longDistanceFullLookupMatchesDesktopReference() = fullParity(names[6])
    @Test fun wrappedBaseFullLookupMatchesDesktopReference() = fullParity(names[7])
    @Test fun everyFixtureIntermediateMatchesExactDesktopFloat32Bits() {
        for (name in names) {
            val p = properties(name)
            val t = NativeEquivalentThermometry.buildLookup(frame(name)).trace
            integer(p, "host_range", t.configuration.range); integer(p, "host_lens", t.configuration.lens)
            bits(p, "host_shutter_fix", t.configuration.shutterFix)
            integer(p, "fpa_word_at_221186", t.fpaWord); bits(p, "fpa_term", t.fpaTerm)
            integer(p, "calibration_word_at_223490", t.calibrationWord)
            bits(p, "calibration_temperature", t.calibrationTemperature)
            integer(p, "get_fix", t.getFix); integer(p, "adjusted_lookup_base", t.adjustedLookupBase)
            bits(p, "init_a", t.initA); bits(p, "init_b", t.initB)
            bits(p, "linear", t.linear); bits(p, "constant", t.constant)
            bits(p, "effective_native_distance", t.effectiveNativeDistance)
            bits(p, "water", t.environment.water); bits(p, "transmission", t.environment.transmission)
            bits(p, "inverse", t.environment.inverse); bits(p, "radiation", t.environment.radiation)
            integer(p, "finite_lookup_entries", t.finiteLookupEntries)
        }
    }
    private fun matrixParity(name: String) {
        val f = frame(name)
        val m = NativeEquivalentThermometry.measure(f, 27, 1000)
        val expected = golden(name, "matrix")
        val actual = m.matrix()
        assertEquals(110592, actual.size)
        val raw = m.raw14()
        for (i in expected.indices) {
            assertEquals("$name matrix[$i]", expected[i].toRawBits(), actual[i].toRawBits())
            assertEquals(f.word(i * 2), raw[i])
        }
        val p = properties(name)
        bits(p, "matrix_min", m.matrixMinimum); bits(p, "matrix_max", m.matrixMaximum)
        bits(p, "trailer_center_c", m.trailerCenter.celsius); bits(p, "literal_center_c", m.literalCenter.celsius)
        bits(p, "high_c", m.high.celsius); bits(p, "low_c", m.low.celsius)
        integer(p, "trailer_center_index", m.trailerCenter.index); integer(p, "literal_center_index", m.literalCenter.index)
        integer(p, "high_index", m.high.index); integer(p, "low_index", m.low.index)
        integer(p, "high_x", m.high.x!!); integer(p, "high_y", m.high.y!!)
        integer(p, "low_x", m.low.x!!); integer(p, "low_y", m.low.y!!)
        integer(p, "image_word_min", m.rawMinimum); integer(p, "image_word_max", m.rawMaximum)
        assertEquals(m.high.celsius, m.temperature(m.high.x, m.high.y), 0f)
        assertEquals(m.low.celsius, m.temperature(m.low.x, m.low.y), 0f)
        assertEquals(27L, m.sequence); assertEquals(1000L, m.receivedMonotonicMs)
    }
    @Test fun completeSettledRoomMatrixAndSummaryMatchDesktop() = matrixParity("radiometric-room-settled")
    @Test fun completeHandMatrixAndSummaryMatchDesktop() = matrixParity("warm-hand-settled")
    @Test fun exactOffsetsKeepFpaAndCalibrationWordsSeparate() {
        val f = frame("radiometric-initial"); val p = FrameParameters.decode(f)
        assertEquals(7180, f.word(Ht301Layout.FPA_WORD)); assertEquals(3105, f.word(Ht301Layout.CALIBRATION_TEMPERATURE_WORD))
        assertEquals(25f, p.ambient, 0f); assertEquals(25f, p.reflected, 0f)
        assertEquals(.45f, p.humidity, 0f); assertEquals(.98f, p.emissivity, 0f); assertEquals(1, p.distance)
        assertEquals(.2705f, p.c0, 0f); assertEquals(35.992f, p.c1, 0f)
        assertEquals(.00004f, p.c2, 0f); assertEquals(.0057f, p.c3, 0f); assertEquals(.8234f, p.c4, 0f)
    }
    @Test fun literalCenterIsNotReplacedByTrailerCenter() {
        val m = NativeEquivalentThermometry.measure(frame("warm-hand-settled"))
        assertEquals(5742, m.literalCenter.index); assertEquals(5740, m.trailerCenter.index)
        assertNotEquals(m.literalCenter.celsius, m.trailerCenter.celsius)
    }
    @Test fun displayWordsCannotBecomeCelsius() = reject(resource("/fixtures/display-room-baseline.raw"))
    @Test fun bothHighBitsAreRejectedWithoutMasking() {
        for (value in listOf(0x4000, 0x8000, 0xffff)) reject(bytes().apply { putWord(this, 0, value) })
    }
    @Test fun nonfiniteSettingsAndInvalidDistanceFailClosed() {
        for (offset in listOf(0, 4, 8, 12, 16)) for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY))
            reject(bytes().apply { putFloat(this, Ht301Layout.PARAMETERS + offset, bad) })
        reject(bytes().apply { putWord(this, Ht301Layout.PARAMETERS + 20, 0) })
        reject(bytes().apply { putFloat(this, Ht301Layout.PARAMETERS + 16, 0f) })
        reject(bytes().apply { putFloat(this, Ht301Layout.PARAMETERS + 12, -1f) })
    }
    @Test fun invalidCalibrationCoefficientRejectedEvenWhenCopiesMatch() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) reject(bytes().apply {
            putFloat(this, Ht301Layout.CALIBRATION, bad); putFloat(this, Ht301Layout.CALIBRATION_COPY, bad)
        })
    }
    @Test fun mismatchedCalibrationCopiesRejected() = reject(bytes().apply { this[Ht301Layout.CALIBRATION_COPY] = 0 })
    @Test fun unsupportedHostConfigurationsRejectedExplicitly() {
        for (configuration in listOf(ThermometryConfiguration(width = 256), ThermometryConfiguration(range = 400),
            ThermometryConfiguration(lens = 0), ThermometryConfiguration(shutterFix = 0f))) {
            assertThrows(IllegalArgumentException::class.java) { NativeEquivalentThermometry.buildLookup(frame(), configuration) }
        }
    }
    @Test fun nanSelectedPixelRejectedWithOtherwiseConsistentExtrema() {
        val raw = bytes(); val f = Ht301Frame.parse(raw)
        val lx = f.word(Ht301Layout.LOW_XY); val ly = f.word(Ht301Layout.LOW_XY + 2)
        putWord(raw, (ly * 384 + lx) * 2, 0); putWord(raw, Ht301Layout.LOW_INDEX, 0)
        assertTrue(Ht301Frame.parse(raw).inspect().summaryValid)
        reject(raw)
    }
    @Test fun invalidAndUndefinedTrailerCenterNeverSubstituted() {
        for (value in listOf(0, 0x4000)) reject(bytes().apply { putWord(this, Ht301Layout.CENTER_INDEX, value) })
    }
    @Test fun mismatchedTrailerCoordinatesRejected() = reject(bytes().apply { putWord(this, Ht301Layout.HIGH_XY, 384) })
    @Test fun correctionAddedExactlyOnceToMatrixAndSummaries() {
        val original = NativeEquivalentThermometry.measure(frame())
        val changed = NativeEquivalentThermometry.measure(Ht301Frame.parse(bytes().apply { putFloat(this, Ht301Layout.PARAMETERS, 2.5f) }))
        val before = original.matrix(); val after = changed.matrix()
        for (i in before.indices) assertEquals((before[i] + 2.5f).toRawBits(), after[i].toRawBits())
        assertEquals(original.trailerCenter.celsius + 2.5f, changed.trailerCenter.celsius, 0f)
        assertEquals(original.high.celsius + 2.5f, changed.high.celsius, 0f)
    }
    @Test fun sourceLookupAndReturnedArraysCannotMutateEvidence() {
        val raw = bytes(); val f = Ht301Frame.parse(raw)
        val lookup = NativeEquivalentThermometry.buildLookup(f); val expected = lookup.value(5500)
        lookup.values().fill(0f); assertEquals(expected, lookup.value(5500), 0f)
        val m = NativeEquivalentThermometry.measure(f); val center = m.literalCenter.celsius
        raw.fill(0); m.raw14().fill(0); m.matrix().fill(0f); m.source.transportBytes().fill(0)
        assertEquals(center, m.temperature(192, 144), 0f); assertEquals(5128, m.source.inspect().minimum)
    }
    @Test fun structurallyReadyAndValidThermometryProducesCurrentMeasurement() {
        val result = MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY, frame(), 8, 99)
        assertNotNull(result.measurement); assertNull(result.reason); assertEquals(8L, result.measurement!!.sequence)
    }
    @Test fun invalidThermometryClearsPriorMeasurementWithoutChangingSession() {
        assertNotNull(MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY, frame(), 1, 0).measurement)
        val invalid = Ht301Frame.parse(bytes().apply { putWord(this, Ht301Layout.CENTER_INDEX, 0) })
        val result = MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY, invalid, 2, 1)
        assertNull(result.measurement); assertNotNull(result.reason)
    }
    @Test fun heldUnsettledDisconnectedOrMissingFrameCannotRetainCelsius() {
        assertNotNull(MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY, frame(), 1, 0).measurement)
        for (state in SessionState.entries.filter { it != SessionState.RADIOMETRIC_READY })
            assertNull(MeasurementGate.evaluate(state, frame(), 2, 1).measurement)
        assertNull(MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY, null, 3, 2).measurement)
    }
    @Test fun nativeCoordinateBoundsCannotIncludeTrailer() {
        val m = NativeEquivalentThermometry.measure(frame())
        assertThrows(IllegalArgumentException::class.java) { m.temperature(384, 0) }
        assertThrows(IllegalArgumentException::class.java) { m.temperature(0, 288) }
    }
}
