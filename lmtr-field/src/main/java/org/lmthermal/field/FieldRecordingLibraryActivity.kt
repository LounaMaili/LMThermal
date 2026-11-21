package org.lmthermal.field

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lmthermal.app.BuildConfig
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Internal Activity uses the normal field lifecycle, with no camera or recorder owner of its own. */
class FieldRecordingLibraryActivity : AppCompatActivity() {
    private val model: FieldRecordingLibraryModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.open(BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SYNTHETIC_LIBRARY, false))
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), model::destination)
            BackHandler(enabled = state.operation != null, onBack = model::cancelWork)
            MaterialTheme {
                LibraryScreen(state, ::finish, model::refresh, model::verify, model::cancelWork) { row ->
                    model.choose(row)?.let { name ->
                        try { picker.launch(name) } catch (_: android.content.ActivityNotFoundException) { model.destination(null) }
                    }
                }
            }
        }
    }
}

/** Bounded rows stay readable while verification or publication runs on the worker. */
@Composable
private fun LibraryScreen(state: FieldLibraryState, back: () -> Unit, refresh: () -> Unit,
    verify: (FieldLibraryRow) -> Unit, cancel: () -> Unit, export: (FieldLibraryRow) -> Unit) {
    val available = state.operation == null && !state.choosingDestination
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recordings", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = back, enabled = available) { Text("Back") }
            }
            if (state.synthetic) Text("Simulation · synthetic LMTR examples", color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                val count = state.rows.size
                Text(if (state.omitted == 0L) "$count private recordings" else "$count shown · ${state.omitted} older files")
                if (state.operation != null) {
                    TextButton(onClick = cancel, enabled = state.operation != "Cancelling…") { Text("Cancel") }
                } else {
                    TextButton(onClick = refresh, enabled = available) { Text("Refresh") }
                }
            }
            state.operation?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.rows.isEmpty() && available && state.message == null) Text("The private library is empty.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rows, key = { it.entry.session }) { row ->
                    RecordingCard(row, state.synthetic, available, { verify(row) }, { export(row) })
                }
            }
        }
    }
}

/** Show only established LMTR integrity and keep the export action explicit for each source. */
@Composable
private fun RecordingCard(row: FieldLibraryRow, synthetic: Boolean, available: Boolean, verify: () -> Unit, export: () -> Unit) {
    val inspection = row.inspection
    val title = if (synthetic) row.entry.session.replace('-', ' ') else "Session ${row.entry.session.take(12)}"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${displayBytes(row.entry.bytes)} · Modified ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(row.entry.modifiedMs))}",
                style = MaterialTheme.typography.bodySmall)
            Text(when (inspection?.integrity) {
                FieldRecordingIntegrity.COMPLETED -> "Finalized · full integrity verified"
                FieldRecordingIntegrity.INTERRUPTED -> "Interrupted · valid END, full integrity verified"
                FieldRecordingIntegrity.FAILED_END -> "Failed recording · valid END, full integrity verified"
                FieldRecordingIntegrity.INCOMPLETE -> "Incomplete or damaged navigation · prefix verified"
                else -> "Unverified"
            }, style = MaterialTheme.typography.labelLarge)
            if (inspection?.integrity == FieldRecordingIntegrity.INCOMPLETE) {
                val prefix = inspection.prefixBytes?.let { "Verified prefix: ${displayBytes(it)}. " }.orEmpty()
                Text(prefix + "Export retains the whole original. Use explicit recovery in Desktop.", style = MaterialTheme.typography.bodySmall)
            } else if (inspection?.integrity == FieldRecordingIntegrity.UNVERIFIED) {
                inspection.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = verify, enabled = available) { Text("Verify") }
                Button(onClick = export, enabled = available && inspection?.sha256 != null) { Text("Export copy…") }
            }
        }
    }
}

/** Preserve exact byte counts for small captures while keeping larger labels compact. */
private fun displayBytes(bytes: Long): String = if (bytes < 1024) "$bytes B"
    else if (bytes < 1024 * 1024) String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    else String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0)
