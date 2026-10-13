package org.lmthermal.lmtr

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Navigation damage is rebuildable; complete promised source corruption is never a gap. */
class LmtrRecoveryTest {
    @Test fun damagedIndexAndCheckpointRecoverOnlyVerifiedImmutablePrefix() {
        for(name in listOf("committed-index-damage","committed-checkpoint-damage")) {
            val file=File(javaClass.getResource("/lmtr/files/$name.lmtr")!!.toURI())
            val before=file.readBytes()
            val result=LmtrReader(file).use { it.check(full=true,recover=true) }
            assertEquals("recovered",result["status"])
            assertEquals("navigation_rebuilt",result["reason"])
            assertEquals("full_verified_prefix",result["integrity_scope"])
            assertEquals(true,result["unknown_tail"])
            assertNull(result["declared_end_status"])
            assertEquals("3",result["summary"].obj()["entries"])
            assertEquals("6306",result["verified_prefix_bytes"])
            assertArrayEquals(before,file.readBytes())
        }
    }
    @Test fun recoveryCannotTurnCommittedPayloadCorruptionIntoMissingObservation() {
        val file=File(javaClass.getResource("/lmtr/files/committed-body-corrupt.lmtr")!!.toURI())
        try { LmtrReader(file).use { it.check(full=true,recover=true) };fail("promised source bytes corrupt") }
        catch(e:LmtrFailure) { assertEquals("committed_payload_integrity",e.code) }
    }
    @Test fun explicitSalvageReportsDamagedCommittedChunkAndIndependentLaterRange() {
        val source=File(javaClass.getResource("/lmtr/files/multi-chunk-context-closure.lmtr")!!.toURI())
        val data=source.readBytes()
        val headerLength=java.io.RandomAccessFile(source,"r").use { LmtrFraming.read(it,0).length }
        data[headerLength.toInt()+40]=(data[headerLength.toInt()+40].toInt() xor 1).toByte()
        val path=java.nio.file.Files.createTempFile("lmtr-salvage-", ".lmtr")
        try {
            path.toFile().writeBytes(data)
            val result=LmtrReader(path.toFile()).use { it.salvage() }
            assertEquals("integrity_damaged",result["status"])
            assertEquals("salvaged_chunks",result["integrity_scope"])
            assertEquals("committed_payload_integrity",result["failures"].arr().first().obj()["error"])
            assertEquals(1,result["ranges"].arr().size)
            assertNull(result["declared_end_status"])
            assertArrayEquals(data,path.toFile().readBytes())
        } finally { java.nio.file.Files.deleteIfExists(path) }
    }
    @Test fun explicitScanCancellationPreservesSourceAndExposesNoRows() {
        val file=File(javaClass.getResource("/lmtr/files/illustration-01-stored.lmtr")!!.toURI())
        val before=file.readBytes()
        val result=LmtrReader(file).use { it.salvage { true } }
        assertEquals(true,result["cancelled"])
        assertEquals("explicit_scan_cancelled",result["reason"])
        assertTrue(result["ranges"].arr().isEmpty())
        assertArrayEquals(before,file.readBytes())
    }

}
