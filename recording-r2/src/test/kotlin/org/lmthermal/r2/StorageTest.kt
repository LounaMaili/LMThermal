package org.lmthermal.r2

import org.junit.Test
import org.junit.Assert.*
import java.io.*

class StorageTest {
    @Test fun refusalOrSyncFailureAtMultipleChunkBoundariesPreservesEarlierCommits() {
        for (fault in listOf("write", "body_sync", "footer_sync")) for (message in listOf("ENOSPC", "provider refusal")) {
            val source=File.createTempFile("r2-refusal", ".r2proto").apply { delete(); deleteOnExit() }
            val delegate=FileSink(source); var failing=false; var syncs=0
            val sink=object: AppendSink {
                override fun write(bytes: ByteArray) {
                    if(failing && fault=="write") throw IOException(message)
                    delegate.write(bytes)
                }
                override fun sync() {
                    if(failing) { syncs++; if(syncs==if(fault=="body_sync")1 else if(fault=="footer_sync")2 else -1) throw IOException(message) }
                    delegate.sync()
                }
                override fun close()=delegate.close()
            }
            PrototypeRecorder(RecordWriter(sink),Profile.FULL,Stored).use { writer ->
                writer.accept(Synthetic.frame(0,3,2)); writer.seal(); failing=true
                writer.accept(Synthetic.frame(1,3,2))
                try { writer.seal(); fail("refusal ignored") } catch (_: IOException) {}
                assertEquals(1L,writer.committedEntries); assertFalse(writer.finalized)
            }
            PrototypeReader(source).use { reader ->
                assertFalse(reader.complete); var count=0; reader.forEachChunk { count+=it.entries.size }
                // An unacknowledged footer sync may still leave verifiable bytes. They were
                // never counted saved by the failed writer; forensic recovery is separate.
                assertEquals(if(fault=="footer_sync")2 else 1,count)
            }
        }
    }
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
