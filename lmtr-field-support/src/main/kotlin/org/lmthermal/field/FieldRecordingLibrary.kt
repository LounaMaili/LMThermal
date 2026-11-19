package org.lmthermal.field

import org.lmthermal.lmtr.LmtrFailure
import org.lmthermal.lmtr.LmtrReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.PriorityQueue
import kotlin.coroutines.cancellation.CancellationException

private const val RECORDING_NAME = "capture.lmtr"
private const val DISPLAY_LIMIT = 200
private const val INSPECTION_BUDGET = 48L * 1024 * 1024
private const val COPY_BUFFER_BYTES = 64 * 1024

/** File identity used for change detection, with SHA-256 bound at successful inspection. */
data class FieldRecordingEntry(val session: String, val bytes: Long, val modifiedMs: Long, val fileKey: Any?)

/** The index keeps the newest regular captures and discloses any display limit. */
data class FieldRecordingInventory(val entries: List<FieldRecordingEntry>, val omitted: Long)

/** A checked prefix has a different authority from a verified END. */
enum class FieldRecordingIntegrity { COMPLETED, INTERRUPTED, FAILED_END, INCOMPLETE, UNVERIFIED }

/** An exportable source records a complete-file byte hash even when only its prefix is valid. */
data class FieldRecordingInspection(
    val integrity: FieldRecordingIntegrity,
    val detail: String? = null,
    val sha256: String? = null,
    val prefixBytes: Long? = null
)

/** Created by the caller's document picker. Only this new destination may be cleaned up. */
interface FieldRecordingDestination {
    fun openWrite(): OutputStream
    fun openRead(): InputStream?
    fun reportedSize(): Long? = null
    fun discardPartial(): Boolean
}

/** Byte-level publication and destination verification are separate outcomes. */
enum class FieldRecordingCopyStatus { VERIFIED, READBACK_UNAVAILABLE, CANCELLED, FAILED }

data class FieldRecordingCopyResult(
    val status: FieldRecordingCopyStatus,
    val partialMayRemain: Boolean = false,
    val detail: String? = null
)

