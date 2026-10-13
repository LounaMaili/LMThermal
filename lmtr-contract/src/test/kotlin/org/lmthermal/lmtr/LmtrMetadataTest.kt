package org.lmthermal.lmtr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LmtrMetadataTest {
    private fun cases(): List<Obj> = LmtrJson.parse(javaClass.getResourceAsStream("/lmtr-examples.json")!!.use { it.readBytes() })["cases"].arr().map { it.obj() }
    private fun first() = cases().first()
    private fun reject(body: () -> Unit): String {
        try { body() } catch (failure: LmtrFailure) { return failure.code }
        throw AssertionError("Malformed metadata was accepted")
    }
    private fun rehash(segment: Obj): Obj = segment + ("id" to LmtrMetadata.hash(segment.filterKeys { it != "id" }))
    private fun replaceSegment(chunk: Obj, replacement: Obj): Obj {
        val old = chunk["segments"].arr().first().obj()["id"]
        return chunk + mapOf("segments" to listOf(replacement), "entries" to chunk["entries"].arr().map { item ->
            item.obj().let { if (it["segment_id"] == old) it + ("segment_id" to replacement["id"]) else it }
        })
    }

    @Test fun illustratedMetadataClosuresPassExceptUnsupportedRequiredFeature() {
        for (case in cases()) {
            val header = case["header"].obj(); val chunk = case["chunk"].obj()
            if (header["required_features"].arr().any { it !in listOf(LmtrMetadata.CORE, LmtrMetadata.VIEW) }) {
                assertEquals("unsupported_required_feature", reject { LmtrMetadata.header(header) })
            } else {
                LmtrMetadata.header(header); LmtrMetadata.chunk(chunk, header)
            }
        }
    }

    @Test fun knownUnionFieldsCannotMasqueradeAsUnknownOptionalFacts() {
        val event = first()["chunk"].obj()["entries"].arr().first().obj()
        assertTrue(reject { LmtrMetadata.local("Entry", event + ("payloads" to emptyList<Any>())) }.isNotEmpty())
        LmtrMetadata.local("Entry", event + ("org.example.note" to mapOf("width_px" to 0, "status" to "known")))
    }

    @Test fun lexicalSmallIntegersAndActualCalendarAreStrict() {
        val geometry = first()["chunk"].obj()["segments"].arr().first().obj()["streams"].arr().first().obj()["geometry"].obj()
        assertTrue(reject { LmtrMetadata.local("Geometry", geometry + ("width_px" to JsonNumber(java.math.BigDecimal("3.0"),false))) }.isNotEmpty())
        assertEquals("invalid_metadata", reject { LmtrMetadata.local("UtcRecord", mapOf("status" to "known", "clock_source" to "host_utc", "utc" to "2026-02-30T12:00:00Z")) })
    }

    @Test fun contextHashIncludesPreciseUnknownOptionalValues() {
        val chunk = first()["chunk"].obj(); val contexts = chunk["contexts"].arr()
        val value = contexts.first().obj()["value"].obj() + ("retained" to JsonNumber(java.math.BigDecimal("1.000000000000000000000000000000000001"),false))
        val changed = contexts.first().obj() + ("value" to value)
        assertEquals("invalid_metadata", reject { LmtrMetadata.chunk(chunk + ("contexts" to listOf(changed)), first()["header"].obj()) })
        assertEquals(LmtrMetadata.hash(mapOf("a" to JsonNumber(java.math.BigDecimal("1.0"),false))), LmtrMetadata.hash(mapOf("a" to 1)))
    }

    @Test fun aCorrectlyRehashedSegmentStillCannotAddAnUndeclaredStream() {
        val case = first(); val chunk = case["chunk"].obj(); val segment = chunk["segments"].arr().first().obj()
        val stream = segment["streams"].arr().first().obj() + ("stream_id" to "undeclared")
        val replacement = rehash(segment + ("streams" to listOf(stream)))
        assertEquals("invalid_metadata", reject { LmtrMetadata.chunk(replaceSegment(chunk,replacement),case["header"].obj()) })
    }

    @Test fun groupSlicesMustCoverEveryDecodedByteWithoutAliases() {
        val case = first(); val chunk = case["chunk"].obj()
        val entries = chunk["entries"].arr().map { it.obj() }.toMutableList()
        val observation = entries.first { it["kind"] == "observation" }
        val payloads = observation["payloads"].arr().map { it.obj() }.toMutableList()
        payloads[0] = payloads[0] + ("offset" to "1")
        entries[entries.indexOf(observation)] = observation + ("payloads" to payloads)
        assertEquals("invalid_payload", reject { LmtrMetadata.chunk(chunk + ("entries" to entries), case["header"].obj()) })
    }

    @Test fun aMissingMeasurementCannotRetainCelsiusPresentationCaches() {
        val case = cases().first { it["chunk"].obj()["entries"].arr().any { x -> x.obj()["kind"] == "observation" && x.obj()["measurement"].obj()["status"] != "available" } }
        val chunk = case["chunk"].obj()
        val entries = chunk["entries"].arr().map { x -> x.obj().let { e -> if (e["kind"] != "observation") e else e + ("presentation" to mapOf("palette_id" to "inferno", "range_mode" to "locked", "effective_bounds" to mapOf("min" to 25,"max" to 45,"unit" to "Cel"), "transform" to mapOf("rotation_degrees" to 0,"mirror_x" to false,"mirror_y" to false))) } }
        assertEquals("invalid_metadata", reject { LmtrMetadata.chunk(chunk + ("entries" to entries), case["header"].obj()) })
    }
    @Test fun optionalExtensionLikeKeysAreRetainedWithoutInterpretation() {
        val header = first()["header"].obj() + ("unknown" to mapOf("extensions" to 42, "required_feature" to "not.a.feature"))
        LmtrMetadata.header(header)
        val actual = header + ("extensions" to mapOf("org.example.fact" to mapOf("schema_version" to mapOf("major" to 1,"minor" to 0), "required_feature" to null, "data" to mapOf("extensions" to 42))))
        LmtrMetadata.header(actual)
        val bad = actual + ("extensions" to mapOf("org.example.fact" to mapOf("schema_version" to mapOf("major" to 1,"minor" to 0), "required_feature" to "org.example.required", "data" to null)))
        assertEquals("invalid_metadata", reject { LmtrMetadata.header(bad) })
    }

    @Test fun emptyCheckpointReferencesHavePairedNullBounds() {
        val reference = mapOf("offset" to "100", "length" to "112", "ordinal" to "1", "first" to null, "last" to null, "hash" to "0".repeat(64))
        LmtrMetadata.local("Ref", reference)
        assertEquals("invalid_metadata", reject { LmtrMetadata.local("Ref", reference + ("first" to "0")) })
        assertEquals("invalid_metadata", reject { LmtrMetadata.local("Ref", reference + mapOf("first" to "2", "last" to "1")) })
    }

    @Test fun typedIdentifiersRejectTrailingNewlineBeforeContainerNavigation() {
        val examples=LmtrJson.parse(javaClass.getResourceAsStream("/lmtr-examples.json")!!.use { it.readBytes() })
        val original=examples["cases"].arr().first().obj()["header"].obj()
        val streams=original["streams"].arr().map { it.obj()+ ("id" to (it.obj()["id"].text()+"\n")) }
        assertEquals("invalid_metadata",reject { LmtrMetadata.header(original+("streams" to streams)) })
        val producer=original["producer"].obj()
        assertEquals("invalid_metadata",reject { LmtrMetadata.header(original+("producer" to (producer+("application_id" to (producer["application_id"].text()+"\n"))))) })
    }

}
