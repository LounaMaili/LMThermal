package org.lmthermal.lmtr

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

/** Regression vectors directly derived from accepted §3, independent of a recording writer. */
class LmtrJsonTest {
    private fun parse(text: String): Obj = LmtrJson.parse(text.toByteArray(Charsets.UTF_8))
    private fun canonical(text: String): String = LmtrJson.encode(parse(text)).toString(Charsets.UTF_8)
    private fun failure(code: String? = null, action: () -> Unit) {
        try { action(); fail("Expected rejection") }
        catch (error: LmtrFailure) { if (code != null) assertEquals(code, error.code) }
    }

    @Test fun equivalentDecimalSpellingsHaveExactCanonicalSemantics() {
        for (input in listOf("1", "1.0", "1e0", "1E+000", "1000e-3"))
            assertEquals("{\"n\":1}", canonical("{\"n\":$input}"))
        assertEquals("{\"n\":1.0000000000000000000000000000000000001}",
            canonical("{\"n\":1.0000000000000000000000000000000000001}"))
        assertEquals("{\"n\":9007199254740993}", canonical("{\"n\":9007199254740993}"))
    }

    @Test fun notationBoundaryAndNegativeNumbersMatchSpecifiedSpelling() {
        val cases = mapOf("1e-7" to "1e-7", "1e-6" to "0.000001", "1e20" to "100000000000000000000",
            "1e21" to "1e21", "1230000000000000000000" to "1.23e21", "-12.3400" to "-12.34",
            "-0.0000001000" to "-1e-7", "-1e20" to "-100000000000000000000")
        cases.forEach { (input, expected) -> assertEquals("{\"n\":$expected}", canonical("{\"n\":$input}")) }
    }

    @Test fun semanticZeroHasNoSignButStrictIntegerPreservesInputSpelling() {
        for (zero in listOf("0", "-0", "-0.0", "0e5120", "-0E-5120", "0e+0000"))
            assertEquals("{\"n\":0}", canonical("{\"n\":$zero}"))
        assertEquals(0, parse("{\"n\":0}")["n"].i())
        for (input in listOf("-0", "1.0", "1e0", "true", "2147483648", "-1"))
            failure("invalid_metadata") { parse("{\"n\":$input}")["n"].i() }
        assertEquals(Int.MAX_VALUE, parse("{\"n\":2147483647}")["n"].i())
        assertEquals(Int.MAX_VALUE - 1, parse("{\"n\":2147483646}")["n"].i())
        assertEquals(4, 4L.i())
        failure { 2147483648L.i() }
        failure { true.i() }
    }

    @Test fun numericalBoundsAreCheckedBeforeExpansionIncludingZeroExponent() {
        listOf("1e-4096", "1e4096", "0e-5120", "0e5120").forEach { canonical("{\"n\":$it}") }
        listOf("1e-4095", "1e4095", "0e-5119", "0e5119").forEach { canonical("{\"n\":$it}") }
        listOf("1e-4097", "1e4097", "0e-5121", "0e5121", "1e999999999999999999999").forEach {
            failure("resource_limit") { parse("{\"n\":$it}") }
        }
        failure("resource_limit") { parse("{\"n\":${"9".repeat(1019)}}") }
        val below = "9".repeat(1017)
        val at = "9".repeat(1018)
        assertTrue(canonical("{\"n\":$below}").length < 1030)
        assertEquals(1024, (parse("{\"n\":$at}")["n"] as JsonNumber).let { LmtrJson.canonicalDecimal(it.decimal).length })
        canonical("{\"n\":0e${"0".repeat(1021)}}")
        canonical("{\"n\":0e${"0".repeat(1022)}}")
        failure("resource_limit") { parse("{\"n\":0e${"0".repeat(1023)}}") }
        failure("resource_limit") { parse("{\"n\":0e${"0".repeat(1024)}}") }
        failure("resource_limit") { LmtrJson.encode(BigDecimal(BigInteger.ONE, Int.MAX_VALUE)) }
        failure("resource_limit") { LmtrJson.encode(BigDecimal(BigInteger.ONE, Int.MIN_VALUE)) }
    }

    @Test fun everyFloatBinaryExtremeCanBeRetainedAsExactDecimalOrTypedBits() {
        val finite = listOf(Float.MIN_VALUE.toDouble(), Float.MAX_VALUE.toDouble(), Double.MIN_VALUE, Double.MAX_VALUE)
        finite.forEach { original ->
            val exact = BigDecimal(original)
            val text = LmtrJson.encode(mapOf("value" to exact)).toString(Charsets.UTF_8)
            val restored = (parse(text)["value"] as JsonNumber).decimal
            assertEquals(0, exact.compareTo(restored))
        }
        assertEquals("{\"bits\":\"00000080\",\"type\":\"f32le\"}",
            LmtrJson.encode(mapOf("type" to "f32le", "bits" to "00000080")).toString(Charsets.UTF_8))
        failure("invalid_metadata") { LmtrJson.encode(mapOf("n" to 1.0)) }
        failure("invalid_metadata") { LmtrJson.encode(mapOf("n" to Float.NaN)) }
    }

