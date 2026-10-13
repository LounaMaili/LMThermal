package org.lmthermal.lmtr

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** On-demand small payloads exercise actual page promotion without large committed fixtures. */
class LmtrScaleTest {
    @Test fun rollingWriterPromotesBeyondLeafFanoutAndSelectedReadStaysLazy() {
        val plan=LmtrJson.parse(javaClass.getResourceAsStream("/lmtr/plans/illustration-01-stored.json")!!.use { it.readBytes() })
        val template=plan["chunks"].arr().single().obj()
        val metadata=template["metadata"].obj();val entries=metadata["entries"].arr().map { it.obj() }
        val chunks=mutableListOf<Obj>()
        fun item(entry:Obj,recipes:Obj,measurement:Boolean):Obj = mapOf(
            "metadata" to (metadata+mapOf("entries" to listOf(entry),"blocks" to if(measurement)metadata["blocks"] else emptyList<Obj>(),
                "contexts" to if(measurement)metadata["contexts"] else emptyList<Obj>())),"payload_recipes" to recipes)
        chunks.add(item(entries[0],emptyMap(),false))
        for(i in 0 until 258) {
            val id=(i+1).toString()
            val observation=entries[1]+mapOf("entry_id" to id,"timeline_ns" to id,"observation_sequence" to i.toString())
            val recipes=template["payload_recipes"].obj().mapKeys { (key,_) -> id+":"+key.substringAfter(':') }
            chunks.add(item(observation,recipes,true))
        }
        chunks.add(item(entries[2]+mapOf("entry_id" to "259","timeline_ns" to "259"),emptyMap(),false))
        val directory=Files.createTempDirectory("lmtr-kotlin-scale-")
        val file=directory.resolve("260.lmtr").toFile()
        try {
            val result=LmtrWriter.writePlan(plan+("chunks" to chunks),file)
            assertEquals("260",result["summary"].obj()["chunks"])
            assertEquals("258",result["summary"].obj()["observations"])
            val before=LmtrFraming.sha256(file.readBytes())
            val selected=LmtrReader(file).use { it.check(entry=257) }
            assertEquals("selected_chunks",selected["integrity_scope"])
            assertEquals(1L,selected["chunks_decoded"].d())
            assertTrue(selected["records_read"].d()<16)
            assertEquals(before,LmtrFraming.sha256(file.readBytes()))
        } finally { Files.deleteIfExists(file.toPath());Files.deleteIfExists(directory) }
    }
}
