package org.lmthermal.field

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import org.lmthermal.lmtr.*

/** Host-only prototype for a future reviewed diagnostic; never used by current field safety.
 * Claims count declared stage holds, not unique heap roots: aliased owners may be overcounted.
 * Slots retain only token/domain/byte scalars, no payload, metadata, callback or weak-reference history.
 * Overflow/unknown release makes diagnostics unqualified; it cannot change recording decisions.
 */
class MemoryClaims(private var nextToken:Long=0) {
    enum class Stage { SOURCE,MEASUREMENT,PRESENTATION,PREPARATION,WRITER,PENDING,CODEC,VALIDATION }
    companion object { const val CAPACITY=64 }
    private val tokens=LongArray(CAPACITY);private val bytes=LongArray(CAPACITY);private val stages=IntArray(CAPACITY)
    private val live=LongArray(Stage.entries.size);private val peak=LongArray(Stage.entries.size)
    private var omitted=0L;private var badRelease=0L;private var qualified=true
    init {require(nextToken>=0)}
    /** Saturate diagnostics without reusing a token or changing the measured operation. */
    private fun omit():Long {if(omitted<Long.MAX_VALUE)omitted++;qualified=false;return 0}
    /** A bounded scalar claim, not a payload registration or a recorder admission decision. */
    @Synchronized fun begin(stage:Stage,byteCount:Long):Long {
        if(byteCount<0 || nextToken==Long.MAX_VALUE || live[stage.ordinal]>Long.MAX_VALUE-byteCount)return omit()
        var slot=0;while(slot<CAPACITY && tokens[slot]!=0L)slot++
        if(slot==CAPACITY)return omit()
        val token=++nextToken;tokens[slot]=token;bytes[slot]=byteCount;stages[slot]=stage.ordinal
        live[stage.ordinal]+=byteCount;peak[stage.ordinal]=maxOf(peak[stage.ordinal],live[stage.ordinal])
        return token
    }
    /** Release exactly this claim; stale tokens cannot reduce another stage owner's charge. */
    @Synchronized fun end(token:Long):Boolean {
        if(token==0L)return false // Already omitted: no owner or release callback was registered.
        var slot=0;while(slot<CAPACITY && tokens[slot]!=token)slot++
        if(slot==CAPACITY){if(badRelease<Long.MAX_VALUE)badRelease++;qualified=false;return false}
        live[stages[slot]]-=bytes[slot];tokens[slot]=0;bytes[slot]=0;stages[slot]=0;return true
    }
    /** Snapshot allocation is explicit and off callbacks; mutation of this copy cannot affect claims. */
    @Synchronized fun snapshot()=Snapshot(live.copyOf(),peak.copyOf(),tokens.count {it!=0L},omitted,badRelease,qualified)
    data class Snapshot(val live:LongArray,val peak:LongArray,val active:Int,val omitted:Long,val badRelease:Long,val qualified:Boolean)
}

/** Last 64 scalar rows only, with explicit overwrite/malformed-row counts. No scene inputs/API.
 * Missing values use -1, not an invented zero. Export copies at most 12 KiB outside hot callbacks.
 */
class MemoryProbeRing {
    companion object {const val ROWS=64;const val WIDTH=24}
    private val values=LongArray(ROWS*WIDTH);private var cursor=0;private var count=0;private var omitted=0L
    /** Copy one complete scalar row; never retain the caller's array or silently pad missing fields. */
    @Synchronized fun record(row:LongArray):Boolean {
        if(row.size!=WIDTH){if(omitted<Long.MAX_VALUE)omitted++;return false}
        if(count==ROWS){if(omitted<Long.MAX_VALUE)omitted++}else count++
        row.copyInto(values,cursor*WIDTH);cursor=(cursor+1)%ROWS;return true
    }
    /** Bounded chronological export for an off-callback consumer, with visible omissions. */
    @Synchronized fun snapshot():Pair<LongArray,Long> {
        val output=LongArray(count*WIDTH);val start=if(count==ROWS)cursor else 0
        repeat(count){i->values.copyInto(output,i*WIDTH,((start+i)%ROWS)*WIDTH,((start+i)%ROWS)*WIDTH+WIDTH)}
        return output to omitted
    }
}

