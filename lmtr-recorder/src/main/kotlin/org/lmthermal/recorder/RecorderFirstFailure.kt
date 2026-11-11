package org.lmthermal.recorder

/** Fixed diagnostic vocabulary, independent of LMTR error codes and serialized file semantics. */
enum class RecorderFailureStage {
    ADMISSION, PREPARATION_LOSS_RESERVE, WRITER_LOSS_RESERVE, SOURCE_LOSS_RESERVE,
    SOURCE, MEMORY, PREPARATION, WRITER_STARTUP, WRITER_ASSEMBLY, CHUNK_ENCODE,
    CHUNK_VALIDATION_AND_APPEND, CHECKPOINT, END_GUARD, FINALIZATION, BACKEND_APPEND,
    BACKEND_CLOSE, READBACK
}
enum class RecorderFailureCode {
    OVERSIZE_METADATA, UNSUPPORTED_OR_OVERSIZE_OBSERVATION, SOURCE_SEQUENCE_REQUIRES_EXPLICIT_GAP,
    LOSS_JOURNAL_EXHAUSTED, SOURCE_FAILURE, MEMORY_PRESSURE, INVALID_MODULE_EVIDENCE,
    PREPARATION_INTERRUPTED, PREPARATION_FAILED, ALLOCATION_FAILED, UNREPRESENTED_LOSS,
    WRITE_OR_VALIDATION_FAILURE
}
enum class RecorderExceptionKind { NONE, ARGUMENT, STATE, IO, CONTRACT, INTERRUPTED, ALLOCATION, OTHER }

/** First failure observed under the engine lock, NOT proof of the earliest physical cause.
 * One slot is allocated before workers start. Capture writes only primitives, preloaded enum
 * references and bounded existing stream-ID/diagnostic-code strings; even the OOM path allocates no witness,
 * reads no diagnostic clock and retains no Throwable, message, stack, payload or source owner.
 * The engine publishes this same object only after capture. All fields then remain immutable.
 * Sequence/record ordinal/type use -1 when unavailable, never an inferred pending-item identity.
 * lastIntakeWitnessNs is the existing last intake witness, not a new failure timestamp.
 */
class RecorderFirstFailure internal constructor() {
    companion object {
        // Preserve only a bounded scalar reference, including for untrusted refused admissions.
        private const val MAX_DIAGNOSTIC_TEXT_CHARS=64
    }
    private var captured=false
    var stage=RecorderFailureStage.ADMISSION; private set
    var code=RecorderFailureCode.OVERSIZE_METADATA; private set
    var exceptionKind=RecorderExceptionKind.NONE; private set
    var streamId:String?=null; private set
    var detailCode:String?=null; private set
    var observationSequence=-1L; private set
    var recordOrdinal=-1L; private set
    var recordType=-1; private set
    var lastIntakeWitnessNs=0L; private set
    var accepted=0L; private set
    var acknowledged=0L; private set
    var preparationDrops=0L; private set
    var writerDrops=0L; private set
    var controlBytes=0L; private set
    var preparationQueuedBytes=0L; private set
    var writerQueuedBytes=0L; private set
    var preparationInFlightBytes=0L; private set
    var writerInFlightBytes=0L; private set
    var pendingBytes=0L; private set
    var unrepresentedLoss=false; private set

    /** Engine-lock-only single assignment; later faults cannot mutate a published witness. */
    internal fun capture(stage:RecorderFailureStage,code:RecorderFailureCode,kind:RecorderExceptionKind,
        streamId:String?,sequence:Long,ordinal:Long,type:Int,lastIntake:Long,accepted:Long,acknowledged:Long,
        preparationDrops:Long,writerDrops:Long,controlBytes:Long,preparationQueued:Long,writerQueued:Long,
        preparationFlight:Long,writerFlight:Long,pendingBytes:Long,unrepresentedLoss:Boolean,detailCode:String?) {
        if(captured)return
        this.stage=stage;this.code=code;this.exceptionKind=kind
        // Do not retain an unbounded caller-supplied ID on a refused admission. Valid v1 slot IDs
        // are bounded; unavailable/oversize diagnostics remain null instead of copying/truncating.
        this.streamId=streamId?.takeIf { it.length<=MAX_DIAGNOSTIC_TEXT_CHARS }
        // Caller source reasons can be arbitrary text. Retain only bounded identifier tokens,
        // never their prose/exception formatting; known contract codes use the same syntax.
        this.detailCode=detailCode?.takeIf { it.length in 1..MAX_DIAGNOSTIC_TEXT_CHARS &&
            it.all { c -> c in 'a'..'z' || c in '0'..'9' || c=='_' } }
        this.observationSequence=sequence;this.recordOrdinal=ordinal;this.recordType=type
        this.lastIntakeWitnessNs=lastIntake;this.accepted=accepted;this.acknowledged=acknowledged
        this.preparationDrops=preparationDrops;this.writerDrops=writerDrops;this.controlBytes=controlBytes
        this.preparationQueuedBytes=preparationQueued;this.writerQueuedBytes=writerQueued
        this.preparationInFlightBytes=preparationFlight;this.writerInFlightBytes=writerFlight
        this.pendingBytes=pendingBytes;this.unrepresentedLoss=unrepresentedLoss;captured=true
    }
}
