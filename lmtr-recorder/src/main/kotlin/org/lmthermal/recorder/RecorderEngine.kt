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

enum class Admission { ACCEPTED, PREPARATION_OVERLOAD, STOPPED, REFUSED }
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
class RecorderEngine(header:Obj, initialStreams:List<Obj>, private val profile:RecordingProfile,
    private val limits:RecorderLimits, private val backend:RecorderBackend, private val codec:Int=0,
    private val now:()->Long=System::nanoTime, private val prepareHook:()->Unit={},
    private val sealMetric:((encodeNs:Long,appendNs:Long,decodedBytes:Long,storedBytes:Long)->Unit)?=null,
    private val codecMetric:((wallNs:Long,decodedBytes:Long,storedBytes:Long)->Unit)?=null) {
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
        val stream:FrozenJson,val drop:String?,val charge:Long,val loss:FrozenJson?=null)
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
    private var stopped=false;private var prepDone=false;private var lastWitness=origin
    private var terminationWitness:Long?=null
    private var failureCode="local_resource_limit"
    private var status="completed";private var reason="user_stop";private var failure:String?=null
    private var state="recording"
    private val completion=CompletableFuture<RecorderResult>()
    private val controlCharge=CONTROL_ENTRY_BYTES
    private val preparationThread=Thread(::prepareLoop,"lmtr-preparation")
    private val writerThread=Thread(::writeLoop,"lmtr-writer")
    init {
        try {
            LmtrMetadata.header(this.header)
            for(stream in initialStreams) {
                val selected=stream+("policy" to mapOf("level" to profile.wire,"choice" to "explicit",
                    "requested_roles" to RecordingProfiles.requested(profile,stream["capabilities"].obj())))
                LmtrMetadata.local("SegmentStream",selected)
                streams[stream["stream_id"].text()]=FrozenJson(selected)
            }
            require(codec in this.header["codecs"].arr().map { it.i() })
            require(streams.isNotEmpty() && streams.size==initialStreams.size)
            require(streams.keys==this.header["streams"].arr().map { it.obj()["id"].text() }.toSet())
            require(initialStreams.sumOf { LmtrJson.encode(it).size.toLong() } <=limits.controlBytes/4)
        } catch(error:Exception) {
            try { backend.close() } catch(close:Exception) { error.addSuppressed(close) }
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
    fun admit(offer:RecordingOffer):Admission = synchronized(lock) {
        if(stopped)return@synchronized Admission.STOPPED
        val witness=now();require(witness>=lastWitness && witness>=origin) { "Intake clock regressed" };lastWitness=witness
        if(offer.streamId !in streams || offer.chargedBytes>limits.maxObservationBytes || offer.stream.byteSize>MAX_OBSERVATION_METADATA_BYTES) {
            terminateLocked("failed","admission_refused","unsupported_or_oversize_observation");return@synchronized Admission.REFUSED
        }
        val session=offer.stream.value()["session_id"].text()
        val next=if(intakeSessions[offer.streamId]==session)nextSequence[offer.streamId] else null
        intakeSessions[offer.streamId]=session
        if(next!=null && offer.sequence!=next) {
            terminateLocked("failed","admission_refused","source_sequence_requires_explicit_gap");return@synchronized Admission.REFUSED
        }
        nextSequence[offer.streamId]=Math.addExact(offer.sequence,1)
        if(prepQueued+offer.chargedBytes>limits.preparationBytes) {
            if(!reserveControlLocked())return@synchronized Admission.REFUSED
            intake.add(Intake(lightOffer(offer.streamId,offer.sequence,offer.stream),witness,"preparation_overload"));prepDrops++;lock.notifyAll()
            return@synchronized Admission.PREPARATION_OVERLOAD
        }
        intake.add(Intake(offer,witness));prepQueued+=offer.chargedBytes;accepted++
        maxPrep=maxOf(maxPrep,prepQueued);lock.notifyAll();Admission.ACCEPTED
    }
    /** Loss entries release the heavy owner immediately; their materializer is never called. */
    private fun lightOffer(id:String,sequence:Long,stream:FrozenJson)=RecordingOffer(id,sequence,stream,1){error("Loss must not materialize source data")}
    /** Admit bounded original loss evidence in the same intake ordering as observations. */
    fun admitLoss(loss:RecordingLoss):Admission = synchronized(lock) {
        if(stopped)return@synchronized Admission.STOPPED
        if(loss.streamId !in streams)return@synchronized Admission.REFUSED
        val witness=now();require(witness>=lastWitness && witness>=origin);lastWitness=witness
        val facts=loss.facts.value()
        val first=facts["observation_first"]?.d();val last=facts["observation_last"]?.d()
        if(first!=null) {
            val session=loss.stream.value()["session_id"].text()
            val next=if(intakeSessions[loss.streamId]==session)nextSequence[loss.streamId] else null
            require(next==null || first==next)
            intakeSessions[loss.streamId]=session
            nextSequence[loss.streamId]=Math.addExact(last!!,1)
        }
        if(!reserveControlLocked())return@synchronized Admission.REFUSED
        intake.add(Intake(lightOffer(loss.streamId,first?:0,loss.stream),witness,"source_loss",loss.facts));lock.notifyAll();Admission.ACCEPTED
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
    fun wakeTimers() = synchronized(lock) { lock.notifyAll() }
    fun snapshot():RecorderSnapshot = synchronized(lock) {
        RecorderSnapshot(state,accepted,acknowledged,prepDrops,writerDrops,prepQueued,writerQueued,prepFlight,writerFlight,
            pendingBytes,controlUsed,maxPrep,maxWriter,chunks,checkpoints,failure,maxPrepFlight,maxWriterFlight,maxPending)
    }
    private fun terminateLocked(nextStatus:String,nextReason:String,error:String?) {
        if(state in listOf("finalized","failed","finalizing"))return
        if(!stopped || nextStatus=="failed") { status=nextStatus;reason=nextReason;if(error!=null)failure=error }
        if(terminationWitness==null)terminationWitness=maxOf(lastWitness,now())
        stopped=true;state="draining";lock.notifyAll()
    }
    private fun reserveControlLocked():Boolean {
        if(controlUsed+controlCharge>limits.controlBytes) {
            unrepresentedLoss=true
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
                    while(intake.isEmpty() && !stopped)lock.wait()
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
                catch(_:Exception) { drop="source_loss";synchronized(lock){reserveControlLocked();terminateLocked("failed","invalid_module_evidence","invalid_module_evidence");failureCode="source_failure"} }
                synchronized(lock) {
                    if(prepared!=null && writerQueued+item.offer.chargedBytes>limits.writerBytes) {
                        if(!reserveControlLocked()) { prepFlight=0;throw IllegalStateException("loss_journal_exhausted") }
                        prepared=null;drop="writer_overload";writerDrops++
                    }
                    if(state=="failed"){prepFlight=0;return}
                    val stream=prepared?.stream ?: FrozenJson(item.offer.stream.value()+("policy" to mapOf(
                        "level" to profile.wire,"choice" to "explicit","requested_roles" to RecordingProfiles.requested(profile,item.offer.stream.value()["capabilities"].obj()))))
                    val charge=if(prepared==null)0L else item.offer.chargedBytes
                    writer.add(Work(prepared,item.offer.streamId,item.offer.sequence,item.witness,stream,drop,charge,item.loss))
                    writerQueued+=charge;maxWriter=maxOf(maxWriter,writerQueued);prepFlight=0;unresolvedIntake=false;lock.notifyAll()
                }
            }
        } catch(_:InterruptedException) { synchronized(lock){unrepresentedLoss=unrepresentedLoss || unresolvedIntake || intake.isNotEmpty();terminateLocked("failed","preparation_interrupted","preparation_interrupted")} }
        catch(_:Exception) { synchronized(lock){unrepresentedLoss=unrepresentedLoss || unresolvedIntake || intake.isNotEmpty();terminateLocked("failed","preparation_failed","preparation_failed")} }
        finally { synchronized(lock){prepFlight=0;prepDone=true;lock.notifyAll()} }
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
            val bytes=payloads.sumOf { it.size.toLong() }
            // Thresholds apply to every entry, including segment closure and Stop.
            if(pending.isNotEmpty() && (pending.size==MAX_CHUNK_ENTRIES ||
                witness-firstPendingWitness!!>=SEAL_INTERVAL_NS || pendingBytes+bytes>limits.preferredChunkBytes))seal()
            pending.add(PendingEntry(entry,active!!,context,payloads))
            if(firstPendingWitness==null)firstPendingWitness=witness
            synchronized(lock){pendingBytes+=bytes+LmtrJson.encode(entry).size;maxPending=maxOf(maxPending,pendingBytes)}
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
                val ack=now()
                synchronized(lock) { acknowledged=summary["observations"].d();chunks=summary["chunks"].d() }
                if(firstUncoveredAck==null)firstUncoveredAck=ack
            },onCheckpointAcknowledged={ synchronized(lock){checkpoints++};firstUncoveredAck=null },validationBudget=limits.memoryBudgetBytes)
            while(true) {
                val work=synchronized(lock) {
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
                        add(event("gap",timeline(work.witness),(work.loss?.value() ?: mapOf("stream_id" to work.streamId,"category" to work.drop,"reason" to work.drop,
                            "count" to "1","observation_first" to work.sequence.toString(),"observation_last" to work.sequence.toString(),
                            "device_first" to null,"device_last" to null,"scheduled" to null,
                            "evidence" to mapOf("basis" to "host_observation","before" to null,"after" to null)))+("stream_id" to work.streamId)),witness=work.witness)
                        synchronized(lock){controlUsed=maxOf(0,controlUsed-controlCharge)}
                    } else {
                        val o=work.observation!!;val contextValue=o.context.value()
                        val context=mapOf("id" to LmtrChunk.sha(LmtrJson.encode(contextValue)),"value" to contextValue)
                        add(event("observation",timeline(work.witness),o.facts.value()+mapOf("stream_id" to o.streamId,
                            "observation_sequence" to o.sequence.toString(),"context_id" to context["id"])),context,o.payloads,work.witness)
                    }
                    synchronized(lock){writerFlight=0}
                }
                val tick=now()
                if(firstPendingWitness!=null && tick-firstPendingWitness!!>=SEAL_INTERVAL_NS)seal()
                if(firstUncoveredAck!=null && tick-firstUncoveredAck!!>=CHECKPOINT_INTERVAL_NS)checkpoint()
                if(synchronized(lock){stopped && prepDone && writer.isEmpty()})break
            }
            synchronized(lock) { check(!unrepresentedLoss) { "Cannot certify unjournaled delivered/admitted loss" } }
            val cutoff=synchronized(lock){terminationWitness!!}
            if(active!=null)add(event("segment_end",timeline(cutoff),mapOf("reason" to reason)),witness=cutoff)
            seal()
            val terminal=synchronized(lock){state="finalizing";status to reason}
            append.finish(terminal.first,terminal.second,mapOf("status" to "unknown","clock_source" to "host_utc","reason" to "not_recorded"),
                if(terminal.first=="failed")mapOf("code" to failureCode,"message" to (failure?:terminal.second)) else null)
            backendCloseAttempted=true;backend.close()
            val report=LmtrReader(backend.destination).use { it.check(full=false) }
            synchronized(lock){state="finalized"}
            completion.complete(RecorderResult(true,terminal.first,terminal.second,report,null))
        } catch(error:Exception) {
            // Any uncertain body/footer/sync/close halts append. Never fabricate a repair END.
            if(!backendCloseAttempted)try { backendCloseAttempted=true;backend.close() } catch(_:Exception) { }
            synchronized(lock){stopped=true;state="failed";failure=error.javaClass.simpleName;intake.clear();writer.clear();prepQueued=0;writerQueued=0;controlUsed=0;lock.notifyAll()}
            completion.complete(RecorderResult(false,"failed","write_or_validation_failure",null,error.toString()))
        } finally { synchronized(lock){writerFlight=0;pendingBytes=0} }
    }
}
