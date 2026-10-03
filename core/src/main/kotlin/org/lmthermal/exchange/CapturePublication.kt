package org.lmthermal.exchange

import java.io.File
import java.io.OutputStream

/** Copies only a complete private artifact to a newly created destination. Close is part of success.
 * Provider atomicity/durability is not invented; partial cleanup is best-effort and disclosed separately.
 */
object CapturePublication {
    fun copy(file: File, openNewDestination: () -> OutputStream, deletePartial: () -> Boolean,
        cancelled: () -> Unit = {}, cleanupFailed: () -> Unit = {}) {
        var success = false
        try {
            openNewDestination().use { output -> file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { cancelled(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                output.flush()
            } }
            cancelled(); success = true
        } finally {
            if (!success && !runCatching(deletePartial).getOrDefault(false)) cleanupFailed()
        }
    }
}
