package org.lmthermal.lmtr

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections

/** Stable contract failure; callers must not turn integrity/resource failures into frame gaps. */
class LmtrFailure(val code: String, message: String = code) : IllegalArgumentException(message)

/** Assert a normative invariant without exposing implementation-specific exception classes. */
fun demand(condition: Boolean, code: String) {
    if (!condition) throw LmtrFailure(code)
}

typealias Obj = Map<String, Any?>

/** Exact JSON numerical authority plus lexical information needed by the strict I type. */
data class JsonNumber(val decimal: BigDecimal, val integerLexeme: Boolean)

/** Return an object only when its keys have the JSON string type. */
@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Obj {
    demand(this is Map<*, *> && this.keys.all { it is String }, "invalid_metadata")
    return this as Obj
}

/** Read an array without implicitly promoting another collection into JSON. */
fun Any?.arr(): List<Any?> {
    demand(this is List<*>, "invalid_metadata")
    return this as List<Any?>
}

/** Read a scalar string; no coercion from numerical or boolean values is permitted. */
fun Any?.text(): String {
    demand(this is String, "invalid_metadata")
    return this as String
}

/** D fields are decimal strings in the checked nonnegative signed-64 domain. */
fun Any?.d(): Long {
    val value = text()
    demand(value.length <= 19 && Regex("0|[1-9][0-9]*").matches(value), "invalid_metadata")
    return value.toLongOrNull() ?: throw LmtrFailure("resource_limit")
}

/** I preserves strict input spelling: -0, exponent/fraction and boolean tokens are illegal. */
fun Any?.i(): Int = when (this) {
    is Int -> { demand(this >= 0, "invalid_metadata"); this }
    is Long -> { demand(this in 0..Int.MAX_VALUE.toLong(), "invalid_metadata"); this.toInt() }
    is JsonNumber -> {
        demand(integerLexeme && decimal.signum() >= 0 && decimal <= BigDecimal(Int.MAX_VALUE), "invalid_metadata")
        try { decimal.intValueExact() } catch (_: ArithmeticException) { throw LmtrFailure("invalid_metadata") }
    }
    else -> throw LmtrFailure("invalid_metadata")
}

/**
 * Strict metadata parser and exact semantic canonicalizer for accepted LMTR v1 §3.
 * Unknown optional values remain exact BigDecimal values; Float/Double are rejected.
 * This utility does not establish higher-level metadata or payload conformance.
 */
object LmtrJson {
    const val MAX_JSON_BYTES = 1_048_576
    const val MAX_DEPTH = 32
    const val MAX_ITEMS = 65_536
    const val MAX_STRING_BYTES = 16_384
    const val MAX_NUMBER_BYTES = 1024
    private val numberPattern = Regex("(-?)(0|[1-9][0-9]*)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?")
    private val integerPattern = Regex("0|[1-9][0-9]*")
    private val scalarOrder = Comparator<String> { a, b ->
        var ai = 0
        var bi = 0
        var result = 0
        while (ai < a.length && bi < b.length) {
            val ac = a.codePointAt(ai)
            val bc = b.codePointAt(bi)
            if (ac != bc) { result = ac.compareTo(bc); break }
            ai += Character.charCount(ac)
            bi += Character.charCount(bc)
        }
        if (result != 0) result else (a.length - ai).compareTo(b.length - bi)
    }

    /** Decode strict UTF-8 and exactly one object, enforcing limits during parsing. */
    fun parse(bytes: ByteArray): Obj {
        demand(bytes.size <= MAX_JSON_BYTES, "resource_limit")
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { throw LmtrFailure("invalid_metadata") }
        demand(!text.startsWith('\uFEFF'), "invalid_metadata")
        return Parser(text).parse()
    }

    /** Serialize semantic metadata exactly; the root need not be an object for hash subvalues. */
    fun encode(value: Any?): ByteArray {
        val output = StringBuilder()
        val limits = Limits()
        emit(value, output, 0, limits)
        demand(output.length <= MAX_JSON_BYTES, "resource_limit")
        val result = output.toString().toByteArray(StandardCharsets.UTF_8)
        demand(result.size <= MAX_JSON_BYTES, "resource_limit")
        return result
    }

