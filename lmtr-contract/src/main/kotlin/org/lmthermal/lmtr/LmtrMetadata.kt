package org.lmthermal.lmtr

import java.math.BigDecimal
import java.security.MessageDigest

/** Contract error classes are stable; detailed rule identifiers remain in the message. */
internal inline fun <T> lmtrMetadataErrors(body: () -> T): T = try { body() } catch (failure: LmtrFailure) {
    val stable = setOf("invalid_metadata", "invalid_payload", "resource_limit", "local_resource_limit", "unsupported_format", "unsupported_major", "unsupported_required_feature", "unsupported_codec")
    if (failure.code in stable) throw failure
    val code = when {
        failure.code == "unsupported_version" -> "unsupported_major"
        failure.code in listOf("metadata_limit", "geometry_limit", "payload_channels", "decoded_limit", "record_limit", "metadata_overflow") -> "resource_limit"
        failure.code.startsWith("payload_") || failure.code.startsWith("plane_") || failure.code.startsWith("view_") || failure.code.startsWith("block_") || failure.code in listOf("temperature_encoding", "validity_encoding", "native_geometry", "visual_encoding", "visual_shape", "opaque_shape", "stored_length") -> "invalid_payload"
        else -> "invalid_metadata"
    }
    throw LmtrFailure(code, failure.code + ": " + failure.message)
}

/** Metadata closure validator for the accepted LMTR v1 grammar.
 * Local closure checks do not establish journal-wide sequence/segment lifecycle,
 * payload integrity, mask/count/cache agreement or backend durability. Those require
 * the container reader's independent byte and full-journal validators.
 */
object LmtrMetadata {
    const val CORE = "org.lmthermal.lmtr.core-v1"
    const val VIEW = "org.lmthermal.lmtr.contiguous-native-view-v1"
    private val roles = listOf("temperature", "validity", "native_samples", "acquisition", "preview", "visible", "module_extension")
    private val dtypes = listOf("u8", "i8", "u16", "i16", "u32", "i32", "u64", "i64", "f32", "f64", "bytes")
    private val endian = listOf("little", "big", "not_applicable")
    private val sizes = mapOf("u8" to 1, "i8" to 1, "u16" to 2, "i16" to 2, "u32" to 4, "i32" to 4, "u64" to 8, "i64" to 8, "f32" to 4, "f64" to 8)
    private const val WARNING = "Native-equivalent temperatures; absolute physical accuracy not yet independently validated."

    /** Bind complete retained semantic JSON to a SHA-256 ID without dropping unknown fields. */
    fun hash(value: Any?): String = MessageDigest.getInstance("SHA-256").digest(LmtrJson.encode(value)).joinToString("") { "%02x".format(it.toInt() and 255) }
    /** Apply byte/structural limits and declared-shape rules before record negotiation. */
    fun local(kind: String, obj: Obj) = lmtrMetadataErrors {
        val bytes = LmtrJson.encode(obj)
        val budget = if (kind in listOf("IndexPage", "Checkpoint", "End")) 524288 else 1048576
        demand(bytes.size <= budget, "metadata_limit")
        LmtrSchema.validate(kind, obj)
    }
    /** Read registry object arrays without scalar coercion or implicit defaults. */
    private fun objects(value: Any?) = value.arr().map { it.obj() }
    /** Require declared ASCII ordering and uniqueness rather than silently sorting input. */
    private fun sortedUnique(values: List<String>) { demand(values == values.sorted() && values.distinct().size == values.size, "metadata_order") }
    /** Build a bounded local closure only after deterministic ordering is verified. */
    private fun ids(list: List<Obj>, key: String): Map<String, Obj> {
        sortedUnique(list.map { it[key].text() }); return list.associateBy { it[key].text() }
    }
    /** Require the nonzero D domain used by lengths and sampling periods. */
    private fun positive(v: Any?): Long { val x = v.d(); demand(x > 0, "metadata_limit"); return x }
    /** Keep all offset/count arithmetic inside the accepted signed-64 domain. */
    private fun add(a: Long, b: Long): Long = try { Math.addExact(a,b) } catch (_: ArithmeticException) { throw LmtrFailure("metadata_overflow") }
    /** Refuse overflowing shape/deadline products before allocation or comparison. */
    private fun multiply(a: Long,b: Long): Long = try { Math.multiplyExact(a,b) } catch (_: ArithmeticException) { throw LmtrFailure("metadata_overflow") }
    /** Compare complete semantic metadata, including unknown optional values. */
    private fun same(a: Any?, b: Any?): Boolean = LmtrJson.encode(a).contentEquals(LmtrJson.encode(b))
    /** Compare exact JSON decimals without binary floating-point conversion. */
    private fun decimal(v: Any?): BigDecimal = when (v) { is JsonNumber -> v.decimal; is Int -> BigDecimal(v); is Long -> BigDecimal(v); else -> throw LmtrFailure("metadata_shape") }

