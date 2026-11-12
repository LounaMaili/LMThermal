package org.lmthermal.lmtr

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Shared immutable registry patterns must not share mutable matcher state or cache source values. */
class LmtrSchemaConcurrencyTest {
    @Test fun parallelValidAndInvalidScalarMatchesKeepTheirIndependentVerdicts() {
        val workers=Executors.newFixedThreadPool(6)
        try {
            val futures=workers.invokeAll((0 until 6).map { worker -> Callable {
                repeat(100) { index ->
                    LmtrSchema.validate("Id","stream_${worker}_$index")
                    LmtrSchema.validate("Hash","0".repeat(64))
                    try {
                        LmtrSchema.validate("Hash","A"+"0".repeat(63))
                        fail("Uppercase hash must remain invalid")
                    } catch(failure:LmtrFailure) { assertEquals("invalid_metadata",failure.code) }
                }
                true
            } })
            for (future in futures) assertTrue(future.get(10,TimeUnit.SECONDS))
        } finally {
            workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS))
        }
    }
}
