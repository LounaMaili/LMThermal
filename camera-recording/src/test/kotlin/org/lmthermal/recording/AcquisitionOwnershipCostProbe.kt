package org.lmthermal.recording

import org.lmthermal.camera.CameraSourceIdentity
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.LmtrJson
import java.io.File

/** Measure the exceptional callback-side owned copy with synthetic malformed transport.
 * Complete frames retain their existing immutable owner; this probe times only the path
 * that must copy invalid transport, without parser/thermometry/hash/codec/file I/O.
 * The finite test owner array escapes the loop so JIT cannot elide the actual copies.
 */
object AcquisitionOwnershipCostProbe {
    @JvmStatic fun main(args:Array<String>) { measure(File(args[0])) }
    fun measure(output:File) {
        val identity=CameraSourceIdentity(Ht301ModuleProfile.metadata)
        val input=ByteArray(Ht301Layout.FRAME_BYTES-1){(it*7).toByte()}
        val inspection=FrameInspection(FrameMode.INVALID,"transport_size",null,null)
        val result=MeasurementResult(null,"transport_size")
        fun owned()=Ht301AcquiredObservation(identity,0,1,System.nanoTime(),null,inspection,
            SessionState.RAW14_UNSETTLED,result,input)
        repeat(10){owned().transportBytes()}
        val owners=arrayOfNulls<Ht301AcquiredObservation>(50)
        val times=LongArray(owners.size)
        for(i in owners.indices){val begin=System.nanoTime();owners[i]=owned();times[i]=System.nanoTime()-begin}
        input[0]=99
        for(owner in owners){check(owner!!.transportBytes()!![0]==0.toByte());check(owner.retainedBytes==input.size.toLong())}
        val ordered=times.sorted()
        output.writeBytes(LmtrJson.encode(mapOf("samples" to owners.size,"synthetic_transport_bytes" to input.size,
            "callback_ownership_copy_p50_ns" to ordered[(ordered.size-1)/2].toString(),
            "callback_ownership_copy_p95_ns" to ordered[((ordered.size-1)*.95).toInt()].toString(),
            "callback_ownership_copy_max_ns" to ordered.last().toString(),
            "ownership_verified" to true,
            "scope" to "synthetic malformed transport wrapper/copy; no parser, thermometry, hash, codec or I/O within timing; not physical camera performance")))
    }
}
