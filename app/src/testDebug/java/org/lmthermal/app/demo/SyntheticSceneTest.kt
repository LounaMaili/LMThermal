package org.lmthermal.app.demo

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.CameraProbeResult
import org.lmthermal.camera.UsbCameraIdentity

/** Independent arithmetic oracles make the interactive fixture usable as a repeatable UX acceptance scene. */
class SyntheticSceneTest {
    @Test fun movingTargetNeverChangesReferencePatchesOrNativeGeometry() {
        assertEquals(384, SyntheticScene.geometry.width)
        assertEquals(288, SyntheticScene.geometry.height)
        for (frame in 0L..63) {
            val values = SyntheticScene.matrix(frame)
            assertEquals(10f, values.minOrNull()!!, 0f)
            assertEquals(40f, values.maxOrNull()!!, 0f)
            assertEquals(35f, values[208 * 384 + 64 + frame.toInt() * 4], 0f)
            var sum = 0.0
            for (y in 48..95) for (x in 128..255) sum += values[y * 384 + x]
            assertEquals(23.5, sum / (128 * 48), 0.0)
        }
        assertArrayEquals(SyntheticScene.matrix(0), SyntheticScene.matrix(64), 0f)
        assertFalse(SyntheticScene.matrix(0).contentEquals(SyntheticScene.matrix(1)))
    }

    @Test fun demonstrationCannotSelectAUsbDeviceOrAdvertiseDeviceProvenance() {
        val module = SyntheticDemoModule()
        assertEquals(CameraProbeResult.UNSUPPORTED, module.probe(UsbCameraIdentity("usb", 0x1514, 1)))
        assertEquals(CameraProbeResult.SUPPORTED, module.probe(SyntheticDemoModule.identity))
        assertEquals("simulated", module.metadata.sourceOrigin)
        assertFalse(module.metadata.capabilities.explicitMeasurementInitialization)
    }
}