    /** Validate the complete local HEADER and supported interpretation inventory. */
    fun header(obj: Obj) = lmtrMetadataErrors { headerChecks(obj) }

    /** Negotiate versions/features after local shapes; retain explicit stream/lineage facts. */
    private fun headerChecks(obj: Obj) {
        obj["format"]?.let { format -> if(format is String)demand(format=="lmthermal-recording","unsupported_format") }
        local("Header", obj)
        demand(LmtrJson.encode(obj).size <= 1048576, "metadata_limit")
        for (key in listOf("container_version", "schema_version")) {
            val version = obj[key].obj(); demand(version["major"].i() == 1, "unsupported_version")
            // Later minor versions remain readable only with all required semantics supported.
        }
        features(obj["required_features"])
        val codecs = obj["codecs"].arr().map { it.i() }
        demand(codecs == codecs.sorted() && codecs.distinct().size == codecs.size && codecs.all { it in 0..1 }, "unsupported_codec")
        ids(objects(obj["streams"]), "id")
        val privacy = obj["privacy"].obj()
        val namespaces = privacy["consented_namespaces"].arr().map { it.text() }; sortedUnique(namespaces)
        obj["lineage"]?.obj()?.let { lineage ->
            if (lineage["operation"] == "reprocessing") demand(lineage["algorithm"].obj()["status"] == "known", "lineage_algorithm")
            for (parent in objects(lineage["parents"])) {
                // Approach B defers directory/NPZ dataset identity; a Parent binds one real file.
                demand(parent["format_id"] != "lmthermal-radiometric-recording", "unsupported_required_feature")
                demand(parent["recording_id"] != obj["recording_id"], "lineage_self_parent")
            }
        }
        extensions("Header", obj, obj["required_features"].arr().map { it.text() }.toSet())
    }

    /** Require the core feature and reject unfamiliar mandatory interpretation. */
    private fun features(v: Any?): Set<String> {
        val list = v.arr().map { it.text() }; sortedUnique(list)
        demand(CORE in list, "missing_core_feature")
        demand(list.all { it == CORE || it == VIEW }, "unsupported_required_feature")
        return list.toSet()
    }

    /** Required extension semantics are resolved from the record-local feature set.
     * Unknown optional data is retained and never interpreted as executable behavior.
     */
    private fun extensions(kind: String, obj: Obj, allowed: Set<String>) {
        LmtrSchema.visit(kind, obj) { shape, value ->
            if (shape == "Extension") {
                val feature = value.obj()["required_feature"]
                if (feature != null) demand(feature.text() in allowed, "undeclared_feature")
            }
        }
    }

