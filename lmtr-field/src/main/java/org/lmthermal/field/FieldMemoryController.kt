package org.lmthermal.field

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit experimental field-only sidecar. Default off; no Connect/init/Start/Stop authority.
 * Persistent buffers are allocated before existing recorder admission, counted in its observed
 * process occupancy without reducing 78+32 MiB. OS transient/ART cost is still unqualified.
 * Only weak source access survives suspensions; a blocked platform probe cannot retain frames.
 */
internal class FieldMemoryController(private val model:WeakReference<FieldModel>) {
    // No shared parent job: a blocked probe must not retain recorder/source sibling coroutines.
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val status=MutableStateFlow("Memory probes off (experimental; not an admission policy)")
    val active=MutableStateFlow(false)
    @Volatile private var window:BracketedMemoryWindow?=null
    private var root:File?=null
    @Volatile private var job:Job?=null
    @Volatile private var saved=true
    private val exporting=AtomicBoolean()

    /** Allocate the complete scalar window before any Start, never select a favorable health probe. */
    fun arm(allowed:Boolean,files:File) {
        if(!allowed || active.value || !saved || exporting.get()) {
            status.value="Memory arm refused: prior work/window still owned";return
        }
        val lease=MemoryProbeProcessLease.lease.acquire()
        if(lease==0L){status.value="Memory arm refused: another collector still owns the process lease";return}
        var completionOwnsLease=false
        try {
            val directory=File(files,"r4-synchronized-memory/${UUID.randomUUID()}");check(directory.mkdirs())
            val next=BracketedMemoryWindow(lease,AndroidMemoryProbe(model),SystemClock::elapsedRealtimeNanos)
            root=directory;window=next;saved=false;active.value=true
            val collector=scope.launch(start=CoroutineStart.LAZY) {
                try {
                    repeat(60) {
                        if(next.stopped())return@launch
                        next.sample();delay(1000)
                    }
                } finally {
                    next.requestStop()
                }
            }
            job=collector
            collector.invokeOnCompletion {
                // A cancelled lazy job also releases; a blocked native read has not completed yet.
                next.requestStop();MemoryProbeProcessLease.lease.release(lease);active.value=false
                status.value="Memory probes stopped: ${next.rowCount()} rows; save after recorder drain (${directory.name})"
            }
            completionOwnsLease=true
            status.value="Memory probes active: max 60 rows, no faster than 1 Hz; normal preview unchanged"
            collector.start()
        } catch(_:Throwable) {
            window?.requestStop()
            if(!completionOwnsLease)MemoryProbeProcessLease.lease.release(lease)else try {job?.cancel()}catch(_:Throwable){}
            active.value=false
            status.value="Memory diagnostic setup failed; source/recorder untouched"
        }
    }

    /** Nonblocking diagnostic cancellation; does not join a blocked read or stop a recorder. */
    fun stop() {window?.requestStop();try {job?.cancel()}catch(_:Throwable){/* The diagnostic latch is already closed. */}}

    /** Dispose only diagnostic jobs; never await or cancel the source/recorder scope. */
    fun dispose() {stop();try {scope.cancel()}catch(_:Throwable){/* No diagnostic exception reaches source cleanup. */}}

    /** Streaming export only after caller confirms collector and recorder quiescence.
     * Exclusive new destination preserves any partial on failure; no LMTR file is opened here.
     * This is private diagnostic storage, not a power-loss/durable LMTR guarantee.
     */
    fun save(allowed:Boolean) {
        val current=window;val directory=root
        if(!allowed || current==null || directory==null || !current.quiescent() || saved || !exporting.compareAndSet(false,true)) {
            status.value="Memory export deferred: collector/recorder still owned or already saved";return
        }
        scope.launch {
            try {
                val partial=File(directory,"memory.jsonl.partial");check(partial.createNewFile())
                FileOutputStream(partial).use {stream->
                    val text=stream.bufferedWriter();current.writeTo(text);text.flush();stream.fd.sync()
                }
                val complete=File(directory,"memory.jsonl");check(!complete.exists() && partial.renameTo(complete))
                saved=true;status.value="Memory scalars saved: ${directory.name}; experimental, not atomic"
            } catch(_:Throwable){status.value="Memory export failed; partial preserved; source/recorder untouched"}
            finally {exporting.set(false)}
        }
    }
}
