package org.lmthermal.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.lmthermal.camera.*
import org.lmthermal.core.*

/** Status remains readable without opening diagnostics; raw counters and protocol details stay secondary. */
@Composable
internal fun CameraSummary(source: CameraSessionState<Bitmap>, ui: CameraUiBindings) {
    val actionableError = source.error?.takeUnless { it.code == CameraErrorCode.CAMERA_NOT_FOUND }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(cameraStatusText(source, ui), style = MaterialTheme.typography.titleMedium,
            color = when {
                actionableError != null -> MaterialTheme.colorScheme.error
                source.module == null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            })
        actionableError?.let { Text(stringResource(cameraErrorResource(it.code)), style = MaterialTheme.typography.bodyMedium) }
        if (source.module != null) {
            Text(stringResource(ui.modelLabel) + (source.geometry?.let {
                " · " + stringResource(R.string.camera_image_info, it.width, it.height, source.statistics.callbackFps)
            } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Values always come from the current coherent measurement/ROI result, never from preview colors. */
@Composable
internal fun MeasurementSummary(measurement: ThermalMeasurement?, pixel: NativePixel?, mode: InspectionMode,
    rect: NativeRect?, statistics: RoiStatistics?) {
    val reading = CursorInspection.read(pixel, measurement)
    val roiMode = mode == InspectionMode.ROI
    val low = if (roiMode) statistics?.minC else measurement?.low?.celsius
    val high = if (roiMode) statistics?.maxC else measurement?.high?.celsius
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp).testTag(if (roiMode) "roi-readings" else "point-readings"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Enlarged fonts use vertical metrics rather than squeezing three unreadable numbers into a row.
            val stacked = LocalDensity.current.fontScale > 1.3f
            val metrics: @Composable RowScope.() -> Unit = {
                TemperatureMetric(if (roiMode) R.string.measurement_mean else R.string.measurement_mode_point,
                    if (roiMode) statistics?.meanC else reading?.celsius?.toDouble(), Modifier.weight(1f), "selected-temperature")
                TemperatureMetric(R.string.measurement_low, low?.toDouble(), Modifier.weight(1f), "minimum-temperature")
                TemperatureMetric(R.string.measurement_high, high?.toDouble(), Modifier.weight(1f), "maximum-temperature")
            }
            if (stacked) {
                TemperatureMetric(if (roiMode) R.string.measurement_mean else R.string.measurement_mode_point,
                    if (roiMode) statistics?.meanC else reading?.celsius?.toDouble(), Modifier.fillMaxWidth(), "selected-temperature")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TemperatureMetric(R.string.measurement_low, low?.toDouble(), Modifier.weight(1f), "minimum-temperature")
                    TemperatureMetric(R.string.measurement_high, high?.toDouble(), Modifier.weight(1f), "maximum-temperature")
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = metrics)
            val detail = if (roiMode) {
                if (rect == null) stringResource(R.string.measurement_roi_hint)
                else stringResource(R.string.measurement_roi_size, rect.width, rect.height) + " · " +
                    (statistics?.let { pluralStringResource(R.plurals.measurement_roi_valid_pixels,
                        it.validPixelCount, it.validPixelCount, it.pixelCount) } ?: stringResource(R.string.measurement_roi_unavailable))
            } else if (measurement == null) stringResource(R.string.measurement_unavailable)
            else if (pixel != null) stringResource(R.string.measurement_point_position, pixel.x, pixel.y)
                else stringResource(R.string.measurement_inspect_hint)
            // Reserve two lines before a gesture; changes in counts and coordinates cannot move the buttons.
            Text(detail, style = MaterialTheme.typography.bodySmall, minLines = 2, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Empty slots explicitly mean unavailable; zero must never stand in for a missing temperature. */
@Composable
private fun TemperatureMetric(label: Int, value: Double?, modifier: Modifier, tag: String) {
    Column(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(value?.let { stringResource(R.string.measurement_value_celsius, it) } ?: stringResource(R.string.measurement_no_value),
            Modifier.testTag(tag), style = MaterialTheme.typography.titleMedium, minLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1,
            maxLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1)
    }
}

/** Always reserved, including gaps: validity changes never resize the fitted image during a gesture. */
@Composable
internal fun ThermalLegend(rendered: CelsiusPresentationSnapshot?) {
    val line = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    Column(Modifier.fillMaxWidth().height(16.dp + line).padding(top = 6.dp)) {
        if (rendered?.range != null && rendered.legend != null) {
            Image(rendered.legend.asImageBitmap(), stringResource(R.string.measurement_celsius_scale_description),
                Modifier.fillMaxWidth().height(6.dp), contentScale = ContentScale.FillBounds)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Endpoints and midpoint remain legible with large fonts; the exact range is also in Appearance.
                listOf(rendered.range.lower, (rendered.range.lower + rendered.range.upper) / 2, rendered.range.upper).forEach {
                    Text(stringResource(R.string.measurement_value_celsius, it), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