/** Measure only host scalar instrumentation cost, not JNI/Debug/graphics/JSON/ART probe cost. */
object MemoryClaimProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1);val destination=File(args[0]);check(!destination.exists())
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported);bean.isThreadAllocatedMemoryEnabled=true
        val claims=MemoryClaims();val stage=MemoryClaims.Stage.PREPARATION
        fun batch(){repeat(100000){val token=claims.begin(stage,442368);check(claims.end(token))}}
        repeat(4){batch()}
        val samples=(0 until 8).map {
            val before=bean.getThreadAllocatedBytes(Thread.currentThread().id);val start=System.nanoTime();batch()
            val wall=System.nanoTime()-start;val allocated=bean.getThreadAllocatedBytes(Thread.currentThread().id)-before
            mapOf("pairs" to 100000,"wall_ns" to wall.toString(),"allocated_bytes" to allocated.toString())
        }
        val ring=MemoryProbeRing();val row=LongArray(MemoryProbeRing.WIDTH){-1}
        repeat(70){row[0]=it.toLong();ring.record(row)}
        val snap=claims.snapshot();val stored=ring.snapshot()
        check(snap.active==0 && snap.qualified && snap.omitted==0L && snap.badRelease==0L)
        destination.writeBytes(LmtrJson.encode(mapOf("scope" to "Host-only scalar claim core, no payloads or Android probe/serialization measurement",
            "forced_gc" to false,"samples" to samples,"claim_slots" to 64,"claim_primitive_backing_bytes" to (64*8*2+64*4+8*8*2),
            "ring_primitive_backing_bytes" to (64*24*8),"ring_snapshot_bytes" to stored.first.size*8,
            "ring_omitted_rows" to stored.second.toString(),"active_claims" to snap.active,"diagnostics_qualified" to snap.qualified)))
        println("Bounded primitive claims/ring measured; no source objects retained")
    }
}

/** Separate escaped/contended cost experiment: the single-thread JIT may elide locks.
 * Three workers share one ledger. Wall includes thread startup/join; worker allocations
 * exclude their creation, Android clocks/probes and export. It is not an ART upper bound.
 */
object MemoryClaimContentionProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1);val destination=File(args[0]);check(!destination.exists())
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported);bean.isThreadAllocatedMemoryEnabled=true
        val claims=MemoryClaims();val samples=mutableListOf<Obj>()
        repeat(12) {iteration ->
            val allocated=LongArray(3);val gate=java.util.concurrent.CountDownLatch(1)
            val error=java.util.concurrent.atomic.AtomicReference<Throwable?>();val start=System.nanoTime()
            val workers=(0..2).map {i->Thread {
                try {
                    gate.await();val id=Thread.currentThread().id;val before=bean.getThreadAllocatedBytes(id)
                    repeat(10000){val token=claims.begin(MemoryClaims.Stage.PREPARATION,442368);check(token>0);check(claims.end(token))}
                    allocated[i]=bean.getThreadAllocatedBytes(id)-before
                } catch(t:Throwable){error.compareAndSet(null,t)}
            }.apply {start()}}
            gate.countDown();workers.forEach {it.join(30000);check(!it.isAlive)};error.get()?.let {throw it}
            if(iteration>=4)samples.add(mapOf("pairs" to 30000,"wall_ns" to (System.nanoTime()-start).toString(),
                "worker_allocated_bytes" to allocated.sum().toString()))
        }
        val snap=claims.snapshot();check(snap.qualified && snap.active==0)
        destination.writeBytes(LmtrJson.encode(mapOf("scope" to "Three contended HotSpot workers; wall includes startup/join, allocation excludes thread creation and platform/export probes",
            "workers" to 3,"forced_gc" to false,"samples" to samples,"active_claims" to snap.active,
            "diagnostics_qualified" to snap.qualified)))
        println("Three-worker contention measured; no payload or device access")
    }
}