    /** Rules attach to declared shape names, never to arbitrary extension key names. */
    internal fun primitive(kind: String, v: Any?) {
        if (v !is Map<*, *>) return
        val o = v.obj()
        when (kind) {
            "Geometry" -> demand(multiply(o["width_px"].i().toLong(), o["height_px"].i().toLong()) in 1..4194304L, "geometry_limit")
            "Producer" -> { demand(o["version"].text().isNotEmpty(), "producer_empty"); o["build_id"]?.let { demand(it.text().isNotEmpty(), "producer_empty") } }
            "UtcRecord" -> when (o["status"]) {
                "known" -> demand(o.containsKey("utc") && !o.containsKey("reason"), "utc_evidence")
                "unreliable" -> demand(o.containsKey("reason"), "utc_evidence")
                "unknown" -> demand(o.containsKey("reason") && !o.containsKey("utc"), "utc_evidence")
            }
            "EvidenceValue" -> evidence(o)
            "Availability" -> demand(if (o["status"] == "present") !o.containsKey("reason") else o.containsKey("reason"), "retention_reason")
            "Ref" -> {
                demand((o["first"] == null) == (o["last"] == null), "reference_range")
                if (o["first"] != null) demand(o["first"].d() <= o["last"].d(), "reference_range")
                demand(o["length"].d() >= 112, "reference_range")
            }
            "IndexPage" -> {
                demand(o["depth"].i() <= 7 && o["first"].d() <= o["last"].d() && o["time_first_ns"].d() <= o["time_last_ns"].d(), "index_range")
                sortedUnique(o["stream_ids"].arr().map { it.text() })
            }
            "IndexChild" -> {
                demand(o["time_first_ns"].d() <= o["time_last_ns"].d(), "index_range")
                sortedUnique(o["stream_ids"].arr().map { it.text() })
            }
            "End" -> demand((o["status"] == "failed") == (o["error"] != null), "end_error")
            "Statistics" -> {
                val valid = o["valid_pixel_count"].i(); val total = o["pixel_count"].i()
                demand(valid <= total, "statistics_count")
                val fields = listOf("min", "max", "mean", "min_xy", "max_xy")
                demand(if (valid == 0) fields.none { o.containsKey(it) } else fields.all { o.containsKey(it) }, "statistics_fields")
            }
        }
    }

    /** Validate an independently closed CHUNK before any numerical payload is exposed. */
    fun chunk(obj: Obj, header: Obj) = lmtrMetadataErrors { chunkChecks(obj, header) }

    /** Resolve all local references and reject unused or contradictory closure definitions. */
    private fun chunkChecks(obj: Obj, header: Obj) {
        local("Chunk", obj)
        demand(obj["recording_id"] == header["recording_id"], "recording_id")
        demand(LmtrJson.encode(obj).size <= 1048576, "metadata_limit")
        val featureSet = features(obj["required_features"])
        demand(header["required_features"].arr().map { it.text() }.containsAll(featureSet), "undeclared_feature")
        extensions("Chunk", obj, featureSet)
        val slots = objects(header["streams"]).associateBy { it["id"].text() }
        val segments = ids(objects(obj["segments"]), "id")
        val contexts = ids(objects(obj["contexts"]), "id")
        for ((id, context) in contexts) {
            demand(hash(context["value"]) == id, "context_hash")
            val value = context["value"].obj()
            for (k in listOf("algorithm", "calibration", "settings", "environment")) evidence(value[k].obj())
        }
        for ((id, segment) in segments) {
            demand(hash(segment.filterKeys { it != "id" }) == id, "segment_hash")
            val streams = ids(objects(segment["streams"]), "stream_id")
            for ((slot, stream) in streams) {
                demand(slot in slots, "undeclared_stream")
                stream(stream)
            }
        }
        val usedSegments = mutableSetOf<String>(); val usedContexts = mutableSetOf<String>()
        val blocks = objects(obj["blocks"])
        val materialized = mutableListOf<Obj>()
        var lastId: Long? = null; var lastTime = -1L
        for (entry in objects(obj["entries"])) {
            val id = entry["entry_id"].d(); val time = entry["timeline_ns"].d()
            lastId?.let { demand(id == add(it, 1), "entry_order") }
            demand(time >= lastTime, "timeline_order"); lastId = id; lastTime = time
            val segmentId = entry["segment_id"].text()
            val segment = segments[segmentId] ?: throw LmtrFailure("segment_reference")
            usedSegments.add(segmentId)
            demand(id >= segment["start_entry"].d() && time >= segment["start_timeline_ns"].d(), "segment_start")
            if (entry["kind"] == "segment_start") demand(id == segment["start_entry"].d() && time == segment["start_timeline_ns"].d(), "segment_start")
            if (entry["kind"] in listOf("observation", "gap")) {
                val stream = objects(segment["streams"]).find { it["stream_id"] == entry["stream_id"] } ?: throw LmtrFailure("stream_reference")
                schedule(entry["scheduled"], stream["sampling"].obj(), entry["kind"] == "observation")
                if (entry["kind"] == "gap") gap(entry, stream)
                else {
                    val contextId = entry["context_id"].text()
                    val context = contexts[contextId]?.get("value")?.obj() ?: throw LmtrFailure("context_reference")
                    usedContexts.add(contextId)
                    demand(same(context["source"], stream["source"]), "context_source")
                    observation(entry, stream, context, header, featureSet)
                    materialized += objects(entry["payloads"]).filter { it["kind"] == "materialized" }
                }
            }
        }
        demand(usedSegments == segments.keys && usedContexts == contexts.keys, "unused_closure")
        blockGroups(blocks, materialized, header, LmtrJson.encode(obj).size)
    }

