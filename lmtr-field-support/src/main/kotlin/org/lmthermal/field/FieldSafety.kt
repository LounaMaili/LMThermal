package org.lmthermal.field

import org.lmthermal.lmtr.Obj

/** Actual sampled prerequisites, separate from logical engine reservations and format limits.
 * Unknown safety facts refuse unattended intake rather than manufacturing a healthy device.
 */
data class FieldHealth(val batteryPercent:Int?,val thermalStatus:Int?,val freeBytes:Long?,
    val javaHeadroomBytes:Long,val systemAvailableBytes:Long,val systemLowMemory:Boolean,
    val rssBytes:Long?,val cameraPermission:Boolean,val foregroundUnlocked:Boolean) {
    fun report():Obj=mapOf("battery_percent" to batteryPercent,"android_thermal_status" to thermalStatus,
        "free_bytes" to freeBytes?.toString(),"java_headroom_bytes" to javaHeadroomBytes.toString(),
        "system_available_bytes" to systemAvailableBytes.toString(),"system_low_memory" to systemLowMemory,
        "rss_bytes" to rssBytes?.toString(),"camera_permission" to cameraPermission,
        "foreground_unlocked" to foregroundUnlocked)
}

/** Conservative test policy, not a claim that sampled RSS bounds ART or indefinite runtime.
 * Admission reserves logical recorder bytes plus 32 MiB Java/system margin. Runtime stops below
 * that margin, below reserved storage headroom, or above baseline RSS + declared memory budget.
 * Five consecutive sub-half-cadence samples or three seconds without source/valid measurements
 * stop the run. These guard thresholds do not replace matched-baseline acceptance analysis.
 */
class FieldSafety(private val plan:FieldPreflight,private val initialRss:Long,private val startNs:Long,
    private val initialReceived:Long) {
    companion object {
        const val HEAP_MARGIN_BYTES=32L*1048576
        const val STALE_NS=3000000000L
        fun refusal(health:FieldHealth,plan:FieldPreflight,admission:Boolean):String? = when {
            !health.cameraPermission -> "camera_permission_missing"
            !health.foregroundUnlocked -> "foreground_or_unlock_missing"
            health.batteryPercent==null -> "battery_unknown"
            health.batteryPercent<30 -> "battery_below_30"
            health.thermalStatus==null -> "thermal_status_unknown"
            health.thermalStatus>=3 -> "thermal_severe"
            health.freeBytes==null -> "storage_unknown"
            health.freeBytes<(if(admission)plan.requiredFreeBytes else plan.headroomBytes) -> "storage_reserve_low"
            health.systemLowMemory -> "android_low_memory"
            health.rssBytes==null -> "rss_unknown"
            health.javaHeadroomBytes<HEAP_MARGIN_BYTES+(if(admission)plan.limits.reservedBytes else 0) -> "java_headroom_low"
            health.systemAvailableBytes<HEAP_MARGIN_BYTES+(if(admission)plan.limits.reservedBytes else 0) -> "system_headroom_low"
            else -> null
        }
    }
    private var lastNs=startNs
    private var lastReceived=initialReceived
    private var sourceChangedNs=startNs
    private var slowSamples=0
    /** Called at approximately 1 Hz; counts use native callbacks, not presentation publications. */
    fun check(health:FieldHealth,nowNs:Long,received:Long,lastValidNs:Long?,queueWithinBounds:Boolean=true):String? {
        refusal(health,plan,false)?.let {return it}
        if(nowNs<lastNs || received<lastReceived)return "source_or_sample_regressed"
        if(!queueWithinBounds)return "queue_bound_exceeded"
        if(health.rssBytes!!>initialRss && health.rssBytes-initialRss>plan.limits.memoryBudgetBytes)return "rss_increment_exceeded"
        if(lastValidNs!=null && lastValidNs>nowNs)return "source_clock_future"
        if(received!=lastReceived)sourceChangedNs=nowNs
        if(nowNs-sourceChangedNs>=STALE_NS)return "source_stalled"
        if(nowNs-(lastValidNs ?: startNs)>=STALE_NS)return "valid_measurement_stalled"
        val elapsed=nowNs-lastNs
        if(elapsed>=500000000L) {
            // Initial five seconds are not treated as steady cadence; no counters are fabricated.
            val fps=(received-lastReceived)*1e9/elapsed
            if(nowNs-startNs>=5000000000L && fps<12.5)slowSamples++ else slowSamples=0
            lastNs=nowNs;lastReceived=received
        }
        return if(slowSamples>=5)"sustained_acquisition_below_half_nominal" else null
    }
}
