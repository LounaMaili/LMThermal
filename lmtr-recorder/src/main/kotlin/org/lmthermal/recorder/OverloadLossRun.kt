package org.lmthermal.recorder

import org.lmthermal.lmtr.*

/** EXPERIMENT ONLY: locked queued-tail aggregation, never another source of capacity.
 * One original reservation remains charged for each loss. Mutation ends when the writer
 * removes its Work under the same engine lock. No payload, caller facts or exception retained.
 * All genuine intake witnesses survive in optional experimental evidence; no source clock is invented.
 */
internal class OverloadLossRun(private val streamId:String,private val stream:FrozenJson,
    private val category:String,val firstSequence:Long,firstWitness:Long) {
    companion object {
        const val MAX_LOSSES=32
        const val NAMESPACE="org.lmthermal.diagnostic.loss-witnesses"
        fun eligible(category:String?,facts:FrozenJson?) = facts==null &&
            (category=="preparation_overload" || category=="writer_overload")
    }
    private val witnesses=LongArray(MAX_LOSSES)
    var count=1;private set
    init { require(eligible(category,null) && firstSequence>=0);witnesses[0]=firstWitness }

    /** Called only while this run is the queued tail and both participants own the intake lock. */
    fun extend(id:String,definition:FrozenJson,kind:String?,sequence:Long,witness:Long,facts:FrozenJson?):Boolean {
        if(!eligible(kind,facts) || kind!=category || id!=streamId || !stream.same(definition) ||
            count==MAX_LOSSES || firstSequence>Long.MAX_VALUE-count || sequence!=firstSequence+count ||
            witness<witnesses[count-1])return false
        witnesses[count]=witness;count++;return true
    }

    /** Writer-only immutable semantic value after dequeue; per-event witnesses remain exact. */
    fun facts():Obj {
        val core:Obj=mapOf("stream_id" to streamId,"category" to category,"reason" to category,
            "count" to count.toString(),"observation_first" to firstSequence.toString(),
            "observation_last" to (firstSequence+count-1).toString(),"device_first" to null,
            "device_last" to null,"scheduled" to null,
            "evidence" to mapOf("basis" to "host_observation","before" to null,"after" to null))
        if(count==1)return core
        return core+("extensions" to mapOf(NAMESPACE to mapOf("schema_version" to mapOf("major" to 1,"minor" to 0),
            "required_feature" to null,"data" to mapOf("experimental" to true,"observation_first" to firstSequence.toString(),
                "intake_monotonic_ns" to (0 until count).map { witnesses[it].toString() }))))
    }
}
