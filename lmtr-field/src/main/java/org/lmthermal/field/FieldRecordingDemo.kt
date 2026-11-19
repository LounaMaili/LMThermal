package org.lmthermal.field

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val RECORDING_UI_DEMO = "org.lmthermal.field.RECORDING_UI_DEMO"

/** Camera-free scripted operator preview. This composition owns no recorder or file. */
@Composable
internal fun FieldRecordingDemo() {
    var phase by rememberSaveable { mutableStateOf(FieldRecordingPhase.READY) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(phase) {
        when (phase) {
            FieldRecordingPhase.CHECKING -> { delay(600); phase = FieldRecordingPhase.RECORDING }
            FieldRecordingPhase.RECORDING -> {
                while (seconds < 10) { delay(1000); seconds++ }
                phase = FieldRecordingPhase.DRAINING
            }
            FieldRecordingPhase.STOP_REQUESTED -> { delay(800); phase = FieldRecordingPhase.DRAINING }
            FieldRecordingPhase.DRAINING -> { delay(1200); phase = FieldRecordingPhase.FINALIZED }
            else -> Unit
        }
    }
    val counts = if (phase in listOf(FieldRecordingPhase.READY, FieldRecordingPhase.CHECKING, FieldRecordingPhase.ERROR)) {
        null
    } else {
        val selected = seconds * 5L
        val accepted = selected - if (seconds >= 2) 1 else 0
        val acknowledged = if (phase == FieldRecordingPhase.FINALIZED) accepted else (accepted - 3).coerceAtLeast(0)
        FieldRecordingCounts(selected, accepted, acknowledged, selected - accepted, 0, 0, 0, 0, 0,
            if (seconds > 0) acknowledged.toDouble() / seconds else null)
    }
    val terminal = phase in listOf(FieldRecordingPhase.FINALIZED, FieldRecordingPhase.INTERRUPTED, FieldRecordingPhase.ERROR)
    val detail = when (phase) {
        FieldRecordingPhase.FINALIZED -> "Example of a checked END completed. File metadata requires a real checked field run."
        FieldRecordingPhase.INTERRUPTED -> "Example interruption · RSS guard · Start locked."
        FieldRecordingPhase.ERROR -> "Example admission refusal · insufficient storage."
        else -> null
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LMThermal Field", style = MaterialTheme.typography.headlineSmall)
            Text("Simulation · generated preview and example counters",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        DemoThermalPreview(Modifier.weight(1f).padding(horizontal = 16.dp).fillMaxWidth())
        if (terminal) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { phase = FieldRecordingPhase.INTERRUPTED }) { Text("Show interruption") }
                TextButton(onClick = { phase = FieldRecordingPhase.ERROR }) { Text("Show error") }
            }
        }
        FieldRecordingPanel(
            FieldRecordingPanelState(
                FieldRecordingProgress(phase, counts, detail = detail), 24.8,
                canStart = phase in listOf(FieldRecordingPhase.READY, FieldRecordingPhase.FINALIZED, FieldRecordingPhase.ERROR),
                canStop = phase == FieldRecordingPhase.RECORDING, startLocked = phase == FieldRecordingPhase.INTERRUPTED),
            start = { seconds = 0; phase = FieldRecordingPhase.CHECKING },
            stop = { phase = FieldRecordingPhase.STOP_REQUESTED },
            readSpace = { FieldRecordingSpace(2L * 1024 * 1024 * 1024) },
            simulated = true)
    }
}

/** A generated thermal-style gradient distinguishes the demo from physical evidence. */
@Composable
private fun DemoThermalPreview(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Generated thermal illustration, no camera" }) {
        drawRect(Brush.linearGradient(listOf(Color(0xff190d31), Color(0xff472257), Color(0xff6c3158)),
            Offset.Zero, Offset(size.width, size.height)))
        val center = Offset(size.width * .54f, size.height * .46f)
        val width = size.width * .50f
        val height = size.height * .64f
        drawOval(
            Brush.radialGradient(
                listOf(Color(0xffffe99a), Color(0xffea7944), Color(0xff913553), Color(0x006d2b58)),
                center, minOf(width, height) * .53f),
            Offset(center.x - width / 2, center.y - height / 2), Size(width, height))
        val arm = 9.dp.toPx()
        drawLine(Color.White, center - Offset(arm, 0f), center + Offset(arm, 0f), 1.5.dp.toPx())
        drawLine(Color.White, center - Offset(0f, arm), center + Offset(0f, arm), 1.5.dp.toPx())
    }
}
