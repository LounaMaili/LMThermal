package org.lmthermal.lmtr

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Fixed known bytes and hostile envelope tests; record-body metadata is validated separately. */
class LmtrFramingTest {
    private fun failure(code: String? = null, action: () -> Unit) {
        try { action(); fail("Expected rejection") }
        catch (error: LmtrFailure) { if (code != null) assertEquals(code, error.code) }
    }

    private fun withFile(action: (RandomAccessFile) -> Unit) {
        val path = Files.createTempFile("lmtr-envelope-", ".tmp")
        try { RandomAccessFile(path.toFile(), "rw").use(action) }
        finally { Files.delete(path) }
    }

    private fun initial(file: RandomAccessFile): Record = LmtrFraming.append(file, LmtrFraming.HEADER, 0,
        LmtrFraming.SENTINEL, "{}".toByteArray())

    @Test fun knownPythonEnvelopeVectorMatchesByteForByteAndDistinctReferenceHash() = withFile { file ->
        val record = initial(file)
        val known = "4c4d545252454331010001000000000000000000000000000200000000000000ffffffffffffff7f7b7d" +
            "4c4d5452434d543172000000000000000000000000000000ffffffffffffff7f" +
            "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a0e21d7b600000000"
        val actual = ByteArray(file.length().toInt()).also { file.seek(0); file.readFully(it) }
        assertEquals(known, actual.joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals("d325350921cff96f83b384f1c50c1eb8511e436fcd09b8caf122a14467188953", record.refHash)
        val restored = LmtrFraming.read(file, 0)
        assertEquals(record.refHash, restored.refHash)
        assertArrayEquals(record.body, restored.body)
        assertEquals(114L, restored.length)
        assertNotEquals(record.bodyHash, record.refHash)
    }

    @Test fun appendUsesCurrentEndAndVerifiesImmediatePredecessorAndNoPostEndAppend() = withFile { file ->
        val header = initial(file)
        file.seek(0)
        val chunk = LmtrFraming.append(file, LmtrFraming.CHUNK, 1, header.offset, byteArrayOf(0, 1, 2))
        assertEquals(header.length, chunk.offset)
        val end = LmtrFraming.append(file, LmtrFraming.END, 2, chunk.offset, "{}".toByteArray())
        assertEquals(chunk.offset + chunk.length, end.offset)
        assertEquals(2L, LmtrFraming.read(file, end.offset).ordinal)
        failure("invalid_container") { LmtrFraming.append(file, LmtrFraming.CHUNK, 3, end.offset, byteArrayOf(1)) }
    }

    @Test fun completeBodyCorruptionIsIntegrityFailureNeverRoutineGap() = withFile { file ->
        val record = initial(file)
        file.seek(40); file.writeByte(0)
        failure("committed_payload_integrity") { LmtrFraming.read(file, record.offset) }
    }

    @Test fun framingOnlyInspectionCanTraverseCorruptedNavigationButCannotVerifyItsBody() = withFile { file ->
        val header = initial(file)
        val page = LmtrFraming.append(file, LmtrFraming.INDEX, 1, header.offset, "{\"x\":1}".toByteArray())
        file.seek(page.offset + 40); file.writeByte(0)
        val envelope = LmtrFraming.inspect(file, page.offset)
        assertEquals(page.refHash, envelope.refHash)
        assertEquals(page.bodyHash, envelope.bodyHash)
        assertEquals(7L, envelope.bodyLength)
        assertEquals(header.offset, envelope.previous)
        failure("committed_payload_integrity") { LmtrFraming.read(file, page.offset) }
        file.seek(page.offset + page.length - 8)
        val old = file.readByte()
        file.seek(page.offset + page.length - 8); file.writeByte(old.toInt() xor 1)
        failure("invalid_container") { LmtrFraming.inspect(file, page.offset) }
    }

    @Test fun footerCrcRepetitionsAndReservedFieldsAreAllValidated() {
        // Each byte belongs to a separate framing invariant; no test merely roundtrips its own writer.
        for (position in listOf(0L, 8L, 10L, 12L, 16L, 32L, 42L, 50L, 58L, 66L, 74L, 106L, 110L)) {
            withFile { file ->
                initial(file)
                file.seek(position); val old = file.readByte()
                file.seek(position); file.writeByte(old.toInt() xor 1)
                failure { LmtrFraming.read(file, 0) }
            }
        }
    }

    @Test fun hostileBodyLengthRejectsBeforeBodyAllocationOrSeekBeyondFile() = withFile { file ->
        initial(file)
        file.seek(24)
        file.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(Long.MAX_VALUE).array())
        failure("resource_limit") { LmtrFraming.read(file, 0) }
        file.seek(24)
        file.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(-1).array())
        failure("resource_limit") { LmtrFraming.read(file, 0) }
    }

    @Test fun truncationsAndLocalLimitsDoNotExposePartialBody() {
        for (length in listOf(0L, 39L, 40L, 41L, 42L, 113L)) withFile { file ->
            initial(file); file.setLength(length)
            failure("invalid_container") { LmtrFraming.read(file, 0) }
        }
        withFile { file ->
            initial(file)
            failure("local_resource_limit") { LmtrFraming.read(file, 0, 1) }
            failure("local_resource_limit") { LmtrFraming.read(file, 0, -1) }
            assertArrayEquals("{}".toByteArray(), LmtrFraming.read(file, 0, 2).body)
        }
    }

    @Test fun invalidAppendIdentityAndTornTailRefuseWithoutChangingExistingBytes() = withFile { file ->
        val header = initial(file)
        val original = file.length()
        failure { LmtrFraming.append(file, LmtrFraming.HEADER, 1, 0, byteArrayOf()) }
        failure { LmtrFraming.append(file, LmtrFraming.CHUNK, 2, 0, byteArrayOf()) }
        failure { LmtrFraming.append(file, LmtrFraming.CHUNK, 1, LmtrFraming.SENTINEL, byteArrayOf()) }
        assertEquals(original, file.length())
        file.seek(original); file.writeByte(0)
        failure { LmtrFraming.append(file, LmtrFraming.CHUNK, 1, header.offset, byteArrayOf()) }
        assertEquals(original + 1, file.length())
    }

    @Test fun bodyBoundsAndCheckedArithmeticRemainSeparateFromLocalMemory() = withFile { file ->
        failure("resource_limit") { LmtrFraming.append(file, LmtrFraming.HEADER, 0, LmtrFraming.SENTINEL,
            ByteArray(1_048_577)) }
        assertEquals(0, file.length())
        assertEquals(Long.MAX_VALUE, LmtrFraming.checkedAdd(Long.MAX_VALUE - 1, 1))
        failure("resource_limit") { LmtrFraming.checkedAdd(Long.MAX_VALUE, 1) }
        failure("resource_limit") { LmtrFraming.checkedAdd(-1, 0) }
        failure("resource_limit") { LmtrFraming.checkedAdd(0, -1) }
    }

    @Test fun referenceJsonRetainsLargeOffsetsAndNullEmptyBounds() {
        val reference = RecordRef(4_294_967_300L, 112, 123, null, null, "a".repeat(64)).json()
        val parsed = LmtrJson.parse(LmtrJson.encode(reference))
        assertEquals(4_294_967_300L, parsed["offset"].d())
        assertNull(parsed["first"])
        assertNull(parsed["last"])
    }

    @Test fun framingReadIsSourceImmutable() = withFile { file ->
        val record = initial(file)
        val before = ByteArray(file.length().toInt()).also { file.seek(0); file.readFully(it) }
        repeat(3) { LmtrFraming.read(file, 0) }
        val after = ByteArray(file.length().toInt()).also { file.seek(0); file.readFully(it) }
        assertArrayEquals(before, after)
        assertEquals(record.bodyHash, MessageDigest.getInstance("SHA-256").digest("{}".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) })
    }
}
