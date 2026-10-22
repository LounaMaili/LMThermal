package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A bounded pending entry carries complete closure, not a reference to a historical chunk. */
internal data class PendingEntry(val entry: Obj,val segment: Obj,val context: Obj?=null,
    val payloads: List<RecordingPayload> = emptyList())

/** Assemble real owned source bytes into the accepted R3 grammar, without synthetic recipes.
 * Native views are opt-in, same-observation and checked against independent module evidence.
 * Statistics are caches computed from original Float32 bits, never a new temperature algorithm.
 */
internal object RecordingChunk {
    fun encode(header: Obj, entries: List<PendingEntry>, codec: Int,validationBudget:Long,
        codecMetric:((Long,Long,Long)->Unit)?=null,now:()->Long=System::nanoTime): ByteArray {
        require(entries.isNotEmpty() && entries.size<=32)
        val blocks=mutableListOf<Obj>();val stored=mutableListOf<ByteArray>();var storedAt=0L
        // Retain references to immutable owners, not a second entire CHUNK of payload copies.
        val groups=linkedMapOf<List<String>,MutableList<RecordingPayload>>()
        fun key(p:Obj)=listOf("role","encoding_id","dtype","byte_order").map { p[it].text() }
        val features=header["required_features"].arr().map { it.text() }
        val viewFeature="org.lmthermal.lmtr.contiguous-native-view-v1"
        var hasView=false
        val wireEntries=entries.map { pending ->
            if(pending.entry["kind"]!="observation") pending.entry else {
                val acquisition=pending.payloads.find { it.role=="acquisition" }
                val descriptors=pending.payloads.map { payload ->
                    val original=payload.descriptor.value()
                    require(original.keys.none { it in listOf("kind","block_id","offset","length","hash","parent_payload_id") })
                    val base=original+mapOf("length" to payload.size.toString(),"hash" to payload.contentHash())
                    if(payload.acquisitionViewOffset!=null && acquisition!=null && viewFeature in features) {
                        require(payload.role=="native_samples" && payload.matchesSlice(acquisition,payload.acquisitionViewOffset)) { "Native view is not exact source evidence" }
                        hasView=true
                        base+mapOf("kind" to "view","offset" to payload.acquisitionViewOffset.toString(),"parent_payload_id" to "acquisition")
                    } else {
                        val group=groups.getOrPut(key(original)){mutableListOf()}
                        val offset=group.sumOf { it.size.toLong() };group.add(payload)
                        base+mapOf("kind" to "materialized","_group" to key(original),"offset" to offset.toString())
                    }
                }
                val temperature=pending.payloads.find { it.role=="temperature" }
                var measurement=pending.entry["measurement"].obj()
                if(temperature!=null && !measurement.containsKey("statistics")) {
                    val geometry=temperature.descriptor.value()["geometry"].obj()
                    val mask=pending.payloads.find { it.role=="validity" }
                    measurement=measurement+("statistics" to temperature.statistics(mask,geometry["width_px"].i(),geometry["height_px"].i())+mapOf("method" to "finite-valid-row-major-f64-v1","temperature_payload_id" to "temperature","unit" to "Cel"))
                }
                pending.entry+mapOf("payloads" to descriptors,"measurement" to measurement)
            }
        }
        val dtypes=listOf("u8","i8","u16","i16","u32","i32","u64","i64","f32","f64","bytes")
        val orders=listOf("little","big","not_applicable")
        val keys=groups.keys.sortedWith(compareBy<List<String>> { LmtrChunk.roles.indexOf(it[0]) }
            .thenBy { it[1] }.thenBy { dtypes.indexOf(it[2]) }.thenBy { orders.indexOf(it[3]) })
        for((index,key) in keys.withIndex()) {
            val slices=groups.getValue(key);val decoded=slices.sumOf { it.size.toLong() }
            require(decoded<=67108864L)
            val all=ByteArray(decoded.toInt());var at=0;for(payload in slices){payload.copyInto(all,at);at+=payload.size}
            val started=if(codecMetric!=null)now() else 0L
            // This concatenation is a private writer allocation: STORED can retain it directly.
            // The public codec API still returns a defensive copy to independent consumers.
            // Local binary workspace cap: segments plus final output cost <=2*cap. This is
            // independent of the decoded hard limit and does not reduce admission safeguards.
            val encoded=if(codec==0)all else {
                require(codec==1)
                val cap=minOf(validationBudget/2,LmtrFraming.MAX_RECORD_BYTES-LmtrFraming.ENVELOPE_BYTES-Int.SIZE_BYTES-storedAt)
                if(cap<=0)throw LmtrFailure("local_resource_limit")
                RecordingDeflate.encode(all,cap.toInt())
            }
            // Test-only observer times only the existing codec call, leaving encoded bytes unchanged.
            if(codecMetric!=null)runCatching { codecMetric.invoke(now()-started,all.size.toLong(),encoded.size.toLong()) }
            stored.add(encoded)
            blocks.add(mapOf("id" to "b$index","role" to key[0],"encoding_id" to key[1],"dtype" to key[2],"byte_order" to key[3],
                "codec" to codec,"offset" to storedAt.toString(),"stored" to encoded.size.toString(),"decoded" to decoded.toString(),"hash" to LmtrChunk.sha(all)))
            storedAt=LmtrChunk.add(storedAt,encoded.size.toLong())
        }
        val placed=wireEntries.map { entry -> if(entry["kind"]!="observation")entry else entry+("payloads" to entry["payloads"].arr().map {
            val p=it.obj();if(p["kind"]=="view")p else p.filterKeys { it!="_group" }+("block_id" to "b${keys.indexOf(p["_group"].arr().map { it.text() })}")
        }) }
        val meta=mapOf("recording_id" to header["recording_id"],
            "required_features" to (listOf("org.lmthermal.lmtr.core-v1")+(if(hasView)listOf(viewFeature) else emptyList())).sorted(),
            "segments" to entries.map { it.segment }.distinctBy { it["id"] }.sortedBy { it["id"].text() },
            "contexts" to entries.mapNotNull { it.context }.distinctBy { it["id"] }.sortedBy { it["id"].text() },
            "entries" to placed,"blocks" to blocks)
        val json=LmtrJson.encode(meta)
        val decoded=blocks.sumOf { it["decoded"].d() }
        require(decoded+4L+json.size<=67108864L && json.size<=1048576)
        val size=4L+json.size+storedAt
        require(size<=LmtrFraming.MAX_RECORD_BYTES-112L)
        val body=ByteArray(size.toInt());ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).putInt(json.size).put(json)
        var at=4+json.size;for(bytes in stored){bytes.copyInto(body,at);at+=bytes.size}
        LmtrChunk.validate(body,header,validationBudget) // Full checks before any promised footer.
        return body
    }
}
