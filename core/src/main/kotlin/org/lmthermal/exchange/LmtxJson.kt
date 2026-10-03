package org.lmthermal.exchange

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

typealias JsonObject = Map<String, Any?>

/** Stable wire errors, separate from localized UI. Resource refusal is never described as corrupt data. */
class LmtxException(val code: String, message: String) : IllegalArgumentException(message)
internal fun demand(condition: Boolean, code: String = "invalid_manifest", message: String = code) {
    if (!condition) throw LmtxException(code, message)
}

/** Bounded UTF-8 JSON with exact decimal values for unknown fields. No Android/parser dependency.
 * Integer tokens remain distinct from decimal tokens for known-field validation. Maps preserve insertion order.
 */
object LmtxJson {
    const val MAX_BYTES = 1_048_576
    private const val MAX_ITEMS = 65_536
    private const val MAX_DEPTH = 32
    private const val MAX_STRING = 16_384

    fun encode(value: JsonObject): ByteArray {
        val text = StringBuilder()
        var items = 0
        fun write(v: Any?, depth: Int) {
            when (v) {
                null -> text.append("null")
                is Boolean -> text.append(v)
                is Number -> {
                    demand(v !is Double || v.isFinite()); demand(v !is Float || v.isFinite())
                    text.append(v.toString())
                }
                is String -> {
                    val utf8 = try { Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(v)) }
                    catch (_: Exception) { throw LmtxException("invalid_manifest", "Invalid Unicode string") }
                    demand(utf8.remaining() <= MAX_STRING, "resource_limit")
                    text.append('"')
                    v.forEach { c -> when (c) {
                        '"' -> text.append("\\\""); '\\' -> text.append("\\\\")
                        else -> if (c.code < 32) text.append("\\u" + c.code.toString(16).padStart(4, '0')) else text.append(c)
                    } }
                    text.append('"')
                }
                is Map<*, *> -> {
                    demand(depth < MAX_DEPTH, "resource_limit"); items += v.size
                    demand(items <= MAX_ITEMS, "resource_limit")
                    text.append('{')
                    v.entries.forEachIndexed { i, entry ->
                        if (i > 0) text.append(',')
                        demand(entry.key is String); write(entry.key, depth + 1); text.append(':'); write(entry.value, depth + 1)
                    }; text.append('}')
                }
                is List<*> -> {
                    demand(depth < MAX_DEPTH, "resource_limit"); items += v.size
                    demand(items <= MAX_ITEMS, "resource_limit"); text.append('[')
                    v.forEachIndexed { i, x -> if (i > 0) text.append(','); write(x, depth + 1) }; text.append(']')
                }
                else -> throw LmtxException("invalid_manifest", "Not a JSON value")
            }
            demand(text.length <= MAX_BYTES, "resource_limit")
        }
        write(value, 0)
        return text.toString().toByteArray(Charsets.UTF_8).also {
            demand(it.size <= MAX_BYTES, "resource_limit")
            // Also reject malformed surrogate sequences supplied by callers, rather than replacing them.
            decode(it)
        }
    }

    fun decode(bytes: ByteArray): JsonObject {
        demand(bytes.size <= MAX_BYTES, "resource_limit")
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: Exception) { throw LmtxException("invalid_manifest", "Invalid UTF-8") }
        val parser = Parser(text)
        val value = parser.value(0)
        parser.space(); demand(parser.at == text.length && value is Map<*, *>)
        @Suppress("UNCHECKED_CAST") return value as JsonObject
    }
    private class Parser(val text: String) {
        var at = 0; private var items = 0
        fun space() { while (at < text.length && text[at] in " \r\n\t") at++ }
        private fun take(c: Char): Boolean { space(); return if (at < text.length && text[at] == c) { at++; true } else false }
        private fun string(): String {
            demand(take('"')); val result = StringBuilder()
            while (at < text.length) {
                val c = text[at++]
                if (c == '"') {
                    val s = result.toString()
                    demand(s.toByteArray().size <= MAX_STRING, "resource_limit")
                    var i = 0
                    while (i < s.length) {
                        if (s[i].isHighSurrogate()) { demand(i + 1 < s.length && s[i + 1].isLowSurrogate()); i += 2 }
                        else { demand(!s[i].isLowSurrogate()); i++ }
                    }
                    return s
                }
                demand(c.code >= 32)
                if (c != '\\') result.append(c) else {
                    demand(at < text.length)
                    result.append(when (val e = text[at++]) {
                        '"', '\\', '/' -> e; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> { demand(at + 4 <= text.length); val hex = text.substring(at, at + 4)
                            demand(hex.all { it in "0123456789abcdefABCDEF" }); at += 4; hex.toInt(16).toChar() }
                        else -> throw LmtxException("invalid_manifest", "Invalid escape")
                    })
                }
            }; throw LmtxException("invalid_manifest", "Unterminated string")
        }
        fun value(depth: Int): Any? {
            space(); demand(at < text.length)
            if (text[at] == '"') return string()
            if (take('{')) {
                demand(depth < MAX_DEPTH, "resource_limit"); val map = linkedMapOf<String, Any?>()
                if (take('}')) return map
                do { val key = string(); demand(!map.containsKey(key)); demand(take(':'))
                    demand(++items <= MAX_ITEMS, "resource_limit"); map[key] = value(depth + 1)
                } while (take(','))
                demand(take('}')); return map
            }
            if (take('[')) {
                demand(depth < MAX_DEPTH, "resource_limit"); val list = mutableListOf<Any?>()
                if (take(']')) return list
                do { demand(++items <= MAX_ITEMS, "resource_limit"); list.add(value(depth + 1)) } while (take(','))
                demand(take(']')); return list
            }
            for ((token, result) in listOf("true" to true, "false" to false, "null" to null)) {
                if (text.startsWith(token, at)) { at += token.length; return result }
            }
            val match = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").find(text, at)
            demand(match != null && match.range.first == at)
            val token = match!!.value; at += token.length
            demand(token.length <= MAX_STRING, "resource_limit", "Local numeric-token limit")
            return try { if (token.none { it in ".eE" }) token.toBigInteger() else BigDecimal(token) }
            catch (_: Exception) { throw LmtxException("invalid_manifest", "Numeric overflow") }
        }
    }
}
