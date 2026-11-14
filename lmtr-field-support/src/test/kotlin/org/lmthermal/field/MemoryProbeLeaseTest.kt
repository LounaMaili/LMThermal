package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

/** No replacement collector can escape boundedness through a new model or stale completion. */
class MemoryProbeLeaseTest {
    @Test fun heldLeaseRefusesReplacementAndStaleReleaseCannotFreeASuccessor() {
        val lease=MemoryProbeLease();val first=lease.acquire();assertTrue(first>0);assertEquals(0L,lease.acquire())
        assertTrue(lease.release(first));val second=lease.acquire();assertTrue(second>first)
        assertFalse(lease.release(first));assertEquals(0L,lease.acquire());assertTrue(lease.release(second))
    }
    @Test fun concurrentArmsHaveOnlyOneOwnerWithoutAWaitingQueue() {
        val lease=MemoryProbeLease();val gate=CountDownLatch(1);val a=AtomicLong();val b=AtomicLong()
        val threads=listOf(Thread {gate.await();a.set(lease.acquire())},Thread {gate.await();b.set(lease.acquire())})
        threads.forEach {it.start()};gate.countDown();threads.forEach {it.join(10000);assertFalse(it.isAlive)}
        assertEquals(1,listOf(a.get(),b.get()).count {it>0});assertTrue(lease.release(maxOf(a.get(),b.get())))
    }
    @Test fun exhaustedTokensNeverWrapOrAcquireTheZeroSentinel() {
        assertEquals(0L,MemoryProbeLease(Long.MAX_VALUE).acquire())
        val last=MemoryProbeLease(Long.MAX_VALUE-1);assertEquals(Long.MAX_VALUE,last.acquire());assertTrue(last.release(Long.MAX_VALUE));assertEquals(0L,last.acquire())
    }
}
