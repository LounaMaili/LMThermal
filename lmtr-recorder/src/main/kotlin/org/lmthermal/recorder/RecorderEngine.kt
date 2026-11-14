package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Local engineering budgets, not universal camera geometry or wire limits.
 * Reservations include queued bytes, one in-flight item per stage, pending data,
 * encoding/validation scratch and an independently bounded loss/termination journal.
 * This is an admission estimate, not a measured JVM/ART RSS guarantee.
 */
data class RecorderLimits(val preparationBytes: Long=4194304,val writerBytes: Long=4194304,
    val preferredChunkBytes: Long=4194304,val maxObservationBytes: Long,
    val memoryBudgetBytes: Long=134217728,val controlBytes: Long=1048576,
    val availableStorageBytes: Long?=null) {
    val reservedBytes: Long get() = Math.addExact(Math.addExact(preparationBytes,writerBytes),
        Math.addExact(Math.multiplyExact(maxObservationBytes,2),Math.addExact(Math.multiplyExact(maxOf(preferredChunkBytes,maxObservationBytes),12),Math.multiplyExact(controlBytes,16))))
    init {
        require(preparationBytes>0 && writerBytes>0 && preferredChunkBytes in 1..33554432 && maxObservationBytes in 1..33554432 && controlBytes in 4096..1048576)
        require(maxObservationBytes<=preparationBytes && maxObservationBytes<=writerBytes)
        require(reservedBytes<=memoryBudgetBytes) { "Insufficient negotiated memory" }
        require(availableStorageBytes==null || availableStorageBytes>=reservedBytes) { "Known insufficient storage" }
    }
}

/** Inject only already-authorized local backends; successful append must mean footer sync.
 * A blocking sync cannot be forcibly made durable by a timer or Stop timeout.
 */
interface RecorderBackend : AutoCloseable {
    val destination: File
    fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record
}

/** Exclusive new app-owned local file; no SAF/provider/export contract is implied. */
class LocalRecorderBackend(override val destination:File):RecorderBackend {
    private val file:RandomAccessFile
    init { require(!destination.exists() && destination.createNewFile());file=RandomAccessFile(destination,"rw") }
    override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record = LmtrFraming.append(file,type,ordinal,previous,body)
    override fun close() = file.close()
}

enum class Admission { ACCEPTED, PREPARATION_OVERLOAD, NOT_SELECTED, STOPPED, REFUSED }
/** Counts distinguish accepted pending observations from successful CHUNK-footer acknowledgments. */
data class RecorderSnapshot(val state:String,val accepted:Long,val acknowledged:Long,val preparationDrops:Long,
    val writerDrops:Long,val preparationQueuedBytes:Long,val writerQueuedBytes:Long,
    val preparationInFlightBytes:Long,val writerInFlightBytes:Long,val pendingBytes:Long,
    val controlBytes:Long,val maxPreparationQueuedBytes:Long,val maxWriterQueuedBytes:Long,
    val chunks:Long,val checkpoints:Long,val error:String?,val maxPreparationInFlightBytes:Long,
    val maxWriterInFlightBytes:Long,val maxPendingBytes:Long)
data class RecorderResult(val finalized:Boolean,val status:String,val reason:String,val report:Obj?,val failure:String?)

/** Isolated two-stage recorder. Admission transfers only pre-owned module observations.
 * The short intake lock serializes real monotonic witnesses, sequence checks and Stop's cutoff;
 * it never compresses, hashes, copies image buffers or performs filesystem operations.
 * One preparation worker and one writer own all downstream ordering. Control messages use a
 * reserved journal, so overload cannot silently erase a known delivered observation.
 * Optional seal/codec observers are bounded diagnostics, disabled by default and isolated from
 * acknowledged bytes. Their extra clock reads do not become source/intake witnesses.
 * Timer flags are coalesced by the writer's wakeup, not queued per tick. Stop is idempotent.
 */
