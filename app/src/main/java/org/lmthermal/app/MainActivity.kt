package org.lmthermal.app

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lmthermal.core.Ht301Layout
import org.lmthermal.core.RadiometricSession

/** Keeps USB ownership out of Compose; background and recreation release instead of retaining stale fd. */
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    val camera = CameraController(application)
    /** ViewModel final disposal tears down the receiver as well as the native source. */
    override fun onCleared() { camera.dispose() }
}

/** Minimal touch surface. Sensor orientation, controls and measurements remain separate concerns. */
class MainActivity : ComponentActivity() {
    private val model: CameraViewModel by viewModels()
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.camera.connect() else model.camera.cameraPermissionDenied()
    }
    /** CAMERA authorization precedes Android's per-USB-device permission dialog. */
    private fun connect() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            model.camera.connect() else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    /** UI consumes a conflated state, never acquires or parses a frame on the main thread. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by model.camera.state.collectAsStateWithLifecycle()
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LMThermal", style = MaterialTheme.typography.headlineMedium)
                        Text(state.usb.message)
                        Text(state.identity)
                        Text("USB permission: ${state.permission}")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { connect() }) { Text("Connect / Open") }
                            OutlinedButton(onClick = { model.camera.close() }) { Text("Close") }
                        }
                        Button(onClick = { model.camera.initializeRadiometric() }, enabled = state.session.canInitialize && !state.transition.active) {
                            Text("Initialize radiometric")
                        }
                        if (BuildConfig.DEBUG) {
                            OutlinedButton(onClick = { model.camera.readZoomInventory() },
                                enabled = state.usb.phase == org.lmthermal.core.UsbPhase.STREAMING && !state.session.active && !state.transition.active) {
                                Text("Read zoom inventory")
                            }
                            Text(state.inventory)
                            OutlinedButton(onClick = { model.camera.testRaw14Transition() },
                                enabled = state.transition.canStart && !state.session.active) {
                                Text("Test raw14 transition (32772)")
                            }
                            Text("Single test: ${state.transition.stage} · discarded ${state.transition.discarded} · distinct ${state.transition.distinct}")
                            state.transition.reason?.let { Text("Single test evidence: $it") }
                        }
                        Text("Session: ${state.session.state} · ${state.session.stage}")
                        Text("Baseline ${state.session.baseline}/${RadiometricSession.BASELINE_FRAMES} · discarded ${state.session.discarded} · shutter ${state.session.shutterFrames}/${RadiometricSession.SHUTTER_DISCARD} · live ${state.session.live}/${RadiometricSession.READY_LIVE}")
                        state.session.reason?.let { Text("Session evidence: $it") }
                        state.bitmap?.let { Image(it.asImageBitmap(), "Native thermal aiming preview",
                            Modifier.fillMaxWidth().aspectRatio(Ht301Layout.WIDTH.toFloat() / Ht301Layout.IMAGE_HEIGHT)) }
                        if (state.bitmap == null) Text("No valid preview frame")
                        Text("${state.mode} · ${state.size} bytes · words ${state.range}")
                        Text("Acquisition %.1f FPS · received %d · parser rejected %d · replaced %d · malformed payloads %d".format(state.fps, state.received, state.invalid, state.replaced, state.malformed))
                        state.reason?.let { Text("Frame evidence: $it") }
                        Text("Transport 384×292 · image 384×288 · four trailer rows retained")
                        Text("Aiming preview. Readiness qualifies structural/live raw14 only. No Celsius or physical accuracy claim.")
                    }
                }
            }
        }
    }
    /** Re-discover without automatically opening or sending camera settings. */
    override fun onStart() { super.onStart(); model.camera.enterForeground() }
    /** Explicit foreground-only lifetime makes background/recreation release predictable. */
    override fun onStop() { model.camera.leaveForeground(); super.onStop() }
}