    /** Distinguish complete, partial and unavailable evidence without fabricated values. */
    private fun evidence(o: Obj) {
        when (o["status"]) {
            "known" -> demand(o.containsKey("value") && !o.containsKey("reason"), "value_evidence")
            "partial" -> demand(o.containsKey("value") && o.containsKey("reason"), "value_evidence")
            else -> demand(!o.containsKey("value") && o.containsKey("reason"), "value_evidence")
        }
    }

    /** Validate immutable source configuration, local clocks, offers and sampling policy. */
    private fun stream(o: Obj) {
        val sampling = o["sampling"].obj()
        if (sampling["mode"] == "acquisition") demand(sampling["period_ns"] == null && sampling["origin_timeline_ns"] == null, "sampling")
        else { positive(sampling["period_ns"]); sampling["origin_timeline_ns"].d() }
        val clocks = objects(o["clocks"])
        demand(clocks.map { it["id"] }.distinct().size == clocks.size, "clock_duplicate")
        for (clock in clocks) if (clock["quality"] == "known") demand(clock["tick_period_ns"] != null, "clock_period")
        for (anchor in objects(o["utc_anchors"])) {
            stamp(anchor["stamp"], clocks, false)
            demand(anchor["utc"].obj().containsKey("utc"), "utc_anchor")
        }
        val offers = objects(o["encodings"])
        demand(offers.map { Pair(it["role"], it["encoding_id"]) }.distinct().size == offers.size, "encoding_duplicate")
        val requested = o["policy"].obj()["requested_roles"].arr().map { it.text() }
        demand(requested.distinct().size == requested.size && requested == requested.sorted(), "policy_order")
        if (o["geometry"] == null) demand(offers.none { it["role"] in listOf("temperature", "validity", "native_samples", "preview", "visible") }, "geometry_required")
    }

    /** Resolve genuine stamps locally and keep receipt evidence on host clock domains. */
    private fun stamp(v: Any?, clocks: List<Obj>, receipt: Boolean) {
        if (v == null) return
        val clock = clocks.find { it["id"] == v.obj()["clock_domain_id"] } ?: throw LmtrFailure("clock_reference")
        if (receipt) demand(clock["kind"] == "host_monotonic", "receipt_clock")
    }