class RecorderEngine(header:Obj, initialStreams:List<Obj>, val profile:RecordingProfile,
    private val limits:RecorderLimits, private val backend:RecorderBackend, private val codec:Int=0,
    private val now:()->Long=System::nanoTime, private val prepareHook:()->Unit={},
    private val sealMetric:((encodeNs:Long,appendNs:Long,decodedBytes:Long,storedBytes:Long)->Unit)?=null,
    private val codecMetric:((wallNs:Long,decodedBytes:Long,storedBytes:Long)->Unit)?=null,
    sampling:FiveHertzSampling?=null, private val stopDeadline:RecordingStopDeadline?=null) {
    companion object {
        // Accepted wire §14 distinguishes intake sealing from post-footer checkpoint timing.
        private const val SEAL_INTERVAL_NS=1000000000L
        private const val CHECKPOINT_INTERVAL_NS=5000000000L
        private const val MAX_CHUNK_ENTRIES=32
        private const val WRITER_WAKE_MS=20L
        private const val CONTROL_ENTRY_BYTES=32768L
        private const val MAX_OBSERVATION_METADATA_BYTES=16384
    }
    private val header=FrozenJson(header).value()
    private val origin=this.header["timeline"].obj()["origin_monotonic_ns"].d()
    private val lock=Object()
    private data class Intake(val offer:RecordingOffer,val witness:Long,val drop:String?=null,val loss:FrozenJson?=null)
    private data class Work(val observation:PreparedObservation?,val streamId:String,val sequence:Long,val witness:Long,
        val stream:FrozenJson,val drop:String?,val charge:Long,val loss:FrozenJson?=null,
        val selection:RecordingSelectionWitness?=null)
    private val intake=ArrayDeque<Intake>();private val writer=ArrayDeque<Work>()
    private val streams=mutableMapOf<String,FrozenJson>()
    private val nextSequence=mutableMapOf<String,Long>()
    private val intakeSessions=mutableMapOf<String,String>()
    private var prepQueued=0L;private var writerQueued=0L;private var prepFlight=0L;private var writerFlight=0L
    private var pendingBytes=0L;private var controlUsed=0L;private var maxPrep=0L;private var maxWriter=0L
    private var maxPrepFlight=0L;private var maxWriterFlight=0L;private var maxPending=0L
    private var accepted=0L;private var acknowledged=0L;private var prepDrops=0L;private var writerDrops=0L
    private var chunks=0L;private var checkpoints=0L
    // Sticky evidence failure: later exceptions may change the diagnostic reason, never
    // restore permission to certify END after a delivered/admitted item lost its journal.
    private var unrepresentedLoss=false
    private var allocationFailed=false
    private var stopped=false;private var prepDone=false;private var lastWitness=origin
    private var terminationWitness:Long?=null
    private var failureCode="local_resource_limit"
    private var status="completed";private var reason="user_stop";private var failure:String?=null
    private var state="recording"
    private var periodic:PeriodicSamplingState?=null
    val periodicSamplingEnabled:Boolean=sampling!=null
    private var periodicClockInvalid=false
    private var firstObservedExpiryNs:Long?=null
    private val completion=CompletableFuture<RecorderResult>()
    private val controlCharge=CONTROL_ENTRY_BYTES
    private val preparationThread=Thread(::prepareLoop,"lmtr-preparation")
    private val writerThread=Thread(::writeLoop,"lmtr-writer")
    init {
        try {
            LmtrMetadata.header(this.header)
            require(stopDeadline==null || (sampling!=null && stopDeadline.monotonicNs>sampling.startMonotonicNs)) {
                "Finite deadline requires an explicit periodic policy and a target after its Start"
            }
            if(sampling!=null) {
                require(initialStreams.size==1 && sampling.startMonotonicNs<=now()) {
                    "Experimental periodic recording requires one stream and a genuine elapsed Start"
                }
                periodic=PeriodicSamplingState(sampling,this.header,initialStreams.single())
            }
            val configured=periodic?.let {listOf(it.selectedStream.value())} ?: initialStreams
            for(stream in configured) {
                val selected=stream+("policy" to mapOf("level" to profile.wire,"choice" to "explicit",
                    "requested_roles" to RecordingProfiles.requested(profile,stream["capabilities"].obj())))
                LmtrMetadata.local("SegmentStream",selected)
                streams[stream["stream_id"].text()]=FrozenJson(selected)
            }
            require(codec in this.header["codecs"].arr().map { it.i() })
            require(streams.isNotEmpty() && streams.size==initialStreams.size)
            require(streams.keys==this.header["streams"].arr().map { it.obj()["id"].text() }.toSet())
            require(initialStreams.sumOf { LmtrJson.encode(it).size.toLong() } <=limits.controlBytes/4)
            if(periodic!=null)require(periodic!!.staticMetadataCharge+streams.values.sumOf {it.byteSize.toLong()}<=limits.controlBytes/4) {
                "Periodic metadata exceeds the existing static stream/termination allowance"
            }
        } catch(error:Exception) {
            try { backend.close() } catch(close:Exception) { error.addSuppressed(close) }
            throw error
        } catch(error:OutOfMemoryError) {
            try {backend.close()} catch(_:Exception) { } catch(_:OutOfMemoryError) { }
            throw error
        }
        // Daemon workers cannot prevent process exit; partial files remain explicit recovery evidence.
        preparationThread.isDaemon=true;writerThread.isDaemon=true
        preparationThread.start();writerThread.start()
    }

    /** Existing owned-byte admission stays source compatible; no data is regenerated. */
    fun admit(observation:RecordingObservation):Admission {
        if(observation.stream.byteSize+observation.context.byteSize+observation.facts.byteSize>MAX_OBSERVATION_METADATA_BYTES)
            return synchronized(lock){terminateLocked("failed","admission_refused","oversize_metadata");Admission.REFUSED}
        return admit(RecordingOffer(observation.streamId,observation.sequence,observation.stream,observation.ownedBytes){observation})
    }
    /** Bounded source admission retains an immutable owner, not an acquisition/UI buffer lease. */
    fun admit(sourceOffer:RecordingOffer):Admission = synchronized(lock) {
        try { admitOfferLocked(sourceOffer) }
        catch(_:OutOfMemoryError) { allocationFailure();Admission.REFUSED }
    }
    private fun admitOfferLocked(sourceOffer:RecordingOffer):Admission {
        if(stopped)return Admission.STOPPED
        val witness=intakeWitnessLocked() ?: return Admission.REFUSED
        if(expireDeadlineLocked(witness))return Admission.STOPPED
        if(sourceOffer.streamId !in streams || sourceOffer.chargedBytes>limits.maxObservationBytes ||
            sourceOffer.stream.byteSize>MAX_OBSERVATION_METADATA_BYTES ||
            (sourceOffer.preparationProfile!=null && sourceOffer.preparationProfile!=profile)) {
            if(periodic!=null)unrepresentedLoss=true
            terminateLocked("failed","admission_refused","unsupported_or_oversize_observation");return Admission.REFUSED
        }
        val offer=if(periodic!=null) {
            val p=periodic!!
            try {
                val problem=p.sourceError(sourceOffer,witness)
                if(problem!=null) {
                    if(problem!="periodic_source_changed") {p.fail(problem);unrepresentedLoss=true}
                    terminateLocked(if(problem=="periodic_source_changed")"interrupted" else "failed",problem,problem,witness)
                    return Admission.REFUSED
                }
                if(!advancePeriodicLocked(witness,false))return Admission.REFUSED
                val selection=p.select(sourceOffer,witness) ?: return Admission.NOT_SELECTED
                sourceOffer.selected(p.selectedStream,selection)
            } catch(_:ArithmeticException) {
                p.fail("periodic_counter_or_deadline_overflow");unrepresentedLoss=true
                terminateLocked("failed","periodic_counter_or_deadline_overflow","periodic_counter_or_deadline_overflow",witness)
                return Admission.REFUSED
            }
        } else sourceOffer
        val session=offer.stream.value()["session_id"].text()
        val next=if(intakeSessions[offer.streamId]==session)nextSequence[offer.streamId] else null
        intakeSessions[offer.streamId]=session
        if(next!=null && offer.sequence!=next) {
            terminateLocked("failed","admission_refused","source_sequence_requires_explicit_gap");return Admission.REFUSED
        }
        nextSequence[offer.streamId]=Math.addExact(offer.sequence,1)
        if(prepQueued+offer.chargedBytes>limits.preparationBytes) {
            if(!reserveControlLocked())return Admission.REFUSED
            intake.add(Intake(offer.lightened(),witness,"preparation_overload"));prepDrops++;lock.notifyAll()
            return Admission.PREPARATION_OVERLOAD
        }
        intake.add(Intake(offer,witness));prepQueued+=offer.chargedBytes;accepted++
        maxPrep=maxOf(maxPrep,prepQueued);lock.notifyAll();return Admission.ACCEPTED
    }
    /** Loss entries release the heavy owner immediately; their materializer is never called. */
    private fun lightOffer(id:String,sequence:Long,stream:FrozenJson)=RecordingOffer(id,sequence,stream,1){error("Loss must not materialize source data")}
    /** Admit bounded original loss evidence in the same intake ordering as observations. */
    fun admitLoss(loss:RecordingLoss):Admission = synchronized(lock) {
        try { admitLossLocked(loss) }
        catch(_:OutOfMemoryError) { allocationFailure();Admission.REFUSED }
        catch(error:ArithmeticException) {
            val p=periodic ?: throw error
            // Individually valid source-loss counts can overflow their cumulative producer ledger.
            // Refuse certification; do not escape to the caller and later close it as ordinary idle.
            p.fail("periodic_counter_or_deadline_overflow");unrepresentedLoss=true
            terminateLocked("failed","periodic_counter_or_deadline_overflow","periodic_counter_or_deadline_overflow",lastWitness)
            Admission.REFUSED
        }
    }
    private fun admitLossLocked(loss:RecordingLoss):Admission {
        if(stopped)return Admission.STOPPED
        if(loss.streamId !in streams)return Admission.REFUSED
        val witness=intakeWitnessLocked() ?: return Admission.REFUSED
        if(expireDeadlineLocked(witness))return Admission.STOPPED
        val facts=loss.facts.value()
        val actualLoss=periodic?.let { p ->
            if(!p.originalStream.same(loss.stream) || facts["observation_first"]!=null) {
                p.fail("periodic_ambiguous_source_loss");unrepresentedLoss=true
                terminateLocked("failed","periodic_ambiguous_source_loss","periodic_ambiguous_source_loss",witness)
                return Admission.REFUSED
            }
            if(!advancePeriodicLocked(witness,false))return Admission.REFUSED
            p.sourceLoss(facts)
            RecordingLoss(loss.streamId,p.selectedStream.value(),facts)
        } ?: loss
        val first=facts["observation_first"]?.d();val last=facts["observation_last"]?.d()
        if(first!=null) {
            val session=loss.stream.value()["session_id"].text()
            val next=if(intakeSessions[loss.streamId]==session)nextSequence[loss.streamId] else null
            require(next==null || first==next)
            intakeSessions[loss.streamId]=session
            nextSequence[loss.streamId]=Math.addExact(last!!,1)
        }
        if(!reserveControlLocked())return Admission.REFUSED
        intake.add(Intake(lightOffer(actualLoss.streamId,first?:0,actualLoss.stream),witness,"source_loss",actualLoss.facts));lock.notifyAll();return Admission.ACCEPTED
    }
    /** First normal termination wins. Resource failures can upgrade it, never reopen intake. */
    fun stop(reason:String="user_stop", interrupted:Boolean=false):CompletableFuture<RecorderResult> = synchronized(lock) {
        if(!stopped)terminateLocked(if(interrupted)"interrupted" else "completed",reason,null)
        completion
    }
    /** Source/adapter failure terminates explicitly; it is not preparation/writer overload. */
    fun failSource(reason:String):CompletableFuture<RecorderResult> = synchronized(lock) {
        terminateLocked("failed",reason,reason);failureCode="source_failure";completion
    }
    /** Adapter failure may hide a delivered periodic callback; never certify its slot as ordinary idle. */
    fun failSourceEvidence(reason:String):CompletableFuture<RecorderResult> = synchronized(lock) {
        if(periodic!=null && !completion.isDone){periodic!!.fail(reason);unrepresentedLoss=true}
        terminateLocked("failed",reason,reason);failureCode="source_failure";completion
    }
    /** Allocation refusal retains only the verified prefix; no formatting, JSON closure or GC here. */
    fun sourceAllocationFailure() = synchronized(lock) {if(!completion.isDone)allocationFailure()}
    /** Memory-pressure callback stops admission; no queue growth or implicit crop/downgrade. */
    fun memoryPressure():CompletableFuture<RecorderResult> = synchronized(lock) {
        terminateLocked("failed","memory_pressure","memory_pressure");completion
    }
    /** Bounded caller wait. Timeout is not a successful finalization and does not repair a blocked sync. */
    fun awaitStop(timeoutMs:Long):RecorderResult? = try { completion.get(timeoutMs,TimeUnit.MILLISECONDS) }
        catch(_:java.util.concurrent.TimeoutException){null}
    /** A failed writer can complete its result while a deferred source materializer unwinds.
     * Test composition must not reuse its oracle/storage reservation until both workers exited.
     */
    fun workersTerminated():Boolean = !preparationThread.isAlive && !writerThread.isAlive
    /** A bounded external notification can wake the existing workers without creating a timer thread.
     * Expiry uses the actual clock read under the same lock as admission. Default callers retain
     * their old no-clock-read wake behavior when no deadline was explicitly selected.
     */
    fun wakeTimers() = synchronized(lock) {
        if(stopDeadline!=null && !stopped)intakeWitnessLocked()?.let {expireDeadlineLocked(it)}
        lock.notifyAll()
    }
    fun snapshot():RecorderSnapshot = synchronized(lock) {
        RecorderSnapshot(state,accepted,acknowledged,prepDrops,writerDrops,prepQueued,writerQueued,prepFlight,writerFlight,
            pendingBytes,controlUsed,maxPrep,maxWriter,chunks,checkpoints,failure,maxPrepFlight,maxWriterFlight,maxPending)
    }
    /** Immutable scalars; no source owner escapes after Stop, cleanup or failed append. */
    fun periodicSnapshot():PeriodicRecordingSnapshot? = synchronized(lock){periodic?.snapshot()}
    fun deadlineSnapshot():RecordingDeadlineSnapshot? = synchronized(lock){stopDeadline?.let {
        RecordingDeadlineSnapshot(it.monotonicNs,firstObservedExpiryNs,terminationWitness,
            !periodicClockInvalid,if(stopped)reason else null)
    }}
    /** Additional encoded definitions/scalars use the existing static quarter-control allowance. */
    fun periodicStaticMetadataBytes():Long? = synchronized(lock){periodic?.staticMetadataCharge}

    /** Timer/admission/Stop use one real witness order. A bad periodic clock cannot certify END. */
    private fun intakeWitnessLocked():Long? {
        val witness=try {now()} catch(_:OutOfMemoryError) {allocationFailure();return null}
            catch(error:Exception) {
                if(periodic==null)throw error
                periodic!!.fail("periodic_clock_unavailable");periodicClockInvalid=true;unrepresentedLoss=true
                terminateLocked("failed","periodic_clock_unavailable","periodic_clock_unavailable",lastWitness)
                return null
            }
        if(periodic==null) {require(witness>=lastWitness && witness>=origin){"Intake clock regressed"};lastWitness=witness;return witness}
        if(witness<lastWitness || witness<periodic!!.policy.startMonotonicNs) {
            periodic!!.fail("periodic_clock_regressed");periodicClockInvalid=true;unrepresentedLoss=true
            terminateLocked("failed","periodic_clock_regressed","periodic_clock_regressed",lastWitness)
            return null
        }
        lastWitness=witness;return witness
    }
    /** Expiry wins before an offer at/after the target is admitted. A late observer still records
     * its actual Stop time, including an honestly missed partial final slot if applicable.
     * First Stop/failure remains authoritative; expiry never repairs an unrepresented loss.
     */
    private fun expireDeadlineLocked(witness:Long):Boolean {
        if(stopped)return true
        val target=stopDeadline ?: return false
        if(witness<target.monotonicNs)return false
        if(firstObservedExpiryNs==null)firstObservedExpiryNs=witness
        terminateLocked("completed",RecordingStopDeadline.REASON,null,witness)
        return true
    }
    /** Coalesce newly closed empty windows into one existing, charged control-journal item. */
    private fun advancePeriodicLocked(witness:Long,stopping:Boolean):Boolean {
        val p=periodic ?: return true
        val range=p.missing(witness,stopping) ?: return true
        // Claim before reserveControlLocked: exhaustion may recursively latch Stop's cutoff.
        p.missingDetected(range)
        val admitted=reserveControlLocked()
        if(!admitted){p.missingAdmitted(range,false);return false}
        val stream=p.selectedStream
        intake.add(Intake(lightOffer(stream.value()["stream_id"].text(),0,stream),witness,"source_loss",FrozenJson(p.missingFacts(range))))
        p.missingAdmitted(range,true)
        lock.notifyAll();return true
    }
    private fun terminateLocked(nextStatus:String,nextReason:String,error:String?,knownWitness:Long?=null) {
        if(state in listOf("finalized","failed","finalizing"))return
        if(!stopped || nextStatus=="failed") { status=nextStatus;reason=nextReason;if(error!=null)failure=error }
        if(terminationWitness==null) {
            val raw=try {knownWitness ?: now()} catch(_:OutOfMemoryError) {allocationFailure();return}
                catch(error:Exception) {
                    if(periodic==null)throw error
                    periodic!!.fail("periodic_clock_unavailable");periodicClockInvalid=true;unrepresentedLoss=true
                    lastWitness
                }
            if(periodic!=null && (raw<lastWitness || raw<periodic!!.policy.startMonotonicNs)) {
                periodic!!.fail("periodic_clock_regressed");periodicClockInvalid=true;unrepresentedLoss=true
            }
            terminationWitness=maxOf(lastWitness,raw)
            // Publish cutoff before closure: a nested journal refusal must not recursively close it.
            stopped=true;state="draining"
            if(periodic!=null) {
                try {
                    if(!periodicClockInvalid && !unrepresentedLoss)advancePeriodicLocked(terminationWitness!!,true)
                    periodic!!.close(terminationWitness!!,!unrepresentedLoss && !periodicClockInvalid)
                } catch(_:OutOfMemoryError) {allocationFailure()}
                catch(_:ArithmeticException) {
                    periodic!!.fail("periodic_counter_or_deadline_overflow");unrepresentedLoss=true
                    status="failed";reason="periodic_counter_or_deadline_overflow";failure=reason
                }
            }
        }
        stopped=true;state="draining";lock.notifyAll()
    }
    private fun reserveControlLocked():Boolean {
        if(controlUsed+controlCharge>limits.controlBytes) {
            unrepresentedLoss=true
            periodic?.fail("loss_journal_exhausted")
            terminateLocked("failed","loss_journal_exhausted","loss_journal_exhausted");return false
        }
        controlUsed+=controlCharge;return true
    }

    /** Preparation owns one item outside its bounded queue; writer overload becomes ordered loss evidence. */
    private fun prepareLoop() {
        var unresolvedIntake=false
        try {
            while(true) {
                val item=synchronized(lock) {
                    while(intake.isEmpty() && !stopped) {
                        // Either existing worker can detect idle windows while its peer is in I/O.
                        // A blocked backend is never made durable by this soft timer.
                        if(periodic!=null)intakeWitnessLocked()?.let {if(!expireDeadlineLocked(it))advancePeriodicLocked(it,false)}
                        if(intake.isEmpty() && !stopped)lock.wait(if(periodic==null)0L else WRITER_WAKE_MS)
                    }
                    if(intake.isEmpty())return@synchronized null
                    intake.removeFirst().also { if(it.drop==null){prepQueued-=it.offer.chargedBytes;prepFlight=it.offer.chargedBytes;maxPrepFlight=maxOf(maxPrepFlight,prepFlight)} }
                } ?: break
                unresolvedIntake=true
                prepareHook()
                var prepared:PreparedObservation?=null;var drop=item.drop
                try { if(drop==null) {
                    val original=item.offer.materialize()
                    require(original.stream.byteSize+original.context.byteSize+original.facts.byteSize<=MAX_OBSERVATION_METADATA_BYTES)
                    prepared=RecordingProfiles.select(original,profile)
                } }
                catch(_:Exception) { drop="source_loss";synchronized(lock){periodic?.invalidMaterialization();reserveControlLocked();terminateLocked("failed","invalid_module_evidence","invalid_module_evidence");failureCode="source_failure"} }
                synchronized(lock) {
                    if(prepared!=null && writerQueued+prepared.ownedBytes>limits.writerBytes) {
                        if(!reserveControlLocked()) { prepFlight=0;throw IllegalStateException("loss_journal_exhausted") }
                        prepared=null;drop="writer_overload";writerDrops++
                    }
                    if(state=="failed"){prepFlight=0;return}
                    val stream=prepared?.stream ?: FrozenJson(item.offer.stream.value()+("policy" to mapOf(
                        "level" to profile.wire,"choice" to "explicit","requested_roles" to RecordingProfiles.requested(profile,item.offer.stream.value()["capabilities"].obj()))))
                    // The writer owns the resolved byte/JSON owners, never the deferred HT source.
                    // Keep its byte capacity unchanged; count the actual prepared ownership.
                    val charge=prepared?.ownedBytes ?: 0L
                    writer.add(Work(prepared,item.offer.streamId,item.offer.sequence,item.witness,stream,drop,charge,item.loss,item.offer.selection))
                    writerQueued+=charge;maxWriter=maxOf(maxWriter,writerQueued);prepFlight=0;unresolvedIntake=false;lock.notifyAll()
                }
            }
        } catch(_:OutOfMemoryError) {
            // A failed allocation may have abandoned a delivered owner before any loss record.
            // Drop heavy queued references, poison END permission and preserve only the committed prefix.
            allocationFailure()
        } catch(_:InterruptedException) { synchronized(lock){unrepresentedLoss=unrepresentedLoss || unresolvedIntake || intake.isNotEmpty();terminateLocked("failed","preparation_interrupted","preparation_interrupted")} }
        catch(_:Exception) { synchronized(lock){unrepresentedLoss=unrepresentedLoss || unresolvedIntake || intake.isNotEmpty();terminateLocked("failed","preparation_failed","preparation_failed")} }
        finally { synchronized(lock){prepFlight=0;prepDone=true;lock.notifyAll()} }
    }

    /** Best-effort bounded failure path, not permission to retry Start after an OOM. No GC,
     * payload allocation, repair END or exception formatting is attempted here. A truly exhausted
     * runtime may still kill the process; previously synchronized records remain recovery evidence.
     */
    private fun allocationFailure() = synchronized(lock) {
        allocationFailed=true;unrepresentedLoss=true;stopped=true;status="failed"
        periodic?.fail("allocation_failed")
        reason="allocation_failed";failure="allocation_failed";state="failed"
        intake.clear();writer.clear();prepQueued=0;writerQueued=0;controlUsed=0;lock.notifyAll()
    }

    /** One file/index owner. No footer acknowledgment is counted before its sync returns. */
    private fun writeLoop() {
        var backendCloseAttempted=false
        var append: LmtrAppendSession?=null
        var active:Obj?=null;var nextEntry=0L;var lastTime=0L
        var firstPendingWitness:Long?=null;var firstUncoveredAck:Long?=null
        val pending=mutableListOf<PendingEntry>()
        fun timeline(witness:Long):Long = maxOf(lastTime,witness-origin).also { lastTime=it }
        fun event(kind:String,time:Long,extra:Obj=emptyMap()):Obj = mapOf("kind" to kind,"entry_id" to (nextEntry++).toString(),
            "timeline_ns" to time.toString(),"segment_id" to active!!["id"])+extra
        fun checkpoint() {
            if(append!!.uncheckpointedChunks>0) { append!!.checkpoint();firstUncoveredAck=null }
        }
        fun seal() {
            if(pending.isEmpty())return
            val started=if(sealMetric!=null)now() else 0L
            val decoded=pending.sumOf { it.payloads.sumOf { payload -> payload.size.toLong() } }
            val body=RecordingChunk.encode(header,pending,codec,limits.memoryBudgetBytes,codecMetric,now)
            val encoded=if(sealMetric!=null)now() else 0L
            append!!.appendChunk(body)
            // Optional bounded test observer; never let diagnostics poison an acknowledged commit.
            if(sealMetric!=null)runCatching { sealMetric?.invoke(encoded-started,now()-encoded,decoded,body.size.toLong()) }
            synchronized(lock) { pendingBytes=0 }
            pending.clear();firstPendingWitness=null
            if(append!!.uncheckpointedChunks==0)firstUncoveredAck=null
        }
        fun add(entry:Obj,context:Obj?=null,payloads:List<RecordingPayload> = emptyList(),witness:Long) {
            // Count complete pending closure, not only image payload bytes. Optional descriptors
            // and contexts can be much larger than tiny payloads; excluding them hid retained
            // ownership until the final wire metadata check. Shared closure is overcharged safely.
            val bytes=payloads.sumOf { it.size.toLong()+it.descriptor.byteSize }+
                LmtrJson.encode(entry).size+LmtrJson.encode(active!!).size+
                (context?.let { LmtrJson.encode(it).size } ?: 0)
            // Thresholds apply to every entry, including segment closure and Stop.
            // A negotiated singleton above preferred remains whole; the hard wire checks still apply.
            if(pending.isNotEmpty() && (pending.size==MAX_CHUNK_ENTRIES ||
                witness-firstPendingWitness!!>=SEAL_INTERVAL_NS || pendingBytes+bytes>limits.preferredChunkBytes))seal()
            pending.add(PendingEntry(entry,active!!,context,payloads))
            if(firstPendingWitness==null)firstPendingWitness=witness
            synchronized(lock){pendingBytes+=bytes;maxPending=maxOf(maxPending,pendingBytes)}
            if(pendingBytes>=limits.preferredChunkBytes)seal()
        }
        fun start(witness:Long,why:String) {
            val definition=mapOf("reason" to why,"start_entry" to nextEntry.toString(),"start_timeline_ns" to timeline(witness).toString(),
                "streams" to streams.toSortedMap().values.map { it.value() })
            active=definition+("id" to LmtrChunk.sha(LmtrJson.encode(definition)))
            add(event("segment_start",lastTime),witness=witness)
        }
        try {
            append=LmtrAppendSession(header,LmtrRecordSink(backend::append),onChunkAcknowledged={ summary ->
                val acquisitionAck=if(periodic==null)now() else null
                synchronized(lock) {
                    acknowledged=summary["observations"].d();chunks=summary["chunks"].d()
                    periodic?.acknowledged(pending)
                }
                // The footer is already synced. A clock fault must not erase its actual ACK count,
                // but it prevents END and a fabricated post-sync checkpoint deadline.
                val ack=acquisitionAck ?: synchronized(lock) {
                    intakeWitnessLocked() ?: error("Periodic clock unavailable after footer sync")
                }
                if(firstUncoveredAck==null)firstUncoveredAck=ack
            },onCheckpointAcknowledged={ synchronized(lock){checkpoints++};firstUncoveredAck=null },validationBudget=limits.memoryBudgetBytes)
            while(true) {
                val work=synchronized(lock) {
                    // Existing writer wakeup drives idle sampling too. There is no second timer queue.
                    if(periodic!=null && !stopped)intakeWitnessLocked()?.let {if(!expireDeadlineLocked(it))advancePeriodicLocked(it,false)}
                    if(writer.isEmpty() && !(prepDone && stopped))lock.wait(WRITER_WAKE_MS)
                    if(writer.isEmpty())null else writer.removeFirst().also { writerQueued-=it.charge;writerFlight=it.charge;maxWriterFlight=maxOf(maxWriterFlight,writerFlight) }
                }
                if(work!=null) {
                    val current=streams.getValue(work.streamId)
                    if(!current.same(work.stream)) {
                        if(active!=null) { add(event("segment_end",timeline(work.witness),mapOf("reason" to "source_configuration_changed")),witness=work.witness);seal();active=null }
                        streams[work.streamId]=work.stream
                    }
                    if(active==null)start(work.witness,if(nextEntry==0L)"recording_start" else "source_configuration_changed")
                    if(work.drop!=null) {
                        val base=work.loss?.value() ?: mapOf("stream_id" to work.streamId,"category" to work.drop,"reason" to work.drop,
                            "count" to "1","observation_first" to work.sequence.toString(),"observation_last" to work.sequence.toString(),
                            "device_first" to null,"device_last" to null,"scheduled" to work.selection?.schedule(),
                            "evidence" to mapOf("basis" to "host_observation","before" to null,"after" to null))
                        val loss=work.selection?.let {PeriodicRecordingEvidence.attach(base,it.evidence())} ?: base
                        add(event("gap",timeline(work.witness),loss+("stream_id" to work.streamId)),witness=work.witness)
                        synchronized(lock){controlUsed=maxOf(0,controlUsed-controlCharge)}
                    } else {
                        val o=work.observation!!;val contextValue=o.context.value()
                        val context=mapOf("id" to LmtrChunk.sha(LmtrJson.encode(contextValue)),"value" to contextValue)
                        add(event("observation",timeline(work.witness),o.facts.value()+mapOf("stream_id" to o.streamId,
                            "observation_sequence" to o.sequence.toString(),"context_id" to context["id"])),context,o.payloads,work.witness)
                    }
                    synchronized(lock){writerFlight=0}
                }
                val tick=if(periodic==null)now() else synchronized(lock) {
                    if(stopped)lastWitness else (intakeWitnessLocked()?.also {expireDeadlineLocked(it)} ?: lastWitness)
                }
                if(firstPendingWitness!=null && tick-firstPendingWitness!!>=SEAL_INTERVAL_NS)seal()
                if(firstUncoveredAck!=null && tick-firstUncoveredAck!!>=CHECKPOINT_INTERVAL_NS)checkpoint()
                if(synchronized(lock){stopped && prepDone && writer.isEmpty()})break
            }
            synchronized(lock) { check(!unrepresentedLoss) { "Cannot certify unjournaled delivered/admitted loss" } }
            val cutoff=synchronized(lock){terminationWitness!!}
            // A zero-observation periodic recording still closes its actual policy/counter ledger.
            if(active==null && periodic!=null)start(cutoff,"recording_start")
            if(active!=null) {
                val closure=synchronized(lock) {
                    periodic?.let {it.close(cutoff,!unrepresentedLoss);mapOf("extensions" to it.closure(prepDrops,writerDrops))} ?: emptyMap()
                }
                add(event("segment_end",timeline(cutoff),mapOf("reason" to reason)+closure),witness=cutoff)
            }
            seal()
            synchronized(lock) { check(!unrepresentedLoss) { "Cannot certify END after a final-seal failure witness" } }
            val terminal=synchronized(lock){state="finalizing";status to reason}
            append.finish(terminal.first,terminal.second,mapOf("status" to "unknown","clock_source" to "host_utc","reason" to "not_recorded"),
                if(terminal.first=="failed")mapOf("code" to failureCode,"message" to (failure?:terminal.second)) else null)
            backendCloseAttempted=true;backend.close()
            val report=LmtrReader(backend.destination).use { it.check(full=false) }
            synchronized(lock){state="finalized"}
            completion.complete(RecorderResult(true,terminal.first,terminal.second,report,null))
        } catch(_:OutOfMemoryError) {
            allocationFailure()
            if(!backendCloseAttempted)try { backendCloseAttempted=true;backend.close() }
                catch(_:Exception) { } catch(_:OutOfMemoryError) { }
            synchronized(lock){state="failed"}
            completion.complete(RecorderResult(false,"failed","allocation_failed",null,"allocation_failed"))
        } catch(error:Exception) {
            // Any uncertain body/footer/sync/close halts append. Never fabricate a repair END.
            if(!backendCloseAttempted)try { backendCloseAttempted=true;backend.close() } catch(_:Exception) { }
            synchronized(lock){periodic?.fail("write_or_validation_failure");stopped=true;state="failed";failure=if(allocationFailed)"allocation_failed" else error.javaClass.simpleName;intake.clear();writer.clear();prepQueued=0;writerQueued=0;controlUsed=0;lock.notifyAll()}
            completion.complete(if(allocationFailed)RecorderResult(false,"failed","allocation_failed",null,"allocation_failed")
                else RecorderResult(false,"failed","write_or_validation_failure",null,error.toString()))
        } finally { pending.clear();synchronized(lock){writerFlight=0;pendingBytes=0} }
    }
}
