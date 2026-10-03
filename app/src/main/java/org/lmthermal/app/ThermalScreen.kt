package org.lmthermal.app

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.lmthermal.core.*

/** Responsive presentation only. Connection/session ownership remains in CameraController. */
@Composable
fun ThermalScreen(camera: CameraController, presenter: CelsiusPresenter, connect: () -> Unit) {
    val source by camera.state.collectAsState()
    val rendered by presenter.state.collectAsState()
    val settings by presenter.settings.collectAsState()
    val pixel by presenter.pixel.collectAsState()
    // A live unavailable state wins immediately, even while a render cancellation is propagating.
    val measurement = if (source.measurement != null) rendered.measurement else null
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.safeDrawingPadding().padding(12.dp)) {
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(2f).fillMaxHeight()) {
                            ThermalImage(source, rendered, measurement, pixel, presenter::select, Modifier.weight(1f).fillMaxWidth())
                            CelsiusLegend(if (measurement != null) rendered else null)
                            Readings(measurement, pixel)
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScreenControls(source, settings, camera, presenter, connect)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LMThermal · ${source.session.state}", style = MaterialTheme.typography.titleLarge)
                        ThermalImage(source, rendered, measurement, pixel, presenter::select,
                            Modifier.fillMaxWidth().aspectRatio(Ht301Layout.WIDTH.toFloat() / Ht301Layout.IMAGE_HEIGHT))
                        CelsiusLegend(if (measurement != null) rendered else null)
                        Readings(measurement, pixel)
                        ScreenControls(source, settings, camera, presenter, connect)
                    }
                }
            }
        }
    }
}