    /** Require actual readings for known/unreliable time and truthful absence reasons. */
    private fun time(o: Obj, clocks: List<Obj>, receipt: Boolean) {
        stamp(o["stamp"], clocks, receipt)
        val status = o["status"]
        when (status) {
            "known" -> {
                demand(o["stamp"] != null || o["utc"].obj()["status"] == "known", "time_evidence")
                if (o["stamp"] != null) {
                    val clock = clocks.first { it["id"] == o["stamp"].obj()["clock_domain_id"] }
                    demand(clock["quality"] == "known" && clock["tick_period_ns"] != null, "clock_quality")
                }
            }
            "unreliable" -> demand(o.containsKey("reason") && (o["stamp"] != null || o["utc"].obj().containsKey("utc")), "time_evidence")
            else -> demand(o.containsKey("reason") && o["stamp"] == null && o["utc"].obj()["status"] == "unknown", "time_evidence")
        }
    }

    /** Verify qualified periodic deadlines with checked arithmetic and no invented slots. */
    private fun schedule(v: Any?, sampling: Obj, observation: Boolean) {
        if (sampling["mode"] == "acquisition") { demand(v == null, "schedule_unexpected"); return }
        if (v == null && !observation) return
        demand(v != null, "schedule_required")
        val o = v.obj(); val first = o["first_slot"].d(); val last = o["last_slot"].d()
        demand(first <= last && (!observation || first == last), "schedule_range")
        val origin = sampling["origin_timeline_ns"].d(); val period = positive(sampling["period_ns"])
        demand(o["first_deadline_ns"].d() == add(origin, multiply(first, period)) && o["last_deadline_ns"].d() == add(origin, multiply(last, period)), "schedule_deadline")
    }

    /** Check every independent loss range/count and genuine time-bound domain. */
    private fun gap(o: Obj, stream: Obj) {
        val counts = mutableListOf<Long>()
        for (prefix in listOf("device", "observation")) {
            val a = o["${prefix}_first"]; val b = o["${prefix}_last"]
            demand((a == null) == (b == null), "gap_range")
            if (a != null) { demand(a.d() <= b.d(), "gap_range"); counts += add(b.d() - a.d(), 1) }
        }
        o["scheduled"]?.obj()?.let { counts += add(it["last_slot"].d() - it["first_slot"].d(), 1) }
        val count = o["count"]
        if (count == null) demand(counts.isEmpty(), "gap_count")
        else {
            positive(count); demand(counts.all { it == count.d() }, "gap_count")
            demand(counts.isNotEmpty() || o["evidence"].obj()["basis"] == "explicit_signal", "gap_basis")
        }
        val clocks = objects(stream["clocks"])
        val evidence = o["evidence"].obj()
        for (key in listOf("before", "after")) stamp(evidence[key], clocks, false)
        if (evidence["before"] != null && evidence["after"] != null) {
            val before = evidence["before"].obj(); val after = evidence["after"].obj()
            if (before["clock_domain_id"] == after["clock_domain_id"]) demand(before["ticks"].d() <= after["ticks"].d(), "gap_bounds")
        }
    }

