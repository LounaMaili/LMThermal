package org.lmthermal.field

import android.os.Debug
import java.io.FileInputStream
import java.lang.ref.WeakReference

/** Opt-in field APK probes only. No native handle access, GC request or source/camera operation.
 * Reused scratch belongs to one diagnostic collector. The weak model binding cannot keep a
 * destroyed camera/presenter alive while a platform probe is blocked. Every dereference is local.
 */
internal class AndroidMemoryProbe(private val model:WeakReference<FieldModel>):MemoryScalarProbe {
    private val memory=Debug.MemoryInfo()
    private val rss=BoundedProcRss()
    private val runtime=Runtime.getRuntime()
    override fun read(domain:MemoryDomain,values:LongArray):MemoryProbeStatus {
        when(domain) {
            MemoryDomain.JAVA -> {
                val maximum=runtime.maxMemory();val occupied=runtime.totalMemory()-runtime.freeMemory()
                values[0]=maximum
                if(occupied !in 0..maximum)return MemoryProbeStatus.PARTIAL
                values[1]=occupied;values[2]=maximum-occupied
            }
            MemoryDomain.GC_BEFORE,MemoryDomain.GC_AFTER -> {
                for((i,key) in GC_KEYS.withIndex())values[i]=number(Debug.getRuntimeStat(key))
            }
            MemoryDomain.RSS -> FileInputStream("/proc/self/status").use {values[0]=rss.read(it)}
            MemoryDomain.ANDROID_MEMORY -> {
                // summary.* uses Android's category accounting, not Runtime occupancy or per-owner PSS.
                // Keep raw category names separate from totalPss; never reconstruct native RSS by subtraction.
                Debug.getMemoryInfo(memory)
                values[0]=memory.totalPss.toLong()*1024
                for((i,key) in MEMORY_KEYS.withIndex()) {
                    val kib=number(memory.getMemoryStat(key))
                    values[i+1]=if(kib in 0..Long.MAX_VALUE/1024)kib*1024 else -1
                }
            }
            MemoryDomain.NATIVE_ALLOCATOR -> {
                values[0]=Debug.getNativeHeapAllocatedSize();values[1]=Debug.getNativeHeapFreeSize();values[2]=Debug.getNativeHeapSize()
            }
            MemoryDomain.PUBLISHED_ROLES -> return model.get()?.memoryRoleScalars(values) ?: MemoryProbeStatus.UNSUPPORTED
            MemoryDomain.RECORDER -> return model.get()?.memoryRecorderScalars(values) ?: MemoryProbeStatus.UNSUPPORTED
            // Current NativeUvc API exposes counters, not allocations/capacities. Never infer these from RSS.
            MemoryDomain.NATIVE_CAPACITY -> return MemoryProbeStatus.UNSUPPORTED
        }
        return MemoryProbeStatus.OK
    }
    /** Reject missing/inexact platform counters instead of constructing a zero or a huge Decimal. */
    private fun number(text:String?):Long = if(text==null || text.length !in 1..19 || text.any {it !in '0'..'9'})-1
        else text.toLongOrNull() ?: -1
    private companion object {
        val GC_KEYS=arrayOf("art.gc.bytes-allocated","art.gc.bytes-freed","art.gc.gc-count","art.gc.gc-time")
        val MEMORY_KEYS=arrayOf("summary.java-heap","summary.native-heap","summary.graphics","summary.stack","summary.code","summary.private-other","summary.system")
    }
}
