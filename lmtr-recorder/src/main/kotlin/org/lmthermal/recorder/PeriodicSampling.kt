package org.lmthermal.recorder

import org.lmthermal.lmtr.*

/** Explicit experimental 5 Hz policy. Start is a genuine reading in the HEADER clock domain.
 * Slot k opens at start+k*200ms and accepts the first newly received observation in that
 * half-open window. Stop excludes an unopened boundary slot, but never retracts a callback
 * already ordered before Stop at the same clock reading. No camera cadence is changed.
 */
data class FiveHertzSampling(val startMonotonicNs:Long) {
    init { require(startMonotonicNs>=0) }
    companion object { const val PERIOD_NS=200000000L }
}

/** Bounded scalar snapshot. Intake decisions and synced acknowledgements are separate quantities. */
data class PeriodicRecordingSnapshot(val acquired:Long,val notSelected:Long,val lateNotSelected:Long,
    val selected:Long,val selectedAvailable:Long,val selectedUnavailable:Long,val missedDetected:Long,
    val missedJournaled:Long,val plannedSlots:Long?,val cutoffNs:Long?,val ledgerComplete:Boolean,
    val failure:String?,val acknowledgedAvailable:Long,val acknowledgedUnavailable:Long,
    val acknowledgedMissed:Long,val acknowledgedPreparationLoss:Long,val acknowledgedWriterLoss:Long,
    val materializationLosses:Long,val unclassifiedCallbacks:Long) {
    /** Diagnostic JSON uses strings for exact counters; it is not a new LMTR required schema. */
    fun report():Obj=mapOf("acquired_callbacks" to acquired.toString(),"intentionally_not_selected" to notSelected.toString(),
        "late_not_selected" to lateNotSelected.toString(),"selected" to selected.toString(),
        "selected_available" to selectedAvailable.toString(),"selected_unavailable" to selectedUnavailable.toString(),
        "missed_detected" to missedDetected.toString(),"missed_journaled" to missedJournaled.toString(),
        "planned_slots" to plannedSlots?.toString(),"cutoff_ns" to cutoffNs?.toString(),"ledger_complete" to ledgerComplete,
        "first_periodic_failure" to failure,"acknowledged_available" to acknowledgedAvailable.toString(),
        "acknowledged_unavailable" to acknowledgedUnavailable.toString(),"acknowledged_missed" to acknowledgedMissed.toString(),
        "acknowledged_preparation_loss" to acknowledgedPreparationLoss.toString(),"acknowledged_writer_loss" to acknowledgedWriterLoss.toString(),
        "materialization_losses" to materializationLosses.toString(),"unclassified_callbacks_at_failure" to unclassifiedCallbacks.toString())
}

/** All mutations run under RecorderEngine's existing intake lock; there is no new thread/queue.
 * Retains two bounded stream definitions and scalars only, never an acquired owner or a matrix.
 * Local metadata bounds fit the existing 32 KiB control-item/termination accounting allowance.
 */
internal class PeriodicSamplingState(val policy:FiveHertzSampling,header:Obj,stream:Obj) {
    val originalStream=FrozenJson(stream)
    private val origin=header["timeline"].obj()["origin_monotonic_ns"].d()
    private val clockId=header["timeline"].obj()["clock_domain_id"].text()
    private val relativeStart=Math.subtractExact(policy.startMonotonicNs,origin)
    val selectedStream:FrozenJson
    // Counter/scalar objects <=4 KiB; a terminal JSON closure <=4 KiB. No retained AST/cache.
    val staticMetadataCharge:Long get()=originalStream.byteSize.toLong()+selectedStream.byteSize+8192
    private var nextOriginal:Long?=null
    private var lastReceipt:Long?=null
    private var nextUncovered=0L
    private var lastSelectedSlot=-1L
    private var acquired=0L;private var notSelected=0L;private var lateNotSelected=0L
    private var selected=0L;private var available=0L;private var unavailable=0L
    private var missedDetected=0L;private var missedJournaled=0L
    private var planned:Long?=null;private var cutoff:Long?=null
    private var sourceLossEvents=0L;private var knownSourceLoss=0L;private var unknownSourceLossEvents=0L
    private var ackAvailable=0L;private var ackUnavailable=0L;private var ackMissed=0L
    private var ackPreparationLoss=0L;private var ackWriterLoss=0L;private var invalidEvidence=0L
    private var complete=false;private var firstFailure:String?=null