/** ContentScale.Fit and the shared mapper define the same centered image rectangle, including letterbox. */
@Composable
private fun ThermalImage(source: CameraSnapshot, rendered: CelsiusPresentationSnapshot,
    measurement: RadiometricMeasurement?, pixel: NativePixel?, select: (NativePixel) -> Unit, modifier: Modifier) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val mapper = remember(viewport) { if (viewport.width > 0 && viewport.height > 0)
        ImageCoordinateMapper(viewport.width.toDouble(), viewport.height.toDouble()) else null }
    val bitmap = if (measurement != null) rendered.bitmap else source.bitmap
    Box(modifier.onSizeChanged { viewport = it }.pointerInput(mapper) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun inspect(position: Offset) {
                mapper?.toNative(DisplayPosition(position.x.toDouble(), position.y.toDouble()))?.let(select)
            }
            inspect(down.position)
            // Consume drags initiated on the image so a parent scroll cannot steal inspection.
            down.consume()
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) break
                if (change.pressed) inspect(change.position)
                change.consume(); pressed = change.pressed
            }
        }
    }) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Thermal image in native sensor orientation",
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(if (source.measurement != null) "Rendering Celsius…" else "${source.usb.message} · temperatures unavailable")
        if (measurement != null && mapper != null) Canvas(Modifier.fillMaxSize()) {
            for ((point, color) in listOf(measurement.high to Color.Red, measurement.low to Color.Cyan)) {
                val position = mapper.toDisplay(NativePixel(point.x!!, point.y!!))
                val center = Offset(position.x.toFloat(), position.y.toFloat())
                drawCircle(Color.Black, 6.dp.toPx(), center, style = Stroke(4.dp.toPx()))
                drawCircle(color, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            pixel?.let {
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

/** A legend exists only alongside a valid rendered measurement; labels use exact display bounds. */
@Composable
private fun CelsiusLegend(rendered: CelsiusPresentationSnapshot?) {
    if (rendered?.range == null || rendered.legend == null) return
    Image(rendered.legend.asImageBitmap(), "Celsius color scale", Modifier.fillMaxWidth().height(12.dp), contentScale = ContentScale.FillBounds)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        rendered.range.ticks().forEach { Text("%.1f".format(it), style = MaterialTheme.typography.labelSmall) }
    }
    Text("${rendered.palette!!.label} · %.2f to %.2f °C".format(rendered.range.lower, rendered.range.upper), style = MaterialTheme.typography.labelMedium)
}

/** Pixel selection survives frame updates/unavailability; raw/Celsius always comes from the displayed measurement. */
@Composable
private fun Readings(measurement: RadiometricMeasurement?, pixel: NativePixel?) {
    val reading = CursorInspection.read(pixel, measurement)
    Text(if (reading != null) "(${reading.pixel.x}, ${reading.pixel.y}) · raw14 ${reading.raw14} · %.2f °C".format(reading.celsius)
        else if (pixel != null) "(${pixel.x}, ${pixel.y}) · temperature unavailable" else "Tap or drag on the image to inspect", style = MaterialTheme.typography.titleMedium)
    measurement?.let { Text("High %.2f °C (%d,%d) · Low %.2f °C (%d,%d)".format(it.high.celsius, it.high.x, it.high.y, it.low.celsius, it.low.x, it.low.y)) }
}

/** Modest grouping keeps normal presentation controls ahead of optional developer diagnostics. */
@Composable
private fun ScreenControls(source: CameraSnapshot, settings: CelsiusPresentationSettings,
    camera: CameraController, presenter: CelsiusPresenter, connect: () -> Unit) {
    var palettesOpen by remember { mutableStateOf(false) }
    var rangeOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { palettesOpen = true }) { Text("Palette: ${settings.palette.label}") }
        DropdownMenu(expanded = palettesOpen, onDismissRequest = { palettesOpen = false }) {
            CelsiusPalette.entries.forEach { palette -> DropdownMenuItem(text = { Text(palette.label) },
                onClick = { presenter.setPalette(palette); palettesOpen = false }) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = settings.automatic, onClick = { presenter.setAutomatic(true) }, label = { Text("Auto") })
        FilterChip(selected = !settings.automatic, onClick = { presenter.setAutomatic(false) }, label = { Text("Locked") })
    }
    OutlinedButton(onClick = { rangeOpen = true }) { Text("Set range: %.1f–%.1f °C".format(settings.locked.lower, settings.locked.upper)) }
    Text(NativeEquivalentThermometry.WARNING, style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Text(source.usb.message)
    Text("Session: ${source.session.state}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = connect) { Text("Connect / Open") }
        OutlinedButton(onClick = { camera.close() }) { Text("Close") }
    }
    Button(onClick = camera::initializeRadiometric, enabled = source.session.canInitialize && !source.transition.active) { Text("Initialize radiometric") }
    source.session.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text("%.1f callback FPS".format(source.fps), style = MaterialTheme.typography.labelMedium)
    TextButton(onClick = { diagnosticsOpen = !diagnosticsOpen }) { Text(if (diagnosticsOpen) "Hide diagnostics" else "Diagnostics") }
    if (diagnosticsOpen) {
        Text(source.identity); Text("USB: ${source.permission}")
        Text("${source.mode} · ${source.size} bytes · raw words ${source.range}")
        Text("Rejected ${source.invalid} · replaced ${source.replaced} · malformed ${source.malformed}")
        Text("Baseline ${source.session.baseline}/${RadiometricSession.BASELINE_FRAMES} · discarded ${source.session.discarded} · shutter ${source.session.shutterFrames}/${RadiometricSession.SHUTTER_DISCARD} · live ${source.session.live}/${RadiometricSession.READY_LIVE}")
        source.measurement?.let { Text("Trailer center %.3f °C · literal (192,144) %.3f °C".format(it.trailerCenter.celsius, it.literalCenter.celsius)) }
        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = camera::readZoomInventory,
                enabled = source.usb.phase == UsbPhase.STREAMING && !source.session.active && !source.transition.active) { Text("Read zoom inventory") }
            Text(source.inventory)
            OutlinedButton(onClick = camera::testRaw14Transition, enabled = source.transition.canStart && !source.session.active) { Text("Test raw14 transition (32772)") }
            Text("Single test: ${source.transition.stage} · discarded ${source.transition.discarded} · distinct ${source.transition.distinct}")
        }
    }
    if (rangeOpen) RangeDialog(settings.locked, { rangeOpen = false }) { presenter.setLocked(it); rangeOpen = false }
}

/** Invalid text/bounds leave the active display scale intact. Decimal comma is accepted for local keyboards. */
@Composable
private fun RangeDialog(initial: CelsiusRange, dismiss: () -> Unit, apply: (CelsiusRange) -> Unit) {
    var lower by remember { mutableStateOf(initial.lower.toString()) }
    var upper by remember { mutableStateOf(initial.upper.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Locked Celsius range") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lower, onValueChange = { lower = it }, label = { Text("Minimum °C") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(upper, onValueChange = { upper = it }, label = { Text("Maximum °C") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val minimum = lower.replace(',', '.').toDoubleOrNull(); val maximum = upper.replace(',', '.').toDoubleOrNull()
        val bounds = if (minimum != null && maximum != null) runCatching { CelsiusRange(minimum, maximum) }.getOrNull() else null
        if (bounds == null) error = "Use finite minimum < maximum" else apply(bounds)
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
