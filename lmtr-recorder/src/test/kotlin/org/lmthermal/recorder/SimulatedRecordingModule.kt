package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deterministic camera-free producer with genuinely different geometry/native/transport evidence. */
internal class SimulatedRecordingModule(val id:String="primary",val width:Int=3,val height:Int=2,
    val thermal:Boolean=true,val native:Boolean=true,val acquisition:Boolean=true,
    val preview:Boolean=false,val session:String="00000000-0000-4000-8000-000000000065") {
    private val geometry=mapOf("width_px" to width,"height_px" to height,"coordinate_space" to "native","origin" to "top_left",
        "x_direction" to "right","y_direction" to "down","matrix_order" to "row_major","orientation" to "source_native")
    private val source=mapOf("module_id" to "org.example.recording.synthetic","model_id" to "Non-USB synthetic module","module_version" to "1","origin" to "simulated")
    private val clock="00000000-0000-4000-8000-000000000384"
    val stream:Obj=mapOf("stream_id" to id,"session_id" to session,"source" to source,"geometry" to geometry,
        "capabilities" to mapOf("temperature" to if(thermal)"supported" else "unsupported","native_samples" to if(native)"supported" else "unsupported",
            "acquisition" to if(acquisition)"supported" else "unsupported","preview" to if(preview)"supported" else "unsupported",
            "visible" to "unsupported","calibration_settings" to "supported"),
        "encodings" to buildList<Obj> {
            if(thermal) { add(mapOf("role" to "temperature","encoding_id" to "org.lmthermal.temperature.f32le-v1"));add(mapOf("role" to "validity","encoding_id" to "org.lmthermal.validity.u8-v1")) }
            if(native)add(mapOf("role" to "native_samples","encoding_id" to "org.example.native.u16-v1"))
            if(acquisition)add(mapOf("role" to "acquisition","encoding_id" to "org.example.packet-v1"))
            if(preview)add(mapOf("role" to "preview","encoding_id" to "org.example.gray.u8-v1"))
        },"clocks" to listOf(mapOf("id" to clock,"kind" to "host_monotonic","quality" to "known","resolution_ticks" to "1",
            "unit" to "ticks","tick_period_ns" to mapOf("numerator" to "1","denominator" to "1"))),"utc_anchors" to emptyList<Any>(),
        "sampling" to mapOf("mode" to "acquisition","period_ns" to null,"origin_timeline_ns" to null))
    fun observation(sequence:Long,calibration:Int=1,masked:Boolean=false,view:Boolean=false,badView:Boolean=false):RecordingObservation {
        val context=mapOf("source" to source,"algorithm" to mapOf("status" to "known","value" to mapOf("id" to "org.example.identity","version" to "1")),
            "calibration" to mapOf("status" to "known","value" to mapOf("revision" to calibration)),
            "settings" to mapOf("status" to "unknown","reason" to "not_available"),"environment" to mapOf("status" to "unknown","reason" to "not_available"))
        fun descriptor(role:String,dtype:String,order:String,encoding:String,grid:Boolean=true):Obj = mapOf(
            "id" to role,"role" to role,"encoding_id" to encoding,"dtype" to dtype,"byte_order" to order,
            "shape" to if(grid)listOf(height,width) else listOf(width*height*2+7),"geometry" to if(grid)geometry else null,
            "layout" to if(grid)"row_major" else "opaque","unit" to if(role=="temperature")"Cel" else null,"lossiness" to "lossless")
        val size=width*height
        val nativeBytes=ByteBuffer.allocate(size*2).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(size){putShort((it+50000).toShort())} }.array()
        val transport=(if(view)nativeBytes else ByteArray(size*2){(it+9).toByte()})+ByteArray(7){77}
        val payloads=buildList {
            if(thermal) {
                val bytes=ByteBuffer.allocate(size*4).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(size){putInt(if(masked && it==1)0 else if(it==0)0x80000000.toInt() else (20f+it+sequence/100f).toRawBits())} }.array()
                add(RecordingPayload(descriptor("temperature","f32","little","org.lmthermal.temperature.f32le-v1"),bytes))
                if(masked)add(RecordingPayload(descriptor("validity","u8","not_applicable","org.lmthermal.validity.u8-v1"),ByteArray(size){if(it==1)0 else 1}))
            }
            if(native)add(RecordingPayload(descriptor("native_samples","u16","little","org.example.native.u16-v1"),
                if(badView)nativeBytes.copyOf().apply { this[0]=0 } else nativeBytes,if(view)0 else null))
            if(acquisition)add(RecordingPayload(descriptor("acquisition","bytes","not_applicable","org.example.packet-v1",false),transport))
            if(preview)add(RecordingPayload(descriptor("preview","u8","not_applicable","org.example.gray.u8-v1"),ByteArray(size){it.toByte()}))
        }
        val unknownUtc=mapOf("status" to "unknown","clock_source" to "unknown","reason" to "no_utc")
        val facts=mapOf("receipt" to mapOf("status" to "known","stamp" to mapOf("clock_domain_id" to clock,"ticks" to sequence.toString()),"utc" to unknownUtc),
            "acquisition" to mapOf("status" to "unknown","reason" to "no_sensor_clock","stamp" to null,"utc" to unknownUtc),"scheduled" to null,"device_sequence" to null,
            "measurement" to if(thermal)mapOf("status" to "available","validity" to if(masked)"partially_valid" else "all_valid","temperature_payload_id" to "temperature",
                "provenance" to mapOf("kind" to "simulated","physical_accuracy" to "not_applicable")).let { if(masked)it+("mask_payload_id" to "validity") else it }
                else mapOf("status" to "unsupported","reason" to "preview_only_module"))
        return RecordingObservation(id,sequence,stream,context,facts,payloads)
    }
    companion object {
        fun header(modules:List<SimulatedRecordingModule>,codec:Int=0,views:Boolean=false):Obj=mapOf(
            "format" to "lmthermal-recording","kind" to "recording","container_version" to mapOf("major" to 1,"minor" to 0),
            "schema_version" to mapOf("major" to 1,"minor" to 0),"recording_id" to "00000000-0000-4000-8000-000000000001",
            "producer" to mapOf("application_id" to "org.lmthermal.recorder.test","version" to "foundation-1"),
            "creation_time" to mapOf("status" to "unknown","clock_source" to "unknown","reason" to "test_clock"),
            "required_features" to (listOf("org.lmthermal.lmtr.core-v1")+(if(views)listOf("org.lmthermal.lmtr.contiguous-native-view-v1") else emptyList())).sorted(),"codecs" to listOf(codec),
            "timeline" to mapOf("clock_domain_id" to "00000000-0000-4000-8000-000000000384","origin_monotonic_ns" to "0","resolution_ns" to "1","source" to "host_monotonic"),
            "streams" to modules.sortedBy { it.id }.map { mapOf("id" to it.id,"purpose" to if(it.thermal)"thermal" else "visible") },
            "privacy" to mapOf("sensitive_metadata" to "omitted","scene_content" to true,"consented_namespaces" to emptyList<Any>()))
    }
}