    /** Exact decimal notation; no MathContext, binary floating conversion or rounding. */
    internal fun canonicalDecimal(value: BigDecimal): String {
        demand(value.precision() <= MAX_NUMBER_BYTES, "resource_limit")
        if (value.signum() == 0) return "0"
        val adjusted = value.precision().toLong() - value.scale().toLong() - 1L
        demand(adjusted in -4096L..4096L, "resource_limit")
        val normalized = value.stripTrailingZeros()
        val digits = normalized.unscaledValue().abs().toString()
        val sign = if (normalized.signum() < 0) "-" else ""
        val exponent = digits.length.toLong() - normalized.scale().toLong() - 1L
        val result = if (exponent in -6L..20L) {
            val point = digits.length.toLong() - normalized.scale().toLong()
            val body = when {
                point <= 0 -> "0." + "0".repeat((-point).toInt()) + digits
                point >= digits.length -> digits + "0".repeat((point - digits.length).toInt())
                else -> digits.substring(0, point.toInt()) + "." + digits.substring(point.toInt())
            }
            sign + body
        } else sign + digits[0] + (if (digits.length > 1) "." + digits.substring(1) else "") + "e" + exponent
        demand(result.length <= MAX_NUMBER_BYTES, "resource_limit")
        return result
    }

    private fun number(token: String): JsonNumber {
        demand(token.length <= MAX_NUMBER_BYTES, "resource_limit")
        val match = numberPattern.matchEntire(token) ?: throw LmtrFailure("invalid_metadata")
        demand(match.groupValues[2].length + match.groupValues[3].length <= MAX_NUMBER_BYTES, "resource_limit")
        val rawExponent = match.groupValues[4].removePrefix("+").removePrefix("-").trimStart('0').ifEmpty { "0" }
        demand(rawExponent.length <= 4 && rawExponent.toInt() <= 5120, "resource_limit")
        val value = try { BigDecimal(token) } catch (_: NumberFormatException) { throw LmtrFailure("invalid_metadata") }
        canonicalDecimal(value)
        return JsonNumber(value, integerPattern.matches(token))
    }

    private class Limits {
        var items = 0
        fun add(count: Int) {
            demand(count >= 0 && count <= MAX_ITEMS - items, "resource_limit")
            items += count
        }
    }

    private fun scalarString(text: String) {
        var index = 0
        var bytes = 0
        while (index < text.length) {
            val c = text[index]
            val width = when {
                c.isHighSurrogate() -> {
                    demand(index + 1 < text.length && text[index + 1].isLowSurrogate(), "invalid_metadata")
                    index++; 4
                }
                c.isLowSurrogate() -> throw LmtrFailure("invalid_metadata")
                c.code <= 0x7f -> 1
                c.code <= 0x7ff -> 2
                else -> 3
            }
            bytes += width
            demand(bytes <= MAX_STRING_BYTES, "resource_limit")
            index++
        }
    }

    private fun quote(text: String, output: StringBuilder) {
        scalarString(text)
        output.append('"')
        text.forEach { c ->
            when {
                c == '"' -> output.append("\\\"")
                c == '\\' -> output.append("\\\\")
                c.code < 32 -> output.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> output.append(c)
            }
        }
        output.append('"')
    }

    private fun emit(value: Any?, output: StringBuilder, depth: Int, limits: Limits) {
        when (value) {
            null -> output.append("null")
            is Boolean -> output.append(value)
            is String -> quote(value, output)
            is JsonNumber -> output.append(canonicalDecimal(value.decimal))
            is BigDecimal -> output.append(canonicalDecimal(value))
            is BigInteger -> output.append(canonicalDecimal(BigDecimal(value)))
            is Byte, is Short, is Int, is Long -> output.append(canonicalDecimal(BigDecimal(value.toString())))
            is Map<*, *> -> {
                demand(depth < MAX_DEPTH, "resource_limit")
                demand(value.keys.all { it is String }, "invalid_metadata")
                limits.add(value.size)
                output.append('{')
                value.keys.map { it as String }.onEach(::scalarString).sortedWith(scalarOrder).forEachIndexed { index, key ->
                    if (index != 0) output.append(',')
                    quote(key, output); output.append(':'); emit(value[key], output, depth + 1, limits)
                }
                output.append('}')
            }
            is List<*> -> {
                demand(depth < MAX_DEPTH, "resource_limit")
                limits.add(value.size)
                output.append('[')
                value.forEachIndexed { index, item ->
                    if (index != 0) output.append(',')
                    emit(item, output, depth + 1, limits)
                }
                output.append(']')
            }
            else -> throw LmtrFailure("invalid_metadata")
        }
        demand(output.length <= MAX_JSON_BYTES, "resource_limit")
    }

