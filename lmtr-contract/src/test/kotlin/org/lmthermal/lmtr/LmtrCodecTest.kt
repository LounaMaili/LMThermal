package org.lmthermal.lmtr

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.Assert.*
import org.junit.Test

/** Independent RFC1950 vectors and adversarial streams, without metadata or file-writer assumptions. */
class LmtrCodecTest {
    private fun failure(code: String? = null, action: () -> Unit) {
        try { action(); fail("Expected rejection") }
        catch (error: LmtrFailure) { if (code != null) assertEquals(code, error.code) }
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun deflate(raw: ByteArray, rawStream: Boolean = false, dictionary: ByteArray? = null): ByteArray {
        val producer = Deflater(6, rawStream)
        try {
            dictionary?.let(producer::setDictionary)
            producer.setInput(raw); producer.finish()
            val result = ByteArrayOutputStream(); val bytes = ByteArray(1024)
            while (!producer.finished()) {
                val count = producer.deflate(bytes)
                assertTrue(count > 0); result.write(bytes, 0, count)
            }
            return result.toByteArray()
        } finally { producer.end() }
    }

    @Test fun storedRoundtripOwnsBytesAndDoesNotMutateSource() {
        val input = byteArrayOf(0, 1, -1, -128, 127)
        val original = input.copyOf()
        val encoded = LmtrCodec.encode(input, 0)
        val decoded = LmtrCodec.decode(encoded, 0, 5, 5)
        assertArrayEquals(original, input)
        assertArrayEquals(input, decoded)
        encoded[0] = 8; decoded[1] = 9
        assertArrayEquals(original, input)
        failure("invalid_payload") { LmtrCodec.decode(original, 0, 4, 5) }
        failure("invalid_payload") { LmtrCodec.decode(original, 0, 6, 6) }
    }

    @Test fun fixedPythonZlibVectorProducesExactLogicalBytes() {
        // Python zlib.compress(b'hello', 1), not this Kotlin implementation's output.
        val fixed = hex("7801cb48cdc9c90700062c0215")
        assertArrayEquals("hello".toByteArray(), LmtrCodec.decode(fixed, 1, 5, 5))
        // Decoder support is independent of the recommended writer compression level.
        for (level in listOf(0, 6, 9)) {
            val producer = Deflater(level)
            try {
                producer.setInput("hello".toByteArray()); producer.finish()
                val buffer = ByteArray(64); val count = producer.deflate(buffer)
                assertTrue(producer.finished())
                assertArrayEquals("hello".toByteArray(), LmtrCodec.decode(buffer.copyOf(count), 1, 5, 5))
            } finally { producer.end() }
        }
    }

    @Test fun completeOutputBoundaryAndSmallLargeLogicalValuesRoundtrip() {
        for (length in listOf(1, 2, 65535, 65536, 65537, 1_048_576)) {
            val raw = ByteArray(length) { (it * 31).toByte() }
            val source = raw.copyOf()
            val encoded = LmtrCodec.encode(raw, 1)
            assertArrayEquals(source, LmtrCodec.decode(encoded, 1, length.toLong(), length.toLong()))
            assertArrayEquals(source, raw)
        }
    }

    @Test fun expansionBeyondDeclaredSizeAndAllWrongExpectedLengthsReject() {
        val compressed = deflate(ByteArray(200000))
        failure("invalid_payload") { LmtrCodec.decode(compressed, 1, 1, 1) }
        failure("invalid_payload") { LmtrCodec.decode(compressed, 1, 199999, 199999) }
        failure("invalid_payload") { LmtrCodec.decode(compressed, 1, 200001, 200001) }
        assertEquals(200000, LmtrCodec.decode(compressed, 1, 200000, 200000).size)
    }

    @Test fun truncationTrailingInputConcatenationAndAdlerDamageReject() {
        val valid = hex("7801cb48cdc9c90700062c0215")
        for (size in 0 until valid.size) failure("invalid_payload") {
            LmtrCodec.decode(valid.copyOf(size), 1, 5, 5)
        }
        failure("invalid_payload") { LmtrCodec.decode(valid + byteArrayOf(0), 1, 5, 5) }
        failure("invalid_payload") { LmtrCodec.decode(valid + valid, 1, 10, 10) }
        val damaged = valid.copyOf(); damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
        failure("committed_payload_integrity") { LmtrCodec.decode(damaged, 1, 5, 5) }
    }

    @Test fun dictionariesRawDeflateGzipAndMalformedHeaderReject() {
        val raw = "hello hello hello".toByteArray()
        failure("invalid_payload") { LmtrCodec.decode(deflate(raw, dictionary = "hello".toByteArray()), 1, raw.size.toLong(), 100) }
        failure("invalid_payload") { LmtrCodec.decode(deflate(raw, rawStream = true), 1, raw.size.toLong(), 100) }
        val gzip = hex("1f8b0800000000000000cb48cdc9c9070086a6103605000000")
        failure("invalid_payload") { LmtrCodec.decode(gzip, 1, 5, 5) }
        val valid = hex("7801cb48cdc9c90700062c0215")
        for (header in listOf(byteArrayOf(0x88.toByte(), 0x1c), byteArrayOf(0x79, 0x00), byteArrayOf(0x78, 0x00))) {
            val damaged = valid.copyOf(); damaged[0] = header[0]; damaged[1] = header[1]
            failure("invalid_payload") { LmtrCodec.decode(damaged, 1, 5, 5) }
        }
    }

    @Test fun boundedCompressedRangeIgnoresSurroundingBytesButRejectsExtraStreamInput() {
        val fixed = hex("7801cb48cdc9c90700062c0215")
        val surrounded = byteArrayOf(99, 88, 77) + fixed + byteArrayOf(66, 55)
        assertArrayEquals("hello".toByteArray(), LmtrCodec.decode(surrounded, 3, fixed.size, 1, 5, 5))
        failure("invalid_payload") { LmtrCodec.decode(surrounded, 3, fixed.size + 1, 1, 5, 5) }
        for ((offset, length) in listOf(-1 to 5, 0 to -1, Int.MAX_VALUE to 5, 3 to Int.MAX_VALUE))
            failure("invalid_payload") { LmtrCodec.decode(surrounded, offset, length, 1, 5, 5) }
    }

    @Test fun hardLimitsLocalBudgetAndUnsupportedCodecsAreDistinct() {
        for (codec in listOf(-1, 2, 65535)) {
            failure("unsupported_codec") { LmtrCodec.encode(byteArrayOf(1), codec) }
            failure("unsupported_codec") { LmtrCodec.decode(byteArrayOf(1), codec, 1, 1) }
        }
        for (decoded in listOf(-1L, 0L, 67_108_865L, Long.MAX_VALUE))
            failure("resource_limit") { LmtrCodec.decode(byteArrayOf(1), 0, decoded, Long.MAX_VALUE) }
        failure("local_resource_limit") { LmtrCodec.decode(byteArrayOf(1), 0, 1, 0) }
        failure("local_resource_limit") { LmtrCodec.decode(byteArrayOf(1), 0, 1, -1) }
    }
}
