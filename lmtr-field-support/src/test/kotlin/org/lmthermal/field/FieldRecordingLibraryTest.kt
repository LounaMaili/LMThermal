package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lmthermal.lmtr.LmtrAppendSession
import org.lmthermal.lmtr.LmtrReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.coroutines.cancellation.CancellationException

/** Canonical inputs are copied into per-test private sessions before any experiment. */
class FieldRecordingLibraryTest {
    @get:Rule val temporary = TemporaryFolder()

    /** Create only the expected private layout. */
    private fun recording(session: String, fixture: String?): File = File(temporary.newFolder(session), "capture.lmtr").apply {
        if (fixture != null) {
            val input = checkNotNull(FieldRecordingLibraryTest::class.java.getResourceAsStream("/files/$fixture.lmtr"))
            input.use { source -> outputStream().use { source.copyTo(it) } }
        }
    }

    /** Model a readable SAF document unless a test supplies explicit failure behavior. */
    private class Destination(var readable: Boolean = true, val cleanup: Boolean = true) : FieldRecordingDestination {
        val saved = ByteArrayOutputStream()
        var discarded = false
        var writeAttempted = false
        var declaredSize: Long? = null
        var readOverride: (() -> InputStream)? = null
        var writeOverride: (() -> OutputStream)? = null

        override fun openWrite(): OutputStream {
            writeAttempted = true
            return writeOverride?.invoke() ?: saved
        }

        override fun openRead(): InputStream? = if (readable) readOverride?.invoke() ?: saved.toByteArray().inputStream() else null
        override fun reportedSize(): Long? = declaredSize
        override fun discardPartial(): Boolean {
            discarded = true
            return cleanup
        }
    }

    /** Missing roots and unrelated files are not phantom recordings. */
    @Test fun listUsesOnlyRegularPrivateSessionCaptures() {
        assertTrue(FieldRecordingLibrary(File(temporary.root, "absent")).list().entries.isEmpty())
        val capture = recording("run-real", "illustration-06-stored")
        recording("no-capture", null)
        File(temporary.root, "unrelated.lmtr").writeText("ignore")
        Files.createSymbolicLink(File(temporary.root, "linked-session").toPath(), capture.parentFile.toPath())
        val linked = recording("linked-capture", null)
        Files.createSymbolicLink(linked.toPath(), capture.toPath())
        val entries = FieldRecordingLibrary(temporary.root).list().entries
        assertEquals(listOf("run-real"), entries.map { it.session })
        assertEquals(capture.length(), entries.single().bytes)
    }

    /** Large libraries retain the newest rows and disclose omitted results. */
    @Test fun inventoryIsBoundedAndCancellable() {
        repeat(201) { recording("run-%03d".format(it), null).writeText("unverified") }
        val library = FieldRecordingLibrary(temporary.root)
        val result = library.list()
        assertEquals(200, result.entries.size)
        assertEquals(1L, result.omitted)
        assertThrows(CancellationException::class.java) { library.list { throw CancellationException() } }
    }

    /** Compare full ENDs with incomplete and invalid inputs using the existing reader. */
    @Test fun verificationDoesNotPromotePresenceOrAValidPrefixToCompleted() {
        val completed = recording("completed", "illustration-06-stored")
        recording("incomplete", "missing-end")
        recording("damaged", "committed-logical-hash-corrupt")
        val interrupted = recording("interrupted", null)
        val header = LmtrReader(completed).use { it.header }
        RandomAccessFile(interrupted, "rw").use { output ->
            LmtrAppendSession(header, output).finish("interrupted", "source_closed",
                mapOf("status" to "unknown", "clock_source" to "host_utc", "reason" to "not_recorded"))
        }
        val library = FieldRecordingLibrary(temporary.root)
        val results = library.list().entries.associate { it.session to library.inspect(it) }
        assertEquals(FieldRecordingIntegrity.COMPLETED, results.getValue("completed").integrity)
        assertEquals(FieldRecordingIntegrity.INTERRUPTED, results.getValue("interrupted").integrity)
        assertEquals(FieldRecordingIntegrity.INCOMPLETE, results.getValue("incomplete").integrity)
        assertNotNull(results.getValue("incomplete").prefixBytes)
        assertEquals(FieldRecordingIntegrity.UNVERIFIED, results.getValue("damaged").integrity)
        assertNull(results.getValue("damaged").sha256)
    }