    /** Bind one observation to its source/context, retention and current analysis snapshot. */
    private fun observation(o: Obj, stream: Obj, context: Obj, header: Obj, featureSet: Set<String>) {
        val clocks = objects(stream["clocks"])
        time(o["receipt"].obj(), clocks, true); time(o["acquisition"].obj(), clocks, false)
        val payloadList = objects(o["payloads"])
        val payloads = payloadList.associateBy { it["id"].text() }
        demand(payloads.size == payloadList.size, "payload_duplicate")
        demand(payloadList == payloadList.sortedWith(compareBy<Obj> { roles.indexOf(it["role"]) }.thenBy { it["id"].text() }), "payload_order")
        val core = payloadList.filter { it["role"] != "module_extension" }
        demand(core.map { it["role"] }.distinct().size == core.size, "payload_role_duplicate")
        val measurement = o["measurement"].obj()
        val available = measurement["status"] == "available"
        val measurable = available && measurement["validity"] != "no_valid_pixels"
        val geometry = stream["geometry"]
        for (payload in payloadList) {
            descriptor(payload)
            val role = payload["role"].text()
            if (role == "module_extension") {
                demand(payload.containsKey("namespace") && payload["id"] !in roles, "payload_namespace")
            }
            else {
                demand(payload["id"] == role, "payload_id")
                demand(objects(stream["encodings"]).any { it["role"] == role && it["encoding_id"] == payload["encoding_id"] }, "encoding_offer")
            }
            if (role in listOf("temperature", "validity", "native_samples", "acquisition")) demand(payload["lossiness"] == "lossless", "payload_lossiness")
            if (role == "temperature" || role == "validity") {
                demand(payload["kind"] == "materialized" && same(payload["geometry"], geometry), "plane_geometry")
                demand(payload["shape"].arr().size == 2, "plane_shape")
                if (role == "temperature") demand(payload["dtype"] == "f32" && payload["byte_order"] == "little" && payload["unit"] == "Cel" && payload["encoding_id"] == "org.lmthermal.temperature.f32le-v1", "temperature_encoding")
                else demand(payload["dtype"] == "u8" && payload["unit"] == null && payload["encoding_id"] == "org.lmthermal.validity.u8-v1", "validity_encoding")
            }
            if (role == "native_samples") demand(same(payload["geometry"], geometry), "native_geometry")
            if (payload["kind"] == "view") {
                demand(VIEW in featureSet && role == "native_samples", "view_feature")
                val parent = payloads[payload["parent_payload_id"]] ?: throw LmtrFailure("view_parent")
                demand(parent["role"] == "acquisition" && parent["kind"] == "materialized", "view_parent")
                demand(add(payload["offset"].d(), payload["length"].d()) <= parent["length"].d(), "view_bounds")
                demand(payload["offset"].d() % (sizes[payload["dtype"]] ?: 1) == 0L, "view_alignment")
            }
        }
        if (available) {
            demand(payloads[measurement["temperature_payload_id"]]?.get("role") == "temperature", "temperature_reference")
            if (measurement["validity"] != "all_valid") demand(measurement.containsKey("mask_payload_id"), "mask_required")
            measurement["mask_payload_id"]?.let { demand(payloads[it]?.get("role") == "validity", "mask_reference") }
            demand(stream["policy"].obj()["level"] != "visual", "visual_measurement")
            demand("temperature" in stream["policy"].obj()["requested_roles"].arr(), "policy_temperature")
            val provenance = measurement["provenance"].obj()
            if (provenance["physical_accuracy"] == "independently_validated") demand(provenance.containsKey("validation_evidence"), "accuracy_evidence")
            val source = stream["source"].obj()
            if (provenance["kind"] == "native_equivalent" && (source["module_id"].text().contains("ht301") || payloadList.any { it["encoding_id"] == "org.lmthermal.ht301.raw14" })) {
                demand(provenance["physical_accuracy"] == "not_independently_validated" && provenance["warning"] == WARNING, "physical_accuracy_warning")
            }
        } else demand(payloadList.none { it["role"] in listOf("temperature", "validity") }, "absent_temperature")
        val retention = o["retention"].obj()
        for (role in listOf("temperature", "native_samples", "acquisition", "preview", "visible")) {
            val present = retention[role].obj()["status"] == "present"
            demand(present == payloadList.any { it["role"] == role }, "retention_payload")
            if (stream["capabilities"].obj()[role] == "unsupported") demand(!present, "unsupported_retention")
        }
        val requested = stream["policy"].obj()["requested_roles"].arr().map { it.text() }
        for (role in requested.filter { it in retention }) demand(retention[role].obj()["status"] != "omitted", "requested_retention")
        val calibration = retention["calibration_settings"].obj()["status"] == "present"
        if (calibration) {
            val known = listOf("calibration", "settings", "environment").any { context[it].obj()["status"] in listOf("known", "partial") }
            // Extension schemas identify opaque calibration evidence; generic code cannot guess it.
            demand(known || payloadList.any { it["role"] == "module_extension" }, "calibration_retention")
            demand(stream["capabilities"].obj()["calibration_settings"] != "unsupported", "unsupported_retention")
        }
        val lineage = header["lineage"]?.obj()
        val conversion = lineage?.get("operation") in listOf("conversion", "reprocessing")
        if(lineage?.get("operation")=="reprocessing") demand(context["algorithm"].obj()["status"]=="known" && context["settings"].obj()["status"]=="known", "reprocessing_context")
        demand(o.containsKey("parent_entry") == conversion, "parent_entry")
        o["parent_entry"]?.obj()?.let {
            demand(it["parent_index"].i() < lineage!!["parents"].arr().size, "parent_reference")
            if (it["entry_id"] == null) demand(o["extensions"]?.obj()?.isNotEmpty() == true, "legacy_parent_reference")
        }
        o["analysis"]?.obj()?.let { analysis(it, geometry, measurable, measurement) }
        o["presentation"]?.obj()?.let {
            val range = it["effective_bounds"]
            if (!measurable) demand(it["range_mode"] == "none" && range == null, "unavailable_presentation")
            if (it["range_mode"] == "none") demand(range == null, "presentation_bounds")
            else { demand(range != null, "presentation_bounds"); demand(decimal(range.obj()["min"]) < decimal(range.obj()["max"]), "presentation_bounds") }
        }
        measurement["statistics"]?.obj()?.let { statistics(it, geometry.obj(), measurement) }
    }

