package org.lmthermal.lmtr

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Offline evaluator for the exact keyword subset in the pinned LMTR shape registry.
 * Unknown optional properties remain owned JSON values. No external schema is resolved.
 * Cross-field, payload and journal rules are checked separately; this is not sufficient
 * to expose a recording's numerical data on its own.
 */
object LmtrSchema {
    private val definitions: Obj by lazy {
        val bytes = LmtrSchema::class.java.getResourceAsStream("/lmtr-schema.json")
            ?.use { it.readBytes() } ?: error("Missing pinned lmtr-schema.json resource")
        LmtrJson.parse(bytes)["\$defs"].obj()
    }

    /** Validate a pinned local shape and expose only stable contract error classes. */
    fun validate(kind: String, value: Any?) = lmtrMetadataErrors {
        validateUnchecked(kind, value)
    }

    /** Apply the registry shape followed by named primitive refinements. */
    private fun validateUnchecked(kind: String, value: Any?) {
        val shape = definitions[kind]?.obj() ?: error("Unknown internal shape $kind")
        check(shape, value)
        alias(kind, value)
        LmtrMetadata.primitive(kind, value)
    }

    /** Visit only registry-declared objects. JsonValue and unknown fields are opaque. */
    internal fun visit(kind: String, value: Any?, visitor: (String, Any?) -> Unit) {
        visitor(kind, value)
        walk(definitions.getValue(kind).obj(), value, visitor)
    }

    /** Follow declared schema branches only; opaque JSON and unknown fields are not traversed. */
    private fun walk(s: Obj, value: Any?, visitor: (String, Any?) -> Unit) {
        s["\$ref"]?.let { visit(it.text().substringAfterLast('/'), value, visitor) }
        for (mode in listOf("oneOf", "anyOf")) s[mode]?.arr()?.let { alternatives ->
            alternatives.firstOrNull { matches(it.obj(), value) }?.let { walk(it.obj(), value, visitor) }
        }
        s["allOf"]?.arr()?.forEach { walk(it.obj(), value, visitor) }
        if (value is Map<*, *>) {
            val properties = s["properties"]?.obj() ?: emptyMap()
            for ((key, item) in value.obj()) {
                val shape = properties[key] ?: s["additionalProperties"]
                if (shape is Map<*, *>) walk(shape.obj(), item, visitor)
            }
        } else if (value is List<*> && s["items"] is Map<*, *>) value.forEach { walk(s["items"].obj(), it, visitor) }
    }

    /** Retain exact decimal comparisons for registry numeric bounds. */
    private fun number(v: Any?): BigDecimal = when (v) {
        is JsonNumber -> v.decimal
        is Int -> BigDecimal.valueOf(v.toLong())
        is Long -> BigDecimal.valueOf(v)
        else -> throw LmtrFailure("metadata_shape")
    }

    /** Test union alternatives without turning their rejected shapes into accepted defaults. */
    private fun matches(shape: Obj, value: Any?): Boolean = try {
        check(shape, value); true
    } catch (_: LmtrFailure) { false }

    /** Evaluate only the pinned registry keyword subset with bounded local recursion. */
    private fun check(s: Obj, value: Any?) {
        s["\$ref"]?.let { validate(it.text().substringAfterLast('/'), value) }
        for (mode in listOf("oneOf", "anyOf")) s[mode]?.let { choices ->
            val count = choices.arr().count { matches(it.obj(), value) }
            demand(if (mode == "oneOf") count == 1 else count > 0, "metadata_union")
        }
        s["allOf"]?.arr()?.forEach { check(it.obj(), value) }
        s["not"]?.let { demand(!matches(it.obj(), value), "forbidden_known_field") }
        s["enum"]?.let { choices ->
            demand(choices.arr().any { equal(it, value) }, "metadata_enum")
        }
        s["type"]?.let {
            val ok = when (it.text()) {
                "object" -> value is Map<*, *>
                "array" -> value is List<*>
                "string" -> value is String
                "integer" -> try { value.i(); true } catch (_: LmtrFailure) { false }
                "number" -> value is JsonNumber || value is Int || value is Long
                "boolean" -> value is Boolean
                "null" -> value == null
                else -> error("Unsupported pinned type")
            }
            demand(ok, "metadata_shape")
        }
        when (value) {
            is Map<*, *> -> {
                val o = value.obj()
                s["required"]?.arr()?.forEach { demand(o.containsKey(it.text()), "metadata_required") }
                s["maxProperties"]?.let { demand(o.size <= it.i(), "metadata_limit") }
                val properties = s["properties"]?.obj() ?: emptyMap()
                for ((key, item) in o) {
                    s["propertyNames"]?.let { check(it.obj(), key) }
                    val child = properties[key] ?: s["additionalProperties"] ?: true
                    demand(child != false, "metadata_extra_field")
                    if (child is Map<*, *>) check(child.obj(), item)
                }
            }
            is List<*> -> {
                s["minItems"]?.let { demand(value.size >= it.i(), "metadata_limit") }
                s["maxItems"]?.let { demand(value.size <= it.i(), "metadata_limit") }
                if (s["uniqueItems"] == true) demand(value.map { LmtrJson.encode(it).toList() }.distinct().size == value.size, "metadata_duplicate")
                s["items"]?.let { child -> value.forEach { check(child.obj(), it) } }
            }
            is String -> {
                val count = value.codePointCount(0, value.length)
                s["minLength"]?.let { demand(count >= it.i(), "metadata_limit") }
                s["maxLength"]?.let { demand(count <= it.i(), "metadata_limit") }
                s["pattern"]?.let { demand(Regex(it.text()).matches(value), "metadata_pattern") }
            }
            is JsonNumber, is Int, is Long -> {
                s["minimum"]?.let { demand(number(value) >= number(it), "metadata_limit") }
                s["maximum"]?.let { demand(number(value) <= number(it), "metadata_limit") }
            }
        }
    }

    /** Compare enum numbers semantically while preserving boolean/string distinctions. */
    private fun equal(a: Any?, b: Any?): Boolean {
        if ((a is JsonNumber || a is Int || a is Long) && (b is JsonNumber || b is Int || b is Long)) return number(a).compareTo(number(b)) == 0
        return a == b
    }

    /** Apply prose-only lexical, calendar, ID and UTF-8 refinements to named aliases. */
    private fun alias(kind: String, value: Any?) {
        when (kind) {
            "D", "PositiveD" -> value.d()
            "Id", "Namespace" -> demand(!value.text().contains(".."), "invalid_id")
            "UUID" -> demand(value != "00000000-0000-0000-0000-000000000000", "nil_uuid")
            "Text" -> demand(value.text().toByteArray(Charsets.UTF_8).size <= 16384, "metadata_limit")
            "Utc" -> try {
                val t = value.text()
                demand(t.substring(17, 19).toInt() <= 59, "invalid_calendar")
                LocalDateTime.parse(t.dropLast(1), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: DateTimeParseException) { throw LmtrFailure("invalid_calendar") }
        }
    }
}
