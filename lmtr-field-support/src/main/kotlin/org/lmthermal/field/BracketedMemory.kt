package org.lmthermal.field

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Fixed field-only scalar schema. These domains are sequential observations, never an atomic heap census.
 * Unknown values stay -1 internally/null in export. Sizes are bytes; clocks are monotonic nanoseconds.
 * Published-role aliases and logical recorder charges MUST NOT be summed as distinct live owners.
 */
enum class MemoryDomain(val fields:List<String>) {
    GC_BEFORE(listOf("allocated", "freed", "count", "time_ms")),
    JAVA(listOf("maximum", "occupied", "headroom")),
    RSS(listOf("proc_vm_rss")),
    ANDROID_MEMORY(listOf("total_pss", "summary_java", "summary_native", "summary_graphics", "summary_stack", "summary_code", "summary_private_other", "summary_system")),
    NATIVE_ALLOCATOR(listOf("allocated", "free", "heap_size")),
    PUBLISHED_ROLES(listOf("source_generation_before", "source_generation_after", "source_sequence", "rendered_sequence", "roi_sequence",
        "source_is_rendered", "source_is_roi", "rendered_is_roi", "bitmap_allocation", "legend_allocation", "nominal_celsius_plane",
        "received", "replaced", "malformed", "active_event_sequence", "pending_event_sequence")),
    RECORDER(listOf("run_origin", "state", "accepted", "acknowledged", "preparation_drops", "writer_drops", "preparation_queued",
        "preparation_in_flight", "writer_queued", "writer_in_flight", "pending", "journal", "chunks", "checkpoints")),
    NATIVE_CAPACITY(listOf("transfer_count", "transfer_capacity", "assembly_capacity", "delivered_capacity", "latest_size", "latest_capacity")),
    GC_AFTER(listOf("allocated", "freed", "count", "time_ms"))
}

enum class MemoryProbeStatus { OK, PARTIAL, UNSUPPORTED, FAILED, CANCELLED, CLOCK_INVALID, SOURCE_CHANGED }

/** Fill only scalars in the supplied fixed scratch, without retaining it or source owners.
 * Invoked only by the diagnostic worker; no callback is installed in acquisition, writer or ACK.
 */
fun interface MemoryScalarProbe {
    fun read(domain:MemoryDomain,values:LongArray):MemoryProbeStatus
}

/** Finite, preallocated diagnostic window, independent from all recorder decisions.
 * A single collector owns scratch; Stop is a nonblocking atomic latch. A blocked platform read
 * cannot be forcibly interrupted or exported as complete. No thread or source is owned here.
 * The first 64 rows are preserved, never overwritten; over-capacity/contended attempts are counted.
 */
class BracketedMemoryWindow(val generation:Long,private val probe:MemoryScalarProbe,private val now:()->Long) {
    companion object {
        const val CAPACITY=64
        const val VALUES=16
        const val HEADER=6
        const val STRIDE=3+VALUES
        val WIDTH=HEADER+MemoryDomain.entries.size*STRIDE
        val PRIMITIVE_BYTES=(CAPACITY*WIDTH+WIDTH+VALUES)*8
        const val DECLARED_PRIMITIVE_CAP=128*1024
    }
    private val rows=LongArray(CAPACITY*WIDTH)
    private val scratch=LongArray(WIDTH)
    private val values=LongArray(VALUES)
    private val stop=AtomicBoolean()
    private val collecting=AtomicBoolean()
    private val contended=AtomicLong()
    private val lock=Any()
    @Volatile private var count=0
    @Volatile private var full=false
    @Volatile private var failed=false
    @Volatile private var clockFailed=false
    init {require(generation>0);check(PRIMITIVE_BYTES<=DECLARED_PRIMITIVE_CAP)}

    /** Never waits for a probe, source lock, writer, file or collector monitor. */
    fun requestStop() {stop.set(true)}
    fun stopped()=stop.get()
    fun quiescent()=stop.get() && !collecting.get()
    fun rowCount()=count

    /** Diagnostic clock failure remains explicit; it never substitutes a source/intake clock. */
    private fun tick():Long=try {now().also {if(it<0)clockFailed=true}}catch(_:Throwable){clockFailed=true;-1}