    /** Validate exact shapes/dtype/endianness without transforming original source bytes. */
    private fun descriptor(p: Obj) {
        val dtype = p["dtype"].text(); val shape = p["shape"].arr().map { it.i() }
        demand(shape.all { it > 0 }, "payload_shape")
        val length = positive(p["length"])
        if (dtype == "bytes") demand(shape.size == 1 && shape[0].toLong() == length && p["layout"] == "opaque" && p["geometry"] == null && p["unit"] == null && p["byte_order"] == "not_applicable", "opaque_shape")
        else {
            val geometry = p["geometry"]?.obj() ?: throw LmtrFailure("payload_geometry")
            demand(p["layout"] == "row_major" && shape.size in 2..3 && shape[0] == geometry["height_px"].i() && shape[1] == geometry["width_px"].i(), "payload_shape")
            if (shape.size == 3) demand(shape[2] in 1..16, "payload_channels")
            var bytes = sizes.getValue(dtype).toLong()
            for (dimension in shape) bytes = multiply(bytes, dimension.toLong())
            demand(length == bytes, "payload_length")
            demand(if (sizes.getValue(dtype) == 1) p["byte_order"] == "not_applicable" else p["byte_order"] in listOf("little", "big"), "payload_endian")
        }
        val visualChannels = mapOf("org.lmthermal.visual.gray8-v1" to 1, "org.lmthermal.visual.rgb8-v1" to 3, "org.lmthermal.visual.rgba8-v1" to 4)
        visualChannels[p["encoding_id"]]?.let { channels ->
            demand(p["role"] in listOf("preview", "visible") && dtype == "u8" && p["byte_order"] == "not_applicable", "visual_encoding")
            demand(if (channels == 1) shape.size == 2 else shape.size == 3 && shape[2] == channels, "visual_shape")
        }
    }

