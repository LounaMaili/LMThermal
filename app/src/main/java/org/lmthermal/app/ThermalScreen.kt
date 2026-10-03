package org.lmthermal.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.lmthermal.camera.*
import org.lmthermal.core.CelsiusPalette
import org.lmthermal.core.CelsiusPresentationSettings
import org.lmthermal.core.CelsiusRange
import org.lmthermal.core.CursorInspection
import org.lmthermal.core.DisplayPosition
import org.lmthermal.core.ImageCoordinateMapper
import org.lmthermal.core.NativePixel
import org.lmthermal.core.NativeRect
import org.lmthermal.core.RoiStatistics

/** Shared camera presentation consumes capabilities/geometry; no protocol, raw encoding or model branch is needed. */
@Composable
fun ThermalScreen(camera: AndroidCameraCoordinator, presenter: CelsiusPresenter, connect: () -> Unit,
    exporter: CaptureExporter? = null, chooseDestination: () -> Unit = {}, share: () -> Unit = {}) {
    val source by camera.state.collectAsState()
    val rendered by presenter.state.collectAsState()
    val settings by presenter.settings.collectAsState()
    val pixel by presenter.pixel.collectAsState()
    val roiSelection by presenter.roi.selection.collectAsState()
    val roiFrame by presenter.roi.state.collectAsState()
    val mode by presenter.roi.mode.collectAsState()
    val ui = camera.uiFor(source.module?.id)
    val measurement = rendered.measurement?.takeIf {
        source.measurement != null && it.geometry == source.geometry && it.provenance.moduleId == source.module?.id
    }
    val roi = roiSelection.rect?.takeIf { roiSelection.geometry == source.geometry &&
        roiSelection.source?.moduleId == source.module?.id && roiSelection.source?.modelId == source.module?.modelId &&
        roiSelection.source?.deviceKey == source.device?.key }
    val roiStatistics = roiFrame.statistics?.takeIf { measurement != null &&
        roiFrame.measurement === measurement && roiFrame.selection == roiSelection && roi != null }
    val roiPanelVisible = roi != null || (mode == InspectionMode.ROI && source.capabilities?.touchInspection == true &&
        source.capabilities?.temperatureMeasurement == true)
    val selectRoi: (NativeRect) -> Unit = { presenter.roi.select(it, roiSourceKey(source), source.geometry) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.safeDrawingPadding().padding(12.dp)) {
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(2f).fillMaxHeight()) {
                            ThermalImage(source, rendered, measurement, pixel, presenter::select, mode, roi,
                                selectRoi, Modifier.weight(1f).fillMaxWidth())
                            CelsiusLegend(if (measurement != null) rendered else null, roiPanelVisible)
                            Readings(measurement, pixel, ui, mode == InspectionMode.POINT && source.capabilities?.touchInspection == true)
                            RoiReadings(roi, roiStatistics, roiPanelVisible)
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScreenControls(source, settings, camera, presenter, connect, ui, exporter, chooseDestination, share)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.app_title_status, cameraStatusText(source, ui)), style = MaterialTheme.typography.titleLarge)
                        val imageModifier = source.geometry?.let { Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height) }
                            ?: Modifier.fillMaxWidth().height(220.dp)
                        ThermalImage(source, rendered, measurement, pixel, presenter::select, mode, roi, selectRoi, imageModifier)
                        CelsiusLegend(if (measurement != null) rendered else null, roiPanelVisible)
                        Readings(measurement, pixel, ui, mode == InspectionMode.POINT && source.capabilities?.touchInspection == true)
                        RoiReadings(roi, roiStatistics, roiPanelVisible)
                        ScreenControls(source, settings, camera, presenter, connect, ui, exporter, chooseDestination, share)
                    }
                }
            }
        }
    }
}

