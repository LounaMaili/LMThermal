package org.lmthermal.exchange

import java.io.*
import java.security.MessageDigest
import java.util.zip.*

/** Streaming v1 producer. Hashes repeatable owned members first, then writes one ordinary DEFLATE archive.
 * Caller supplies an exclusively created private file; public destinations only receive the finalized validated artifact.
 */
object LmtxWriter {
    fun write(capture: LmtxCapture, file: File): JsonObject {
        val geometry = capture.geometry
        val grid = mapOf("shape" to listOf(geometry.height, geometry.width), "order" to "row_major", "coordinate_space" to "native")
        val members = mutableListOf<EvidencePayload>()
        capture.temperature?.let {
            members += EvidencePayload("temperature", "data/temperature.f32le", "temperature", "application/octet-stream", it,
                grid + mapOf("encoding" to "ieee754", "dtype" to "f32", "byte_order" to "little", "unit" to "Cel"))
            it.validityMember()?.let { mask -> members += EvidencePayload("validity", "data/validity.u8", "temperature_validity", "application/octet-stream",
                mask, grid + mapOf("encoding" to "validity.u8", "dtype" to "u8", "byte_order" to "not_applicable")) }
        }
        capture.preview?.let { members += it }; members += capture.evidence.payloads
        demand(members.size in 1..255, "resource_limit")
        val descriptors = members.map { member ->
            demand(member.bytes.size in 1..134_217_728, "resource_limit")
            LmtxContainer.safePath(member.member)
            val attributes = member.attributes()
            demand(attributes.keys.none { it in setOf("id", "member", "role", "media_type", "byte_length", "sha256") })
            attributes + mapOf("id" to member.id, "member" to member.member, "role" to member.role,
                "media_type" to member.mediaType, "byte_length" to member.bytes.size.toInt(), "sha256" to hash(member.bytes))
        }
        demand(descriptors.map { it["id"] }.toSet().size == members.size)
        demand(members.map { it.member }.toSet().size == members.size, "unsafe_path")
        val capabilities = LmtxSchema.ROLES.associateWith { capture.capabilities[it] ?: capture.evidence.capabilities[it] ?: "unknown" }
        val roleMapping = mapOf("temperature" to "temperature", "native_samples" to "native_samples",
            "acquisition_payload" to "acquisition", "preview" to "preview", "visible_image" to "visible_image")
        val availability = LmtxSchema.ROLES.associateWith { role ->
            val present = if (role == "calibration_settings") capture.evidence.settingsExtensionIds.isNotEmpty()
                else members.any { it.role == roleMapping[role] }
            if (present) mapOf("status" to "present") else mapOf("status" to "absent", "reason" to
                if (capabilities[role] == "unsupported") "unsupported" else if (role == "temperature") capture.unavailableReason else "not_exposed")
        }
        val temp = capture.temperature
        val manifest: JsonObject = buildMap {
            put("format", "lmthermal-exchange"); put("schema_version", mapOf("major" to 1, "minor" to 0)); put("kind", "still")
            put("capture_id", capture.captureId); put("complete", true)
            put("content_class", when {
                temp != null -> "radiometric"; members.any { it.role == "native_samples" } -> "native_samples"
                members.any { it.role in setOf("preview", "visible_image") } -> "visual"; else -> "opaque_evidence"
            })
            put("required_features", buildList {
                add("core.still"); add("core.native-coordinates")
                if (temp != null) add("core.temperature-f32le")
                if (members.any { it.role == "temperature_validity" }) add("core.validity-u8")
            })
            put("producer", capture.producer.json()); put("creation_time", capture.creation.json()); put("source", capture.source.json())
            put("geometry", mapOf("width_px" to geometry.width, "height_px" to geometry.height,
                "coordinate_space" to "native", "origin" to "top_left", "x_direction" to "right", "y_direction" to "down",
                "matrix_order" to "row_major", "orientation" to "source_native"))
            put("acquisition", buildMap {
                put("time", capture.acquisition.json()); capture.sequence?.let { put("sequence", it.toString()) }
                capture.receiptMonotonicNs?.let { put("receipt", mapOf("clock_domain_id" to capture.clockDomainId, "monotonic_ns" to it.toString())) }
                if (capture.evidence.settingsExtensionIds.isNotEmpty()) put("settings_extension_ids", capture.evidence.settingsExtensionIds)
            })
            put("capabilities", capabilities); put("availability", availability)
            put("measurement", if (temp == null) mapOf("status" to "unavailable", "reason" to capture.unavailableReason) else buildMap {
                put("status", "available"); put("temperature_payload_id", "temperature"); put("validity", temp.validity)
                if (members.any { it.role == "temperature_validity" }) put("mask_payload_id", "validity")
                put("provenance", temp.provenance()); put("extrema", temp.fullStatistics.exchangeJson())
            })
            put("payloads", descriptors)
            if (capture.evidence.extensions().isNotEmpty()) put("extensions", capture.evidence.extensions())
            if (capture.roi != null || capture.point != null) put("analysis", buildMap {
                put("producer", capture.producer.json()); put("creation_time", capture.creation.json())
                capture.roi?.let { roi -> put("shapes", listOf(buildMap {
                    put("id", "roi"); put("type", "rectangle"); put("coordinate_space", "native"); put("interval", "half_open")
                    put("bounds", mapOf("x1_px" to roi.x1, "y1_px" to roi.y1, "x2_px" to roi.x2, "y2_px" to roi.y2))
                    temp?.let { put("statistics", it.statistics(roi).exchangeJson()) }
                })) }
                capture.point?.let { pixel -> put("points", listOf(buildMap {
                    put("id", "cursor"); put("coordinate_space", "native"); put("x_px", pixel.x); put("y_px", pixel.y)
                    temp?.temperature(pixel)?.let { put("temperature_c", it) }
                })) }
            })
            if (capture.presentation().isNotEmpty()) put("presentation", capture.presentation())
        }
        val json = LmtxJson.encode(manifest)
        demand(members.sumOf { it.bytes.size } + json.size <= 536_870_912, "resource_limit")
        FileOutputStream(file).use { raw ->
            val limited = object : FilterOutputStream(raw) {
                var count = 0L
                override fun write(b: Int) { demand(++count <= 268_435_456, "resource_limit"); out.write(b) }
                override fun write(b: ByteArray, off: Int, len: Int) { count += len; demand(count <= 268_435_456, "resource_limit"); out.write(b, off, len) }
            }
            ZipOutputStream(limited).use { zip ->
                zip.setLevel(3)
                fun member(name: String, bytes: MemberBytes) {
                    val entry = ZipEntry(name); entry.time = 315532800000L; entry.method = ZipEntry.DEFLATED
                    zip.putNextEntry(entry); bytes.writeTo(zip); zip.closeEntry()
                }
                members.forEach { member(it.member, it.bytes) }; member("manifest.json", OwnedMember(json))
                zip.finish(); zip.flush(); raw.fd.sync()
            }
        }
        LmtxChecker.check(file)
        return manifest
    }
    private fun hash(bytes: MemberBytes): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        bytes.writeTo(object : OutputStream() {
            override fun write(b: Int) { digest.update(b.toByte()); count++ }
            override fun write(b: ByteArray, off: Int, len: Int) { digest.update(b, off, len); count += len }
        })
        demand(count == bytes.size, "invalid_payload")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
