package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.CameraStatistics
import org.lmthermal.lmtr.LmtrJson
import java.math.BigDecimal

/** Exercise the actual per-sample projection, rather than only aggregate rate helpers. */
class FieldCallbackSampleTest {
    @Test fun callbackSampleUsesDecimalAndPreservesCounters() {
        val facts=fieldCallbackSample(CameraStatistics(24.993110024394024,4,2,1))
        assertEquals(BigDecimal.valueOf(24.993110024394024),facts["callback_fps"])
        val encoded=LmtrJson.encode(mapOf("elapsed_ns" to "138655965")+facts)
        assertEquals("{\"callback_fps\":24.993110024394024,\"elapsed_ns\":\"138655965\",\"malformed\":\"1\",\"received\":\"4\",\"replaced\":\"2\"}",encoded.toString(Charsets.UTF_8))
        assertArrayEquals(encoded,LmtrJson.encode(LmtrJson.parse(encoded)))
    }

    @Test fun initialAndIntegralRatesRemainNumbersWhileRawDoubleIsStillRejected() {
        assertTrue(LmtrJson.encode(fieldCallbackSample(CameraStatistics())).toString(Charsets.UTF_8).contains("\"callback_fps\":0"))
        assertTrue(LmtrJson.encode(fieldCallbackSample(CameraStatistics(25.0))).toString(Charsets.UTF_8).contains("\"callback_fps\":25"))
        // Reproduce the old sampler boundary failure without weakening canonical wire JSON.
        assertThrows(IllegalArgumentException::class.java){LmtrJson.encode(mapOf("callback_fps" to 25.0))}
    }

    @Test fun nonFiniteCallbackRateFailsInsteadOfFabricatingASample() {
        for(rate in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY))
            assertThrows(IllegalArgumentException::class.java){fieldCallbackSample(CameraStatistics(rate))}
    }
}
