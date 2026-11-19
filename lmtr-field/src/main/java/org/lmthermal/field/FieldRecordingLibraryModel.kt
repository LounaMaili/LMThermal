package org.lmthermal.field

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** Experimental demo records are isolated from real private sessions. */
internal const val EXTRA_SYNTHETIC_LIBRARY = "org.lmthermal.field.SYNTHETIC_LIBRARY"

internal data class FieldLibraryRow(val entry: FieldRecordingEntry, val inspection: FieldRecordingInspection? = null)
internal data class FieldLibraryState(val rows: List<FieldLibraryRow> = emptyList(), val omitted: Long = 0,
    val operation: String? = null, val choosingDestination: Boolean = false, val message: String? = null,
    val synthetic: Boolean = false)

/** One screen owns one background operation and an immutable selection across the SAF picker. */
internal class FieldRecordingLibraryModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(FieldLibraryState())
    val state = mutableState.asStateFlow()
    private var initialized = false
    private var job: Job? = null
    private var cancellation: AtomicBoolean? = null
    private var pickedSource: FieldLibraryRow? = null

    /** Load once per screen owner; Activity recreation retains the picker transaction. */
    fun open(synthetic: Boolean) {
        if (initialized) return
        initialized = true
        mutableState.value = FieldLibraryState(synthetic = synthetic)
        refresh()
    }

    /** Preserve results only for entries with the same file identity. */
    fun refresh() = work("Reading private recordings…") { check ->
        if (state.value.synthetic) installDemoExamples(check)
        val inventory = library().list(check)
        val previous = state.value.rows.associateBy { it.entry }
        val rows = inventory.entries.map { previous[it] ?: FieldLibraryRow(it) }
        return@work { current -> current.copy(rows = rows, omitted = inventory.omitted) }
    }

    /** Verify the selected file on demand to keep list opening independent of file size. */
    fun verify(row: FieldLibraryRow) = work("Verifying recording…") { check ->
        val inspection = library().inspect(row.entry, check)
        return@work { current ->
            current.copy(rows = current.rows.map { if (it.entry == row.entry) it.copy(inspection = inspection) else it })
        }
    }

    /** The chooser is only launched for a previously inspected immutable byte source. */
    fun choose(row: FieldLibraryRow): String? {
        if (job?.isActive == true || state.value.choosingDestination || row.inspection?.sha256 == null) return null
        pickedSource = row
        mutableState.value = state.value.copy(choosingDestination = true, message = null)
        val label = row.entry.session.take(40).map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("")
        return "LMThermal-${label}.lmtr"
    }

    /** Accept only a new document from SAF; never use a destination as another source. */
    fun destination(uri: Uri?) {
        val row = pickedSource
        pickedSource = null
        mutableState.value = state.value.copy(choosingDestination = false)
        if (uri == null) {
            mutableState.value = state.value.copy(message = "Destination selection cancelled.")
            return
        }
        if (row == null) {
            mutableState.value = state.value.copy(message = "The selected source is no longer available. The new document may be empty.")
            return
        }
        work("Copying and checking destination…") { check ->
            val context = getApplication<Application>()
            require(uri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isDocumentUri(context, uri)) {
                "Invalid document destination"
            }
            val result = library().copy(row.entry, checkNotNull(row.inspection), SafRecordingDestination(context.contentResolver, uri), check)
            val message = when (result.status) {
                FieldRecordingCopyStatus.VERIFIED -> "Copy exported and SHA-256 verified."
                FieldRecordingCopyStatus.READBACK_UNAVAILABLE -> "Copy written. Destination integrity could not be confirmed."
                FieldRecordingCopyStatus.CANCELLED -> "Export cancelled."
                FieldRecordingCopyStatus.FAILED -> "Export failed. ${result.detail.orEmpty()}".trim()
            } + if (result.partialMayRemain) " An unfinished destination may remain." else ""
            return@work { current -> current.copy(message = message) }
        }
    }

    /** Pollable cancellation keeps the operation owned until any cleanup has finished. */
    fun cancelWork() {
        cancellation?.set(true)
        if (job?.isActive == true) mutableState.value = state.value.copy(operation = "Cancelling…")
    }

    /** Serialized, cooperative work. Exceptional providers may block inside a single I/O call. */
    private fun work(label: String, run: (() -> Unit) -> (FieldLibraryState) -> FieldLibraryState) {
        if (job?.isActive == true || state.value.choosingDestination) return
        val flag = AtomicBoolean()
        cancellation = flag
        mutableState.value = state.value.copy(operation = label, message = null)
        job = viewModelScope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    val context = coroutineContext
                    run { context.ensureActive(); if (flag.get()) throw CancellationException("Cancelled") }
                }
                mutableState.value = update(state.value).copy(operation = null)
            } catch (_: CancellationException) {
                mutableState.value = state.value.copy(operation = null, message = "Operation cancelled.")
            } catch (error: Exception) {
                mutableState.value = state.value.copy(operation = null, message = error.message?.take(160) ?: "Operation failed.")
            } finally {
                cancellation = null
            }
        }
    }

    /** Normal mode never scans the disposable synthetic namespace. */
    private fun library() = FieldRecordingLibrary(storageRoot())
    private fun storageRoot(): File = getApplication<Application>().let { context ->
        File(if (state.value.synthetic) context.cacheDir else context.filesDir,
            if (state.value.synthetic) "r4-field-demo" else "r4-field")
    }

    /** Populate only demo cache entries from untouched canonical synthetic assets. */
    private fun installDemoExamples(check: () -> Unit) {
        val assets = getApplication<Application>().assets
        val samples = mapOf("completed" to "illustration-06-stored", "incomplete" to "missing-end",
            "damaged" to "committed-logical-hash-corrupt")
        for ((label, asset) in samples) {
            check()
            val directory = File(storageRoot(), "sample-$label")
            val target = File(directory, "capture.lmtr")
            if (target.exists()) continue
            check(directory.isDirectory || directory.mkdirs())
            val temporary = File.createTempFile("sample-", ".tmp", directory)
            try {
                assets.open("recording-demo/$asset.lmtr").use { source ->
                    temporary.outputStream().use { source.copyTo(it, 8192) }
                }
                check()
                try { Files.move(temporary.toPath(), target.toPath()) } catch (_: FileAlreadyExistsException) { }
            } finally {
                temporary.delete()
            }
        }
    }
}

/** SAF may allow writing without allowing a verified reread through the returned grant. */
private class SafRecordingDestination(private val resolver: ContentResolver, private val uri: Uri) : FieldRecordingDestination {
    override fun openWrite(): OutputStream = resolver.openOutputStream(uri, "wt")
        ?: throw IOException("Provider did not open the new document")

    override fun openRead(): InputStream? = try {
        resolver.openInputStream(uri) ?: throw IOException("Provider did not reopen the copy")
    } catch (error: FileNotFoundException) {
        // This exception can also mean the new document disappeared.
        if (reportedSize() == null) throw error else null
    } catch (_: SecurityException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }

    override fun reportedSize(): Long? = try {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0).takeIf { it >= 0 }
        }
    } catch (_: Exception) {
        null
    }

    override fun discardPartial(): Boolean = DocumentsContract.deleteDocument(resolver, uri)
}
