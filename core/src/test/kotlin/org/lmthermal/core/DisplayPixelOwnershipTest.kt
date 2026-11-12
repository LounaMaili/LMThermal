package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The bitmap handoff transfers display pixels once; source Celsius and previous copies stay immutable. */
class DisplayPixelOwnershipTest {
    @Test fun transferCannotExposeSourceOrInvalidateEarlierPublicCopies() {
        val matrix=floatArrayOf(20f,30f,-0.0f,25f);val before=matrix.copyOf()
        val rendered=CelsiusRenderer.render(matrix,NativeImageGeometry(2,2),CelsiusPresentationSettings())
        val copy=rendered.argb();val expected=copy.copyOf();copy[0]=0
        assertArrayEquals(expected,rendered.argb())
        val owned=rendered.takeArgb();assertArrayEquals(expected,owned);owned[0]=1
        assertArrayEquals(before,matrix,0f);assertEquals(256,rendered.legend().size)
        assertThrows(IllegalStateException::class.java){rendered.argb()}
        assertThrows(IllegalStateException::class.java){rendered.takeArgb()}
    }
    @Test fun concurrentTransferHasExactlyOneOwner() {
        val image=CelsiusRenderer.render(floatArrayOf(25f),NativeImageGeometry(1,1),CelsiusPresentationSettings())
        val owners=AtomicInteger();val rejected=AtomicInteger()
        val workers=(1..2).map {Thread{try {image.takeArgb();owners.incrementAndGet()}catch(_:IllegalStateException){rejected.incrementAndGet()}}.apply{start()}}
        workers.forEach{it.join()};assertEquals(1,owners.get());assertEquals(1,rejected.get())
    }
}
