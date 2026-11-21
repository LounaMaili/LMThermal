package org.lmthermal.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.lmthermal.camera.*
import org.lmthermal.core.DisplayPosition
import org.lmthermal.core.ImageCoordinateMapper
import org.lmthermal.core.NativePixel
import org.lmthermal.core.NativeRect

/** Touch and every marker use the same supplied native geometry and centered Fit rectangle. */
@Composable
internal fun ThermalImage(source: CameraSessionState<Bitmap>, rendered: CelsiusPresentationSnapshot,
    measurement: ThermalMeasurement?, pixel: NativePixel?, select: (NativePixel) -> Unit,
    mode: InspectionMode, roi: NativeRect?, selectRoi: (NativeRect) -> Unit, modifier: Modifier) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val geometry = source.geometry
    val mapper = remember(viewport, geometry) { if (geometry != null && viewport.width > 0 && viewport.height > 0)
        ImageCoordinateMapper(geometry, viewport.width.toDouble(), viewport.height.toDouble()) else null }
    val bitmap = if (measurement != null) rendered.bitmap else source.preview?.image
    val inspectionEnabled = CameraUiPolicy.canInspect(source) && measurement != null
    // The mode/geometry keys cancel obsolete drags; live frame arrivals do not restart the gesture.
    Box(modifier.background(Color(0xFF080D12), RoundedCornerShape(16.dp)).testTag("thermal-image").onSizeChanged { viewport = it }.pointerInput(mapper, inspectionEnabled, mode,
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
        else Column(Modifier.align(Alignment.Center).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (source.measurement != null && rendered.error == null)
                R.string.measurement_rendering else R.string.camera_empty_title), style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center)
            Text(stringResource(if (source.lifecycle == CameraLifecycle.STREAMING) R.string.measurement_unavailable
                else R.string.camera_empty_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
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
