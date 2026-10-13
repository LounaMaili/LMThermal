package org.lmthermal.recording

import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** HT-specific encoder of existing immutable gate output. It never calls MeasurementGate/thermometry.
 * Large matrix/raw/transport copies occur only on RecorderEngine's charged preparation worker.
 * Source callback performs a typed epoch check and creates a small deferred offer. No SHA or I/O.
 */
class Ht301RecordingAdapter(override val identity:CameraSourceIdentity):CameraRecordingAdapter {
    override val maximumCharge=3L*1024*1024
    private val geometry=mapOf("width_px" to Ht301Layout.WIDTH,"height_px" to Ht301Layout.IMAGE_HEIGHT,"coordinate_space" to "native",
        "origin" to "top_left","x_direction" to "right","y_direction" to "down","matrix_order" to "row_major","orientation" to "source_native")
    private val source=mapOf("module_id" to "org.lmthermal.camera.ht301","model_id" to identity.module.modelId,
        "module_version" to null,"origin" to identity.module.sourceOrigin)
    override val stream=FrozenJson(mapOf("stream_id" to "primary","session_id" to identity.sessionId,"source" to source,"geometry" to geometry,
        "capabilities" to mapOf("temperature" to "partial","native_samples" to "partial","acquisition" to "supported",
            "calibration_settings" to "partial","preview" to "unsupported","visible" to "unsupported"),
        "encodings" to listOf(mapOf("role" to "temperature","encoding_id" to "org.lmthermal.temperature.f32le-v1"),
            mapOf("role" to "validity","encoding_id" to "org.lmthermal.validity.u8-v1"),
            mapOf("role" to "native_samples","encoding_id" to "org.lmthermal.ht301.raw14"),
            mapOf("role" to "acquisition","encoding_id" to "org.lmthermal.ht301.uvc-transport-v1")),
        "clocks" to listOf(RecordingSourceMetadata.clock(identity)),"utc_anchors" to emptyList<Any>(),
        "sampling" to mapOf("mode" to "acquisition","period_ns" to null,"origin_timeline_ns" to null)))
    override fun offer(source:CameraAcquiredObservation):RecordingOffer {
        require(source is Ht301AcquiredObservation && source.identity==identity && source.retainedBytes<=maximumCharge)
        return RecordingOffer("primary",source.ordinal,stream,maximumCharge){freeze(source)}
    }
    override fun loss(source:CameraSourceLoss):RecordingLoss { require(source.identity==identity);return RecordingSourceMetadata.loss(stream,source) }
    private fun freeze(value:Ht301AcquiredObservation):RecordingObservation {
        val measurement=value.result.measurement
        val rawAvailable=value.frame!=null && value.inspection.mode==FrameMode.RAW14 && value.inspection.reason==null
        val transport=value.transportBytes()
        val parsed=measurement?.parameters ?: value.frame?.let { runCatching { FrameParameters.decode(it) }.getOrNull() }
        val interpreted=measurement?.let { Ht301ExportEvidence.interpretation(it) }
        fun known(v:Any?)=mapOf("status" to "known","value" to exact(v))
        val unknown=mapOf("status" to "unavailable","reason" to "radiometry_not_valid")
        val context=mapOf("source" to source,"algorithm" to known(mapOf("id" to "org.lmthermal.ht301.native-equivalent-normal","version_status" to "not_exposed")),
            "settings" to if(parsed==null)unknown else known(mapOf("correction_c" to parsed.correction,"reflected_c" to parsed.reflected,
                "ambient_c" to parsed.ambient,"humidity" to parsed.humidity,"emissivity" to parsed.emissivity,"distance" to parsed.distance,
                "host_configuration" to (interpreted?.get("host_configuration") ?: mapOf("status" to "unknown","reason" to "not_evaluated")))),
            "calibration" to if(parsed==null)unknown else known(mapOf("coefficients" to listOf(parsed.c0,parsed.c1,parsed.c2,parsed.c3,parsed.c4),
                "lookup_trace" to (interpreted?.get("lookup_trace") ?: mapOf("status" to "unknown","reason" to "not_evaluated")))),
            "environment" to if(interpreted==null)unknown else known(interpreted["lookup_trace"].obj()["environment"]))
        fun descriptor(role:String,dtype:String,encoding:String,grid:Boolean=true):Obj=mapOf("id" to role,"role" to role,"encoding_id" to encoding,
            "dtype" to dtype,"byte_order" to if(dtype=="f32" || dtype=="u16")"little" else "not_applicable",
            "shape" to if(grid)listOf(Ht301Layout.IMAGE_HEIGHT,Ht301Layout.WIDTH) else listOf(transport!!.size),
            "layout" to if(grid)"row_major" else "opaque","geometry" to if(grid)geometry else null,"unit" to if(role=="temperature")"Cel" else null,"lossiness" to "lossless")
        val payloads=buildList {
            if(measurement!=null) {
                val values=measurement.matrix();val bytes=ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN)
                for(v in values)bytes.putInt(v.toRawBits())
                add(RecordingPayload(descriptor("temperature","f32","org.lmthermal.temperature.f32le-v1"),bytes.array()))
            }
            if(rawAvailable)add(RecordingPayload(descriptor("native_samples","u16","org.lmthermal.ht301.raw14"),value.frame!!.imageBytes(),0))
            transport?.let { add(RecordingPayload(descriptor("acquisition","bytes","org.lmthermal.ht301.uvc-transport-v1",false),it)) }
        }
        val availability=buildMap<String,Any?> {
            if(measurement==null)put("temperature",mapOf("status" to "unavailable","reason" to value.unavailableReason))
            if(!rawAvailable)put("native_samples",mapOf("status" to "unavailable","reason" to "not_valid_raw14"))
            if(parsed==null)put("calibration_settings",mapOf("status" to "unavailable","reason" to "radiometry_not_valid"))
        }
        val facts=mapOf("receipt" to RecordingSourceMetadata.receipt(identity,value.receivedMonotonicNs),"acquisition" to RecordingSourceMetadata.acquisition,
            "device_sequence" to null,"scheduled" to null,"availability" to availability,
            "measurement" to if(measurement==null)mapOf("status" to "unavailable","reason" to value.unavailableReason)
                else mapOf("status" to "available","validity" to "all_valid","temperature_payload_id" to "temperature",
                    "provenance" to mapOf("kind" to "native_equivalent","physical_accuracy" to "not_independently_validated","warning" to NativeEquivalentThermometry.WARNING)),
            "source_evidence" to mapOf("module_namespace" to "org.lmthermal.camera.ht301","host_uvc_callback_sequence" to value.sourceSequence.toString(),
                "original_measurement_received_monotonic_ms" to measurement?.receivedMonotonicMs?.toString(),
                "measurement_reason" to value.result.reason,
                "session_state" to value.sessionState.name,"inspection_reason" to value.inspection.reason,
                "native_observations" to exact(interpreted?.get("observations"))))
        return RecordingObservation("primary",value.ordinal,stream.value(),context,facts,payloads)
    }
    /** JSON carries exact finite decimal values plus source IEEE bits, including -0/nonfinite evidence. */
    private fun exact(value:Any?):Any? = when(value) {
        is Float -> mapOf("ieee754_f32_bits" to "%08x".format(value.toRawBits()),"value" to if(value.isFinite())BigDecimal(value.toDouble()) else null)
        is Double -> mapOf("ieee754_f64_bits" to "%016x".format(value.toRawBits()),"value" to if(value.isFinite())BigDecimal(value) else null)
        is Map<*,*> -> value.entries.associate { it.key.toString() to exact(it.value) }
        is List<*> -> value.map(::exact)
        else -> value
    }
}
