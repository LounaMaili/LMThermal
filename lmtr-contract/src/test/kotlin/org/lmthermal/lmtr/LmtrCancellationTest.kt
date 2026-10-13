package org.lmthermal.lmtr

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Cooperative cancellation never advertises partial full verification as success. */
class LmtrCancellationTest {
    @Test fun fullAndRecoveryCancellationPropagateAndRestoreCallback() {
        for((name,recover) in listOf("illustration-01-stored" to false,"committed-index-damage" to true)) {
            val source=File(javaClass.getResource("/lmtr/files/$name.lmtr")!!.toURI())
            val before=source.readBytes()
            LmtrReader(source).use { reader ->
                var polls=0
                try {
                    reader.check(full=true,recover=recover,cancelled={ ++polls==3 })
                    fail("cancel must not yield a successful integrity report")
                } catch(e:LmtrFailure) { assertEquals("cancelled",e.code) }
                assertEquals(3,polls)
                // The failed invocation must not leave its callback installed.
                assertEquals(if(recover)"recovered" else "valid",reader.check(full=true,recover=recover)["status"])
            }
            assertArrayEquals(before,source.readBytes())
        }
    }

    @Test fun cancelledSalvageRetainsOnlyOneVerifiedChunkWithoutCompletionClaim() {
        val source=File(javaClass.getResource("/lmtr/files/illustration-01-stored.lmtr")!!.toURI())
        val before=source.readBytes()
        val result=LmtrReader(source).use { reader ->
            var polls=0
            reader.salvage { ++polls==3 }
        }
        assertEquals(true,result["cancelled"])
        assertEquals("salvaged_chunks",result["integrity_scope"])
        assertEquals(1,result["ranges"].arr().size)
        assertNull(result["declared_end_status"])
        assertArrayEquals(before,source.readBytes())
    }

    @Test fun resourceRefusalExposesNoUnverifiedPayloadHashes() {
        val source=File(javaClass.getResource("/lmtr/files/illustration-04-stored.lmtr")!!.toURI())
        LmtrReader(source,workingBudget=16384).use { reader ->
            try { reader.check(full=true);fail("body exceeds local allocation admission") }
            catch(e:LmtrFailure) { assertEquals("local_resource_limit",e.code) }
            assertEquals(0L,reader.chunksDecoded)
        }
    }
}
