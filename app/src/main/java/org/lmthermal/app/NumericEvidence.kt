package org.lmthermal.app

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/** Bounded developer evidence survives wireless-ADB loss; never stores scene bytes or image hashes.
 * Reset/record are serialized by the owning camera or presentation worker. Debug-only
 * persistence covers lifecycle events, deliberate diagnostics and initialized measurements.
 */
internal class NumericEvidence(context: Context, name: String, private val tag: String) {
    private val file = File(context.filesDir, name)
    private var enabled = false
    private var count = 0
    /** Replace the previous deliberate experiment, avoiding an unbounded acquisition log. */
    fun reset() {
        enabled = BuildConfig.DEBUG; count = 0
        if (enabled) file.writeText("")
    }
    /** Bound even unexpected repeated events; logcat remains supplemental evidence. */
    fun record(event: Map<String, Any?>) {
        val json = JSONObject(event).toString()
        Log.i(tag, json)
        if (enabled && count++ < MAX_EVENTS) file.appendText(json + "\n")
    }
    private companion object { const val MAX_EVENTS = 2048 }
}