    init {
        require(relativeStart>=0 && originalStream.byteSize<=8192)
        require(stream["sampling"].obj()["mode"]=="acquisition")
        val sourceClock=stream["clocks"].arr().map {it.obj()}.single {it["id"]==clockId}
        require(sourceClock["kind"]=="host_monotonic" && sourceClock["quality"]=="known" &&
            sourceClock["tick_period_ns"].obj()==mapOf("numerator" to "1","denominator" to "1")) {
            "Periodic selection requires an explicitly shared host-monotonic epoch"
        }
        val annotated=PeriodicRecordingEvidence.attach(stream,mapOf("counter_domain" to "selected_callback_count",
            "original_counter" to "original_acquisition_ordinal","selection" to "first_new_receipt_in_half_open_window",
            "start_monotonic_ns" to policy.startMonotonicNs.toString(),
            "stop_boundary" to "exclude_unopened_boundary_include_preordered_admission"))+
            ("sampling" to mapOf("mode" to "periodic","period_ns" to FiveHertzSampling.PERIOD_NS.toString(),
                "origin_timeline_ns" to relativeStart.toString()))
        selectedStream=FrozenJson(annotated);require(selectedStream.byteSize<=16384)
    }

    /** Observe every delivered callback before selection; holes/regressions are not disguised as decimation. */
    fun sourceError(offer:RecordingOffer,now:Long):String? {
        if(!originalStream.same(offer.stream))return "periodic_source_changed"
        val source=offer.sourceWitness ?: return "periodic_missing_source_witness"
        if(source.receiptClockId!=clockId || source.receiptNs>now)return "periodic_uncorrelated_receipt"
        if(lastReceipt?.let { source.receiptNs<it }==true)return "periodic_receipt_regressed"
        if(nextOriginal?.let { offer.sequence!=it }==true)return "periodic_original_ordinal_discontinuity"
        nextOriginal=Math.addExact(offer.sequence,1);lastReceipt=source.receiptNs
        acquired=Math.addExact(acquired,1)
        return null
    }

