package org.lmthermal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lmthermal.camera.*
import org.lmthermal.core.NativeRect

/** Presentation-only destinations survive rotation; the existing ViewModel owns the camera and all measurements. */
internal enum class ThermalPanel { APPEARANCE, SETTINGS, CAPTURE }

/** Fixed image workspace with a separately scrollable command area. Neither ROI results nor export phases
 * participate in viewport sizing, so a held pointer keeps the same native mapping across live frames.
 */
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
    var panel by rememberSaveable { mutableStateOf<ThermalPanel?>(null) }
    val ui = camera.uiFor(source.module?.id)
    val measurement = rendered.measurement?.takeIf {
        source.measurement != null && it.geometry == source.geometry && it.provenance.moduleId == source.module?.id
    }
    val roi = roiSelection.rect?.takeIf { roiSelection.geometry == source.geometry &&
        roiSelection.source?.moduleId == source.module?.id && roiSelection.source?.modelId == source.module?.modelId &&
        roiSelection.source?.deviceKey == source.device?.key }
    val roiStatistics = roiFrame.statistics?.takeIf { measurement != null &&
        roiFrame.measurement === measurement && roiFrame.selection == roiSelection && roi != null }
    val selectRoi: (NativeRect) -> Unit = { presenter.roi.select(it, roiSourceKey(source), source.geometry) }
    // A stable, high-contrast scheme avoids dynamic color altering the apparent thermal palette surround.
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF79E0CF), onPrimary = Color(0xFF00382F),
        background = Color(0xFF10171E), surface = Color(0xFF10171E), surfaceContainer = Color(0xFF1C2731),
        secondaryContainer = Color(0xFF23483F), onSecondaryContainer = Color(0xFFB4F3DE),
        onSurface = Color(0xFFEDF3F8), onSurfaceVariant = Color(0xFFB8C6D2), outline = Color(0xFF71838F))) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { panel = ThermalPanel.SETTINGS }, modifier = Modifier.heightIn(min = 48.dp).testTag("settings")) {
                        Text(stringResource(R.string.app_settings))
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val landscape = maxWidth > maxHeight
                    val railWidth = (maxWidth * 0.38f).coerceIn(240.dp, 360.dp)
                    val portraitControlsHeight = maxHeight * 0.46f
                    val pinReadings = LocalDensity.current.fontScale <= 1.3f &&
                        (if (landscape) maxHeight else portraitControlsHeight) >= 220.dp
                    val controls: @Composable ColumnScope.() -> Unit = {
                        if (pinReadings && CameraUiPolicy.showTemperatureControls(source)) MeasurementSummary(measurement, pixel, mode, roi, roiStatistics)
                        Column(Modifier.weight(1f).fillMaxWidth().testTag("command-area")
                            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CameraSummary(source, ui)
                            if (!pinReadings && CameraUiPolicy.showTemperatureControls(source)) MeasurementSummary(measurement, pixel, mode, roi, roiStatistics)
                            PrimaryControls(source, settings, presenter, camera, connect, ui, exporter,
                                openAppearance = { panel = ThermalPanel.APPEARANCE }, openCapture = { panel = ThermalPanel.CAPTURE })
                        }
                    }
                    val viewer: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier) {
                            ThermalImage(source, rendered, measurement, pixel, presenter::select, mode, roi,
                                selectRoi, Modifier.weight(1f).fillMaxWidth())
                            ThermalLegend(if (measurement != null) rendered else null)
                        }
                    }
                    if (landscape) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            viewer(Modifier.weight(1f).fillMaxHeight())
                            Column(Modifier.width(railWidth).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) { controls() }
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            viewer(Modifier.weight(1f).fillMaxWidth())
                            // Keep this fraction independent of text wrapping, selection and camera state.
                            Column(Modifier.height(portraitControlsHeight).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { controls() }
                        }
                    }
                }
                panel?.let { destination ->
                    ThermalPanelContent(destination, source, settings, camera, presenter, ui, exporter,
                        chooseDestination, share, dismiss = { panel = null })
                }
            }
        }
    }
}
