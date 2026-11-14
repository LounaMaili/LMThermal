package org.lmthermal.recorder

import org.lmthermal.lmtr.Obj

/** Optional finite-run stop target in the very same monotonic ns domain as HEADER.
 * This is a notification/admission boundary, not permission to backdate a late Stop.
 * The existing intake lock orders expiry, observations, losses and ordinary Stop. Workers
 * still drain accepted owners and acknowledge only after the unchanged validation/sync.
 */
data class RecordingStopDeadline(val monotonicNs:Long) {
    init { require(monotonicNs>=0) }
    companion object { const val REASON="duration_deadline" }
}

/** Scalar-only diagnostics. Unqualified clock failure never becomes an exact duration claim.
 * These few scalars fit the existing periodic metadata allowance; no timer queue or thread.
 */
data class RecordingDeadlineSnapshot(val targetNs:Long,val firstObservedExpiryNs:Long?,
    val cutoffNs:Long?,val clockQualified:Boolean,val reason:String?) {
    fun report():Obj=mapOf("target_monotonic_ns" to targetNs.toString(),
        "first_observed_expiry_monotonic_ns" to firstObservedExpiryNs?.toString(),
        "cutoff_monotonic_ns" to cutoffNs?.toString(),"clock_qualified" to clockQualified,
        "current_engine_reason" to reason,"overshoot_ns" to if(clockQualified)cutoffNs?.let {maxOf(0,it-targetNs).toString()} else null,
        "scope" to "actual_serialized_stop_witness_not_backdated_to_target")
}
