package org.lmthermal.r2

import org.junit.Test
import org.junit.Assert.*
import java.io.*

class StorageTest {
    @Test fun exportRequiresSuccessfulCloseAndExactReadback() {
        val source = File.createTempFile("r2-source", ".bin").apply { writeBytes(ByteArray(180000) { it.toByte() }); deleteOnExit() }
        val destination = File.createTempFile("r2-dest", ".bin").apply { deleteOnExit() }
        for (kind in listOf("success", "cancel", "refusal", "short", "close", "mismatch", "full")) {
            val result = VerifiedExport.copy(source, {
                if (kind == "refusal") throw IOException("refused")
                if (kind == "full") throw IOException("ENOSPC")
                val stream = destination.outputStream()
                object: FilterOutputStream(stream) {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, if (kind == "short") length/2 else length) }
                    override fun close() { super.close(); if (kind == "close") throw IOException("close failed") }
                }
            }, { if (kind == "mismatch") ByteArrayInputStream(byteArrayOf(1)) else destination.inputStream() }, { kind == "cancel" })
            assertEquals(kind == "success", result.success)
            if (kind == "full") assertEquals("out_of_space", result.status)
            if (kind == "refusal") assertEquals("storage_failure", result.status)
            if (kind == "cancel") assertEquals("cancelled", result.status)
            if (kind == "short" || kind == "mismatch") assertEquals("readback_mismatch", result.status)
        }
    }
    @Test fun unknownCapacityPermitsStartAndKnownInsufficientCapacityBlocks() {
        assertTrue(StoragePolicy.start(null, 200, 100).allowStart)
        assertNotNull(StoragePolicy.start(null, 200, 100).warning)
        assertFalse(StoragePolicy.start(299, 200, 100).allowStart)
        assertTrue(StoragePolicy.start(300, 200, 100).allowStart)
        assertEquals("low", StoragePolicy.running(99, 100).label)
        assertNull(StoragePolicy.running(101, 100).warning)
    }
    @Test fun boundedQueueStopClosesShortChunkWithoutStaleFiller() {
        val file = File.createTempFile("r2-queue", ".r2proto").apply { delete(); deleteOnExit() }
        val writer = BoundedRecorder(file, Profile.FULL, Deflate1)
        repeat(10) { writer.offer(Synthetic.frame(it.toLong(), 3, 2)) }
        assertTrue(writer.stop("usb_detach", 5000)); assertTrue(writer.maxBytes <= Bounds.QUEUE)
        PrototypeReader(file).use { reader -> assertTrue(reader.complete); var entries = 0; reader.forEachChunk { entries += it.entries.size }; assertEquals(10, entries) }
    }
}
