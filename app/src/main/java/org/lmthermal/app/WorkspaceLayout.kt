package org.lmthermal.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Product navigation controls only presentation; selecting a section cannot open USB or initialize it. */
enum class WorkspaceSection(val label:Int,val tag:String) {
    LIVE(R.string.app_workspace_live,"live"), MEASURE(R.string.app_workspace_measure,"measure"),
    CAPTURE(R.string.app_workspace_capture,"capture"), SETTINGS(R.string.app_workspace_settings,"settings"),
    DIAGNOSTICS(R.string.app_workspace_diagnostics,"diagnostics")
}

/** The image has its own stable allocation, independent of readout/control reflow.
 * In portrait controls scroll BELOW it; in landscape they scroll beside it. A panel
 * change/ROI update cannot resize the thermal viewport during an active gesture.
 * A caller may replace stage content without importing any camera/session dependency.
 */
@Composable
internal fun WorkspaceLayout(title:String,stage:@Composable ()->Unit,
    controls:@Composable (WorkspaceSection)->Unit) {
    var selected by rememberSaveable { mutableStateOf(WorkspaceSection.LIVE) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge,maxLines=2)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            WorkspaceSection.entries.forEach { section ->
                FilterChip(selected=selected==section,onClick={selected=section},
                    label={Text(stringResource(section.label))},modifier=Modifier.testTag("workspace-tab-${section.tag}"))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape=maxWidth>maxHeight
            val panel:@Composable ()->Unit = {
                // Each panel owns its scroll state. Frame arrivals never replace the panel key.
                key(selected) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("workspace-panel"),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) { controls(selected) } }
            }
            if(landscape) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(2f).fillMaxHeight().testTag("workspace-stage")){stage()}
                Box(Modifier.weight(1f).fillMaxHeight()){panel()}
            } else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1.45f).fillMaxWidth().testTag("workspace-stage")){stage()}
                Box(Modifier.weight(1f).fillMaxWidth()){panel()}
            }
        }
    }
}
