package org.lmthermal.r2

import org.junit.Test
import org.junit.Assert.*
import java.io.*

class StorageTest {
    @Test fun failedCloseDoesNotReportSuccessfulStopEvenIfEndBytesSurvive() {
        val source=File.createTempFile("r2-close", ".r2proto").apply { delete(); deleteOnExit() }
        val writer=BoundedRecorder(source,Profile.FULL,Stored) { file ->
            val delegate=FileSink(file)
            object: AppendSink {
                override fun write(bytes: ByteArray)=delegate.write(bytes)
                override fun sync()=delegate.sync()
                override fun close() { delegate.close(); throw IOException("injected close failure") }
            }
        }
        writer.offer(Synthetic.frame(0,3,2))
        assertFalse(writer.stop(timeoutMs=5000)); assertTrue(writer.failure!!.startsWith("close_failed"))
        assertFalse(writer.offer(Synthetic.frame(1,3,2)))
        PrototypeReader(source).use { reader -> assertTrue(reader.complete) }
    }
    @Test fun queuedMetadataConsumesBudgetEvenForTinyPlanes() {
        val source=File.createTempFile("r2-metadata", ".r2proto").apply { delete(); deleteOnExit() }
        val entered=java.util.concurrent.CountDownLatch(1); val release=java.util.concurrent.CountDownLatch(1)
        val codec=object: BlockCodec {
            override val id=0; override val name="blocked-stored"
            override fun encode(input: ByteArray): ByteArray { entered.countDown(); check(release.await(10,java.util.concurrent.TimeUnit.SECONDS)); return Stored.encode(input) }
            override fun decode(input: ByteArray,size: Int)=Stored.decode(input,size)
        }
        val context=(0 until 30).associate { "field$it" to "x".repeat(10000) }
        val writer=BoundedRecorder(source,Profile.FULL,codec)
        fun frame(i: Int)=Synthetic.frame(i.toLong(),3,2).copy(relativeNs=i*2000000000L,context=context)
        try {
            writer.offer(frame(0)); writer.offer(frame(1)); assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            repeat(30) { writer.offer(frame(it+2)) }
            assertTrue(writer.drops>0); assertTrue(writer.maxBytes<=Bounds.QUEUE)
        } finally { release.countDown(); assertTrue(writer.stop(timeoutMs=5000)) }
    }
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