    /** Validate native-coordinate snapshot references; numerical caches require valid data. */
    private fun analysis(o: Obj, geometry: Any?, measurable: Boolean, measurement: Obj) {
        val points = objects(o["points"]); val rectangles = objects(o["rectangles"]); val annotations = objects(o["annotations"])
        val all = points + rectangles + annotations; val ids = all.map { it["id"].text() }
        demand(ids.distinct().size == ids.size, "analysis_duplicate")
        val g = geometry?.obj()
        for (point in points) {
            demand(g != null && point["x_px"].i() < g["width_px"].i() && point["y_px"].i() < g["height_px"].i(), "point_bounds")
            if (!measurable) demand(!point.containsKey("temperature_c"), "unavailable_analysis")
        }
        for (rectangle in rectangles) {
            val bounds = rectangle["bounds"].obj()
            demand(g != null && bounds["x1_px"].i() < bounds["x2_px"].i() && bounds["x2_px"].i() <= g["width_px"].i() && bounds["y1_px"].i() < bounds["y2_px"].i() && bounds["y2_px"].i() <= g["height_px"].i(), "roi_bounds")
            rectangle["statistics"]?.obj()?.let { cache ->
                demand(measurable, "unavailable_analysis")
                statistics(cache, g!!, measurement)
                val count = multiply((bounds["x2_px"].i() - bounds["x1_px"].i()).toLong(), (bounds["y2_px"].i() - bounds["y1_px"].i()).toLong())
                demand(cache["pixel_count"].i().toLong() == count, "roi_count")
                for (name in listOf("min_xy", "max_xy")) cache[name]?.arr()?.let { xy -> demand(xy[0].i() in bounds["x1_px"].i() until bounds["x2_px"].i() && xy[1].i() in bounds["y1_px"].i() until bounds["y2_px"].i(), "roi_extrema") }
            }
        }
        for (annotation in annotations) demand(annotation["target_id"] == null || annotation["target_id"] in ids, "annotation_reference")
    }

    /** Check metadata cache references/bounds; the byte validator independently recomputes values. */
    private fun statistics(o: Obj, geometry: Obj, measurement: Obj) {
        demand(o["temperature_payload_id"] == measurement["temperature_payload_id"], "statistics_reference")
        for (key in listOf("min_xy", "max_xy")) o[key]?.arr()?.let { demand(it[0].i() < geometry["width_px"].i() && it[1].i() < geometry["height_px"].i(), "statistics_bounds") }
        if (o["valid_pixel_count"].i() > 0) demand(decimal(o["min"]) <= decimal(o["mean"]) && decimal(o["mean"]) <= decimal(o["max"]), "statistics_range")
    }

    /** Use the complete physical grouping key; role alone cannot merge encodings. */
    private fun group(p: Obj) = listOf(p["role"].text(), p["encoding_id"].text(), p["dtype"].text(), p["byte_order"].text())
    private val groupOrder = Comparator<List<String>> { a, b ->
        var result = roles.indexOf(a[0]).compareTo(roles.indexOf(b[0]))
        if (result == 0) result = a[1].compareTo(b[1])
        if (result == 0) result = dtypes.indexOf(a[2]).compareTo(dtypes.indexOf(b[2]))
        if (result == 0) result = endian.indexOf(a[3]).compareTo(endian.indexOf(b[3]))
        result
    }
    /** Require role-major contiguous blocks and exact logical-slice coverage within budgets. */
    private fun blockGroups(blocks: List<Obj>, materialized: List<Obj>, header: Obj, metadataBytes: Int) {
        val groups = materialized.groupBy(::group)
        val keys = groups.keys.sortedWith(groupOrder)
        demand(blocks.size == keys.size, "block_groups")
        var stored = 0L; var decoded = 0L
        for ((index, block) in blocks.withIndex()) {
            demand(block["id"] == "b$index" && group(block) == keys[index], "block_order")
            demand(block["codec"].i() in header["codecs"].arr().map { it.i() }, "undeclared_codec")
            demand(block["offset"].d() == stored, "block_offset")
            stored = add(stored, positive(block["stored"])); decoded = add(decoded, positive(block["decoded"]))
            if (block["codec"].i() == 0) demand(block["stored"] == block["decoded"], "stored_length")
            var offset = 0L
            for (payload in groups.getValue(keys[index])) {
                demand(payload["block_id"] == block["id"] && payload["offset"].d() == offset, "block_slice")
                offset = add(offset, positive(payload["length"]))
            }
            demand(offset == block["decoded"].d(), "block_coverage")
        }
        demand(add(decoded, metadataBytes.toLong() + 4L) <= 67108864L, "decoded_limit")
        demand(add(stored, metadataBytes.toLong() + 116L) <= 68157440L, "record_limit")
    }
}
