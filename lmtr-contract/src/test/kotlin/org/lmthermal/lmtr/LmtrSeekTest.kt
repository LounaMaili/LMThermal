package org.lmthermal.lmtr

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

/** Oracle scans small known fixtures, independently of the production indexed search path. */
class LmtrSeekTest {
    @Test fun indexedTimeStreamSeekMatchesOriginalIntakeEntriesAcrossEntirePositiveCorpus() {
        val manifest=LmtrJson.parse(javaClass.getResourceAsStream("/lmtr/manifest.json")!!.use { it.readBytes() })
        for(case in manifest["cases"].arr().map { it.obj() }.filter { it["classification"]=="positive" }) {
            val file=File(javaClass.getResource("/lmtr/${case["file"]}")!!.toURI())
            val entries=mutableListOf<Pair<Obj,Set<String>>>()
            val header=RandomAccessFile(file,"r").use { source ->
                val header=LmtrJson.parse(LmtrFraming.read(source,0).body);var offset=0L
                while(offset<source.length()) {
                    val record=LmtrFraming.read(source,offset)
                    if(record.type==LmtrFraming.CHUNK) {
                        val (metadata,_)=LmtrChunk.metadata(record.body)
                        val segments=metadata["segments"].arr().map { it.obj() }.associateBy { it["id"] }
                        for(e in metadata["entries"].arr().map { it.obj() }) {
                            val involved=if(e["kind"] in listOf("segment_start","segment_end"))
                                segments.getValue(e["segment_id"])["streams"].arr().map { it.obj()["stream_id"].text() }.toSet()
                            else setOf(e["stream_id"].text())
                            entries.add(e to involved)
                        }
                    }
                    offset+=record.length
                }
                header
            }
            val last=entries.lastOrNull()?.first?.get("timeline_ns")?.d()?:0L
            val filters=listOf<String?>(null)+header["streams"].arr().map { it.obj()["id"].text() }
            for(time in setOf(0L,last/2,last,last+1)) for(filter in filters) {
                val expected=entries.firstOrNull { it.first["timeline_ns"].d()>=time && (filter==null || filter in it.second) }?.first
                val result=LmtrReader(file).use { it.seek(time,filter) }
                assertEquals("${case["id"]}/$time/$filter",expected?.get("entry_id"),result["selected_entry_id"])
                assertEquals(expected?.get("timeline_ns"),result["selected_timeline_ns"])
                assertEquals(expected?.get("kind"),result["selected_entry_kind"])
                assertEquals(if(expected==null)"not_found" else "found",result["seek_result"])
            }
        }
    }
    @Test fun invalidQueryCannotBeMisrepresentedAsSuccessfulNarrowerRead() {
        val file=File(javaClass.getResource("/lmtr/files/illustration-01-stored.lmtr")!!.toURI())
        for(action in listOf<()->Unit>({ LmtrReader(file).use { it.seek(-1) } },{ LmtrReader(file).use { it.seek(0,"undeclared") } })) {
            try { action();fail("invalid query") } catch(e:LmtrFailure) { assertEquals("invalid_metadata",e.code) }
        }
    }
    @Test fun syntheticWriterDoesNotFabricateFinalizationFromKnownCreationTime() {
        val original=LmtrJson.parse(javaClass.getResourceAsStream("/lmtr/plans/illustration-01-stored.json")!!.use { it.readBytes() })
        val header=original["header"].obj()+ ("creation_time" to mapOf("status" to "known","clock_source" to "synthetic_utc","utc" to "2026-10-13T00:00:00Z"))
        val plan=original.filterKeys { it!="finalization_time" }+("header" to header)
        val directory=java.nio.file.Files.createTempDirectory("lmtr-finalization-");val file=directory.resolve("new.lmtr").toFile()
        try {
            LmtrWriter.writePlan(plan,file)
            RandomAccessFile(file,"r").use { source ->
                var offset=0L;var end:Obj?=null
                while(offset<source.length()) { val record=LmtrFraming.read(source,offset);if(record.type==LmtrFraming.END)end=LmtrJson.parse(record.body);offset+=record.length }
                assertEquals("unknown",end!!["finalization_time"].obj()["status"])
                assertEquals("not_recorded",end["finalization_time"].obj()["reason"])
            }
        } finally { java.nio.file.Files.deleteIfExists(file.toPath());java.nio.file.Files.deleteIfExists(directory) }
    }

}
