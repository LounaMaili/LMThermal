package org.lmthermal.lmtr

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Synthetic body-level probes separate logical integrity from valid-looking metadata and framing. */
class LmtrChunkTest {
    private fun cases(): List<Obj> = LmtrJson.parse(javaClass.getResourceAsStream("/lmtr-examples.json")!!.use { it.readBytes() })["cases"].arr().map { it.obj() }
    private fun case(id: String): Obj = cases().single { it["id"] == id }
    private fun failure(code: String? = null, action: () -> Unit) {
        try { action(); fail("Expected rejection") }
        catch (error: LmtrFailure) { if (code != null) assertEquals(code, error.code) }
    }

    private fun recipe(value: Obj): ByteArray = when (value["kind"]) {
        "hex" -> value["hex"].text().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        "f32_constant" -> {
            val count = value["count"].i()
            val scalar = (value["value"] as JsonNumber).decimal.toFloat()
            ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(count) { putFloat(scalar) } }.array()
        }
        "u16_constant" -> {
            val count = value["count"].i(); val scalar = value["value"].i()
            ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(count) { putShort(scalar.toShort()) } }.array()
        }
        "transport" -> recipe(value["native"].obj()) + ByteArray(value["trailer_bytes"].i())
        else -> throw AssertionError("Unsupported test recipe")
    }

    private data class Body(val bytes: ByteArray, val header: Obj, val metadata: Obj)

    private fun body(case: Obj, replacements: Map<String, ByteArray> = emptyMap()): Body {
        val chunk = case["chunk"].obj()
        val recipes = case["payload_recipes"].obj()
        val data = recipes.mapValues { (key, value) -> replacements[key] ?: recipe(value.obj()) }
        val entries = chunk["entries"].arr().map { item ->
            val entry = item.obj()
            if (entry["kind"] != "observation") entry else entry + ("payloads" to entry["payloads"].arr().map { original ->
                val payload = original.obj(); val bytes = data.getValue("${entry["entry_id"]}:${payload["id"]}")
                payload + ("hash" to LmtrChunk.sha(bytes))
            })
        }
        val rawBlocks = linkedMapOf<String, ByteArray>()
        for (blockItem in chunk["blocks"].arr()) {
            val block = blockItem.obj(); val output = ByteArrayOutputStream()
            for (entry in entries) if (entry["kind"] == "observation") {
                for (p in entry["payloads"].arr().map { it.obj() }) if (p["kind"] == "materialized" && p["block_id"] == block["id"])
                    output.write(data.getValue("${entry["entry_id"]}:${p["id"]}"))
            }
            rawBlocks[block["id"].text()] = output.toByteArray()
        }
        val updated = chunk + mapOf("entries" to entries, "blocks" to chunk["blocks"].arr().map { item ->
            item.obj().let { block -> block + ("hash" to LmtrChunk.sha(rawBlocks.getValue(block["id"].text()))) }
        })
        val metadata = LmtrJson.encode(updated)
        val output = ByteArrayOutputStream()
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(metadata.size).array())
        output.write(metadata)
        rawBlocks.values.forEach(output::write)
        return Body(output.toByteArray(), case["header"].obj(), updated)
    }

    @Test fun allSupportedReviewScenariosDecodeExactSyntheticLogicalBytes() {
        for (case in cases().filter { it["id"] != "11" }) {
            val source = body(case)
            val before = source.bytes.copyOf()
            val decoded = LmtrChunk.decode(source.bytes, source.header)
            for ((key, value) in case["payload_recipes"].obj()) {
                assertArrayEquals("case=${case["id"]} payload=$key", recipe(value.obj()), decoded.payloads.getValue(key))
                assertEquals(LmtrChunk.sha(recipe(value.obj())), decoded.logicalHashes[key])
            }
            assertArrayEquals(before, source.bytes)
        }
    }

    @Test fun partialMaskAndValidNegativeZeroRemainOriginalBits() {
        val source = body(case("01"))
        val decoded = LmtrChunk.decode(source.bytes, source.header)
        val temperature = ByteBuffer.wrap(decoded.payloads.getValue("1:temperature")).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Int.MIN_VALUE, temperature.getInt(8))
        assertEquals(0, temperature.getInt(12))
        assertArrayEquals(byteArrayOf(1, 1, 1, 0, 1, 1), decoded.payloads.getValue("1:validity"))
    }

    @Test fun integrityMatchingButSemanticallyInvalidMaskAndFillerReject() {
        val source = case("01")
        val invalidMask = recipe(source["payload_recipes"].obj()["1:validity"].obj()).apply { this[0] = 2 }
        val invalidMaskBody = body(source, mapOf("1:validity" to invalidMask))
        failure("invalid_payload") { LmtrChunk.decode(invalidMaskBody.bytes, invalidMaskBody.header) }
        val invalidFiller = recipe(source["payload_recipes"].obj()["1:temperature"].obj())
        ByteBuffer.wrap(invalidFiller).order(ByteOrder.LITTLE_ENDIAN).putInt(12, Int.MIN_VALUE)
        val invalidFillerBody = body(source, mapOf("1:temperature" to invalidFiller))
        failure("invalid_payload") { LmtrChunk.decode(invalidFillerBody.bytes, invalidFillerBody.header) }
    }

    @Test fun integrityMatchingNonfiniteValidTemperatureRejects() {
        val source = case("01")
        val raw = recipe(source["payload_recipes"].obj()["1:temperature"].obj())
        for (bits in listOf(0x7f800000, 0xff800000.toInt(), 0x7fc00001)) {
            val changed = raw.copyOf()
            ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(0, bits)
            val invalid = body(source, mapOf("1:temperature" to changed))
            failure("invalid_payload") { LmtrChunk.decode(invalid.bytes, invalid.header) }
        }
    }

    @Test fun corruptedStoredBlockIsNotConvertedToUnavailableObservation() {
        val source = body(case("01"))
        source.bytes[source.bytes.lastIndex] = (source.bytes.last().toInt() xor 1).toByte()
        failure("committed_payload_integrity") { LmtrChunk.decode(source.bytes, source.header) }
    }

    @Test fun fullNativeViewIsExactParentSliceAndTrailerIsExcluded() {
        val source = body(case("04"))
        val decoded = LmtrChunk.decode(source.bytes, source.header)
        val parent = decoded.payloads.getValue("1:acquisition")
        val native = decoded.payloads.getValue("1:native_samples")
        assertEquals(224256, parent.size); assertEquals(221184, native.size)
        assertArrayEquals(parent.copyOfRange(0, native.size), native)
        assertEquals(3072, parent.size - native.size)
    }

    @Test fun maskStatisticsTiesNoValidAndNativeRoiAreDeterministic() {
        val raw = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(Int.MIN_VALUE).putInt(0).putFloat(1f).putFloat(1f).array()
        val before = raw.copyOf()
        val stats = LmtrChunk.statistics(raw, null, 2, 2)
        assertEquals(listOf(0, 0), stats["min_xy"])
        assertEquals(listOf(0, 1), stats["max_xy"])
        assertEquals(BigDecimal("0.5"), stats["mean"])
        val noValid = LmtrChunk.statistics(ByteArray(16), ByteArray(4), 2, 2)
        assertEquals(0, noValid["valid_pixel_count"])
        assertFalse("mean" in noValid); assertFalse("min_xy" in noValid)
        val roi = LmtrChunk.statistics(raw, null, 2, 2, listOf(1, 0, 2, 2))
        assertEquals(2, roi["pixel_count"])
        assertEquals(listOf(1, 0), roi["min_xy"])
        assertArrayEquals(before, raw)
    }

    @Test fun metadataPrefixAndLocalBudgetRefuseBeforePayloadAllocation() {
        failure("invalid_container") { LmtrChunk.metadata(ByteArray(3)) }
        failure("resource_limit") { LmtrChunk.metadata(ByteArray(4)) }
        failure("resource_limit") { LmtrChunk.metadata(byteArrayOf(-1, -1, -1, -1)) }
        failure("invalid_container") { LmtrChunk.metadata(byteArrayOf(1, 0, 0, 0)) }
        val source = body(case("01"))
        failure("local_resource_limit") { LmtrChunk.decode(source.bytes, source.header, 0) }
    }
}
