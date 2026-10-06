package org.lmthermal.app

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import org.lmthermal.camera.AndroidCameraCoordinator
import org.lmthermal.camera.CameraUiLifetime
import androidx.appcompat.app.AppCompatDelegate

/** Retains the sole coordinator, presentation choices and frozen export through safe UI recreation.
 * No Activity/View/launcher is held here; disposal still awaits native release in the coordinator.
 */
class CameraViewModel(application: Application, val camera: AndroidCameraCoordinator) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, AndroidCameraCoordinator(application))
    val presentation = CelsiusPresenter(application, camera.state, camera::measurementEvidence, camera::cursorEvidence)
    val exporter = CaptureExporter(application)
    val uiLifetime = CameraUiLifetime(camera::leaveForeground, camera::recordLifecycle)
    init { camera.screenOff = uiLifetime::screenOff }
    /** ViewModel final disposal tears down the receiver as well as the native source. */
    override fun onCleared() { uiLifetime.dispose(); exporter.dispose(); presentation.dispose(); camera.dispose() }
}

/** Minimal touch surface. Sensor orientation, controls and measurements remain separate concerns. */
open class MainActivity : AppCompatActivity() {
    private val model: CameraViewModel by viewModels()
    private var uiToken = -1L
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (model.uiLifetime.current(uiToken)) model.camera.cameraPermissionResult(granted)
    }
    private val createCapture = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.lmthermal.exchange+zip")) {
        if (model.uiLifetime.endPicker(uiToken)) model.exporter.publish(it)
    }
    /** A retained transaction is registered before SAF can stop this Activity. Failed launches clear it. */
    private fun chooseCaptureDestination() {
        val name = model.exporter.state.value.name ?: return
        if (!model.uiLifetime.beginPicker(uiToken)) return
        try { createCapture.launch(name) }
        catch (failure: RuntimeException) { model.uiLifetime.endPicker(uiToken); throw failure }
    }
    /** Only a finalized file is granted to the chosen share target; no internal path is exposed. */
    private fun shareCapture() {
        val file = model.exporter.shareFile() ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(this, packageName + ".captures", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.lmthermal.exchange+zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("LMThermal capture", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.export_share)))
    }
    /** CAMERA authorization precedes Android's per-USB-device permission dialog. */
    private fun connect() {
        if (model.uiLifetime.current(uiToken) && model.camera.connect()) cameraPermission.launch(Manifest.permission.CAMERA)
    }
    /** UI consumes a conflated state, never acquires or parses a frame on the main thread. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindUi()
        setContent { ThermalScreen(model.camera, model.presentation, ::connect, model.exporter, ::chooseCaptureDestination, ::shareCapture) }
    }

    /** Resolve both application preference and effective resource locale, including OS Settings changes. */
    private fun bindUi() {
        uiToken = model.uiLifetime.started(AppCompatDelegate.getApplicationLocales().toLanguageTags() + ":" +
            resources.configuration.locales.toLanguageTags())
    }
    /** Foreground only discovers; it never opens or initializes. Result delivery uses the already bound UI. */
    override fun onStart() { super.onStart(); model.camera.enterForeground() }
    /** A pause alone (permission dialog/system overlay) leaves acquisition untouched. A real non-picker stop
     * closes. Configuration stops retain the ViewModel; locale differences close when the new UI binds.
     */
    override fun onStop() {
        model.uiLifetime.stopped(uiToken, isChangingConfigurations, isFinishing)
        super.onStop()
    }
}
