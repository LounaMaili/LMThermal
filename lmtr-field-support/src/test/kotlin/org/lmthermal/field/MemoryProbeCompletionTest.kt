package org.lmthermal.field

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Verify the coroutine completion guarantee used by the field controller, without Android.
 * Cancellation is a request; synchronous platform work may still own the collector's lease.
 */
class MemoryProbeCompletionTest {
    @Test fun cancellationCannotReleaseACollectorLeaseWhileTheProbeIsStillBlocked()=runBlocking {
        val lease=MemoryProbeLease();val token=lease.acquire();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val window=BracketedMemoryWindow(token,MemoryScalarProbe {_,_->entered.countDown();check(release.await(10,TimeUnit.SECONDS));MemoryProbeStatus.UNSUPPORTED},System::nanoTime)
        val job=scope.launch(start=CoroutineStart.LAZY){try {window.sample()}finally {window.requestStop()}}
        job.invokeOnCompletion {window.requestStop();lease.release(token)}
        try {
            job.start();assertTrue(entered.await(5,TimeUnit.SECONDS));window.requestStop();job.cancel()
            assertFalse(window.quiescent());assertEquals(0L,lease.acquire())
            release.countDown();withTimeout(10000){job.join()};assertTrue(window.quiescent())
            val next=lease.acquire();assertTrue(next>token);assertTrue(lease.release(next))
        } finally {release.countDown();scope.cancel()}
    }
    @Test fun cancelledLazyJobReleasesItsLeaseWithoutInvokingAProbe()=runBlocking {
        val lease=MemoryProbeLease();val token=lease.acquire();val called=AtomicBoolean()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val window=BracketedMemoryWindow(token,MemoryScalarProbe {_,_->called.set(true);MemoryProbeStatus.UNSUPPORTED},System::nanoTime)
        val job=scope.launch(start=CoroutineStart.LAZY){window.sample()}
        job.invokeOnCompletion {window.requestStop();lease.release(token)}
        job.cancel();withTimeout(10000){job.join()};assertFalse(called.get());assertTrue(window.quiescent())
        val next=lease.acquire();assertTrue(next>token);lease.release(next);scope.cancel()
    }
}