/** One caller serializes bounded scans and copies. The private directory is always read only. */
class FieldRecordingLibrary(private val root: File) {
    /** Enumerate without following links or materializing an unbounded directory listing. */
    fun list(checkCancellation: () -> Unit = {}): FieldRecordingInventory {
        val rootAttrs = try {
            Files.readAttributes(root.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return FieldRecordingInventory(emptyList(), 0)
        }
        check(rootAttrs.isDirectory) { "Recording storage is not a directory" }
        val order = compareBy<FieldRecordingEntry> { it.modifiedMs }.thenBy { it.session }
        val newest = PriorityQueue(order)
        var total = 0L
        Files.newDirectoryStream(root.toPath()).use { directories ->
            for (directory in directories) {
                checkCancellation()
                val entry = try {
                    snapshot(directory.fileName.toString())
                } catch (_: NoSuchFileException) {
                    continue
                } ?: continue
                total++
                newest.add(entry)
                if (newest.size > DISPLAY_LIMIT) newest.remove()
            }
        }
        return FieldRecordingInventory(newest.sortedWith(order.reversed()), total - newest.size)
    }

    /** Verify strictly when there is an END, or recognize a recoverable prefix without rewriting. */
    fun inspect(entry: FieldRecordingEntry, checkCancellation: () -> Unit = {}): FieldRecordingInspection {
        return try {
            checkCancellation()
            val source = currentFile(entry)
            val report = LmtrReader(source, INSPECTION_BUDGET).use { reader ->
                reader.check(full = true, recover = true, cancelled = { checkCancellation(); false })
            }
            val status = when {
                report["status"] == "recovered" && report["integrity_scope"] == "full_verified_prefix" ->
                    FieldRecordingIntegrity.INCOMPLETE
                report["status"] == "valid" && report["integrity_scope"] == "full" -> when (report["declared_end_status"]) {
                    "completed" -> FieldRecordingIntegrity.COMPLETED
                    "interrupted" -> FieldRecordingIntegrity.INTERRUPTED
                    "failed" -> FieldRecordingIntegrity.FAILED_END
                    else -> error("Unknown END status")
                }
                else -> error("No qualifying verification result")
            }
            val hash = openSource(source).use { digestStream(it, entry.bytes, checkCancellation) }
            currentFile(entry)
            FieldRecordingInspection(status, report["reason"] as? String, hash,
                (report["verified_prefix_bytes"] as? String)?.toLongOrNull())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FieldRecordingInspection(FieldRecordingIntegrity.UNVERIFIED, (error as? LmtrFailure)?.code ?: error.message?.take(120))
        }
    }

    /** Write and close first, then compare both length and SHA-256 if a readable destination exists. */
    fun copy(entry: FieldRecordingEntry, inspection: FieldRecordingInspection, destination: FieldRecordingDestination,
        checkCancellation: () -> Unit = {}): FieldRecordingCopyResult {
        return try {
            checkCancellation()
            val expected = checkNotNull(inspection.sha256) { "Verify the source before export" }
            check(inspection.integrity != FieldRecordingIntegrity.UNVERIFIED)
            val source = currentFile(entry)
            val copiedHash = openSource(source).use { input ->
                destination.openWrite().use { output ->
                    digestStream(input, entry.bytes, checkCancellation, output).also { output.flush() }
                }
            }
            check(copiedHash == expected) { "Source bytes changed since verification" }
            currentFile(entry)
            checkCancellation()
            val copied = destination.openRead()?.use { digestStream(it, entry.bytes, checkCancellation) }
            check(copied == null || copied == expected) { "Destination SHA-256 mismatch" }
            if (copied == null) {
                val reportedSize = destination.reportedSize()
                check(reportedSize == null || reportedSize == entry.bytes) { "Destination length mismatch" }
            }
            checkCancellation()
            FieldRecordingCopyResult(if (copied == null) FieldRecordingCopyStatus.READBACK_UNAVAILABLE
                else FieldRecordingCopyStatus.VERIFIED)
        } catch (error: Exception) {
            val discarded = try { destination.discardPartial() } catch (_: Exception) { false }
            FieldRecordingCopyResult(
                if (error is CancellationException) FieldRecordingCopyStatus.CANCELLED else FieldRecordingCopyStatus.FAILED,
                !discarded, error.message?.take(120))
        }
    }

    /** Revalidate the selected path immediately before opening it and after streaming. */
    private fun currentFile(entry: FieldRecordingEntry): File {
        check(snapshot(entry.session) == entry) { "Source changed or is missing. Refresh recordings." }
        return File(File(root, entry.session), RECORDING_NAME)
    }

    /** Read metadata for the exact expected layout, with no sidecar-derived claims. */
    private fun snapshot(session: String): FieldRecordingEntry? {
        require(session.isNotEmpty() && session !in listOf(".", "..") && '/' !in session && '\\' !in session)
        val dir = File(root, session).toPath()
        if (!Files.readAttributes(root.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS).isDirectory ||
            !Files.readAttributes(dir, BasicFileAttributes::class.java, NOFOLLOW_LINKS).isDirectory) return null
        val attributes = Files.readAttributes(dir.resolve(RECORDING_NAME), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        return if (attributes.isRegularFile) FieldRecordingEntry(session, attributes.size(),
            attributes.lastModifiedTime().toMillis(), attributes.fileKey()) else null
    }

    /** A symlink must never become a source for reads or publication. */
    private fun openSource(source: File): InputStream = Files.newInputStream(source.toPath(), NOFOLLOW_LINKS)
}

/** Bound both streams by the inspected byte length and reject zero-progress reads. */
private fun digestStream(input: InputStream, expectedBytes: Long, checkCancellation: () -> Unit,
    output: OutputStream? = null): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var bytes = 0L
    while (true) {
        checkCancellation()
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0 || count > expectedBytes - bytes) throw IOException("Unexpected stream length")
        output?.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        bytes += count
    }
    if (bytes != expectedBytes) throw IOException("Stream ended before the expected byte count")
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