    /** One contiguous range for all newly closed empty windows; no per-tick queued timer records. */
    fun missing(now:Long,stopping:Boolean):LongRange? {
        val elapsed=Math.subtractExact(now,policy.startMonotonicNs);require(elapsed>=0)
        val last=if(stopping) {
            val beforeCutoff=if(elapsed==0L)-1 else (elapsed-1)/FiveHertzSampling.PERIOD_NS
            maxOf(beforeCutoff,lastSelectedSlot)
        } else elapsed/FiveHertzSampling.PERIOD_NS-1
        return if(nextUncovered<=last)nextUncovered..last else null
    }
    fun missingFacts(range:LongRange):Obj=mapOf("category" to "missed_schedule","reason" to "no_new_observation_in_window",
        "count" to Math.addExact(range.last-range.first,1).toString(),"scheduled" to schedule(range),
        "observation_first" to null,"observation_last" to null,"device_first" to null,"device_last" to null,
        "evidence" to mapOf("basis" to "declared_schedule","before" to null,"after" to null))
    fun missingDetected(range:LongRange) {
        val count=Math.addExact(range.last-range.first,1)
        missedDetected=Math.addExact(missedDetected,count)
        nextUncovered=Math.addExact(range.last,1)
    }
    fun missingAdmitted(range:LongRange,admitted:Boolean) {
        val count=Math.addExact(range.last-range.first,1)
        if(admitted)missedJournaled=Math.addExact(missedJournaled,count) else fail("periodic_schedule_journal_refused")
    }
    /** Return null for intentional policy nonselection. An old receipt cannot fill the current window. */
    fun select(offer:RecordingOffer,now:Long):RecordingSelectionWitness? {
        val slot=(now-policy.startMonotonicNs)/FiveHertzSampling.PERIOD_NS
        val opening=Math.addExact(policy.startMonotonicNs,Math.multiplyExact(slot,FiveHertzSampling.PERIOD_NS))
        val source=checkNotNull(offer.sourceWitness)
        if(nextUncovered>slot || source.receiptNs<opening) {
            notSelected=Math.addExact(notSelected,1)
            if(source.receiptNs<opening)lateNotSelected=Math.addExact(lateNotSelected,1)
            return null
        }
        val witness=RecordingSelectionWitness(slot,deadline(slot),offer.sequence,selected,source)
        selected=Math.addExact(selected,1)
        if(source.measurementAvailable)available=Math.addExact(available,1) else unavailable=Math.addExact(unavailable,1)
        nextUncovered=Math.addExact(slot,1);lastSelectedSlot=slot
        return witness
    }
    fun sourceLoss(facts:Obj) {
        sourceLossEvents=Math.addExact(sourceLossEvents,1)
        facts["count"]?.let {knownSourceLoss=Math.addExact(knownSourceLoss,it.d())}
            ?:run {unknownSourceLossEvents=Math.addExact(unknownSourceLossEvents,1)}
    }
    fun invalidMaterialization() { invalidEvidence=Math.addExact(invalidEvidence,1) }
    fun fail(reason:String) { if(firstFailure==null)firstFailure=reason;complete=false }
    fun close(now:Long,represented:Boolean) {
        cutoff=now
        val elapsed=now-policy.startMonotonicNs
        planned=maxOf(if(elapsed<=0)0 else Math.addExact((elapsed-1)/FiveHertzSampling.PERIOD_NS,1),Math.addExact(lastSelectedSlot,1))
        complete=represented && firstFailure==null && selected+missedJournaled==planned && acquired==selected+notSelected
    }
    /** Called only from the existing successful CHUNK-footer acknowledgement callback. */
    fun acknowledged(entries:List<PendingEntry>) {
        for(pending in entries) {
            val entry=pending.entry
            when(entry["kind"]) {
                "observation" -> if(entry["measurement"].obj()["status"]=="available")ackAvailable++ else ackUnavailable++
                "gap" -> when(entry["category"]) {
                    "missed_schedule" -> ackMissed=Math.addExact(ackMissed,entry["count"].d())
                    "preparation_overload" -> ackPreparationLoss=Math.addExact(ackPreparationLoss,entry["count"].d())
                    "writer_overload" -> ackWriterLoss=Math.addExact(ackWriterLoss,entry["count"].d())
                }
            }
        }
    }
    /** Segment-end evidence describes producer decisions, not unsynced pending work as durable ACKs. */
    fun closure(preparationDrops:Long,writerDrops:Long):Obj {
        val data=mapOf("counter_scope" to "producer_decisions_not_durable_acknowledgements",
            "start_monotonic_ns" to policy.startMonotonicNs.toString(),"cutoff_monotonic_ns" to cutoff?.toString(),
            "period_ns" to FiveHertzSampling.PERIOD_NS.toString(),"planned_slots" to planned?.toString(),
            "acquired_callbacks" to acquired.toString(),"intentionally_not_selected" to notSelected.toString(),
            "unclassified_callbacks_at_failure" to (acquired-selected-notSelected).toString(),
            "late_receipt_not_selected" to lateNotSelected.toString(),"selected_observations" to selected.toString(),
            "selected_available" to available.toString(),"selected_unavailable" to unavailable.toString(),
            "missed_slots_detected" to missedDetected.toString(),"missed_slots_journaled" to missedJournaled.toString(),
            "preparation_losses" to preparationDrops.toString(),"writer_losses" to writerDrops.toString(),
            "invalid_evidence_losses" to invalidEvidence.toString(),"source_loss_events" to sourceLossEvents.toString(),
            "known_source_loss_count" to knownSourceLoss.toString(),"unknown_source_loss_events" to unknownSourceLossEvents.toString(),
            "selection_ledger_complete" to complete,"failure" to firstFailure)
        require(LmtrJson.encode(data).size<=4096)
        return mapOf(PeriodicRecordingEvidence.NAMESPACE to PeriodicRecordingEvidence.extension(data))
    }
    fun snapshot()=PeriodicRecordingSnapshot(acquired,notSelected,lateNotSelected,selected,available,unavailable,
        missedDetected,missedJournaled,planned,cutoff,complete,firstFailure,ackAvailable,ackUnavailable,
        ackMissed,ackPreparationLoss,ackWriterLoss,invalidEvidence,acquired-selected-notSelected)
    private fun deadline(slot:Long)=Math.addExact(relativeStart,Math.multiplyExact(slot,FiveHertzSampling.PERIOD_NS))
    private fun schedule(range:LongRange):Obj=mapOf("first_slot" to range.first.toString(),"last_slot" to range.last.toString(),
        "first_deadline_ns" to deadline(range.first).toString(),"last_deadline_ns" to deadline(range.last).toString())
}
