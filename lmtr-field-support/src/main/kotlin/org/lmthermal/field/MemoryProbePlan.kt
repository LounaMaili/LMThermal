package org.lmthermal.field

/** Field diagnostic choices, not source cadence or recorder policy. The original plan remains
 * the default. The opt-in tiered plan skips the entire costly Android MemoryInfo domain (PSS
 * and all summary categories) on four of five rows. Every actual read keeps its own brackets;
 * skipped values/times are unknown, never carried forward. Row cadence is not wall-clock Hz.
 */
enum class MemoryProbePlan(val format:String,val androidMemoryEveryRows:Int) {
    REFERENCE("r4-memory-probes-1",1),
    TIERED("r4-memory-probes-2",5);

    fun scheduled(domain:MemoryDomain,row:Int):Boolean =
        domain!=MemoryDomain.ANDROID_MEMORY || row%androidMemoryEveryRows==0
}