    private class Parser(val text: String) {
        var cursor = 0
        val limits = Limits()

        fun parse(): Obj {
            whitespace()
            val result = value(0).obj()
            whitespace()
            demand(cursor == text.length, "invalid_metadata")
            return result
        }

        fun whitespace() {
            while (cursor < text.length && text[cursor] in " \t\r\n") cursor++
        }

        fun expect(c: Char) {
            demand(cursor < text.length && text[cursor] == c, "invalid_metadata")
            cursor++
        }

        fun value(depth: Int): Any? {
            demand(cursor < text.length, "invalid_metadata")
            return when (text[cursor]) {
                '{' -> objectValue(depth + 1)
                '[' -> arrayValue(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> {
                    val start = cursor
                    while (cursor < text.length && text[cursor] in "-+0123456789.eE") {
                        cursor++
                        demand(cursor - start <= MAX_NUMBER_BYTES, "resource_limit")
                    }
                    number(text.substring(start, cursor))
                }
                else -> throw LmtrFailure("invalid_metadata")
            }
        }

        fun literal(token: String, result: Any?): Any? {
            demand(text.startsWith(token, cursor), "invalid_metadata")
            cursor += token.length
            return result
        }

        fun objectValue(depth: Int): Obj {
            demand(depth <= MAX_DEPTH, "resource_limit")
            expect('{'); whitespace()
            val result = linkedMapOf<String, Any?>()
            if (cursor < text.length && text[cursor] == '}') { cursor++; return Collections.unmodifiableMap(result) }
            while (true) {
                val key = string()
                demand(!result.containsKey(key), "invalid_metadata")
                limits.add(1)
                whitespace(); expect(':'); whitespace()
                result[key] = value(depth)
                whitespace()
                demand(cursor < text.length, "invalid_metadata")
                if (text[cursor] == '}') { cursor++; return Collections.unmodifiableMap(result) }
                expect(','); whitespace()
            }
        }

        fun arrayValue(depth: Int): List<Any?> {
            demand(depth <= MAX_DEPTH, "resource_limit")
            expect('['); whitespace()
            val result = ArrayList<Any?>()
            if (cursor < text.length && text[cursor] == ']') { cursor++; return Collections.unmodifiableList(result) }
            while (true) {
                limits.add(1)
                result.add(value(depth)); whitespace()
                demand(cursor < text.length, "invalid_metadata")
                if (text[cursor] == ']') { cursor++; return Collections.unmodifiableList(result) }
                expect(','); whitespace()
            }
        }

        fun string(): String {
            expect('"')
            val result = StringBuilder()
            while (cursor < text.length) {
                val c = text[cursor++]
                if (c == '"') {
                    val value = result.toString()
                    scalarString(value)
                    return value
                }
                demand(c.code >= 32, "invalid_metadata")
                if (c == '\\') {
                    demand(cursor < text.length, "invalid_metadata")
                    when (val escape = text[cursor++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            demand(cursor + 4 <= text.length, "invalid_metadata")
                            val hex = text.substring(cursor, cursor + 4)
                            demand(hex.all { it in "0123456789abcdefABCDEF" }, "invalid_metadata")
                            result.append(hex.toInt(16).toChar()); cursor += 4
                        }
                        else -> throw LmtrFailure("invalid_metadata")
                    }
                } else result.append(c)
                // UTF-8 is never shorter than its UTF-16 character count, even before scalar validation.
                demand(result.length <= MAX_STRING_BYTES, "resource_limit")
            }
            throw LmtrFailure("invalid_metadata")
        }
    }
}
