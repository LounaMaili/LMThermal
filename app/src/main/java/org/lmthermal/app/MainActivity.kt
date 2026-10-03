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

/** Keeps USB ownership out of Compose; background and recreation release instead of retaining stale fd. */
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    val camera = AndroidCameraCoordinator(application)
    val presentation = CelsiusPresenter(application, camera.state, camera::measurementEvidence, camera::cursorEvidence)
    val exporter = CaptureExporter(application)
    /** ViewModel final disposal tears down the receiver as well as the native source. */
    override fun onCleared() { exporter.dispose(); presentation.dispose(); camera.dispose() }
}

/** Minimal touch surface. Sensor orientation, controls and measurements remain separate concerns. */
class MainActivity : AppCompatActivity() {
    private val model: CameraViewModel by viewModels()
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.camera.cameraPermissionResult(granted)
    }
    private val createCapture = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.lmthermal.exchange+zip")) {
        model.exporter.publish(it)
    }
    private fun chooseCaptureDestination() { model.exporter.state.value.name?.let(createCapture::launch) }
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
        if (model.camera.connect()) cameraPermission.launch(Manifest.permission.CAMERA)
    }
    /** UI consumes a conflated state, never acquires or parses a frame on the main thread. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThermalScreen(model.camera, model.presentation, ::connect, model.exporter, ::chooseCaptureDestination, ::shareCapture) }
    }

    /** Re-discover without automatically opening or sending camera settings. */
    override fun onStart() { super.onStart(); model.camera.enterForeground() }
    /** Existing conservative policy releases on every stop, including a SAF picker and rotation/locale recreation.
     * This is a lifecycle policy, not a USB requirement. Export's ViewModel-owned frozen artifact survives separately;
     * preserving live camera continuity across selected stops needs a deliberate ownership-policy change.
     */
    override fun onStop() { model.camera.leaveForeground(); super.onStop() }
}
