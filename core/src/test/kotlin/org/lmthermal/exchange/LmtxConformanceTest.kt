package org.lmthermal.exchange

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Published corpus is an interoperability input, not a writer implementation mirror. All archive bytes are pinned. */
class LmtxConformanceTest {
    @Test fun sharedCorpusHashesAndExpectedOutcomesRemainPinned() {
        val corpus = javaClass.getResourceAsStream("/lmtx/corpus.json")!!.use { LmtxJson.decode(it.readBytes()) }
        for (case in corpus["cases"].array().map { it.obj() }) {
            val bytes = javaClass.getResourceAsStream("/lmtx/" + case["file"])!!.use { it.readBytes() }
            assertEquals(case["archive_bytes"].integer(), bytes.size)
            assertEquals(case["archive_sha256"], sha(bytes))
            val file = File.createTempFile("corpus", ".lmtx")
            try { file.writeBytes(bytes)
                if (case["expected"] == "valid") LmtxChecker.check(file)
                else reject(case["expected"].text()) { LmtxChecker.check(file) }
            } finally { file.delete() }
        }
    }
    @Test fun unsafeZipFlagsAttributesDuplicatesAndLocalContradictionsFailBeforeData() = artifact { file ->
        val base = file.readBytes()
        val central = find(base, byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        val invalids = listOf(
            base.copyOf().apply { this[central + 8] = (this[central + 8].toInt() or 1).toByte() }, // Encryption.
            base.copyOf().apply { this[central + 6] = 45 }, // ZIP64 profile version.
            base.copyOf().apply { this[central + 5] = 3; this[central + 41] = 0xa0.toByte() }, // Unix symlink.
            base.copyOf().apply { this[central + 5] = 3; this[central + 40] = 0x40 }, // Unix execute bit.
            base.copyOf().apply { this[8] = 0 }, // Local/central method mismatch.
            base.copyOf().apply { this[18] = 1 }, // Contradictory local compressed size.
            base.copyOf().apply { this[central + 24] = 1; this[central + 27] = 8 } // Advertised member >128MiB.
        )
        invalids.forEachIndexed { index, bytes -> file.writeBytes(bytes)
            reject(if (index == invalids.lastIndex) "resource_limit" else "invalid_container") { LmtxChecker.check(file) } }
        val members = LmtxTestSupport.members(File.createTempFile("duplicate-base", ".lmtx").also { it.writeBytes(base); it.deleteOnExit() }).toMutableMap()
        members["data/temperature.f32lf"] = byteArrayOf(1)
        LmtxTestSupport.archive(file, members)
        val duplicate = file.readBytes()
        val old = "data/temperature.f32lf".toByteArray()
        var offset = 0
        while (true) { val position = find(duplicate, old, offset); if (position < 0) break
            duplicate[position + old.lastIndex] = 'e'.code.toByte(); offset = position + old.size }
        file.writeBytes(duplicate); reject("unsafe_path") { LmtxChecker.check(file) }
    }
    @Test fun masksNonfiniteValuesAndNoncanonicalInvalidFillersAreRejected() {
        for ((member, mutate) in listOf<Pair<String, (ByteArray) -> Unit>>(
            "data/validity.u8" to { it[1] = 2 },
            "data/temperature.f32le" to { it[7] = 0x80.toByte() }, // Invalid-cell negative zero.
            "data/temperature.f32le" to { it[2] = 0x80.toByte(); it[3] = 0x7f })) { // Valid-cell infinity.
            artifact({ file -> LmtxWriter.write(LmtxTestSupport.capture(mask = byteArrayOf(1, 0, 1, 1)), file) }) { file ->
                val members = LmtxTestSupport.members(file).toMutableMap(); val bytes = members.getValue(member).copyOf(); mutate(bytes); members[member] = bytes
                val m = LmtxJson.decode(members.getValue("manifest.json")).toMutableMap()
                m["payloads"] = m["payloads"].array().map { it.obj().let { p -> if (p["member"] == member) p + ("sha256" to sha(bytes)) else p } }
                members["manifest.json"] = LmtxJson.encode(m); LmtxTestSupport.archive(file, members)
                reject("invalid_payload") { LmtxChecker.check(file) }
            }
        }
    }
    @Test fun knownOptionalNullAndMissingPointCoordinatesFailButUnknownNullSurvives() = artifact { file ->
        val base = LmtxTestSupport.members(file)
        for (mutation in listOf<(MutableMap<String, Any?>) -> Unit>(
            { it["presentation"] = null },
            { it["analysis"] = mapOf("producer" to LmtxTestSupport.producer.json(), "creation_time" to LmtxTestSupport.clock.json(),
                "points" to listOf(mapOf("id" to "point", "coordinate_space" to "native"))) })) {
            val members = base.toMutableMap(); val m = LmtxJson.decode(members.getValue("manifest.json")).toMutableMap(); mutation(m)
            members["manifest.json"] = LmtxJson.encode(m); LmtxTestSupport.archive(file, members)
            reject("invalid_manifest") { LmtxChecker.check(file) }
        }
        val members = base.toMutableMap(); val m = LmtxJson.decode(members.getValue("manifest.json")) + ("future_optional" to null)
        members["manifest.json"] = LmtxJson.encode(m); LmtxTestSupport.archive(file, members)
        assertTrue(LmtxChecker.check(file).containsKey("future_optional"))
    }
    @Test fun optionalExtensionVersionZeroIsIndependentOfAcceptedRootMajor() {
        val bytes = javaClass.getResourceAsStream("/lmtx/optional-extension-shape-palette.lmtx")!!.use { it.readBytes() }
        artifact({ it.writeBytes(bytes) }) { file ->
            val members = LmtxTestSupport.members(file).toMutableMap(); val m = LmtxJson.decode(members.getValue("manifest.json")).toMutableMap()
            m["extensions"] = m["extensions"].array().map { it.obj() + ("schema_version" to mapOf("major" to 0, "minor" to 2)) }
            members["manifest.json"] = LmtxJson.encode(m); LmtxTestSupport.archive(file, members)
            assertEquals(1, LmtxChecker.check(file)["schema_version"].obj()["major"].integer())
        }
    }
    @Test fun visibleImagesSharingCoordinateSpaceMustShareDimensions() = artifact { file ->
        val base = LmtxJson.decode(LmtxTestSupport.members(file).getValue("manifest.json")).toMutableMap()
        fun visible(id: String, width: Int) = mapOf("id" to id, "member" to "preview/$id.png", "role" to "visible_image", "media_type" to "image/png",
            "byte_length" to 10, "sha256" to "0".repeat(64), "image" to mapOf("width_px" to width, "height_px" to 2, "coordinate_space" to "rgb", "orientation" to "stored_pixels"))
        base["payloads"] = base["payloads"].array() + listOf(visible("visible1", 2), visible("visible2", 3))
        base["availability"] = base["availability"].obj() + ("visible_image" to mapOf("status" to "present"))
        reject("invalid_manifest") { LmtxSchema.validate(LmtxJson.decode(LmtxJson.encode(base))) }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun find(bytes: ByteArray, target: ByteArray, start: Int = 0): Int = (start..bytes.size - target.size).firstOrNull {
        position -> target.indices.all { bytes[position + it] == target[it] } } ?: -1
    private fun reject(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: LmtxException) { assertEquals(code, failure.code) }
    }
    private fun artifact(write: (File) -> Unit = { LmtxWriter.write(LmtxTestSupport.capture(), it) }, block: (File) -> Unit) {
        val file = File.createTempFile("lmtx-negative", ".lmtx")
        try { write(file); block(file) } finally { file.delete() }
    }
}
