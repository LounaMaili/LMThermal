package org.lmthermal.exchange

import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import org.lmthermal.core.*

internal fun Any?.obj(): JsonObject {
    demand(this is Map<*, *>); @Suppress("UNCHECKED_CAST") return this as JsonObject
}
internal fun Any?.array(): List<Any?> { demand(this is List<*>); return this as List<Any?> }
internal fun Any?.text(): String { demand(this is String && isNotEmpty()); return this as String }
internal fun Any?.integer(min: Int = 0, max: Int = Int.MAX_VALUE): Int {
    demand(this is BigInteger && this >= min.toBigInteger() && this <= max.toBigInteger()); return (this as BigInteger).toInt()
}
internal fun Any?.number(): Double { demand(this is Number); return (this as Number).toDouble().also { demand(it.isFinite()) } }

/** Understood v1 fields and references; unknown optional JSON is retained by the exact-decimal JSON model.
 * Cross-payload binary checks follow inventory/hash verification, before any numerical results are returned.
 */
object LmtxSchema {
    private fun optionalFields(objectValue: JsonObject, vararg names: String) {
        names.forEach { demand(!objectValue.containsKey(it) || objectValue[it] != null) }
    }
    val ROLES = listOf("preview", "temperature", "native_samples", "acquisition_payload", "calibration_settings", "visible_image")
    private val features = setOf("core.still", "core.temperature-f32le", "core.validity-u8", "core.native-coordinates", "core.analysis-v1")
    private val idPattern = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    fun id(value: Any?): String = value.text().also { demand(it.matches(idPattern) && !it.contains("..")) }
    private fun uuid(value: Any?) {
        val s = value.text(); demand(s.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        demand(UUID.fromString(s) != UUID(0, 0))
    }
    private fun decimal(value: Any?) { val s = value.text(); demand(s.matches(Regex("0|[1-9][0-9]*")) &&
        s.toBigInteger() <= BigInteger("18446744073709551615")) }
    private fun producer(value: Any?) { val p = value.obj(); optionalFields(p, "build_id"); id(p["application_id"]); p["version"].text(); p["build_id"]?.text() }
    private fun version(value: Any?): Pair<Int, Int> { val v = value.obj(); return v["major"].integer() to v["minor"].integer() }
    private fun clock(value: Any?) {
        val c = value.obj(); id(c["clock_source"])
        optionalFields(c, "utc", "uncertainty_ns", "reason")
        val status = c["status"].text(); demand(status in setOf("known", "unreliable", "unknown"))
        if (status != "known") id(c["reason"])
        demand(status != "known" || c.containsKey("utc")); demand(status != "unknown" || !c.containsKey("utc"))
        c["utc"]?.let { val s = it.text()
            demand(s.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-5][0-9](?:\\.[0-9]{1,9})?Z")))
            try { Instant.parse(s) } catch (_: Exception) { throw LmtxException("invalid_manifest", "Invalid UTC") }
        }
        c["uncertainty_ns"]?.let(::decimal)
    }
    internal fun geometry(manifest: JsonObject): NativeImageGeometry {
        val g = manifest["geometry"].obj(); val w = g["width_px"].integer(1); val h = g["height_px"].integer(1)
        demand(w.toLong() * h <= 33_554_432, "resource_limit")
        for ((key, value) in mapOf("coordinate_space" to "native", "origin" to "top_left", "x_direction" to "right",
            "y_direction" to "down", "matrix_order" to "row_major", "orientation" to "source_native")) demand(g[key] == value)
        return NativeImageGeometry(w, h)
    }
    fun validate(manifest: JsonObject): List<JsonObject> {
        optionalFields(manifest, "extensions", "analysis", "presentation", "lineage")
        demand(manifest["format"] == "lmthermal-exchange", "unsupported_format")
        demand(version(manifest["schema_version"]).first == 1, "unsupported_major")
        demand(manifest["kind"] == "still", "unsupported_kind"); demand(manifest["complete"] == true)
        uuid(manifest["capture_id"]); producer(manifest["producer"]); clock(manifest["creation_time"])
        val source = manifest["source"].obj(); demand(id(source["module_id"]).matches(Regex("[a-z0-9][a-z0-9.-]*")))
        optionalFields(source, "module_version")
        source["model_id"].text(); source["module_version"]?.text()
        demand(source["origin"] in setOf("device", "simulated", "imported_legacy", "unknown"))
        val grid = geometry(manifest)
        val required = manifest["required_features"].array().map(::id)
        demand(required.size <= 64 && required.toSet().size == required.size)
        demand(required.all { it in features }, "unsupported_required_feature")
        demand(required.containsAll(listOf("core.still", "core.native-coordinates")))
        val acquisition = manifest["acquisition"].obj(); clock(acquisition["time"])
        optionalFields(acquisition, "producer", "sequence", "receipt", "settings_extension_ids")
        acquisition["producer"]?.let(::producer); acquisition["sequence"]?.let(::decimal)
        acquisition["receipt"]?.obj()?.let { uuid(it["clock_domain_id"]); decimal(it["monotonic_ns"]); it["utc"]?.let(::clock) }
        val payloads = manifest["payloads"].array().map { it.obj() }; demand(payloads.size in 1..255, "resource_limit")
        val ids = payloads.map { id(it["id"]) }; demand(ids.toSet().size == ids.size)
        val names = payloads.map { it["member"].text().also(LmtxContainer::safePath) }
        demand(names.toSet().size == names.size && "manifest.json" !in names, "unsafe_path")
        payloads.forEach { p ->
            val role = id(p["role"]); val member = p["member"].text(); p["media_type"].text()
            p["byte_length"].integer(1, 134_217_728)
            demand(p["sha256"].text().matches(Regex("[0-9a-f]{64}")))
            if (role in setOf("temperature", "temperature_validity", "native_samples")) {
                val shape = p["shape"].array().map { it.integer(1) }
                demand(shape.size == 2 || role == "native_samples" && shape.size == 3)
                demand(shape.take(2) == listOf(grid.height, grid.width)); if (shape.size == 3) demand(shape[2] <= 16)
                demand(p["order"] == "row_major" && p["coordinate_space"] == "native")
                val size = mapOf("u8" to 1, "u16" to 2, "u32" to 4, "i16" to 2, "i32" to 4, "f32" to 4, "f64" to 8)[p["dtype"]]
                demand(size != null); demand(shape.fold(size!!.toLong()) { a, b -> a * b } == p["byte_length"].integer().toLong())
                demand(p["byte_order"] == if (size == 1) "not_applicable" else "little")
                demand(member.startsWith("data/"))
                if (role == "temperature") demand(p["encoding"] == "ieee754" && p["dtype"] == "f32" &&
                    p["unit"] == "Cel" && p["media_type"] == "application/octet-stream")
                if (role == "temperature_validity") demand(p["encoding"] == "validity.u8" && p["dtype"] == "u8")
                if (role == "native_samples") demand(p["encoding"].text().contains('.'))
            } else if (role == "acquisition") { demand(member.startsWith("data/")); id(p["encoding"]) }
            else if (role in setOf("preview", "visible_image")) {
                demand(member.startsWith("preview/") && p["media_type"] in setOf("image/png", "image/jpeg"))
                val image = p["image"].obj(); val w = image["width_px"].integer(1); val h = image["height_px"].integer(1)
                demand(w.toLong() * h <= 33_554_432, "resource_limit"); demand(image["orientation"] == "stored_pixels")
                val space = id(image["coordinate_space"])
                demand(if (role == "preview") space == "native" && w == grid.width && h == grid.height else space != "native")
            } else if (role in setOf("extension_json", "extension_blob")) demand(member.startsWith("extensions/"))
        }
        fun byRole(role: String) = payloads.filter { it["role"] == role }
        val temperatures = byRole("temperature"); val masks = byRole("temperature_validity")
        demand(temperatures.size <= 1 && masks.size <= 1)
        val measurement = manifest["measurement"].obj()
        optionalFields(measurement, "extrema", "mask_payload_id")
        val present = measurement["status"] == "available"
        demand(measurement["status"] in setOf("available", "unavailable") && present == temperatures.isNotEmpty())
        if (!present) { id(measurement["reason"]); demand(masks.isEmpty() &&
            listOf("temperature_payload_id", "mask_payload_id", "extrema", "provenance", "validity").none { measurement.containsKey(it) }) }
        else {
            demand(measurement["temperature_payload_id"] == temperatures.single()["id"] && "core.temperature-f32le" in required)
            demand(measurement["validity"] in setOf("all_valid", "partially_valid", "no_valid_pixels"))
            demand(if (masks.isEmpty()) !measurement.containsKey("mask_payload_id") && measurement["validity"] == "all_valid"
                else measurement["mask_payload_id"] == masks.single()["id"] && "core.validity-u8" in required)
            val provenance = measurement["provenance"].obj()
            optionalFields(provenance, "algorithm", "warning")
            demand(provenance["kind"] in setOf("native_equivalent", "device_reported", "simulated", "derived", "unknown"))
            demand(provenance["physical_accuracy"] in setOf("not_independently_validated", "independently_validated", "unknown", "not_applicable"))
            provenance["algorithm"]?.obj()?.let { id(it["id"]); it["version"].text() }
            // Evidence field names are module-owned; the core schema does not invent a validation-metadata API.
        }
        val extensions = manifest["extensions"]?.array()?.map { it.obj() } ?: emptyList()
        val namespaces = extensions.map { id(it["id"]).also { n -> demand(n.matches(Regex("[a-z0-9][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+"))) } }
        demand(namespaces.toSet().size == namespaces.size)
        val assigned = mutableSetOf<String>()
        extensions.forEach { extension ->
            optionalFields(extension, "metadata_payload_id", "blob_payload_ids")
            version(extension["schema_version"])
            val refs = mutableListOf<Pair<String, String>>()
            extension["metadata_payload_id"]?.let { refs += id(it) to "extension_json" }
            extension["blob_payload_ids"]?.array()?.forEach { refs += id(it) to "extension_blob" }
            demand(refs.isNotEmpty())
            refs.forEach { (ref, role) -> val payload = payloads.singleOrNull { it["id"] == ref }
                demand(assigned.add(ref) && payload != null && payload["role"] == role &&
                    payload["member"].text().startsWith("extensions/" + extension["id"] + "/"))
                if (role == "extension_json") demand(payload!!["media_type"] == "application/json")
            }
        }
        demand(payloads.filter { it["role"] in setOf("extension_json", "extension_blob") }.all { it["id"] in assigned })
        val settings = acquisition["settings_extension_ids"]?.array()?.map(::id) ?: emptyList()
        demand(settings.toSet().size == settings.size && settings.all { it in namespaces })
        val caps = manifest["capabilities"].obj(); val availability = manifest["availability"].obj()
        val roles = mapOf("preview" to "preview", "temperature" to "temperature", "native_samples" to "native_samples",
            "acquisition_payload" to "acquisition", "visible_image" to "visible_image")
        for (role in ROLES) {
            demand(caps[role] in setOf("supported", "unsupported", "unknown"))
            val a = availability[role].obj(); demand(a["status"] in setOf("present", "absent"))
            val exists = if (role == "calibration_settings") settings.isNotEmpty() else byRole(roles.getValue(role)).isNotEmpty()
            demand((a["status"] == "present") == exists && (!exists || caps[role] != "unsupported"))
            if (!exists) id(a["reason"])
        }
        val classification = when { present -> "radiometric"; byRole("native_samples").isNotEmpty() -> "native_samples"
            byRole("preview").isNotEmpty() || byRole("visible_image").isNotEmpty() -> "visual"
            byRole("acquisition").isNotEmpty() -> "opaque_evidence"; else -> throw LmtxException("invalid_payload", "No source payload") }
        demand(manifest["content_class"] == classification)
        byRole("visible_image").groupBy { it["image"].obj()["coordinate_space"] }.values.forEach { images ->
            demand(images.map { p -> p["image"].obj().let { listOf(it["width_px"], it["height_px"], it["orientation"]) } }.toSet().size == 1)
        }
        manifest["presentation"]?.obj()?.let { p ->
            optionalFields(p, "palette_id", "range_mode", "effective_bounds", "transform")
            p["palette_id"]?.let(::id); p["range_mode"]?.let { demand(it in setOf("auto", "manual")) }
            if (p.containsKey("effective_bounds")) { demand(p.containsKey("range_mode") && present)
                val b = p["effective_bounds"].obj(); demand(b["unit"] == "Cel" && b["min"].number() < b["max"].number()) }
            if (present && measurement["validity"] != "no_valid_pixels" && p.containsKey("range_mode")) demand(p.containsKey("effective_bounds"))
            p["transform"]?.obj()?.let { t -> demand(t["rotation_degrees"].integer() in setOf(0, 90, 180, 270))
                demand(t["mirror_x"] is Boolean && t["mirror_y"] is Boolean) }
        }
        manifest["analysis"]?.obj()?.let { analysis -> producer(analysis["producer"]); clock(analysis["creation_time"])
            optionalFields(analysis, "points", "shapes", "annotations")
            val points = analysis["points"]?.array()?.map { it.obj() } ?: emptyList()
            val shapes = analysis["shapes"]?.array()?.map { it.obj() } ?: emptyList()
            val annotations = analysis["annotations"]?.array()?.map { it.obj() } ?: emptyList()
            val items = listOf("points", "shapes", "annotations").flatMap { analysis[it]?.array()?.map { a -> a.obj() } ?: emptyList() }
            demand(items.map { id(it["id"]) }.toSet().size == items.size)
            for (item in items) {
                optionalFields(item, "label", "temperature_c", "statistics", "anchor", "target_id")
                val space = id(item["coordinate_space"])
                val image = payloads.firstOrNull { it["role"] == "visible_image" && it["image"].obj()["coordinate_space"] == space }
                demand(space == "native" || image != null)
                val bounds = if (space == "native") grid else image!!["image"].obj().let { NativeImageGeometry(it["width_px"].integer(1), it["height_px"].integer(1)) }
                if (item in points) bounds.pixel(item["x_px"].integer(), item["y_px"].integer())
                if (item in shapes) {
                    id(item["type"])
                    if (item["type"] == "rectangle") { demand(space == "native" && item["interval"] == "half_open"); rectangle(item).validate(bounds) }
                    else demand(item.containsKey("geometry") && item["geometry"] != null)
                }
                if (item in annotations) demand(item["text"] is String)
                if (item.containsKey("statistics") || item.containsKey("temperature_c")) demand(space == "native" && present)
                item["label"]?.let { demand(it is String) }
                item["anchor"]?.obj()?.let { bounds.pixel(it["x_px"].integer(), it["y_px"].integer()) }
                item["target_id"]?.let { ref -> demand((points + shapes).any { it["id"] == ref }) }
            }
        }
        // Analysis derivatives are not produced here. Their reference/byte-preservation validation requires a parent-aware path.
        if (manifest.containsKey("lineage")) throw LmtxException("unsupported_required_feature", "Derivative validation is not implemented")
        return payloads
    }
    internal fun rectangle(shape: JsonObject): NativeRect = shape["bounds"].obj().let {
        NativeRect(it["x1_px"].integer(), it["y1_px"].integer(), it["x2_px"].integer(), it["y2_px"].integer()) }
}
