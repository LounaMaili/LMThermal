package org.lmthermal.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Camera-free real Compose layout: changing panels/readout length cannot move the image allocation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class WorkspaceLayoutTest {
    @get:Rule val compose=createComposeRule()
    private fun stableViewport(width:Int,height:Int,fontScale:Float=1f) {
        val detail=mutableStateOf("ROI unavailable")
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) { MaterialTheme { Box(Modifier.requiredSize(width.dp,height.dp)) {
            WorkspaceLayout("LMThermal",stage={Box(Modifier.fillMaxSize().testTag("test-image"))}) { section ->
                Text(section.name);Text(detail.value)
            }
        } } } }
        val original=compose.onNodeWithTag("test-image").fetchSemanticsNode().boundsInRoot
        assertTrue(original.width>0 && original.height>0)
        for(section in WorkspaceSection.entries) {
            compose.onNodeWithTag("workspace-tab-${section.tag}").performScrollTo().performClick()
            compose.runOnIdle {detail.value="Changing ROI statistics and qualification\n".repeat(40)}
            compose.onNodeWithTag("test-image").assertIsDisplayed()
            assertEquals(original,compose.onNodeWithTag("test-image").fetchSemanticsNode().boundsInRoot)
        }
    }
    @Test fun portraitImageDoesNotReflowWhenPanelsOrReadingsChange()=stableViewport(360,640)
    @Test fun landscapeImageDoesNotReflowWhenPanelsOrReadingsChange()=stableViewport(800,360)
    @Test @Config(qualifiers="fr") fun frenchLargeTextPortraitKeepsViewportStable()=stableViewport(360,640,2f)
    @Test @Config(qualifiers="fr") fun frenchLargeTextLandscapeKeepsViewportStable()=stableViewport(800,360,2f)
}

