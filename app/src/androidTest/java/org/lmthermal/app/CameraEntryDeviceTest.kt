package org.lmthermal.app

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Repeat root entry must reuse the same Activity/ViewModel without touching USB or camera controls. */
@RunWith(AndroidJUnit4::class)
class CameraEntryDeviceTest {
    @Test fun launcherReentryDoesNotAllocateAnotherCameraCompositionRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var first: MainActivity
            lateinit var model: CameraViewModel
            scenario.onActivity {
                first = it; model = ViewModelProvider(it)[CameraViewModel::class.java]
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                assertEquals(1, resumed.size); assertSame(first, resumed.single())
                assertSame(model, ViewModelProvider(resumed.single())[CameraViewModel::class.java])
                assertNull(model.camera.state.value.preview); assertNull(model.camera.state.value.measurement)
            }
        }
    }
}