    @Test fun scalarOrderingDiffersFromUtf16AndDoesNotNormalizeUnicode() {
        val output = LmtrJson.encode(linkedMapOf("\uD800\uDC00" to 1, "\uE000" to 2, "é" to 3, "e\u0301" to 4, "a" to 5, "aa" to 6))
            .toString(Charsets.UTF_8)
        assertEquals("{\"a\":5,\"aa\":6,\"é\":4,\"é\":3,\"\uE000\":2,\"\uD800\uDC00\":1}", output)
        assertEquals("{\"x\":\"\uD800\uDC00\"}", canonical("""{"x":"\ud800\udc00"}"""))
    }

    @Test fun escapesAndUnknownNestedValuesPreserveSemanticAuthority() {
        val original = mapOf("x" to (0..31).map { it.toChar() }.joinToString("") + "\"\\/\u2028\u2029")
        val encoded = LmtrJson.encode(original).toString(Charsets.UTF_8)
        assertTrue(encoded.contains("\\u0008"))
        assertFalse(encoded.contains("\\b"))
        // A literal backslash immediately before slash legitimately contains the same substring.
        assertEquals("\"/\"", LmtrJson.encode("/").toString(Charsets.UTF_8))
        assertEquals(original["x"], parse(encoded)["x"])
        val input = """{"optional":[null,true,{"n":1.0000000000000000000000000000000000001}],"s":"a\/b"}"""
        val parsed = parse(input)
        assertEquals(parsed, parse(LmtrJson.encode(parsed).toString(Charsets.UTF_8)))
    }

    @Test fun invalidUtf8BomSurrogatesDuplicateKeysAndMalformedJsonReject() {
        listOf(byteArrayOf(0xc0.toByte(), 0xaf.toByte()), byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte())).forEach {
            failure("invalid_metadata") { LmtrJson.parse(it) }
        }
        val bad = listOf("\uFEFF{}", """{"x":1,"\u0078":2}""", """{"x":"\ud800"}""", """{"x":"\udc00"}""",
            """{"x":"\ud800x"}""", "{\"x\":NaN}", "{\"x\":Infinity}", "{\"x\":01}", "{\"x\":+1}",
            "{\"x\":1.}", "{\"x\":.1}", "{\"x\":1e}", "{\"x\":truefalse}", "{}x", "[]", "null",
            "{\"x\":1,}", "{\"x\":/*a*/1}", "{\"x\":\"\n\"}", "{\"x\":[1,]}")
        bad.forEach { input -> failure("invalid_metadata") { parse(input) } }
        failure("invalid_metadata") { LmtrJson.encode("\uD800") }
    }

    @Test fun structuralLimitsApplyAtBoundaryAndOneBeyond() {
        fun nested(depth: Int): String = "{\"x\":" + "[".repeat(depth - 1) + "0" + "]".repeat(depth - 1) + "}"
        parse(nested(31)); parse(nested(32))
        failure("resource_limit") { parse(nested(33)) }
        parse("{\"x\":\"${"a".repeat(16383)}\"}")
        parse("{\"x\":\"${"a".repeat(16384)}\"}")
        failure("resource_limit") { parse("{\"x\":\"${"a".repeat(16385)}\"}") }
        parse("{\"x\":\"${"é".repeat(8192)}\"}")
        failure("resource_limit") { parse("{\"x\":\"${"é".repeat(8193)}\"}") }
        parse("{\"x\":\"${"\uD800\uDC00".repeat(4096)}\"}")
        failure("resource_limit") { parse("{\"x\":\"${"\uD800\uDC00".repeat(4097)}\"}") }
        fun array(count: Int) = "{\"x\":[" + List(count) { "0" }.joinToString(",") + "]}"
        parse(array(65534)); parse(array(65535))
        failure("resource_limit") { parse(array(65536)) }
    }

    @Test fun parsedObjectsAndArraysAreImmutableAndDFieldsNeverCoerce() {
        val parsed = parse("{\"x\":[{}],\"d\":\"9223372036854775807\"}")
        assertEquals(Long.MAX_VALUE, parsed["d"].d())
        assertEquals(Long.MAX_VALUE - 1, "9223372036854775806".d())
        try { (parsed as MutableMap<String, Any?>)["x"] = 1; fail("Mutable map") } catch (_: UnsupportedOperationException) { }
        try { (parsed["x"] as MutableList<Any?>).add(1); fail("Mutable array") } catch (_: UnsupportedOperationException) { }
        listOf("01", "+1", "1.0", "1e0", "-0", "-1").forEach { failure { it.d() } }
        failure("resource_limit") { "9223372036854775808".d() }
        failure("invalid_metadata") { 1.d() }
        failure("invalid_metadata") { null.obj() }
    }
}