    /** At most one attempt executes; diagnostic faults cannot escape into a recorder or its Stop. */
    fun sample():Boolean {
        if(stop.get())return false
        if(!collecting.compareAndSet(false,true)){contended.updateAndGet {if(it==Long.MAX_VALUE)it else it+1};return false}
        try {
            if(stop.get())return false
            if(count==CAPACITY){full=true;requestStop();return false}
            scratch.fill(-1);scratch[0]=count.toLong();scratch[1]=generation;scratch[2]=tick()
            for(domain in MemoryDomain.entries) {
                if(stop.get())break
                values.fill(-1)
                val offset=HEADER+domain.ordinal*STRIDE
                val begin=tick();var status=MemoryProbeStatus.FAILED
                try {status=probe.read(domain,values)}
                catch(_:CancellationException){status=MemoryProbeStatus.CANCELLED;requestStop()}
                catch(_:OutOfMemoryError){failed=true;requestStop()}
                catch(_:Throwable){failed=true}
                if(status==MemoryProbeStatus.FAILED || status==MemoryProbeStatus.CANCELLED)failed=true
                if(status==MemoryProbeStatus.CANCELLED)requestStop()
                if(status==MemoryProbeStatus.CLOCK_INVALID)clockFailed=true
                if(status==MemoryProbeStatus.UNSUPPORTED)values.fill(-1)
                val end=tick()
                for(i in domain.fields.indices)if(values[i]<0)values[i]=-1
                if(status==MemoryProbeStatus.OK && domain.fields.indices.any {values[it]<0})status=MemoryProbeStatus.PARTIAL
                if(begin<0 || end<begin){clockFailed=true;status=MemoryProbeStatus.CLOCK_INVALID}
                scratch[offset]=begin;scratch[offset+1]=end;scratch[offset+2]=status.ordinal.toLong()
                values.copyInto(scratch,offset+3,0,domain.fields.size)
            }
            scratch[3]=tick();scratch[4]=if(stop.get())1 else 0
            val before=HEADER+MemoryDomain.GC_BEFORE.ordinal*STRIDE+3+2
            val after=HEADER+MemoryDomain.GC_AFTER.ordinal*STRIDE+3+2
            // 0 unknown, 1 unchanged counter (still not atomic), 2 advanced, 3 regressed.
            scratch[5]=when {scratch[before]<0 || scratch[after]<0 -> 0;scratch[after]<scratch[before] -> 3
                scratch[after]>scratch[before] -> 2;else -> 1}
            if(scratch[3]<scratch[2])clockFailed=true
            synchronized(lock){scratch[4]=if(stop.get())1 else 0;scratch.copyInto(rows,count*WIDTH);count++}
            return true
        } finally {collecting.set(false)}
    }

    /** Stream scalars after quiescence, never copy the ring or encode a whole JSON history.
     * Output failure belongs to diagnostics only; the caller preserves partial files separately.
     */
    fun writeTo(out:Appendable) {
        synchronized(lock) {
            check(quiescent()){"Diagnostic probe still running; no complete export"}
            out.append("{\"format\":\"r4-memory-probes-1\",\"generation\":\"").append(generation.toString())
                .append("\",\"rows\":").append(count.toString()).append(",\"contended_attempts\":\"").append(contended.get().toString())
                .append("\",\"capacity_exceeded\":").append(full.toString()).append(",\"probe_failed\":").append(failed.toString())
                .append(",\"clock_failed\":").append(clockFailed.toString()).append(",\"atomic\":false,\"primitive_bytes\":")
                .append(PRIMITIVE_BYTES.toString()).append(",\"domains\":[")
            for(d in MemoryDomain.entries) {
                if(d.ordinal>0)out.append(',')
                out.append("{\"id\":\"").append(d.name).append("\",\"fields\":[")
                d.fields.forEachIndexed {i,f->if(i>0)out.append(',');out.append('"').append(f).append('"')};out.append("]}")
            }
            out.append("],\"statuses\":[")
            MemoryProbeStatus.entries.forEachIndexed {i,s->if(i>0)out.append(',');out.append('"').append(s.name).append('"')}
            out.append("]}\n")
            repeat(count) {index ->
                val start=index*WIDTH
                out.append("{\"sample\":").append(index.toString()).append(",\"values\":[")
                repeat(WIDTH) {i->if(i>0)out.append(',');val v=rows[start+i];if(v<0)out.append("null")else out.append('"').append(v.toString()).append('"')}
                out.append("]}\n")
            }
        }
    }
}
