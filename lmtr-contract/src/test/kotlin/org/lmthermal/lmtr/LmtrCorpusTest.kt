package org.lmthermal.lmtr

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Independently generated canonical corpus expectations, not a writer validating only itself. */
class LmtrCorpusTest {
    private fun resource(name: String): ByteArray = javaClass.getResourceAsStream("/lmtr/$name")!!.use { it.readBytes() }
    private fun cases(): List<Obj> = LmtrJson.parse(resource("manifest.json"))["cases"].arr().map { it.obj() }
    private fun file(case: Obj): File = File(javaClass.getResource("/lmtr/${case["file"].text()}")!!.toURI())
    private fun equalJson(label: String, expected: Any?, actual: Any?) {
        assertArrayEquals(label, LmtrJson.encode(expected), LmtrJson.encode(actual))
    }
    private fun failure(action: () -> Unit): String {
        try { action(); fail("Expected contract rejection") }
        catch (error: LmtrFailure) { return error.code }
        return "unreachable"
    }

    @Test fun allThirtyThreePythonCanonicalPositiveFilesPassFullKotlinReadback() {
        val positives = cases().filter { it["classification"] == "positive" }
        assertEquals(33, positives.size)
        for (case in positives) {
            val expected = case["expected"].obj(); val source = file(case); val before = source.readBytes()
            assertEquals(case["id"].text(), case["sha256"].text(), LmtrFraming.sha256(before))
            assertEquals(case["bytes"].i().toLong(), source.length())
            val result = try { LmtrReader(source).use { it.check(full = true) } }
                catch(error:LmtrFailure) { throw AssertionError("${case["id"]}: ${error.code}",error) }
            assertEquals(case["id"].text(), expected["status"], result["status"])
            assertEquals(case["id"].text(), "full", result["integrity_scope"])
            assertEquals(expected["declared_end_status"], result["declared_end_status"])
            equalJson(case["id"].text() + " summary", expected["summary"], result["summary"])
            equalJson(case["id"].text() + " logical payload hashes", expected["logical_hashes"], result["logical_hashes"])
            assertEquals(expected["selected_chunks"].i().toLong(), result["chunks_decoded"].d())
            assertArrayEquals(case["id"].text() + " source immutability", before, source.readBytes())
        }
    }

