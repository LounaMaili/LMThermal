package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException

/** Byte-bounded Linux scalar parsing, including short reads; never reads a camera or Android. */
class BoundedProcRssTest {
    private fun parse(s:String)=BoundedProcRss().read(ByteArrayInputStream(s.toByteArray()))
    @Test fun parsesExactKibibytesAndArbitraryShortReads() {
        val input=ByteArrayInputStream("Name:\ttest\nVmRSS:\t12345 kB\nOther:\t9\n".toByteArray())
        val short=object:InputStream(){override fun read()=input.read();override fun read(b:ByteArray,o:Int,n:Int)=input.read(b,o,minOf(n,2))}
        assertEquals(12345L*1024,BoundedProcRss().read(short));assertEquals(0L,parse("VmRSS: 0 kB\n"))
    }
    @Test fun missingTruncatedDuplicatedUnitAndOverflowAreNotHealthyZeros() {
        for(s in listOf("Name: test\n","VmRSS: 10 kB","VmRSS: 1 kB\nVmRSS: 2 kB\n","VmRSS: 1 MB\n","VmRSS: -1 kB\n","VmRSS: 99999999999999999999 kB\n"))
            assertThrows(IOException::class.java){parse(s)}
    }
    @Test fun boundedLongLinesAndTotalBudgetCannotMakeUnboundedScratch() {
        assertEquals(1024L,parse("Other: "+"x".repeat(1000)+"\nVmRSS: 1 kB\n"))
        assertThrows(IOException::class.java){parse("x".repeat(65537)+"\nVmRSS: 1 kB\n")}
        assertThrows(IOException::class.java){parse("VmRSS: "+"0".repeat(200)+" kB\n")}
    }
    @Test fun zeroProgressOrMidReadFailureDoesNotReturnAnEarlierValue() {
        val stuck=object:InputStream(){override fun read()=0;override fun read(b:ByteArray,o:Int,n:Int)=0}
        assertThrows(IOException::class.java){BoundedProcRss().read(stuck)}
        val fail=object:InputStream(){var first=true;override fun read():Int=throw IOException()
            override fun read(b:ByteArray,o:Int,n:Int):Int {if(!first)throw IOException();first=false;val a="VmRSS: 1 kB\n".toByteArray();a.copyInto(b,o);return a.size}}
        assertThrows(IOException::class.java){BoundedProcRss().read(fail)}
    }
}
