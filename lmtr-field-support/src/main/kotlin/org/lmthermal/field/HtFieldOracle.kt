package org.lmthermal.field

import org.lmthermal.camera.CameraAcquiredObservation
import org.lmthermal.camera.ht301.Ht301AcquiredObservation
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import java.security.MessageDigest

/** Independently walks original Float32 bits and immutable image/transport owners before adapter.freeze.
 * Hashing is on preparation, never acquisition/UI. Does not call RecordingProfiles or the LMTR encoder.
 * Absence of a mask is original all-valid evidence, not a synthesized byte mask.
 */
object HtFieldOracle {
    private fun digest(bytes:ByteArray)=mapOf("hash" to LmtrChunk.sha(bytes),"length" to bytes.size.toString())
    /** Independent original-bit oracle with one call-local4 KiB scratch, not110592 tiny digest
     * updates per HT frame. No shared pool or recorder-produced bytes are used. Preserve NaN payloads,
     * signed zero and row-major little-endian order; the last partial batch has no padding authority.
     */
    fun float32Hash(values:FloatArray):String {
        val hash=MessageDigest.getInstance("SHA-256");val scratch=ByteArray(4096);var used=0
        for(value in values) {
            val bits=value.toRawBits()
            for(index in 0..3)scratch[used++]=(bits ushr (index*8)).toByte()
            if(used==scratch.size){hash.update(scratch);used=0}
        }
        if(used>0)hash.update(scratch,0,used)
        return hash.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    fun capture(source:CameraAcquiredObservation):Obj {
        require(source is Ht301AcquiredObservation)
        val roles=linkedMapOf<String,Any?>()
        source.measurement?.let { m ->
            val values=m.matrix()
            roles["temperature"]=mapOf("hash" to float32Hash(values),"length" to (values.size*4).toString())
            m.validityMask()?.let { roles["validity"]=digest(it) }
        }
        val raw=source.frame?.takeIf { source.inspection.mode==FrameMode.RAW14 && source.inspection.reason==null }?.imageBytes()
        raw?.let { roles["native_samples"]=digest(it) }
        val transport=source.transportBytes()
        transport?.let { roles["acquisition"]=digest(it) }
        val exactView=raw!=null && transport!=null && raw.size<=transport.size && raw.indices.all { raw[it]==transport[it] }
        return mapOf("original_payloads" to roles,"original_receipt_ns" to source.receivedMonotonicNs.toString(),
            "original_clock_id" to source.identity.receiptClockId,"original_session_id" to source.identity.sessionId,
            "original_source_sequence" to source.sourceSequence.toString(),"original_ordinal" to source.ordinal.toString(),
            "original_measurement_available" to (source.measurement!=null),"original_unavailable_reason" to source.unavailableReason,
            "original_session_state" to source.sessionState.name,"original_inspection_reason" to source.inspection.reason,
            "original_native_transport_same_observation_view" to exactView)
    }
}
