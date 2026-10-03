package org.lmthermal.exchange

import java.io.File
import java.security.MessageDigest
import org.lmthermal.core.*

/** Shared deterministic synthetic/sanitized corpus, generated from the production writer and checked after reopen. */
object LmtxFixtureGenerator {
    @JvmStatic fun main(args: Array<String>) {
        val directory = File(args.single()).apply { mkdirs() }
        val grid = NativeImageGeometry(160, 120)
        val captures = linkedMapOf(
            "temperature-only" to LmtxTestSupport.capture(),
            "validity-mask" to LmtxTestSupport.capture(values = floatArrayOf(20f, Float.NaN, 31f, 24f), mask = byteArrayOf(1, 0, 1, 1), id = "ca8a1d7b-f38c-45aa-b94b-c7777fa31002"),
            "no-valid-pixels" to LmtxTestSupport.capture(mask = ByteArray(4), id = "ca8a1d7b-f38c-45aa-b94b-c7777fa31003"),
            "preview-only-160x120" to LmtxTestSupport.capture(grid, null, roi = null, preview = LmtxTestSupport.preview(grid), id = "ca8a1d7b-f38c-45aa-b94b-c7777fa31004"),
            "alternate-7x19" to LmtxTestSupport.capture(NativeImageGeometry(7, 19), FloatArray(133) { it.toFloat() }, id = "ca8a1d7b-f38c-45aa-b94b-c7777fa31005"),
            "ht301-rich-sanitized" to LmtxTestSupport.ht())
        val results = mutableListOf<JsonObject>()
        fun record(file: File, expected: String) {
            val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            results += mapOf("file" to file.name, "archive_sha256" to sha, "archive_bytes" to file.length(), "expected" to expected)
        }
        for ((name, capture) in captures) {
            val file = File(directory, name + ".lmtx"); LmtxWriter.write(capture, file); record(file, "valid")
        }
        val base = LmtxTestSupport.members(File(directory, "temperature-only.lmtx"))
        for ((name, change) in linkedMapOf<String, (MutableMap<String, Any?>) -> Unit>(
            "newer-minor-optional" to { m -> m["schema_version"] = mapOf("major" to 1, "minor" to 7); m["unknown_optional"] = java.math.BigDecimal("1.12345678901234567890123456789") },
            "unsupported-major" to { m -> m["schema_version"] = mapOf("major" to 2, "minor" to 0) },
            "unsupported-required" to { m -> m["required_features"] = listOf("core.still", "core.native-coordinates", "vendor.unrecognized") })) {
            val members = base.toMutableMap(); val manifest = LmtxJson.decode(members.getValue("manifest.json")).toMutableMap(); change(manifest)
            members["manifest.json"] = LmtxJson.encode(manifest)
            val file = File(directory, name + ".lmtx"); LmtxTestSupport.archive(file, members)
            val expected = when (name) { "unsupported-major" -> "unsupported_major"; "unsupported-required" -> "unsupported_required_feature"; else -> "valid" }
            if (expected == "valid") LmtxChecker.check(file) else check(runCatching { LmtxChecker.check(file) }.exceptionOrNull().let { it is LmtxException && it.code == expected })
            record(file, expected)
        }
        val corrupt = base.toMutableMap(); corrupt["data/temperature.f32le"] = corrupt.getValue("data/temperature.f32le").copyOf().apply { this[0] = 1 }
        val file = File(directory, "corrupt-sha.lmtx"); LmtxTestSupport.archive(file, corrupt); record(file, "integrity_mismatch")
        // Optional future data stays declarative/opaque; it cannot override the authoritative matrix or core analysis.
        val future = base.toMutableMap(); val manifest = LmtxJson.decode(future.getValue("manifest.json")).toMutableMap()
        val namespace = "org.example.optional"
        val metadata = LmtxJson.encode(mapOf("optional_value" to java.math.BigDecimal("0.12345678901234567890123456789")))
        val additions = listOf(Triple("optional-json", "extension_json", metadata), Triple("optional-blob", "extension_blob", byteArrayOf(0, -1, 7)))
        val descriptors = additions.map { (id, role, bytes) ->
            val name = "extensions/$namespace/" + if (role == "extension_json") "metadata.json" else "opaque.bin"
            future[name] = bytes
            mapOf("id" to id, "member" to name, "role" to role, "media_type" to if (role == "extension_json") "application/json" else "application/octet-stream",
                "byte_length" to bytes.size, "sha256" to MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        manifest["payloads"] = manifest["payloads"].array() + descriptors
        manifest["extensions"] = listOf(mapOf("id" to namespace, "schema_version" to mapOf("major" to 3, "minor" to 2),
            "metadata_payload_id" to "optional-json", "blob_payload_ids" to listOf("optional-blob")))
        val analysis = manifest["analysis"].obj().toMutableMap()
        analysis["shapes"] = analysis["shapes"].array() + listOf(mapOf("id" to "future-shape", "type" to "org.example.outline",
            "coordinate_space" to "native", "geometry" to mapOf("control_points" to listOf(listOf(0, 0), listOf(1, 1)))))
        manifest["analysis"] = analysis; manifest["presentation"] = mapOf("palette_id" to "org.example.optional-palette")
        future["manifest.json"] = LmtxJson.encode(manifest)
        val optionalFile = File(directory, "optional-extension-shape-palette.lmtx")
        LmtxTestSupport.archive(optionalFile, future); LmtxChecker.check(optionalFile); record(optionalFile, "valid")
        File(directory, "corpus.json").writeBytes(LmtxJson.encode(mapOf("format" to "lmthermal-conformance-corpus", "version" to 1, "cases" to results)))
        println("Generated " + results.size + " shared cases")
    }
}
