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
}
