package org.lmthermal.exchange

import java.io.*
import org.junit.Assert.*
import org.junit.Test

/** Simulated backend failures exercise write/flush/close/cancellation, without risking user storage. */
class CapturePublicationTest {
    private fun file(block: (File) -> Unit) { val f = File.createTempFile("publication-test", ".lmtx")
        try { f.writeBytes(ByteArray(30000) { it.toByte() }); block(f) } finally { f.delete() } }
    @Test fun successRequiresCloseAndExactCopy() = file { source ->
        val output = object : ByteArrayOutputStream() { var closed = false; override fun close() { closed = true } }
        var removed = false
        CapturePublication.copy(source, { output }, { removed = true; true })
        assertTrue(output.closed); assertFalse(removed); assertArrayEquals(source.readBytes(), output.toByteArray())
    }
    @Test fun openFailureAttemptsPartialDeletion() = file { source -> var removed = false
        assertThrows(IOException::class.java) { CapturePublication.copy(source, { throw IOException("Full") }, { removed = true; true }) }
        assertTrue(removed)
    }
    @Test fun closeFailureCannotReportSuccess() = file { source -> var removed = false
        val output = object : ByteArrayOutputStream() { override fun close() { throw IOException("Close failed") } }
        assertThrows(IOException::class.java) { CapturePublication.copy(source, { output }, { removed = true; true }) }
        assertTrue(removed)
    }
    @Test fun writeFailureCannotPublishOrSharePartialData() = file { source -> var removed = false
        val output = object : OutputStream() { override fun write(b: Int) { throw IOException("Storage full") } }
        assertThrows(IOException::class.java) { CapturePublication.copy(source, { output }, { removed = true; true }) }
        assertTrue(removed)
    }
    @Test fun cancellationClosesAndDeletesPartialDocument() = file { source -> var removed = false; var calls = 0
        assertThrows(InterruptedIOException::class.java) { CapturePublication.copy(source, { ByteArrayOutputStream() }, { removed = true; true },
            cancelled = { if (++calls == 2) throw InterruptedIOException("Cancelled") }) }
        assertTrue(removed)
    }
    @Test fun unsupportedProviderDeletionIsDisclosed() = file { source -> var disclosed = false
        assertThrows(IOException::class.java) { CapturePublication.copy(source, { throw IOException("Storage full") }, { false }, cleanupFailed = { disclosed = true }) }
        assertTrue(disclosed)
    }
}
