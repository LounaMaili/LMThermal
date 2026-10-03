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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
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

/** Shared camera presentation consumes capabilities/geometry; no protocol, raw encoding or model branch is needed. */
@Composable
fun ThermalScreen(camera: AndroidCameraCoordinator, presenter: CelsiusPresenter, connect: () -> Unit) {
    val source by camera.state.collectAsState()
    val rendered by presenter.state.collectAsState()
    val settings by presenter.settings.collectAsState()
    val pixel by presenter.pixel.collectAsState()
    val ui = camera.uiFor(source.module?.id)
    val measurement = rendered.measurement?.takeIf {
        source.measurement != null && it.geometry == source.geometry && it.provenance.moduleId == source.module?.id
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.safeDrawingPadding().padding(12.dp)) {
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(2f).fillMaxHeight()) {
                            ThermalImage(source, rendered, measurement, pixel, presenter::select, Modifier.weight(1f).fillMaxWidth())
                            CelsiusLegend(if (measurement != null) rendered else null)
                            Readings(measurement, pixel, ui, source.capabilities?.touchInspection == true)
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScreenControls(source, settings, camera, presenter, connect, ui)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.app_title_status, cameraStatusText(source, ui)), style = MaterialTheme.typography.titleLarge)
                        val imageModifier = source.geometry?.let { Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height) }
                            ?: Modifier.fillMaxWidth().height(220.dp)
                        ThermalImage(source, rendered, measurement, pixel, presenter::select, imageModifier)
                        CelsiusLegend(if (measurement != null) rendered else null)
                        Readings(measurement, pixel, ui, source.capabilities?.touchInspection == true)
                        ScreenControls(source, settings, camera, presenter, connect, ui)
                    }
                }
            }
        }
    }
}

/** Touch and every marker use the same supplied native geometry and centered Fit rectangle. */
@Composable
private fun ThermalImage(source: CameraSessionState<Bitmap>, rendered: CelsiusPresentationSnapshot,
    measurement: ThermalMeasurement?, pixel: NativePixel?, select: (NativePixel) -> Unit, modifier: Modifier) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val geometry = source.geometry
    val mapper = remember(viewport, geometry) { if (geometry != null && viewport.width > 0 && viewport.height > 0)
        ImageCoordinateMapper(geometry, viewport.width.toDouble(), viewport.height.toDouble()) else null }
    val bitmap = if (measurement != null) rendered.bitmap else source.preview?.image
    val inspectionEnabled = CameraUiPolicy.canInspect(source) && measurement != null
    Box(modifier.onSizeChanged { viewport = it }.pointerInput(mapper, inspectionEnabled) {
        if (!inspectionEnabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun inspect(position: Offset) {
                mapper?.toNative(DisplayPosition(position.x.toDouble(), position.y.toDouble()))?.let(select)
            }
            inspect(down.position); down.consume()
            var pressed = true
            while (pressed) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.pressed) inspect(change.position)
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
            pixel?.takeIf { geometry!!.contains(it) }?.let {
                val position = mapper.toDisplay(it); val center = Offset(position.x.toFloat(), position.y.toFloat())
                val arm = 9.dp.toPx()
                for (stroke in listOf(4.dp.toPx() to Color.Black, 2.dp.toPx() to Color.White)) {
                    drawLine(stroke.second, center - Offset(arm, 0f), center + Offset(arm, 0f), stroke.first)
                    drawLine(stroke.second, center - Offset(0f, arm), center + Offset(0f, arm), stroke.first)
                }
            }
        }
    }
}

@Composable
private fun CelsiusLegend(rendered: CelsiusPresentationSnapshot?) {
    if (rendered?.range == null || rendered.legend == null) return
    Image(rendered.legend.asImageBitmap(), stringResource(R.string.measurement_celsius_scale_description),
        Modifier.fillMaxWidth().height(12.dp), contentScale = ContentScale.FillBounds)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        rendered.range.ticks().forEach { Text(stringResource(R.string.measurement_legend_tick, it), style = MaterialTheme.typography.labelSmall) }
    }
    Text(stringResource(R.string.measurement_range_label, stringResource(PaletteResources.label(rendered.palette!!)),
        rendered.range.lower, rendered.range.upper), style = MaterialTheme.typography.labelMedium)
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
    camera: AndroidCameraCoordinator, presenter: CelsiusPresenter, connect: () -> Unit, ui: CameraUiBindings) {
    var palettesOpen by remember { mutableStateOf(false) }
    var rangeOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    if (CameraUiPolicy.showTemperatureControls(source)) {
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
