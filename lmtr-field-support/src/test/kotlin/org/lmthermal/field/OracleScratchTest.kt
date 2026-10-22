package org.lmthermal.field

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Independent ByteBuffer authority exercises batch boundaries and every source bit category. */
class OracleScratchTest {
    @Test fun batchedOracleMatchesIndependentOriginalFloatBitsWithoutMutation() {
        val bits=intArrayOf(0,Int.MIN_VALUE,0x7f800000,0xff800000.toInt(),0x7fc00042,1,0x3f800000)
        for(size in listOf(0,1,1023,1024,1025,110592)) {
            val values=FloatArray(size){Float.fromBits(bits[it%bits.size])}
            val raw=ByteBuffer.allocate(size*4).order(ByteOrder.LITTLE_ENDIAN)
            for(value in values)raw.putInt(value.toRawBits())
            val expected=MessageDigest.getInstance("SHA-256").digest(raw.array()).joinToString(""){"%02x".format(it.toInt() and 255)}
            assertEquals(expected,HtFieldOracle.float32Hash(values))
            for(i in values.indices)assertEquals(bits[i%bits.size],values[i].toRawBits())
        }
    }
}
