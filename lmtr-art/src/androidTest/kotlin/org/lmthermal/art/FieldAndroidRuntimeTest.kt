package org.lmthermal.art

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.field.*
import java.io.File

/** Actual ART elapsedRealtime API and the same pre-transform field oracle, no camera/USB. */
class FieldAndroidRuntimeTest {
    @Test fun elapsedRealtimeFieldWitnessAndExistingRoomOracle() {
        val clock=FieldClock(SystemClock::elapsedRealtimeNanos)
        val before=SystemClock.elapsedRealtimeNanos();val read=clock.now();val after=SystemClock.elapsedRealtimeNanos()
        assertTrue(read in before..after);assertNull(clock.header()["timeline"].let { (it as Map<*,*>)["resolution_ns"] })
        val output=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"r4/field-oracle")
        FieldOracleProbe.main(arrayOf(output.path))
    }
}
