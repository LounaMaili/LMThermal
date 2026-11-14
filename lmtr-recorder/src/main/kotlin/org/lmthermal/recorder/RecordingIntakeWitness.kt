package org.lmthermal.recorder

import org.lmthermal.lmtr.*

/** Small immutable original-source facts, captured without copying a matrix or retaining its owner.
 * Periodic selection requires the receipt clock to be the HEADER's actual host-monotonic domain.
 * It never guesses a mapping between nanoTime, elapsedRealtime or a sensor clock.
 */
data class RecordingSourceWitness(val receiptClockId:String,val receiptNs:Long,
    val callbackSequence:Long?,val measurementAvailable:Boolean,val unavailableReason:String?) {
    init {
        require(receiptNs>=0 && (callbackSequence==null || callbackSequence>=0))
        require(unavailableReason==null || unavailableReason.length<=256)
        require(measurementAvailable || (unavailableReason!=null && unavailableReason.length<=256))
    }
    /** A malformed adapter cannot change receipt/availability when its deferred owner is frozen. */
    internal fun verify(facts:Obj) {
        val receipt=facts["receipt"].obj();val stamp=receipt["stamp"].obj()
        require(receipt["status"]=="known" && stamp["clock_domain_id"]==receiptClockId && stamp["ticks"].d()==receiptNs)
        val measurement=facts["measurement"].obj()
        require((measurement["status"]=="available")==measurementAvailable)
        if(!measurementAvailable)require(measurement["reason"]==unavailableReason)
    }
}

/** Selected-slot identity survives both overload stages; only scalars and one bounded reason remain.
 * `originalOrdinal` counts all acquired callbacks, `selectedSequence` only selected observations.
 * Deadline is planned time; receipt remains the original reading and is never replaced by it.
 */
data class RecordingSelectionWitness(val slot:Long,val deadlineNs:Long,val originalOrdinal:Long,
    val selectedSequence:Long,val source:RecordingSourceWitness) {
    init { require(slot>=0 && deadlineNs>=0 && originalOrdinal>=0 && selectedSequence>=0) }
    internal fun schedule():Obj=mapOf("first_slot" to slot.toString(),"last_slot" to slot.toString(),
        "first_deadline_ns" to deadlineNs.toString(),"last_deadline_ns" to deadlineNs.toString())
    internal fun evidence():Obj=mapOf("original_acquisition_ordinal" to originalOrdinal.toString(),
        "original_callback_sequence" to source.callbackSequence?.toString(),"selected_sequence" to selectedSequence.toString(),
        "original_receipt_monotonic_ns" to source.receiptNs.toString(),
        "measurement_available_at_source" to source.measurementAvailable,"unavailable_reason" to source.unavailableReason)
}

/** Optional producer convention using the existing v1 extension envelope, not a new required feature. */
object PeriodicRecordingEvidence {
    const val NAMESPACE="org.lmthermal.recorder.periodic-v1"
    internal fun extension(data:Obj):Obj=mapOf("schema_version" to mapOf("major" to 1,"minor" to 0),
        "required_feature" to null,"data" to data)
    internal fun attach(original:Obj,data:Obj):Obj {
        val extensions=original["extensions"]?.obj() ?: emptyMap()
        require(NAMESPACE !in extensions) { "Producer extension namespace collision" }
        return original+("extensions" to (extensions+(NAMESPACE to extension(data))))
    }
}
