package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.LmtrCodec
import org.lmthermal.lmtr.LmtrFailure
import java.util.Random

/** Independent original codec byte comparison, incompressible expansion and exclusive lifetimes. */
class RecordingDeflateTest {
    @Test fun exactOriginalBytesAcrossSegmentAndFullSizeBoundaries() {
        for (size in listOf(0, 1, 65535, 65536, 65537, 442368, 224256, 4*442368)) {
            for (random in listOf(false,true)) {
                val source=ByteArray(size)
                if(random)Random(size.toLong()).nextBytes(source)
                val original=source.copyOf()
                val reference=LmtrCodec.encode(source,1)
                val allocations=ArrayList<ByteArray>()
                val result=RecordingDeflate.encode(source,reference.size+65536) {
                    ByteArray(it).also(allocations::add)
                }
                assertArrayEquals(reference,result)
                assertArrayEquals(original,source)
                val capacity=allocations.dropLast(1).sumOf { it.size.toLong() }
                assertTrue(capacity<=reference.size.toLong()+65535)
                assertTrue(allocations.dropLast(1).all { it.size<=65536 })
                // Mutating returned bytes cannot mutate source or any temporary segment.
                val first=allocations.first()[0]
                result[0]=(result[0].toInt() xor 1).toByte()
                assertEquals(first,allocations.first()[0])
                assertArrayEquals(original,source)
            }
        }
    }

    @Test fun exactCapacityWorksAndOneByteLessRefusesWithoutTruncation() {
        val source=ByteArray(224256).also { Random(84).nextBytes(it) }
        val reference=LmtrCodec.encode(source,1)
        assertTrue(reference.size>source.size)
        assertArrayEquals(reference,RecordingDeflate.encode(source,reference.size))
        for(cap in listOf(-1,0,1,65536,reference.size-1)) {
            try { RecordingDeflate.encode(source,cap);fail("Expected local refusal") }
            catch(error:LmtrFailure) { assertEquals("local_resource_limit",error.code) }
        }
        assertArrayEquals(reference,RecordingDeflate.encode(source,reference.size))
    }

    @Test fun allocationAndCancellationFailuresCannotPoisonAnotherInvocation() {
        val source=ByteArray(224256).also { Random(85).nextBytes(it) }
        val reference=LmtrCodec.encode(source,1)
        // Fail each output segment and the final owned copy; there is no shared retained arena.
        val calls=(reference.size+65535)/65536+1
        for(failAt in 1..calls) {
            var call=0
            try {
                RecordingDeflate.encode(source,reference.size) {
                    if(++call==failAt)throw OutOfMemoryError("injected workspace allocation")
                    ByteArray(it)
                }
                fail("Expected injected OOM")
            } catch(_:OutOfMemoryError) { }
            assertArrayEquals(reference,RecordingDeflate.encode(source,reference.size))
        }
        try {
            RecordingDeflate.encode(source,reference.size) { throw java.util.concurrent.CancellationException("injected") }
            fail("Expected cancellation")
        } catch(_:java.util.concurrent.CancellationException) { }
        assertArrayEquals(reference,RecordingDeflate.encode(source,reference.size))
    }
}
