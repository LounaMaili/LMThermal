package org.lmthermal.lmtr

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Owned deterministic fixture-plan publication; no live camera, Android storage or timer. */
object LmtrWriter {
    /** Publish a new file only, validate every serialized chunk, close and reopen independently. */
    fun writePlan(plan: Obj, destination: File): Obj {
        val header=plan["header"].obj();LmtrMetadata.header(header)
        demand(!destination.exists() && destination.createNewFile(),"storage_failure")
        try {
            RandomAccessFile(destination,"rw").use { file ->
                val session=LmtrAppendSession(header,file)
                for (item in plan["chunks"].arr().map { it.obj() }) {
                    val original=item["metadata"].obj();val recipes=item["payload_recipes"].obj()
                    // Plans have provisional stored offsets/hashes, not wire
                    // metadata. Admit local shapes and declared byte intervals
                    // before recipes; validate full closure after actual encoding.
                    val declaredBlocks=original["blocks"].arr().map { it.obj() }
                    for(block in declaredBlocks)demand(block["codec"].i() in 0..1,"unsupported_codec")
                    LmtrMetadata.local("Chunk",original)
                    val declaredBytes=declaredBlocks.fold(0L) { total,block -> LmtrChunk.add(total,block["decoded"].d()) }
                    demand(LmtrChunk.add(declaredBytes,4L+LmtrJson.encode(original).size)<=67108864L,"resource_limit")
                    val declaredById=declaredBlocks.associateBy { it["id"].text() }
                    for(entry in original["entries"].arr().map { it.obj() })if(entry["kind"]=="observation")
                        for(p in entry["payloads"].arr().map { it.obj() })if(p["kind"]=="materialized") {
                            val block=declaredById[p["block_id"].text()] ?: throw LmtrFailure("invalid_metadata")
                            demand(LmtrChunk.add(p["offset"].d(),p["length"].d())<=block["decoded"].d(),"invalid_payload")
                        }
                    val owned=linkedMapOf<String,ByteArray>()
                    val entries=original["entries"].arr().map { it.obj() }.map { entry ->
                        if(entry["kind"]!="observation")entry else {
                            val descriptors=entry["payloads"].arr().map { it.obj() }
                            for(payload in descriptors) {
                                val key="${entry["entry_id"].text()}:${payload["id"].text()}"
                                val bytes=recipe(recipes[key].obj())
                                demand(bytes.size.toLong()==payload["length"].d(),"invalid_payload")
                                owned[key]=bytes
                            }
                            for(payload in descriptors.filter { it["kind"]=="view" }) {
                                // A source plan carries independently owned native evidence. Never
                                // discard it just because an acquisition slice happens to exist.
                                val parentId=payload["parent_payload_id"].text()
                                val parent=descriptors.find { it["id"]==parentId } ?: throw LmtrFailure("invalid_payload")
                                demand(parent["kind"]=="materialized" && parent["role"]=="acquisition","invalid_payload")
                                val actual=owned.getValue("${entry["entry_id"].text()}:$parentId")
                                val native=owned.getValue("${entry["entry_id"].text()}:${payload["id"].text()}")
                                val start=payload["offset"].d();val end=LmtrChunk.add(start,native.size.toLong())
                                demand(end<=actual.size && native.indices.all { native[it]==actual[start.toInt()+it] },"invalid_payload")
                            }
                            entry+("payloads" to descriptors.map { payload ->
                                val bytes=owned.getValue("${entry["entry_id"].text()}:${payload["id"].text()}")
                                payload+("hash" to LmtrChunk.sha(bytes))
                            })
                        }
                    }
                    val physical=linkedMapOf<String,MutableList<Pair<Long,ByteArray>>>()
                    for(entry in entries) if(entry["kind"]=="observation") {
                        for(p in entry["payloads"].arr().map { it.obj() }.filter { it["kind"]=="materialized" }) {
                            val key="${entry["entry_id"].text()}:${p["id"].text()}"
                            physical.getOrPut(p["block_id"].text()){mutableListOf()}.add(p["offset"].d() to owned.getValue(key))
                        }
                    }
                    val stored=mutableListOf<ByteArray>();var offset=0L
                    val blocks=original["blocks"].arr().map { raw ->
                        val block=raw.obj();val slices=physical[block["id"].text()] ?: throw LmtrFailure("invalid_payload")
                        var decoded=0L;for((start,bytes)in slices){demand(start==decoded,"invalid_payload");decoded=LmtrChunk.add(decoded,bytes.size.toLong())}
                        demand(decoded<=67108864,"resource_limit");val all=ByteArray(decoded.toInt());var at=0
                        for((_,bytes)in slices){bytes.copyInto(all,at);at+=bytes.size}
                        val encoded=LmtrCodec.encode(all,block["codec"].i());stored.add(encoded)
                        val updated=block.toMutableMap();updated["offset"]=offset.toString();updated["decoded"]=decoded.toString();updated["stored"]=encoded.size.toString();updated["hash"]=LmtrChunk.sha(all)
                        offset=LmtrChunk.add(offset,encoded.size.toLong());updated.toMap()
                    }
                    val meta=original.toMutableMap().apply { put("entries",entries);put("blocks",blocks) }.toMap();val json=LmtrJson.encode(meta)
                    val bodySize=LmtrChunk.add(4L+json.size,offset);demand(bodySize<=LmtrFraming.MAX_RECORD_BYTES-112,"resource_limit")
                    val body=ByteArray(bodySize.toInt());ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).putInt(json.size).put(json)
                    var at=4+json.size;for(b in stored){b.copyInto(body,at);at+=b.size}
                    session.appendChunk(body)
                }
                session.finish("completed","synthetic_fixture_stop",plan["finalization_time"]?.obj()
                    ?: mapOf("status" to "unknown","clock_source" to "host_utc","reason" to "not_recorded"))
            }
            // Directory persistence/platform close failures are not certified by
            // this synthetic-plan harness. Production backend admission is R4 work.
            return LmtrReader(destination).use { it.check(full=true) }
        } catch(e:Exception) { throw e } // Preserve this owned unfinished file as failure evidence.
    }

    /** Retained test entry point; rolling index partitioning belongs to the shared append owner. */
    internal fun partitionIndexChildren(children: List<Obj>, fits: (List<Obj>) -> Boolean): List<List<Obj>> =
        LmtrAppendSession.partitionIndexChildren(children,fits)

    /** Synthetic recipe construction is a fixture transport, never wire calibration logic. */
    fun recipe(value: Obj): ByteArray {
        fun count():Int { val n=value["count"].i();demand(n>0,"invalid_metadata");return n }
        fun scalar(value: Any?): Float {
            // Recipes are explicit synthetic source instructions, not wire JSON measurement authority.
            // Exact bit-sensitive values (notably negative zero and NaN payloads) use hex recipes.
            val result=when(value) {
                is JsonNumber -> value.decimal.toFloat()
                is java.math.BigDecimal -> value.toFloat()
                is Int -> value.toFloat()
                is Long -> value.toFloat()
                is String -> value.toFloatOrNull() ?: throw LmtrFailure("invalid_metadata")
                else -> throw LmtrFailure("invalid_metadata")
            }
            demand(result.isFinite(),"invalid_payload");return result
        }
        return when(value["kind"].text()) {
            "hex" -> { val hex=value["hex"].text();demand(hex.length%2==0 && hex.all { it in "0123456789abcdef" },"invalid_metadata");ByteArray(hex.length/2){i->hex.substring(i*2,i*2+2).toInt(16).toByte()} }
            "f32_constant" -> { val n=count();demand(n<=67108864/4,"resource_limit");val number=scalar(value["value"]);ByteBuffer.allocate(n*4).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(n){putFloat(number)} }.array() }
            "u16_constant" -> { val n=count();val number=value["value"].i();demand(n<=67108864/2 && number in 0..65535,"resource_limit");ByteBuffer.allocate(n*2).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(n){putShort(number.toShort())} }.array() }
            "f32_values" -> { val values=value["values"].arr();demand(values.size<=67108864/4,"resource_limit");ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN).apply { for(v in values)putFloat(scalar(v)) }.array() }
            "transport" -> { val native=recipe(value["native"].obj());val tail=value["trailer_bytes"].i();demand(native.size+tail.toLong()<=67108864,"resource_limit");native+ByteArray(tail) }
            else -> throw LmtrFailure("invalid_metadata")
        }
    }
}
