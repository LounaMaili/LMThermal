package org.lmthermal.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lmthermal.camera.*
import org.lmthermal.core.*
import org.lmthermal.exchange.CaptureFreeze

/** Keep common actions close to the image; actual capability/action gates still belong to CameraUiPolicy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrimaryControls(source: CameraSessionState<Bitmap>, settings: CelsiusPresentationSettings,
    presenter: CelsiusPresenter, camera: AndroidCameraCoordinator, connect: () -> Unit, ui: CameraUiBindings,
    exporter: CaptureExporter?, openAppearance: () -> Unit, openCapture: () -> Unit) {
    val streaming = source.lifecycle == CameraLifecycle.STREAMING
    if (streaming && CameraUiPolicy.showTemperatureControls(source) && source.capabilities?.touchInspection == true) {
        val mode by presenter.roi.mode.collectAsState()
        val selection by presenter.roi.selection.collectAsState()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == InspectionMode.POINT, onClick = { presenter.roi.setMode(InspectionMode.POINT) },
                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(R.string.measurement_mode_point)) })
            FilterChip(selected = mode == InspectionMode.ROI, onClick = { presenter.roi.setMode(InspectionMode.ROI) },
                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(R.string.measurement_mode_roi)) })
            if (mode == InspectionMode.ROI) TextButton(onClick = presenter.roi::clear, enabled = selection.rect != null,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.measurement_roi_clear)) }
        }
    }
    if (streaming && CameraUiPolicy.showTemperatureControls(source)) {
        OutlinedButton(onClick = openAppearance, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("appearance")) {
            Text(stringResource(R.string.measurement_appearance_summary, stringResource(PaletteResources.label(settings.palette)),
                stringResource(if (settings.automatic) R.string.measurement_auto else R.string.measurement_locked)))
        }
    }
    exporter?.let {
        val state by it.state.collectAsState()
        val busy = state.phase in setOf(ExportPhase.PREPARING, ExportPhase.READY, ExportPhase.WRITING)
        if (streaming || state.phase != ExportPhase.IDLE) Button(onClick = {
            if (!busy && CaptureFreeze.canCapture(camera.state.value))
                it.prepare(camera.state.value, presenter.roi.selection.value, presenter.settings.value, presenter.pixel.value)
            openCapture()
        }, enabled = CaptureFreeze.canCapture(source) || state.phase != ExportPhase.IDLE,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("capture")) {
            Text(stringResource(when (state.phase) {
                ExportPhase.READY -> R.string.export_destination
                ExportPhase.PREPARING, ExportPhase.WRITING -> R.string.export_progress
                else -> R.string.export_save
            }))
        }
    }
    if (source.lifecycle == CameraLifecycle.STREAMING && CameraUiPolicy.showInitialize(source) &&
        source.status.code != CameraStatusCode.MEASUREMENT_READY) {
        Button(onClick = { camera.perform(CameraAction.INITIALIZE_MEASUREMENT) },
            enabled = CameraUiPolicy.canPerform(source, CameraAction.INITIALIZE_MEASUREMENT),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(ui.initializeLabel)) }
    }
    val canClose = CameraUiPolicy.canPerform(source, CameraAction.CLOSE)
    OutlinedButton(onClick = if (canClose) camera::close else connect,
        enabled = canClose || CameraUiPolicy.canPerform(source, CameraAction.CONNECT),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("connection")) {
        Text(stringResource(if (canClose) R.string.camera_close else R.string.camera_connect))
    }
    if (source.lifecycle in setOf(CameraLifecycle.OPENING, CameraLifecycle.PERMISSION_PENDING) ||
        source.status.code == CameraStatusCode.INITIALIZING) LinearProgressIndicator(Modifier.fillMaxWidth())
    ui.accuracyWarning?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** In-app secondary panels do not start/stop a session. Only explicit callbacks enter the existing owners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThermalPanelContent(panel: ThermalPanel, source: CameraSessionState<Bitmap>, settings: CelsiusPresentationSettings,
    camera: AndroidCameraCoordinator, presenter: CelsiusPresenter, ui: CameraUiBindings, exporter: CaptureExporter?,
    chooseDestination: () -> Unit, share: () -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(when (panel) {
                    ThermalPanel.APPEARANCE -> R.string.measurement_appearance
                    ThermalPanel.SETTINGS -> R.string.app_settings
                    ThermalPanel.CAPTURE -> R.string.export_save
                }), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("dismiss-panel")) {
                    Text(stringResource(R.string.app_done))
                }
            }
            when (panel) {
                ThermalPanel.APPEARANCE -> if (CameraUiPolicy.showTemperatureControls(source)) AppearanceControls(settings, presenter)
                    else Text(stringResource(R.string.measurement_unavailable))
                ThermalPanel.SETTINGS -> {
                    LanguageSelector()
                    CameraSummary(source, ui)
                    HorizontalDivider()
                    Text(stringResource(R.string.camera_diagnostics), style = MaterialTheme.typography.titleMedium)
                    source.module?.let { module -> source.geometry?.let { geometry -> Text(stringResource(R.string.camera_module_metadata,
                        module.id.value, module.modelId, geometry.width, geometry.height)) } }
                    Text(stringResource(R.string.camera_diagnostic_counts, source.statistics.received, source.statistics.replaced, source.statistics.malformed))
                    // Preserve native sample and detailed coordinates as diagnostics, rather than primary measurements.
                    val rendered by presenter.state.collectAsState()
                    val pixel by presenter.pixel.collectAsState()
                    val reading = CursorInspection.read(pixel, rendered.measurement?.takeIf { source.measurement != null })
                    reading?.sample?.let { sample -> Text(stringResource(R.string.measurement_cursor_sample_temperature,
                        reading.pixel.x, reading.pixel.y, stringResource(ui.sampleLabel(sample.encodingId)), sample.value, reading.celsius)) }
                    val diagnostics by camera.diagnostics.collectAsState()
                    diagnostics?.invoke()
                    ui.accuracyWarning?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                }
                ThermalPanel.CAPTURE -> exporter?.let { CapturePanel(it, source, chooseDestination, share) }
            }
        }
    }
}

/** Color-only settings reuse the existing presenter and strict localized range editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceControls(settings: CelsiusPresentationSettings, presenter: CelsiusPresenter) {
    var rangeOpen by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.measurement_palette_title), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CelsiusPalette.entries.forEach { palette ->
            FilterChip(selected = settings.palette == palette, onClick = { presenter.setPalette(palette) },
                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(PaletteResources.label(palette))) })
        }
    }
    Text(stringResource(R.string.measurement_range_title), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = settings.automatic, onClick = { presenter.setAutomatic(true) },
            modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(R.string.measurement_auto)) })
        FilterChip(selected = !settings.automatic, onClick = { presenter.setAutomatic(false) },
            modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(R.string.measurement_locked)) })
    }
    OutlinedButton(onClick = { rangeOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.measurement_set_range, settings.locked.lower, settings.locked.upper))
    }
    Text(stringResource(R.string.measurement_color_only_hint), style = MaterialTheme.typography.bodyMedium)
    if (rangeOpen) RangeDialog(settings.locked, { rangeOpen = false }) { presenter.setLocked(it); rangeOpen = false }
}

/** Frozen captures stay in the existing exporter across panel dismissal, rotation and the registered SAF picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapturePanel(exporter: CaptureExporter, source: CameraSessionState<Bitmap>, chooseDestination: () -> Unit, share: () -> Unit) {
    val state by exporter.state.collectAsState()
    val message = when (state.phase) {
        ExportPhase.IDLE -> if (!CaptureFreeze.canCapture(source)) R.string.export_unavailable
            else if (source.measurement == null) R.string.export_preview_only else R.string.export_available
        ExportPhase.PREPARING, ExportPhase.WRITING -> R.string.export_progress
        ExportPhase.READY -> R.string.export_prepared
        ExportPhase.SUCCESS -> R.string.export_success
        ExportPhase.CANCELLED -> R.string.export_cancelled
        ExportPhase.ERROR -> if (state.errorId == "resource_limit") R.string.export_resource_error else R.string.export_write_error
    }
    Text(stringResource(message), modifier = Modifier.testTag("capture-status"))
    // Describe the frozen artifact, not a newer live frame that may have become radiometric meanwhile.
    if (state.phase in setOf(ExportPhase.READY, ExportPhase.WRITING, ExportPhase.SUCCESS) && !state.radiometric)
        Text(stringResource(R.string.export_preview_only), style = MaterialTheme.typography.bodyMedium)
    if (state.partialCleanupFailed) Text(stringResource(R.string.export_partial_remaining), color = MaterialTheme.colorScheme.error)
    if (state.phase in setOf(ExportPhase.PREPARING, ExportPhase.WRITING)) LinearProgressIndicator(Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.phase == ExportPhase.READY) Button(onClick = chooseDestination, modifier = Modifier.heightIn(min = 48.dp).testTag("capture-destination")) {
            Text(stringResource(R.string.export_destination))
        }
        if (state.phase in setOf(ExportPhase.PREPARING, ExportPhase.READY, ExportPhase.WRITING))
            TextButton(onClick = exporter::cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.export_cancel)) }
        if (state.phase == ExportPhase.SUCCESS) Button(onClick = share, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.export_share))
        }
    }
    Text(stringResource(R.string.export_privacy), style = MaterialTheme.typography.bodySmall)
}
