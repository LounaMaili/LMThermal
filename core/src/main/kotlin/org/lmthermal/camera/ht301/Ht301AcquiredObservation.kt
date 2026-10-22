package org.lmthermal.camera.ht301

import org.lmthermal.camera.*
import org.lmthermal.core.*

/** Source wrapper retains already-owned frame/matrix/index arrays; it never reruns thermometry.
 * Complete frames are immutable Ht301Frame owners. Only malformed transport needs a new owned copy.
 * Pixel/grid/orientation semantics stay native. Source sequence here is a host UVC callback sequence. Optional session reason/gate wall duration
 * retain existing diagnostics for field accounting; neither changes the gate or marks readiness.
 */
class Ht301AcquiredObservation(override val identity:CameraSourceIdentity,override val ordinal:Long,
    override val sourceSequence:Long,override val receivedMonotonicNs:Long,val frame:Ht301Frame?,
    val inspection:FrameInspection,val sessionState:SessionState,val result:MeasurementResult,
    incompleteTransport:ByteArray?=null,val sessionReason:String?=null,val measurementWallNs:Long?=null):CameraAcquiredObservation {
    private val incomplete=incompleteTransport?.copyOf()
    override val measurement:ThermalMeasurement?=result.measurement?.let(::Ht301ThermalMeasurement)
    override val unavailableReason:String?=if(result.measurement!=null)null else when {
        frame==null -> "malformed_transport"
        sessionState!=SessionState.RADIOMETRIC_READY -> "session_not_ready"
        else -> "thermometry_rejected"
    }
    override val retainedBytes:Long=(frame?.size ?: incomplete?.size ?: 0).toLong()+
        if(result.measurement!=null)Ht301Layout.IMAGE_WORDS*8L else 0L
    fun transportBytes():ByteArray?=frame?.transportBytes() ?: incomplete?.copyOf()
    init {
        require(frame==null || incomplete==null);require(receivedMonotonicNs>=0 && ordinal>=0)
        require(result.measurement==null || (result.measurement.source===frame && result.measurement.sequence==sourceSequence))
    }
}
