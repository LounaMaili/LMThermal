package org.lmthermal.field

import org.lmthermal.camera.ThermalMeasurement
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.lmtr.Obj
import java.util.IdentityHashMap

/** Field-only, call-local census of explicitly supplied UI roles. It never keeps a strong
 * history or copies a source plane. This is not a controller/coroutine/Compose heap census.
 */
object VisibleMeasurementOwners {
    /** Collapse explicit HT wrappers sharing the same immutable evidence; unknown module
     * wrappers remain distinct. Anonymous owner labels exist only inside this one sample.
     */
    fun report(roles:Map<String,ThermalMeasurement?>):Obj {
        require(roles.size<=8)
        val owners=IdentityHashMap<Any,String>()
        val rows=roles.mapValues { (_,measurement) ->
            measurement?.let {
                val owner=(it as? Ht301ThermalMeasurement)?.evidence ?: it
                val label=owners[owner] ?: "o${owners.size}".also { name -> owners[owner]=name }
                mapOf("owner" to label,"sequence" to it.sequence.toString(),
                    "received_monotonic_ms" to it.receivedMonotonicMs.toString(),
                    "module_id" to it.provenance.moduleId.value)
            }
        }
        return mapOf("roles" to rows,"distinct_visible_owners" to owners.size,
            "scope" to "explicit_visible_roles_only_not_entire_source_UI_or_reachable_heap",
            "strong_history_retained" to false)
    }
}
