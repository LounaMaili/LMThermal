package org.lmthermal.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301UiBindings

/** Camera-free product regressions: the coordinator receives synthetic identities, never a USB device. */
@RunWith(AndroidJUnit4::class)
class ThermalWorkspaceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** Normal application startup must make an unavailable camera explicit and keep secondary tools reachable. */
    @Test fun noCameraHasNoEnabledCaptureOrInitializationAndSettingsSurviveRecreation() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onNodeWithTag("thermal-image").assertIsDisplayed()
            compose.onNodeWithTag("capture").assertDoesNotExist()
            compose.onNodeWithTag("connection").assertIsNotEnabled()
            compose.onNodeWithTag("appearance").assertDoesNotExist()
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("dismiss-panel").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithTag("dismiss-panel").assertIsDisplayed().performClick()
            compose.onNodeWithTag("thermal-image").assertIsDisplayed()
            compose.onNodeWithTag("capture").assertDoesNotExist()
        } finally { scenario.close() }
    }

    /** State-driven actions must never initialize on open, panel navigation, permission wait or readiness. */
    @Test fun previewInitializationAndTransientGapsFollowExistingActionPolicy() = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val module = LocalizationLayoutDeviceTest.LayoutModule()
        lateinit var coordinator: AndroidCameraCoordinator
        lateinit var presenter: CelsiusPresenter
        lateinit var initialize: String
        scenario.onActivity { activity ->
            initialize = activity.getString(R.string.camera_ht301_initialize_radiometric)
            coordinator = AndroidCameraCoordinator(activity.applicationContext,
                listOf(AndroidModuleRegistration(module, Ht301UiBindings)), devices = {
                    listOf(object : CameraDeviceIdentity { override val key = "ux-state-test" })
                })
            presenter = CelsiusPresenter(activity.applicationContext, coordinator.state)
            coordinator.enterForeground()
            activity.setContent { ThermalScreen(coordinator, presenter, { coordinator.connect() }) }
        }
        try {
            compose.onNodeWithTag("connection").performScrollTo().assertIsEnabled().performClick()
            withTimeout(5000) { while (presenter.state.value.measurement == null) delay(10) }
            val ready = module.openedState.value
            assertEquals(0, module.initializationCount)
            compose.onNodeWithText(initialize).assertDoesNotExist()
            module.openedState.value = ready.copy(measurement = null, status = CameraStatus(CameraStatusCode.PREVIEW_ONLY))
            compose.waitForIdle()
            compose.onNodeWithText(initialize).performScrollTo().assertIsEnabled().performClick()
            withTimeout(5000) { while (module.initializationCount != 1) delay(10) }
            module.openedState.value = module.openedState.value.copy(status = CameraStatus(CameraStatusCode.INITIALIZING),
                actions = setOf(CameraAction.CLOSE))
            compose.waitForIdle()
            compose.onNodeWithText(initialize).assertIsNotEnabled()
            val bounds = compose.onNodeWithTag("thermal-image").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("dismiss-panel").performClick()
            assertEquals(1, module.initializationCount)
            module.openedState.value = ready
            withTimeout(5000) { while (presenter.state.value.measurement == null) delay(10) }
            compose.onNodeWithText(initialize).assertDoesNotExist()
            compose.onNodeWithTag("minimum-temperature").assertTextContains("24", substring = true)
            module.openedState.value = ready.copy(measurement = null,
                status = CameraStatus(CameraStatusCode.MEASUREMENT_UNAVAILABLE), actions = setOf(CameraAction.CLOSE))
            withTimeout(5000) { while (presenter.state.value.measurement != null) delay(10) }
            compose.onNodeWithTag("minimum-temperature").assertTextEquals("— °C")
            compose.onNodeWithTag("maximum-temperature").assertTextEquals("— °C")
            assertEquals(bounds, compose.onNodeWithTag("thermal-image").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithText(initialize).assertIsNotEnabled()
            // Permission denial must not leave an enabled initialization button or stale temperatures.
            module.openedState.value = ready.copy(lifecycle = CameraLifecycle.PERMISSION_PENDING, preview = null,
                measurement = null, status = CameraStatus(CameraStatusCode.PERMISSION_REQUIRED), actions = emptySet(),
                error = CameraError(CameraErrorCode.PERMISSION_DENIED))
            compose.waitForIdle()
            compose.onNodeWithTag("connection").assertIsNotEnabled()
            compose.onNodeWithText(initialize).assertDoesNotExist()
            assertEquals(1, module.initializationCount)
        } finally {
            withContext(Dispatchers.Main) { presenter.dispose(); coordinator.dispose() }
            scenario.close()
        }
    }

    /** An invalid edit cannot alter colors, and accepted French decimal commas use the existing range parser. */
    @Test fun rangeValidationKeepsPreviousBoundsAndAcceptsDecimalCommas() = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val module = LocalizationLayoutDeviceTest.LayoutModule()
        lateinit var coordinator: AndroidCameraCoordinator
        lateinit var presenter: CelsiusPresenter
        lateinit var rangeLabel: String
        lateinit var apply: String
        scenario.onActivity { activity ->
            rangeLabel = activity.getString(R.string.measurement_set_range, 25.0, 45.0)
            apply = activity.getString(R.string.measurement_apply)
            coordinator = AndroidCameraCoordinator(activity.applicationContext,
                listOf(AndroidModuleRegistration(module, Ht301UiBindings)), devices = {
                    listOf(object : CameraDeviceIdentity { override val key = "ux-range-test" })
                })
            presenter = CelsiusPresenter(activity.applicationContext, coordinator.state)
            coordinator.enterForeground(); coordinator.connect()
            activity.setContent { ThermalScreen(coordinator, presenter, { coordinator.connect() }) }
        }
        try {
            withTimeout(5000) { while (presenter.state.value.measurement == null) delay(10) }
            val measurement = presenter.state.value.measurement
            compose.onNodeWithTag("appearance").performScrollTo().performClick()
            compose.onNodeWithText(rangeLabel).performScrollTo().performClick()
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("50")
            compose.onNodeWithText(apply).performClick()
            assertEquals(25.0, presenter.settings.value.locked.lower, 0.0)
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("20,5")
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("40,5")
            compose.onNodeWithText(apply).performClick()
            assertEquals(20.5, presenter.settings.value.locked.lower, 0.0)
            assertEquals(40.5, presenter.settings.value.locked.upper, 0.0)
            assertFalse(presenter.settings.value.automatic)
            withTimeout(5000) { while (presenter.state.value.range?.lower != 20.5) delay(10) }
            assertSame(measurement, presenter.state.value.measurement)
            assertEquals(0, module.initializationCount)
        } finally {
            withContext(Dispatchers.Main) { presenter.dispose(); coordinator.dispose() }
            scenario.close()
        }
    }

    /** The prepared artifact's content class stays truthful even when newer frames become radiometric. */
    @Test fun previewCapturePanelKeepsFrozenContentClassWhenLiveMeasurementReturns() = runBlocking<Unit> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val module = LocalizationLayoutDeviceTest.LayoutModule()
        lateinit var coordinator: AndroidCameraCoordinator
        lateinit var presenter: CelsiusPresenter
        lateinit var exporter: CaptureExporter
        lateinit var previewOnly: String
        lateinit var cancel: String
        scenario.onActivity { activity ->
            previewOnly = activity.getString(R.string.export_preview_only)
            cancel = activity.getString(R.string.export_cancel)
            coordinator = AndroidCameraCoordinator(activity.applicationContext,
                listOf(AndroidModuleRegistration(module, Ht301UiBindings)), devices = {
                    listOf(object : CameraDeviceIdentity { override val key = "ux-preview-capture" })
                })
            presenter = CelsiusPresenter(activity.applicationContext, coordinator.state)
            exporter = CaptureExporter(activity.applicationContext)
            coordinator.enterForeground(); coordinator.connect()
            activity.setContent { ThermalScreen(coordinator, presenter, { coordinator.connect() }, exporter) }
        }
        try {
            withTimeout(5000) { while (presenter.state.value.measurement == null) delay(10) }
            val ready = module.openedState.value
            module.openedState.value = ready.copy(measurement = null, status = CameraStatus(CameraStatusCode.PREVIEW_ONLY))
            withTimeout(5000) { while (presenter.state.value.measurement != null) delay(10) }
            compose.onNodeWithTag("capture").performScrollTo().performClick()
            withTimeout(10000) { while (exporter.state.value.phase != ExportPhase.READY) delay(10) }
            val frozenName = exporter.state.value.name
            assertFalse(exporter.state.value.radiometric)
            compose.onNodeWithText(previewOnly).performScrollTo().assertIsDisplayed()
            module.openedState.value = ready
            withTimeout(5000) { while (presenter.state.value.measurement == null) delay(10) }
            compose.onNodeWithTag("dismiss-panel").performScrollTo().performClick()
            compose.onNodeWithTag("capture").performScrollTo().performClick()
            compose.onNodeWithText(previewOnly).performScrollTo().assertIsDisplayed()
            assertEquals(frozenName, exporter.state.value.name)
            compose.onNodeWithText(cancel).performScrollTo().performClick()
        } finally {
            withContext(Dispatchers.Main) { exporter.dispose(); presenter.dispose(); coordinator.dispose() }
            scenario.close()
        }
    }

}
