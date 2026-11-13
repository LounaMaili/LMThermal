package org.lmthermal.field

import org.lmthermal.camera.CameraStatistics
import org.lmthermal.lmtr.Obj

/** Diagnostic callback facts need an explicit decimal at the strict JSON boundary.
 * This projection does not control admission, clocks, source delivery or acknowledgements.
 * A raw Double here interrupted the first physical smoke before its first sample was written.
 */
fun fieldCallbackSample(statistics:CameraStatistics):Obj = mapOf(
    "callback_fps" to fieldMetricNumber(statistics.callbackFps),
    "received" to statistics.received.toString(),
    "replaced" to statistics.replaced.toString(),
    "malformed" to statistics.malformed.toString())