/** Touch and every marker use the same supplied native geometry and centered Fit rectangle. */
@Composable
private fun ThermalImage(source: CameraSessionState<Bitmap>, rendered: CelsiusPresentationSnapshot,
    measurement: ThermalMeasurement?, pixel: NativePixel?, select: (NativePixel) -> Unit,
    mode: InspectionMode, roi: NativeRect?, selectRoi: (NativeRect) -> Unit, modifier: Modifier) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val geometry = source.geometry
    val mapper = remember(viewport, geometry) { if (geometry != null && viewport.width > 0 && viewport.height > 0)
        ImageCoordinateMapper(geometry, viewport.width.toDouble(), viewport.height.toDouble()) else null }
    val bitmap = if (measurement != null) rendered.bitmap else source.preview?.image
    val inspectionEnabled = CameraUiPolicy.canInspect(source) && measurement != null
    // The mode/geometry keys cancel obsolete drags; live frame arrivals do not restart the gesture.
    Box(modifier.testTag("thermal-image").onSizeChanged { viewport = it }.pointerInput(mapper, inspectionEnabled, mode,
        roiSourceKey(source)) {
        if (!inspectionEnabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val first = mapper?.toNative(DisplayPosition(down.position.x.toDouble(), down.position.y.toDouble()))
            fun inspect(position: Offset) {
                val display = DisplayPosition(position.x.toDouble(), position.y.toDouble())
                if (mode == InspectionMode.POINT) mapper?.toNative(display)?.let(select)
                else if (first != null) mapper?.toNativeClamped(display)?.let { last ->
                    selectRoi(NativeRect.fromDrag(first, last, mapper.geometry))
                }
            }
            inspect(down.position); down.consume()
            var pressed = true
            while (pressed) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.pressed || mode == InspectionMode.ROI) inspect(change.position)
                change.consume(); pressed = change.pressed
            }
        }
    }) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.camera_native_image_description),
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(stringResource(if (source.measurement != null && rendered.error == null)
            R.string.measurement_rendering else R.string.measurement_unavailable))
        if (measurement != null && mapper != null) Canvas(Modifier.fillMaxSize()) {
            for ((point, color) in listOf(measurement.high to Color.Red, measurement.low to Color.Cyan)) {
                val position = mapper.toDisplay(point.pixel); val center = Offset(position.x.toFloat(), position.y.toFloat())
                drawCircle(Color.Black, 6.dp.toPx(), center, style = Stroke(4.dp.toPx()))
                drawCircle(color, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            pixel?.takeIf { mode == InspectionMode.POINT && geometry!!.contains(it) }?.let {
                val position = mapper.toDisplay(it); val center = Offset(position.x.toFloat(), position.y.toFloat())
                val arm = 9.dp.toPx()
                for (stroke in listOf(4.dp.toPx() to Color.Black, 2.dp.toPx() to Color.White)) {
                    drawLine(stroke.second, center - Offset(arm, 0f), center + Offset(arm, 0f), stroke.first)
                    drawLine(stroke.second, center - Offset(0f, arm), center + Offset(0f, arm), stroke.first)
                }
            }
        }
        if (roi != null && mapper != null && bitmap != null) Canvas(Modifier.fillMaxSize()) {
            val edges = mapper.toDisplay(roi)
            val topLeft = Offset(edges.left.toFloat(), edges.top.toFloat())
            val rectSize = androidx.compose.ui.geometry.Size(edges.width.toFloat(), edges.height.toFloat())
            drawRect(Color.Black, topLeft, rectSize, style = Stroke(3.dp.toPx()))
            drawRect(Color.Yellow, topLeft, rectSize, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Font-scaled fixed slots exist before pointer-down and through gaps/clear.
 * Inserting/wrapping readouts during a drag otherwise moves controls and resizes the landscape viewport,
 * cancelling the mapper-keyed gesture. Only a coherent current result supplies numbers.
 */
@Composable
private fun RoiReadings(rect: NativeRect?, statistics: RoiStatistics?, visible: Boolean) {
    if (!visible) return
    val body = MaterialTheme.typography.bodyLarge
    val lineHeight = with(LocalDensity.current) { body.lineHeight.toDp() }
    Column(Modifier.testTag("roi-readings")) {
        Text(if (rect == null) stringResource(R.string.measurement_mode_roi)
            else stringResource(R.string.measurement_roi_size, rect.width, rect.height),
            Modifier.height(lineHeight), style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val readings = when {
            rect == null -> stringResource(R.string.measurement_roi_hint)
            statistics != null && statistics.validPixelCount > 0 -> stringResource(R.string.measurement_roi_statistics,
                statistics.minC!!, statistics.maxC!!, statistics.meanC!!)
            else -> stringResource(R.string.measurement_roi_unavailable)
        }
        Text(readings, Modifier.height(lineHeight * 2), style = body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.height(lineHeight * 2)) {
            statistics?.let {
                Text(pluralStringResource(R.plurals.measurement_roi_valid_pixels, it.validPixelCount,
                    it.validPixelCount, it.pixelCount), style = body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CelsiusLegend(rendered: CelsiusPresentationSnapshot?, reserveSpace: Boolean = false) {
    val tickHeight = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() } * 2
    val content: @Composable () -> Unit = {
        if (rendered?.range != null && rendered.legend != null) {
            Image(rendered.legend.asImageBitmap(), stringResource(R.string.measurement_celsius_scale_description),
                Modifier.fillMaxWidth().height(12.dp), contentScale = ContentScale.FillBounds)
            Row(Modifier.fillMaxWidth().then(if (reserveSpace) Modifier.height(tickHeight) else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween) {
                rendered.range.ticks().forEach { Text(stringResource(R.string.measurement_legend_tick, it), style = MaterialTheme.typography.labelSmall) }
            }
            Text(stringResource(R.string.measurement_range_label, stringResource(PaletteResources.label(rendered.palette!!)),
                rendered.range.lower, rendered.range.upper),
                if (reserveSpace) Modifier.height(labelHeight) else Modifier,
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (reserveSpace) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        }
    }
    // Keep a single parent slot on gaps too; varying sibling counts also changes the portrait Column's spacing.
    // Empty space replaces unavailable content, never a stale Celsius legend.
    if (reserveSpace) Column(Modifier.height(12.dp + tickHeight + labelHeight)) { content() }
    else content()
}

@Composable
private fun Readings(measurement: ThermalMeasurement?, pixel: NativePixel?, ui: CameraUiBindings, inspectionSupported: Boolean) {
    if (!inspectionSupported) return
    val reading = CursorInspection.read(pixel, measurement)
    val sample = reading?.sample
    val text = when {
        sample != null -> stringResource(R.string.measurement_cursor_sample_temperature,
            reading.pixel.x, reading.pixel.y, stringResource(ui.sampleLabel(sample.encodingId)), sample.value, reading.celsius)
        reading != null -> stringResource(R.string.measurement_cursor_temperature, reading.pixel.x, reading.pixel.y, reading.celsius)
        pixel != null -> stringResource(R.string.measurement_cursor_unavailable, pixel.x, pixel.y)
        else -> stringResource(R.string.measurement_inspect_hint)
    }
    Text(text, style = MaterialTheme.typography.titleMedium)
    measurement?.let { Text(stringResource(R.string.measurement_extrema, it.high.celsius, it.high.pixel.x, it.high.pixel.y,
        it.low.celsius, it.low.pixel.x, it.low.pixel.y)) }
}

/** Capabilities control visibility, while supported actions control enablement. Module diagnostics are optional extensions. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ScreenControls(source: CameraSessionState<Bitmap>, settings: CelsiusPresentationSettings,
    camera: AndroidCameraCoordinator, presenter: CelsiusPresenter, connect: () -> Unit, ui: CameraUiBindings,
    exporter: CaptureExporter?, chooseDestination: () -> Unit, share: () -> Unit) {
    var palettesOpen by remember { mutableStateOf(false) }
    var rangeOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    exporter?.let { CaptureControls(it, source, { camera.state.value }, presenter, chooseDestination, share) }
    if (CameraUiPolicy.showTemperatureControls(source)) {
        if (source.capabilities?.touchInspection == true) {
            val mode by presenter.roi.mode.collectAsState()
            val selection by presenter.roi.selection.collectAsState()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == InspectionMode.POINT, onClick = { presenter.roi.setMode(InspectionMode.POINT) },
                    label = { Text(stringResource(R.string.measurement_mode_point)) })
                FilterChip(selected = mode == InspectionMode.ROI, onClick = { presenter.roi.setMode(InspectionMode.ROI) },
                    label = { Text(stringResource(R.string.measurement_mode_roi)) })
                TextButton(onClick = presenter.roi::clear, enabled = selection.rect != null) {
                    Text(stringResource(R.string.measurement_roi_clear))
                }
            }
        }
        Box {
            OutlinedButton(onClick = { palettesOpen = true }) { Text(stringResource(R.string.measurement_palette,
                stringResource(PaletteResources.label(settings.palette)))) }
            DropdownMenu(expanded = palettesOpen, onDismissRequest = { palettesOpen = false }) {
                CelsiusPalette.entries.forEach { palette -> DropdownMenuItem(text = { Text(stringResource(PaletteResources.label(palette))) },
                    onClick = { presenter.setPalette(palette); palettesOpen = false }) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = settings.automatic, onClick = { presenter.setAutomatic(true) }, label = { Text(stringResource(R.string.measurement_auto)) })
            FilterChip(selected = !settings.automatic, onClick = { presenter.setAutomatic(false) }, label = { Text(stringResource(R.string.measurement_locked)) })
        }
        OutlinedButton(onClick = { rangeOpen = true }) { Text(stringResource(R.string.measurement_set_range, settings.locked.lower, settings.locked.upper)) }
        ui.accuracyWarning?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
    }
    HorizontalDivider()
    Text(stringResource(ui.modelLabel)); Text(cameraStatusText(source, ui))
    source.error?.let { Text(stringResource(cameraErrorResource(it.code))) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = connect, enabled = CameraUiPolicy.canPerform(source, CameraAction.CONNECT)) { Text(stringResource(R.string.camera_connect)) }
        OutlinedButton(onClick = camera::close, enabled = CameraUiPolicy.canPerform(source, CameraAction.CLOSE)) { Text(stringResource(R.string.camera_close)) }
    }
    if (CameraUiPolicy.showInitialize(source)) Button(onClick = { camera.perform(CameraAction.INITIALIZE_MEASUREMENT) },
        enabled = CameraUiPolicy.canPerform(source, CameraAction.INITIALIZE_MEASUREMENT)) { Text(stringResource(ui.initializeLabel)) }
    Text(stringResource(R.string.camera_callback_fps, source.statistics.callbackFps), style = MaterialTheme.typography.labelMedium)
    LanguageSelector()
    TextButton(onClick = { diagnosticsOpen = !diagnosticsOpen }) { Text(stringResource(
        if (diagnosticsOpen) R.string.camera_hide_diagnostics else R.string.camera_diagnostics)) }
    if (diagnosticsOpen) {
        source.module?.let { module -> source.geometry?.let { geometry -> Text(stringResource(R.string.camera_module_metadata,
            module.id.value, module.modelId, geometry.width, geometry.height)) } }
        Text(stringResource(R.string.camera_diagnostic_counts, source.statistics.received, source.statistics.replaced, source.statistics.malformed))
        val diagnostics by camera.diagnostics.collectAsState()
        diagnostics?.invoke()
    }
    if (rangeOpen && CameraUiPolicy.showTemperatureControls(source)) RangeDialog(settings.locked, { rangeOpen = false }) {
        presenter.setLocked(it); rangeOpen = false
    }
}

/** Invalid text leaves the current range untouched. Resources supply messages, not core exception text. */
@Composable
private fun RangeDialog(initial: CelsiusRange, dismiss: () -> Unit, apply: (CelsiusRange) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var lower by remember { mutableStateOf(editableCelsius(initial.lower, locale)) }
    var upper by remember { mutableStateOf(editableCelsius(initial.upper, locale)) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.measurement_range_dialog)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lower, onValueChange = { lower = it }, label = { Text(stringResource(R.string.measurement_minimum)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(upper, onValueChange = { upper = it }, label = { Text(stringResource(R.string.measurement_maximum)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            if (invalid) Text(stringResource(R.string.measurement_invalid_range), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val minimum = parseEditableCelsius(lower, locale); val maximum = parseEditableCelsius(upper, locale)
        val bounds = if (minimum != null && maximum != null) runCatching { CelsiusRange(minimum, maximum) }.getOrNull() else null
        if (bounds == null) invalid = true else apply(bounds)
    }) { Text(stringResource(R.string.measurement_apply)) } }, dismissButton = {
        TextButton(onClick = dismiss) { Text(stringResource(R.string.measurement_cancel)) }
    })
}

/** Fixed action/status slots avoid readout reflow during an ROI gesture or export phase change. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CaptureControls(exporter: CaptureExporter, source: CameraSessionState<Bitmap>, currentSource: () -> CameraSessionState<Bitmap>, presenter: CelsiusPresenter,
    chooseDestination: () -> Unit, share: () -> Unit) {
    val state by exporter.state.collectAsState()
    val canCapture = org.lmthermal.exchange.CaptureFreeze.canCapture(source)
    val busy = state.phase in setOf(ExportPhase.PREPARING, ExportPhase.READY, ExportPhase.WRITING)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = canCapture && !busy, onClick = {
            exporter.prepare(currentSource(), presenter.roi.selection.value, presenter.settings.value, presenter.pixel.value)
        }) { Text(stringResource(R.string.export_save)) }
        OutlinedButton(enabled = state.phase == ExportPhase.READY, onClick = chooseDestination) { Text(stringResource(R.string.export_destination)) }
        TextButton(enabled = busy, onClick = exporter::cancel) { Text(stringResource(R.string.export_cancel)) }
        TextButton(enabled = state.phase == ExportPhase.SUCCESS, onClick = share) { Text(stringResource(R.string.export_share)) }
    }
    val message = when (state.phase) {
        ExportPhase.IDLE -> if (!canCapture) R.string.export_unavailable else if (source.measurement == null) R.string.export_preview_only else R.string.export_available
        ExportPhase.PREPARING, ExportPhase.WRITING -> R.string.export_progress
        ExportPhase.READY -> R.string.export_prepared
        ExportPhase.SUCCESS -> R.string.export_success
        ExportPhase.CANCELLED -> R.string.export_cancelled
        ExportPhase.ERROR -> if (state.errorId == "resource_limit") R.string.export_resource_error else R.string.export_write_error
    }
    val line = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    Text(stringResource(message) + if (state.partialCleanupFailed) "\n" + stringResource(R.string.export_partial_remaining) else "",
        Modifier.height(line * 3), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Text(stringResource(R.string.export_privacy), Modifier.height(line * 3), style = MaterialTheme.typography.bodySmall,
        maxLines = 3, overflow = TextOverflow.Ellipsis)
}
