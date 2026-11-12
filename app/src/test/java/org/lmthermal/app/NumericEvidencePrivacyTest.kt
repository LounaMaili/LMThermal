package org.lmthermal.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/** Disabled/release evidence is a no-op BEFORE serialization, not just a private-file toggle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class NumericEvidencePrivacyTest {
    @Test fun disabledEvidenceNeitherSerializesWritesNorLogs() {
        val context=RuntimeEnvironment.getApplication();val tag="DisabledLMThermalEvidence"
        val evidence=NumericEvidence(context,"disabled-test.jsonl",tag,false)
        val value=object {override fun toString():String=error("Disabled evidence serialized")}
        evidence.reset();evidence.record(mapOf("private" to value))
        assertFalse(File(context.filesDir,"disabled-test.jsonl").exists())
        assertTrue(ShadowLog.getLogsForTag(tag).isEmpty())
    }
}