    @Test fun allCanonicalNegativeFixturesKeepTheirExpectedRejectionOrRecoveryMeaning() {
        val negatives = cases().filter { it["classification"] == "negative" }
        assertEquals(18, negatives.size)
        // Collect every mismatch, so a known pending recovery implementation does not hide later failures.
        val mismatches = mutableListOf<String>()
        for (case in negatives) {
            val expected = case["expected"].obj(); val source = file(case); val before = source.readBytes()
            try {
                if (expected["status"] == "error") {
                    val error = failure { LmtrReader(source).use { it.check(full = true, recover = case["recover"] == true) } }
                    assertEquals(case["id"].text(), expected["error"], error)
                } else {
                    val result = LmtrReader(source).use { it.check(full = true, recover = true) }
                    for (field in listOf("status", "integrity_scope", "unknown_tail", "declared_end_status",
                        "verified_prefix_bytes", "last_verified_entry", "last_verified_timeline_ns", "reason"))
                        assertEquals(case["id"].text() + " $field", expected[field], result[field])
                    equalJson(case["id"].text() + " recovered summary", expected["summary"], result["summary"])
                    equalJson(case["id"].text() + " recovered hashes", expected["logical_hashes"], result["logical_hashes"])
                }
            } catch (error: Throwable) {
                mismatches.add("${case["id"]}: ${error.javaClass.simpleName}: ${error.message}")
            } finally { assertArrayEquals(case["id"].text() + " source immutability", before, source.readBytes()) }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test fun selectedSeekDecodesOnlyOneChunkAndDoesNotAssertFullIntegrity() {
        val case = cases().single { it["id"] == "multi-chunk-context-closure" }
        val plan = LmtrJson.parse(resource(case["plan"].text()))
        val target = plan["chunks"].arr().last().obj()["metadata"].obj()["entries"].arr().last().obj()["entry_id"].d()
        val source = file(case); val before = source.readBytes()
        val selected = LmtrReader(source).use { it.check(entry = target) }
        assertEquals("selected_chunks", selected["integrity_scope"])
        assertEquals(1L, selected["chunks_decoded"].d())
        val lastIds = plan["chunks"].arr().last().obj()["metadata"].obj()["entries"].arr().map { it.obj()["entry_id"].text() }.toSet()
        val expectedHashes = case["expected"].obj()["logical_hashes"].obj().filterKeys { it.substringBefore(':') in lastIds }
        equalJson("selected payload subset", expectedHashes, selected["logical_hashes"])
        assertArrayEquals(before, source.readBytes())
    }

    @Test fun canonicalMaskAndSignedZeroPayloadBitsSurviveReaderBoundary() {
        val case = cases().single { it["id"] == "illustration-01-stored" }
        RandomAccessFile(file(case), "r").use { source ->
            val first = LmtrFraming.read(source, 0)
            val header = LmtrJson.parse(first.body)
            val record = LmtrFraming.read(source, first.length)
            assertEquals(LmtrFraming.CHUNK, record.type)
            val data = LmtrChunk.decode(record.body, header)
            val values = ByteBuffer.wrap(data.payloads.getValue("1:temperature")).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(Int.MIN_VALUE, values.getInt(8))
            assertEquals(0, values.getInt(12))
            assertArrayEquals(byteArrayOf(1, 1, 1, 0, 1, 1), data.payloads.getValue("1:validity"))
        }
    }

    @Test fun allThirtyThreePythonPlansProduceKotlinFilesWithExpectedLogicalHashesAndSummaries() {
        val directory = Files.createTempDirectory("lmtr-kotlin-plan-corpus-")
        try {
            for (case in cases().filter { it["classification"] == "positive" }) {
                val planBytes = resource(case["plan"].text())
                assertEquals(case["id"].text(), case["plan_sha256"], LmtrFraming.sha256(planBytes))
                val destination = directory.resolve(case["id"].text() + ".lmtr").toFile()
                val report = try { LmtrWriter.writePlan(LmtrJson.parse(planBytes), destination) }
                    catch(error:LmtrFailure) { throw AssertionError("${case["id"]}: ${error.code}",error) }
                val expected = case["expected"].obj()
                assertEquals(expected["status"], report["status"])
                assertEquals("full", report["integrity_scope"])
                equalJson(case["id"].text() + " Kotlin summary", expected["summary"], report["summary"])
                equalJson(case["id"].text() + " Kotlin logical hashes", expected["logical_hashes"], report["logical_hashes"])
                // Exact container bytes are not required across independent DEFLATE encoders.
                val before = destination.readBytes()
                LmtrReader(destination).use { it.check(full = true) }
                assertArrayEquals(before, destination.readBytes())
            }
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun fullVerificationRejectsMalformedHistoricalPageEvenWhenFinalRootIsValid() {
        val source=cases().single { it["id"]=="illustration-01-stored" }
        val original=RandomAccessFile(file(source),"r").use { file ->
            val records=mutableListOf<Record>();var offset=0L
            while(offset<file.length()) { val r=LmtrFraming.read(file,offset);records.add(r);offset+=r.length }
            records
        }
        val path=Files.createTempFile("lmtr-historical-index-", ".lmtr")
        try {
            RandomAccessFile(path.toFile(),"rw").use { target ->
                var ordinal=0L;var previous=LmtrFraming.SENTINEL
                fun append(type:Int,body:ByteArray):Record {
                    val record=LmtrFraming.append(target,type,ordinal,previous,body)
                    ordinal++;previous=record.offset;return record
                }
                append(LmtrFraming.HEADER,original[0].body)
                val chunk=append(LmtrFraming.CHUNK,original[1].body)
                val page=LmtrJson.parse(original.single { it.type==LmtrFraming.INDEX }.body)
                val child=page["children"].arr().single().obj()
                val childRef=child["record"].obj()+ ("hash" to "0".repeat(64))
                // It is a committed, structurally valid snapshot outside the final root.
                append(LmtrFraming.INDEX,LmtrJson.encode(page+("children" to listOf(child+("record" to childRef)))))
                val good=append(LmtrFraming.INDEX,LmtrJson.encode(page))
                val root=RecordRef(good.offset,good.length,good.ordinal,page["first"].d(),page["last"].d(),good.refHash).json()
                val oldCp=LmtrJson.parse(original.single { it.type==LmtrFraming.CHECKPOINT }.body)
                val cp=append(LmtrFraming.CHECKPOINT,LmtrJson.encode(oldCp+mapOf("root" to root,
                    "last_chunk" to RecordRef(chunk.offset,chunk.length,chunk.ordinal,page["first"].d(),page["last"].d(),chunk.refHash).json())))
                val checkpoint=RecordRef(cp.offset,cp.length,cp.ordinal,page["first"].d(),page["last"].d(),cp.refHash).json()
                val oldEnd=LmtrJson.parse(original.single { it.type==LmtrFraming.END }.body)
                append(LmtrFraming.END,LmtrJson.encode(oldEnd+mapOf("root" to root,"checkpoint" to checkpoint)))
            }
            val before=Files.readAllBytes(path)
            val lazy=LmtrReader(path.toFile()).use { it.check() }
            assertEquals("opened_records",lazy["integrity_scope"])
            assertEquals("invalid_container",failure { LmtrReader(path.toFile()).use { it.check(full=true) } })
            assertArrayEquals(before,Files.readAllBytes(path))
        } finally { Files.delete(path) }
    }

    @Test fun writerRejectsIndependentNativeEvidenceThatDoesNotMatchAdvertisedAcquisitionView() {
        val source=cases().single { it["id"]=="illustration-04-stored" }
        val original=LmtrJson.parse(resource(source["plan"].text()))
        val chunk=original["chunks"].arr().single().obj()
        val recipes=chunk["payload_recipes"].obj()
        val native=recipes["1:native_samples"].obj()+("value" to 5121)
        val altered=original+("chunks" to listOf(chunk+("payload_recipes" to recipes+("1:native_samples" to native))))
        val directory=Files.createTempDirectory("lmtr-native-view-source-")
        val destination=directory.resolve("mismatching.lmtr").toFile()
        try {
            assertEquals("invalid_payload",failure { LmtrWriter.writePlan(altered,destination) })
        } finally {
            Files.deleteIfExists(destination.toPath());Files.delete(directory)
        }
    }
}
