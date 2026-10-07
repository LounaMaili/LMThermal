package org.lmthermal.r2

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Optional independent copy. Only closed-and-readback-verified destinations qualify as published.
 * Cancellation/refusal never mutates the canonical source or creates a saved-frame claim.
 */
object VerifiedExport {
    data class Result(val success: Boolean, val status: String, val bytes: Long, val sourceHash: String)
    fun hash(file: File): String = file.inputStream().use { digest(it) }
    private fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        return hex(digest.digest())
    }
    fun copy(source: File, destination: () -> OutputStream, readback: () -> InputStream,
        cancelled: () -> Boolean = { false }): Result {
        val original = hash(source); var count = 0L; var status = "verified"
        try {
            destination().use { output -> source.inputStream().use { input ->
                val scratch = ByteArray(65536)
                while (true) {
                    if (cancelled()) throw InterruptedException("cancelled")
                    val n = input.read(scratch); if (n < 0) break
                    output.write(scratch, 0, n); count = Bounds.add(count, n.toLong())
                }; output.flush()
            } }
            readback().use { require(digest(it) == original) { "readback_mismatch" } }
        } catch (_: InterruptedException) { status = "cancelled" }
        catch (error: Exception) { status = when {
            error.message?.contains("ENOSPC") == true -> "out_of_space"
            error.message == "readback_mismatch" -> "readback_mismatch"
            else -> "storage_failure"
        } }
        check(original == hash(source)) { "canonical_source_mutated" }
        return Result(status == "verified", status, count, original)
    }
}