    /** Destination parity never alters the source, including a verified-prefix original. */
    @Test fun exportsWholeOriginalAndDistinguishesReadbackUnavailable() {
        for ((session, fixture) in listOf("full" to "illustration-06-stored", "partial" to "missing-end")) {
            val source = recording(session, fixture)
            val original = source.readBytes()
            val library = FieldRecordingLibrary(temporary.root)
            val entry = library.list().entries.single { it.session == session }
            val checked = library.inspect(entry)
            for (readable in listOf(true, false)) {
                val destination = Destination(readable)
                val result = library.copy(entry, checked, destination)
                assertEquals(if (readable) FieldRecordingCopyStatus.VERIFIED else FieldRecordingCopyStatus.READBACK_UNAVAILABLE,
                    result.status)
                assertFalse(destination.discarded)
                assertArrayEquals(original, destination.saved.toByteArray())
                assertArrayEquals(original, source.readBytes())
            }
        }
    }

    /** Truncated, different-byte or provider-error readbacks may never be reported as success. */
    @Test fun mismatchesAndProviderReadFailuresDiscardOnlyTheNewDestination() {
        val source = recording("source", "illustration-06-stored")
        val original = source.readBytes()
        val library = FieldRecordingLibrary(temporary.root)
        val entry = library.list().entries.single()
        val inspection = library.inspect(entry)
        val shorter = Destination().apply { readOverride = { ByteArrayInputStream(original.copyOf(1)) } }
        val corrupt = Destination(cleanup = false).apply {
            readOverride = { ByteArrayInputStream(original.clone().apply { this[0] = (this[0].toInt() xor 1).toByte() }) }
        }
        val failing = Destination().apply { readOverride = { throw IOException("Read failed") } }
        val declaredShorter = Destination(readable = false).apply { declaredSize = entry.bytes - 1 }
        for (target in listOf(shorter, corrupt, failing, declaredShorter)) {
            val result = library.copy(entry, inspection, target)
            assertEquals(FieldRecordingCopyStatus.FAILED, result.status)
            assertTrue(target.discarded)
            assertEquals(target === corrupt, result.partialMayRemain)
            assertArrayEquals(original, source.readBytes())
        }
    }

    /** Closing the output and honoring cancellation are required before reporting completion. */
    @Test fun outputCloseFailureAndCancellationCannotPublishSuccess() {
        val source = recording("source", "illustration-06-stored")
        val original = source.readBytes()
        val library = FieldRecordingLibrary(temporary.root)
        val entry = library.list().entries.single()
        val inspection = library.inspect(entry)
        assertThrows(CancellationException::class.java) { library.inspect(entry) { throw CancellationException() } }

        val broken = Destination().apply {
            writeOverride = { object : ByteArrayOutputStream() { override fun close() { throw IOException("Close failed") } } }
        }
        assertEquals(FieldRecordingCopyStatus.FAILED, library.copy(entry, inspection, broken).status)
        assertTrue(broken.discarded)
        val cancelled = Destination()
        val result = library.copy(entry, inspection, cancelled) {
            if (cancelled.saved.size() > 0) throw CancellationException()
        }
        assertEquals(FieldRecordingCopyStatus.CANCELLED, result.status)
        assertTrue(cancelled.discarded)
        assertArrayEquals(original, source.readBytes())
    }

    /** Digest verification detects even a same-length edit with the previous mtime restored. */
    @Test fun staleVerificationCannotAuthorizeChangedBytes() {
        val source = recording("source", "illustration-06-stored")
        val library = FieldRecordingLibrary(temporary.root)
        val entry = library.list().entries.single()
        val inspection = library.inspect(entry)
        val changed = source.readBytes().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        source.writeBytes(changed)
        Files.setLastModifiedTime(source.toPath(), FileTime.fromMillis(entry.modifiedMs))
        val destination = Destination()
        assertEquals(FieldRecordingCopyStatus.FAILED, library.copy(entry, inspection, destination).status)
        assertTrue(destination.writeAttempted)
        assertTrue(destination.discarded)
        assertArrayEquals(changed, source.readBytes())
    }
}
