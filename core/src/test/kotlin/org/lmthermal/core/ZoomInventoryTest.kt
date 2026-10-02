package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test

/** Fixed GET-only fake proves inventory queries and packet interpretation without a camera. */
class ZoomInventoryTest {
    private class Reader : ReadOnlyZoomControl {
        val queries = mutableListOf<ZoomQuery>()
        var responses = mutableMapOf<ZoomQuery, ZoomTransfer>()
        var owned = true
        override fun descriptor() = ZoomDescriptor(0x100, 5, 3, 1L shl 9, 0x201, 0, 65535, 0)
        override fun query(query: ZoomQuery): ZoomTransfer {
            queries += query
            return responses[query] ?: ZoomTransfer(query.length, List(query.length) { 1 })
        }
    }
    @Test fun inventoryUsesFixedGetsAndCurrentProbesOnDescriptorSelectedControl() {
        val reader = Reader(); val events = mutableListOf<Map<String, Any?>>()
        ZoomInventory(reader, { true }, { 123L }, events::add).run()
        assertEquals(listOf(ZoomQuery.CURRENT) + ZoomQuery.entries.flatMap {
            if (it == ZoomQuery.CURRENT) listOf(it) else listOf(it, ZoomQuery.CURRENT)
        }, reader.queries)
        val queries = events.filter { it["event"] == "zoom_query" }
        assertEquals(14, queries.size)
        assertTrue(queries.all { it["bmRequestType"] == 0xa1 && it["wValue"] == 0x0b00 && it["wIndex"] == 0x0503 })
        assertEquals(listOf(0x81,0x86,0x81,0x85,0x81,0x82,0x81,0x83,0x81,0x84,0x81,0x87,0x81,0x81), queries.map { it["bRequest"] })
        assertTrue(events.first()["zoom_advertised"] == true)
    }
    @Test fun littleEndianDecodingPreservesUnsignedHighBit() {
        assertEquals(32772, ZoomTransfer(2, listOf(4,128)).decoded(ZoomQuery.CURRENT))
        assertEquals(65535, ZoomTransfer(2, listOf(255,255)).decoded(ZoomQuery.MAXIMUM))
        assertEquals(3, ZoomTransfer(1, listOf(3)).decoded(ZoomQuery.INFO))
    }
    @Test fun failedShortOrOversizedTransfersNeverDecodeAsSuccess() {
        for (response in listOf(ZoomTransfer(-9, emptyList()), ZoomTransfer(0, emptyList()),
            ZoomTransfer(1,listOf(1)), ZoomTransfer(3,listOf(1,0,0)), ZoomTransfer(2,listOf(1)),
            ZoomTransfer(2,listOf(1,256)))) assertNull(response.decoded(ZoomQuery.CURRENT))
    }
    @Test fun unsupportedLengthQueryIsRecordedAndOtherGetsStillRun() {
        val reader = Reader(); reader.responses[ZoomQuery.LENGTH] = ZoomTransfer(-9, emptyList())
        val events = mutableListOf<Map<String, Any?>>()
        ZoomInventory(reader, { true }, { 123L }, events::add).run()
        val len = events.first { it["query"] == "LENGTH" }
        assertEquals(-9, len["actual_length"]); assertNull(len["decoded"]); assertEquals(false,len["exact_length"])
        assertEquals(listOf(ZoomQuery.CURRENT) + ZoomQuery.entries.flatMap {
            if (it == ZoomQuery.CURRENT) listOf(it) else listOf(it, ZoomQuery.CURRENT)
        }, reader.queries)
    }
    @Test fun zoomAdvertisementUsesBitmapBitNineNotSelectorMinusOne() {
        assertTrue(ZoomDescriptor(0x100,1,0,1L shl 9,0x201,0,0,0).zoomAdvertised)
        assertFalse(ZoomDescriptor(0x100,1,0,1L shl 10,0x201,0,0,0).zoomAdvertised)
    }
    @Test fun lostOwnershipStopsRemainingDiagnosticReads() {
        val reader = Reader()
        val events = mutableListOf<Map<String, Any?>>()
        assertThrows(IllegalStateException::class.java) {
            ZoomInventory(reader, { reader.owned }, { 123L }, {
                events += it
                if (it["event"] == "zoom_query") reader.owned = false
            }).run()
        }
        assertEquals(listOf(ZoomQuery.CURRENT), reader.queries)
        assertFalse(events.any { it["event"] == "zoom_inventory_complete" })
    }
    @Test fun queryDependentCurrentRepliesArePreservedWithoutSoftwareAcknowledgment() {
        // Real Android observation: CURRENT echoed the preceding INFO/MAX response.
        val values = listOf(1, 3, 3, 0, 0, 0, 0, 65535, 65535, 0, 0, 0, 0, 0)
        var ordinal = 0
        val reader = object : ReadOnlyZoomControl {
            override fun descriptor() = ZoomDescriptor(0x100, 1, 0, 0x220, 0x201, 0, 0, 0)
            override fun query(query: ZoomQuery): ZoomTransfer {
                val value = values[ordinal++]
                return ZoomTransfer(query.length, if (query.length == 1) listOf(value)
                    else listOf(value and 255, value ushr 8))
            }
        }
        val events = mutableListOf<Map<String, Any?>>()
        ZoomInventory(reader, { true }, { 123L }, events::add).run()
        assertEquals(listOf(1, 3, 0, 0, 65535, 0, 0, 0),
            events.filter { it["query"] == "CURRENT" }.map { it["decoded"] })
        assertTrue(events.filter { it["event"] == "zoom_query" }.all { it["exact_length"] == true })
    }

}
