package org.lmthermal.exchange

import java.io.File
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.core.*
import org.lmthermal.camera.*

/** Independent byte expectations and malformed archive mutations, never a camera/thermometry mock writer. */
class LmtxWriterTest {
    private fun artifact(capture: LmtxCapture = LmtxTestSupport.capture(), block: (File, JsonObject) -> Unit) {
        val file = File.createTempFile("lmtx-test-", ".lmtx")
        try { LmtxWriter.write(capture, file); block(file, LmtxChecker.check(file)) } finally { file.delete() }
    }
    private fun mutate(file: File, body: (MutableMap<String, ByteArray>) -> Unit) {
        val contents = LmtxTestSupport.members(file).toMutableMap(); body(contents); LmtxTestSupport.archive(file, contents)
    }
    private fun manifest(contents: MutableMap<String, ByteArray>, body: (MutableMap<String, Any?>) -> Unit) {
        val m = LmtxJson.decode(contents.getValue("manifest.json")).toMutableMap(); body(m)
        contents["manifest.json"] = LmtxJson.encode(m)
    }
    private fun error(code: String, block: () -> Unit) { assertEquals(code, assertThrows(LmtxException::class.java, block).code) }
    @Test fun identityRequiredFieldsInventoryAndLengths() = artifact { file, m ->
        assertEquals("lmthermal-exchange", m["format"]); assertEquals("still", m["kind"]); assertEquals(true, m["complete"])
        assertEquals(1, m["schema_version"].obj()["major"].integer()); assertEquals(0, m["schema_version"].obj()["minor"].integer())
        for (field in listOf("capture_id", "producer", "creation_time", "source", "geometry", "acquisition", "capabilities", "availability", "measurement", "required_features")) assertTrue(m.containsKey(field))
        val members = LmtxTestSupport.members(file)
        val payloads = m["payloads"].array().map { it.obj() }
        assertEquals(members.keys - "manifest.json", payloads.map { it["member"] }.toSet())
        for (p in payloads) { val bytes = members.getValue(p["member"].text())
            assertEquals(bytes.size, p["byte_length"].integer()); assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, p["sha256"]) }
        assertFalse(m.containsKey("lineage"))
    }
    @Test fun exactFloat32BitsIncludingSignedZeroAndNonRoundedValues() {
        val values = floatArrayOf(-0f, 1.2345678f, Float.MIN_VALUE, Float.MAX_VALUE)
        artifact(LmtxTestSupport.capture(values = values)) { file, _ ->
            assertEquals(values.map { it.toRawBits() }, LmtxTestSupport.floatBits(LmtxTestSupport.members(file).getValue("data/temperature.f32le"))) }
    }
    @Test fun noUnnecessaryAllOnesMask() = artifact { file, m ->
        assertFalse(LmtxTestSupport.members(file).containsKey("data/validity.u8")); assertEquals("all_valid", m["measurement"].obj()["validity"]) }
    @Test fun canonicalMaskedExampleMatchesPublishedHashesAndStatistics() = artifact(LmtxTestSupport.capture(
        values = floatArrayOf(20f, Float.NaN, 31f, 24f), mask = byteArrayOf(1, 0, 1, 1))) { file, m ->
        assertEquals(listOf(20f.toRawBits(), 0, 31f.toRawBits(), 24f.toRawBits()), LmtxTestSupport.floatBits(LmtxTestSupport.members(file).getValue("data/temperature.f32le")))
        val payloads = m["payloads"].array().map { it.obj() }
        assertEquals("2840d544ef96d01aa388ad89cabc70e860c16b48949aab03e7e7b5ee2ea8699a", payloads.first()["sha256"])
        assertEquals("52a5c4a10657220cac05c63adfa923c7771c55d868a58ee360eb3d1511985c3e", payloads[1]["sha256"])
        assertEquals(25.0, m["measurement"].obj()["extrema"].obj()["mean"].number(), 0.0)
    }
    @Test fun allInvalidHasCountsWithoutReadingsOrCoordinates() = artifact(LmtxTestSupport.capture(mask = ByteArray(4))) { _, m ->
        assertEquals("no_valid_pixels", m["measurement"].obj()["validity"])
        val stats = m["measurement"].obj()["extrema"].obj(); assertEquals(0, stats["valid_pixel_count"].integer())
        for (key in listOf("min", "max", "mean", "min_xy", "max_xy")) assertFalse(stats.containsKey(key))
    }
    @Test fun alternateGeometryAndRowMajorWords() { val g = NativeImageGeometry(7, 19); val values = FloatArray(g.pixelCount) { it.toFloat() }
        artifact(LmtxTestSupport.capture(g, values)) { file, m -> assertEquals(7, m["geometry"].obj()["width_px"].integer())
            assertEquals(values.map { it.toRawBits() }, LmtxTestSupport.floatBits(LmtxTestSupport.members(file).getValue("data/temperature.f32le"))) } }
    @Test fun roiBoundsAndStatsMatchAuthoritativeCore() = artifact { _, m ->
        val s = m["analysis"].obj()["shapes"].array().single().obj()
        assertEquals("half_open", s["interval"]); assertEquals(4, s["statistics"].obj()["pixel_count"].integer())
        assertEquals(18.75, s["statistics"].obj()["mean"].number(), 0.0)
    }
    @Test fun unavailableTemperatureCanRetainGeometryNotStatistics() {
        val g = NativeImageGeometry(160, 120)
        artifact(LmtxTestSupport.capture(g, null, preview = LmtxTestSupport.preview(g))) { _, m ->
            assertEquals("visual", m["content_class"]); assertEquals("unavailable", m["measurement"].obj()["status"])
            assertFalse(m["analysis"].obj()["shapes"].array().single().obj().containsKey("statistics"))
            assertFalse(m.containsKey("extensions")); assertFalse(m["measurement"].obj().containsKey("provenance"))
        }
    }
    @Test fun htRichPreservesNativeTransportSettingsAndSeparateCenters() = artifact(LmtxTestSupport.ht()) { file, m ->
        val members = LmtxTestSupport.members(file); assertEquals(224256, members.getValue("data/acquisition.bin").size)
        assertArrayEquals(members.getValue("data/acquisition.bin").copyOfRange(0, 221184), members.getValue("data/native.u16le"))
        val metadata = LmtxJson.decode(members.getValue("extensions/org.lmthermal.camera.ht301/metadata.json"))
        assertTrue(metadata["observations"].obj().keys.containsAll(listOf("trailer_center", "literal_center")))
        assertEquals(NativeEquivalentThermometry.WARNING, m["measurement"].obj()["provenance"].obj()["warning"])
        assertFalse(m["source"].obj().containsKey("module_version"))
    }
    @Test fun snapshotOwnsArraysMasksAndPresentationAfterChanges() {
        val values = floatArrayOf(20f, 0f, 31f, 24f); val mask = byteArrayOf(1, 0, 1, 1)
        val temp = CapturedTemperature(LmtxTestSupport.grid, values, mask, mapOf("kind" to "simulated", "physical_accuracy" to "not_applicable"))
        values.fill(999f); mask.fill(0)
        val c = LmtxCapture(LmtxTestSupport.grid, CaptureSource("test", "test"), LmtxTestSupport.producer, LmtxTestSupport.clock, temperature = temp)
        artifact(c) { file, _ -> assertEquals(listOf(20f.toRawBits(), 0, 31f.toRawBits(), 24f.toRawBits()), LmtxTestSupport.floatBits(LmtxTestSupport.members(file).getValue("data/temperature.f32le"))) }
    }
    @Test fun closedDetachedOrMissingSourceCannotFreezeLastReady() {
        val module = CameraModuleMetadata(CameraModuleId("test"), "test", CameraCapabilities(true, true, false, true))
        val m = OwnedThermalMeasurement(LmtxTestSupport.grid, FloatArray(4), 1, 1, MeasurementProvenance(module.id, module.modelId, TemperatureProvenanceKind.SIMULATED))
        for (life in listOf(CameraLifecycle.CLOSED, CameraLifecycle.DISCONNECTED, CameraLifecycle.ERROR)) assertFalse(CaptureFreeze.canCapture(CameraSessionState<Any>(module = module, lifecycle = life, geometry = m.geometry, measurement = m)))
    }
    @Test fun hashesRejectModifiedOptionalPayload() = artifact(LmtxTestSupport.capture(preview = LmtxTestSupport.preview(LmtxTestSupport.grid))) { file, _ ->
        mutate(file) { it["preview/thermal.png"] = it.getValue("preview/thermal.png").copyOf().apply { this[30] = (this[30] + 1).toByte() } }
        error("integrity_mismatch") { LmtxChecker.check(file) }
    }
    @Test fun missingPayloadFails() = artifact { file, _ -> mutate(file) { it.remove("data/temperature.f32le") }; error("missing_payload") { LmtxChecker.check(file) } }
    @Test fun undeclaredPayloadFails() = artifact { file, _ -> mutate(file) { it["data/extra"] = byteArrayOf(1) }; error("missing_payload") { LmtxChecker.check(file) } }
    @Test fun storedAndDeflatedBothValidateWithoutZip64() = artifact { file, _ ->
        assertTrue(LmtxContainer.inspect(file).all { it.method == 8 })
        LmtxTestSupport.archive(file, LmtxTestSupport.members(file), ZipEntry.STORED)
        assertTrue(LmtxContainer.inspect(file).all { it.method == 0 }); LmtxChecker.check(file)
    }
    @Test fun windowsNamesAbsoluteTraversalMixedCaseAndEmptyComponentsRejected() {
        for (p in listOf("/data/x", "data/../x", "data/a..b", "data/CON", "data/con.bin", "data/lpt9.txt", "data/a/", "data//x", "data/a\\x", "data/a.", "other/x")) error("unsafe_path") { LmtxContainer.safePath(p) }
    }
    @Test fun parentFileCollisionRejectedBeforeFollowingPaths() = artifact { file, _ ->
        mutate(file) { it["data/temperature.f32le/child"] = byteArrayOf(1) }; error("unsafe_path") { LmtxChecker.check(file) } }
    @Test fun newerMinorUnknownOptionalNumberRetainsSemanticPrecision() = artifact { file, _ ->
        mutate(file) { contents -> manifest(contents) { it["schema_version"] = mapOf("major" to 1, "minor" to 9); it["optional_decimal"] = BigDecimal("0.123456789012345678901234567890") } }
        assertEquals(BigDecimal("0.123456789012345678901234567890"), LmtxChecker.check(file)["optional_decimal"])
    }
    @Test fun unsupportedMajorFails() = artifact { file, _ -> mutate(file) { c -> manifest(c) { it["schema_version"] = mapOf("major" to 2, "minor" to 0) } }; error("unsupported_major") { LmtxChecker.check(file) } }
    @Test fun unknownRequiredFeatureFailsBeforeMeasurements() = artifact { file, _ -> mutate(file) { c -> manifest(c) { it["required_features"] = listOf("core.still", "core.native-coordinates", "vendor.required") } }; error("unsupported_required_feature") { LmtxChecker.check(file) } }
    @Test fun wrongStatisticsLocationFails() = artifact { file, _ -> mutate(file) { c -> manifest(c) {
        val measurement = it["measurement"].obj().toMutableMap(); val stats = measurement["extrema"].obj().toMutableMap()
        stats["max_xy"] = listOf(0, 0); measurement["extrema"] = stats; it["measurement"] = measurement
    } }; error("invalid_payload") { LmtxChecker.check(file) } }
    @Test fun clockKnownUnreliableAndUnknownRemainDistinct() {
        for (c in listOf(CaptureClock("known", "device_clock", "2026-10-03T12:30:00Z", null),
            CaptureClock("unreliable", "host_wall_clock", "2026-10-03T12:30:00.123Z", "not_verified"), LmtxTestSupport.clock))
            artifact(LmtxTestSupport.capture(creation = c)) { _, m -> assertEquals(c.status, m["creation_time"].obj()["status"]); assertFalse(m["acquisition"].obj()["time"].obj().containsKey("utc")) }
    }
    @Test fun duplicateJsonKeysBomTrailingTextAndNonfiniteReject() {
        for (s in listOf("{\"x\":1,\"x\":2}", "\uFEFF{}", "{}true", "{\"x\":NaN}", "{\"x\":Infinity}", "{\"x\":01}")) error("invalid_manifest") { LmtxJson.decode(s.toByteArray()) }
    }
    @Test fun jsonStringDepthItemsAndMemberNameCeilings() {
        error("resource_limit") { LmtxJson.encode(mapOf("x" to "a".repeat(16_385))) }
        var v: Any? = 1; repeat(33) { v = listOf(v) }; error("resource_limit") { LmtxJson.encode(mapOf("x" to v)) }
        error("resource_limit") { LmtxJson.encode(mapOf("x" to List(65_537) { 0 })) }
        LmtxContainer.safePath("data/" + "a".repeat(235)); error("unsafe_path") { LmtxContainer.safePath("data/" + "a".repeat(236)) }
    }
    @Test fun truncatedArchiveFailsAndManifestIsNotSelfHashed() = artifact { file, m ->
        assertTrue(m["payloads"].array().none { it.obj()["member"] == "manifest.json" })
        java.io.RandomAccessFile(file, "rw").use { it.setLength(file.length() - 2) }; error("invalid_container") { LmtxChecker.check(file) }
    }
    @Test fun displayPreviewFreezeDoesNotUsePreviousMeasurementOrHtEvidence() {
        val module = CameraModuleMetadata(CameraModuleId("simulated-preview"), "simulated-160x120", CameraCapabilities(true, false, false, false), "simulated")
        val g = NativeImageGeometry(160, 120)
        val source = CameraSessionState(module = module, geometry = g, lifecycle = CameraLifecycle.STREAMING,
            preview = CameraPreview(g, 3, 1000, Unit))
        val capture = CaptureFreeze.freeze(source, NativeRoiSelection(), CelsiusPresentationSettings(), null, LmtxTestSupport.producer,
            LmtxTestSupport.clock, "dba2f0a6-ecb2-4a92-9049-f07f0e094b72", { _, _ -> error("No numeric rendering") }, { LmtxTestSupport.png(160, 120) })
        artifact(capture) { _, m -> assertEquals("visual", m["content_class"]); assertEquals("simulated", m["source"].obj()["origin"])
            assertFalse(m["presentation"].obj().containsKey("effective_bounds")); assertFalse(m.containsKey("extensions"))
            assertEquals("1000000000", m["acquisition"].obj()["receipt"].obj()["monotonic_ns"]) }
    }
}
