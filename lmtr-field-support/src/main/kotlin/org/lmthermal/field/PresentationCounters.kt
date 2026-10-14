package org.lmthermal.field

import org.lmthermal.camera.ThermalMeasurement
import org.lmthermal.lmtr.Obj
import java.lang.ref.WeakReference

/** Bounded field-only numerical observer: weak owners never retain source matrices.
 * Reachability samples are observations without GC; request replacement is not source loss.
 */
class PresentationCounters {
    private val counts=mutableMapOf<String,Long>()
    private val owners=linkedMapOf<Long,WeakReference<Any>>()
    private val render=FieldDistribution();private val roi=FieldDistribution()
    private var active:Long?=null;private var pending:Long?=null
    @Synchronized fun event(kind:String,m:ThermalMeasurement,elapsedNs:Long) {
        counts[kind]=(counts[kind]?:0)+1
        owners[m.sequence]=WeakReference((m as? org.lmthermal.camera.ht301.Ht301ThermalMeasurement)?.evidence ?: m)
        while(owners.size>128)owners.remove(owners.keys.first())
        when(kind) {
            "request" -> {if(pending!=null)counts["pending_replaced"]=(counts["pending_replaced"]?:0)+1;pending=m.sequence}
            "started" -> {active=m.sequence;if(pending==m.sequence)pending=null}
            "completed" -> {active=null;render.add(elapsedNs)}
            "failed" -> active=null
            "rejected_before" -> if(pending==m.sequence)pending=null
            "roi_completed" -> roi.add(elapsedNs)
        }
    }
    @Synchronized fun report():Obj=mapOf("events" to counts.toMap(),"render_wall_ns" to render.report(),
        "roi_wall_ns" to roi.report(),"active_sequence" to active?.toString(),"pending_sequence" to pending?.toString(),
        "observed_weak_owners" to owners.size,"observed_alive_weak_owners" to owners.values.count{it.get()!=null},
        "weak_owner_capacity" to 128,"reachability_scope" to "sampled_HT_measurements_else_wrappers_not_entire_heap_no_forced_GC")
}
