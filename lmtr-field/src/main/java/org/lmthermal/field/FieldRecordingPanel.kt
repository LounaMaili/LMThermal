package org.lmthermal.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.lmthermal.core.NativeEquivalentThermometry
import java.util.Locale

/** Small operator projection. Acquisition rate and acknowledged average remain distinct. */
internal data class FieldRecordingPanelState(
    val progress: FieldRecordingProgress = FieldRecordingProgress(),
    val acquisitionFps: Double? = null,
    val canStart: Boolean = false,
    val canStop: Boolean = false,
    val startLocked: Boolean = false,
    val canOpenRecordings: Boolean = false
)

/** Compact controls keep the thermal preview visible during the entire run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldRecordingPanel(state: FieldRecordingPanelState, start: () -> Unit, stop: () -> Unit,
    readSpace: suspend () -> FieldRecordingSpace, simulated: Boolean = false, openRecordings: () -> Unit) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val progress = state.progress
    val counts = progress.counts
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("field-recording-panel")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Recording", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(progress.phase.label(), style = MaterialTheme.typography.labelLarge,
                    color = when (progress.phase) {
                        FieldRecordingPhase.ERROR, FieldRecordingPhase.INTERRUPTED -> MaterialTheme.colorScheme.error
                        FieldRecordingPhase.RECORDING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }, modifier = Modifier.testTag("field-recording-phase"))
            }
            Text("Experimental · Analysis · 5 Hz target · 10 s", style = MaterialTheme.typography.bodyMedium)
            Text("Acquisition: ${rate(state.acquisitionFps, "FPS")}    Stored ACK: ${rate(counts?.acknowledgedHz, "Hz avg")}",
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Counter("Selected", counts?.selected, Modifier.weight(1f))
                Counter("Accepted", counts?.accepted, Modifier.weight(1f))
                Counter("Stored ACK", counts?.acknowledged, Modifier.weight(1f), emphasized = true)
                Counter("Queue losses", counts?.queueLosses, Modifier.weight(1f))
            }
            if (state.startLocked) Text("Start locked · inspect the previous run", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { detailsOpen = true }, modifier = Modifier.testTag("field-recording-details")) {
                    Text(if (progress.file != null) "Recording details" else "Profile & storage")
                }
                TextButton(onClick = openRecordings, enabled = state.canOpenRecordings) { Text("Recordings") }
                if (state.canStop) {
                    Button(onClick = stop, modifier = Modifier.testTag("field-recording-stop")) { Text(if (simulated) "Stop demo" else "Stop") }
                } else {
                    Button(onClick = start, enabled = state.canStart, modifier = Modifier.testTag("field-recording-start")) {
                        Text(if (simulated) "Start demo" else "Start")
                    }
                }
            }
        }
    }
    if (detailsOpen) {
        var space by remember { mutableStateOf<FieldRecordingSpace?>(null) }
        LaunchedEffect(Unit) { space = readSpace() }
        ModalBottomSheet(onDismissRequest = { detailsOpen = false }) {
            RecordingDetails(progress, space, simulated)
        }
    }
}

/** Counts have text labels and an accented storage tile to distinguish it from admission. */
@Composable
private fun Counter(label: String, value: Long?, modifier: Modifier, emphasized: Boolean = false) {
    Surface(modifier, shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
            Text(value?.toString() ?: "—", style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Read-only explanatory sheet keeps long details out of the live preview controls. */
@Composable
private fun RecordingDetails(progress: FieldRecordingProgress, space: FieldRecordingSpace?, simulated: Boolean) {
    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (simulated) Text("Simulation · example values and states", style = MaterialTheme.typography.labelLarge)
        Text("Recording details", style = MaterialTheme.typography.headlineSmall)
        Text("Analysis · selected profile", style = MaterialTheme.typography.titleMedium)
        Text("Stored Float32 temperature matrices when available, timing, calibration/settings and explicit gaps. Native samples and USB transport are omitted.")
        Text("5 Hz is the supported experimental setting here. Other cadences and profiles await qualification.")
        HorizontalDivider()
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text(if (space == null) "Reading available space…" else
            "Available: ${space.availableBytes?.let(::mib) ?: "unknown"} · Start preflight: ${mib(space.preflight.requiredFreeBytes)} free required")
        space?.let { Text("About ${mib(it.uncompressedPlaneBytes)} of uncompressed temperature planes for the requested window. This excludes metadata and sidecars. Actual size varies with DEFLATE-1, data and losses.") }
        Text("The storage preflight retains its conservative acquisition-rate allowance.", style = MaterialTheme.typography.bodySmall)
        Text("Accepted enters the recorder's memory queue. Stored ACK counts observations committed to storage, including observations whose temperature was unavailable. A final END is checked separately.")
        progress.counts?.let {
            Text("Preparation losses: ${it.preparationLosses} · Writer losses: ${it.writerLosses}\n" +
                "Materialization losses: ${it.materializationLosses ?: "unknown"} · Missed slots: ${it.missedSlots ?: "unknown"}\n" +
                "ACKs without temperature: ${it.acknowledgedUnavailable ?: "unknown"} · Unclassified callbacks: ${it.unclassifiedCallbacks ?: "unknown"}")
        }
        progress.detail?.let { Text(it.take(240)) }
        progress.file?.let { file ->
            HorizontalDivider()
            Text("Checked file", style = MaterialTheme.typography.titleMedium)
            Text("${file.name} · ${file.bytes} bytes\nRun ${file.runId}\nEND ${file.endStatus} · ${file.endReason}\nSHA-256 ${file.sha256}")
        }
        if (simulated) Text("No LMTR file is created by the simulation.")
        Text(NativeEquivalentThermometry.WARNING, style = MaterialTheme.typography.bodySmall)
    }
}

/** Stable short labels fit the same control strip at every stage. */
private fun FieldRecordingPhase.label(): String = when (this) {
    FieldRecordingPhase.WAITING -> "Waiting for ready source"
    FieldRecordingPhase.READY -> "Ready"
    FieldRecordingPhase.CHECKING -> "Checking admission"
    FieldRecordingPhase.RECORDING -> "Recording"
    FieldRecordingPhase.STOP_REQUESTED -> "Stop requested"
    FieldRecordingPhase.DRAINING -> "Draining / checking"
    FieldRecordingPhase.FINALIZED -> "Finalized"
    FieldRecordingPhase.INTERRUPTED -> "Interrupted"
    FieldRecordingPhase.ERROR -> "Error"
}

/** Estimated MiB and observed rates use a fixed diagnostic format. */
private fun mib(bytes: Long): String = String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0)
private fun rate(value: Double?, unit: String): String = value?.let { String.format(Locale.ROOT, "%.1f %s", it, unit) } ?: "—"
