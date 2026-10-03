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
    /** ViewModel final disposal tears down the receiver as well as the native source. */
    override fun onCleared() { presentation.dispose(); camera.dispose() }
}

/** Minimal touch surface. Sensor orientation, controls and measurements remain separate concerns. */
class MainActivity : AppCompatActivity() {
    private val model: CameraViewModel by viewModels()
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.camera.cameraPermissionResult(granted)
    }
    /** CAMERA authorization precedes Android's per-USB-device permission dialog. */
    private fun connect() {
        if (model.camera.connect()) cameraPermission.launch(Manifest.permission.CAMERA)
    }
    /** UI consumes a conflated state, never acquires or parses a frame on the main thread. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThermalScreen(model.camera, model.presentation, ::connect) }
    }

    /** Re-discover without automatically opening or sending camera settings. */
    override fun onStart() { super.onStart(); model.camera.enterForeground() }
    /** Explicit foreground-only lifetime makes background/recreation release predictable. */
    override fun onStop() { model.camera.leaveForeground(); super.onStop() }
}
