package org.lmthermal.lmtr

import org.junit.Assert.*
import org.junit.Test

/** Capacity boundaries are tested without producing a duration-sized physical corpus. */
class LmtrFrontierTest {
    @Test fun snapshotWithFullUpperLevelAndLowerCarrySplitsWithoutReordering() {
        val children=(0..256).map { mapOf<String,Any?>("ordinal" to it) }
        val pages=LmtrWriter.partitionIndexChildren(children) { it.size<=256 }
        assertEquals(listOf(256,1),pages.map { it.size })
        assertEquals(children,pages.flatten())
    }
    @Test fun exactBodyBudgetSealsEarlyAndRefusesAnOversizedSingleton() {
        val children=(0..8).map { mapOf<String,Any?>("ordinal" to it) }
        assertEquals(listOf(3,3,3),LmtrWriter.partitionIndexChildren(children) { it.size<=3 }.map { it.size })
        try { LmtrWriter.partitionIndexChildren(children) { false };fail("oversized child") }
        catch(e:LmtrFailure) { assertEquals("resource_limit",e.code) }
    }
    @Test fun publicStatisticsRefusesMalformedDimensionsMaskAndBounds() {
        for(action in listOf<()->Unit>(
            { LmtrChunk.statistics(ByteArray(4),null,Int.MAX_VALUE,2) },
            { LmtrChunk.statistics(ByteArray(3),null,1,1) },
            { LmtrChunk.statistics(ByteArray(4),byteArrayOf(2),1,1) },
            { LmtrChunk.statistics(ByteArray(4),null,1,1,listOf(0,0,2,1)) })) {
            try { action();fail("unsafe public statistics input") } catch(_:LmtrFailure) { }
        }
    }
    @Test fun wholeChunkBudgetRejectsBeforeAnyMissingRecipeIsMaterialized() {
        val plan=LmtrJson.parse(javaClass.getResourceAsStream("/lmtr/plans/illustration-01-stored.json")!!.use { it.readBytes() })
        val item=plan["chunks"].arr().single().obj();val metadata=item["metadata"].obj()
        val blocks=metadata["blocks"].arr().mapIndexed { i,b -> if(i==0)b.obj()+mapOf("decoded" to "67108864","stored" to "67108864") else b.obj() }
        val modified=item+mapOf("metadata" to (metadata+("blocks" to blocks)),"payload_recipes" to emptyMap<String,Any?>())
        val directory=java.nio.file.Files.createTempDirectory("lmtr-admission-");val file=directory.resolve("refused.lmtr").toFile()
        try {
            try { LmtrWriter.writePlan(plan+("chunks" to listOf(modified)),file);fail("whole chunk admission bypassed") }
            catch(e:LmtrFailure) { assertEquals("resource_limit",e.code) }
        } finally { java.nio.file.Files.deleteIfExists(file.toPath());java.nio.file.Files.deleteIfExists(directory) }
    }

}
