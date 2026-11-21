package org.lmthermal.app.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.lmthermal.app.CameraViewModel
import org.lmthermal.app.MainActivity
import org.lmthermal.camera.AndroidCameraCoordinator
import org.lmthermal.camera.AndroidModuleRegistration

/** Debug-only composition root; the real MainActivity still owns UI, SAF and lifecycle policy unchanged.
 * The injected discovery list contains one synthetic identity and no real camera registration.
 * ViewModelStore retains this exact coordinator across rotations, just like the production entry point.
 */
class SyntheticDemoActivity : MainActivity() {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == CameraViewModel::class.java)
                val camera = AndroidCameraCoordinator(application,
                    listOf(AndroidModuleRegistration(SyntheticDemoModule(), SyntheticDemoModule.ui)),
                    devices = { listOf(SyntheticDemoModule.identity) })
                return modelClass.cast(CameraViewModel(application, camera))!!
            }
        }
}
