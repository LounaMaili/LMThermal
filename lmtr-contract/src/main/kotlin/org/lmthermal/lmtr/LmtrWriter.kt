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
                var ordinal=0L;var previous=LmtrFraming.SENTINEL
                fun append(type:Int,body:ByteArray):Record {
                    val record=LmtrFraming.append(file,type,ordinal,previous,body)
                    previous=record.offset;ordinal=LmtrChunk.add(ordinal,1);return record
                }
                append(LmtrFraming.HEADER,LmtrJson.encode(header))
                val levels=mutableListOf<MutableList<Obj>>();val state=JournalState();val summary=SummaryAccumulator(header)
                var lastChunk:Obj?=null;var previousCheckpoint:Obj?=null;var sinceCheckpoint=0
                fun makePage(depth:Int,children:List<Obj>):Obj {
                    demand(depth in 0..7 && children.isNotEmpty() && children.size<=256,"resource_limit")
                    val refs=children.map { it["record"].obj() };val streamIds=children.flatMap { it["stream_ids"].arr().map { v->v.text() } }.sorted().distinct()
                    val page=linkedMapOf<String,Any?>("depth" to depth,"children" to children,"first" to refs.first()["first"],"last" to refs.last()["last"],
                        "time_first_ns" to children.first()["time_first_ns"],"time_last_ns" to children.last()["time_last_ns"],"stream_ids" to streamIds,
                        "chunks" to children.fold(0L){n,c->LmtrChunk.add(n,c["_chunks"].d())}.toString(),
                        "entries" to children.fold(0L){n,c->LmtrChunk.add(n,c["_entries"].d())}.toString())
                    page["children"]=children.map { it.filterKeys { k->!k.startsWith("_") } }
                    LmtrMetadata.local("IndexPage",page);demand(LmtrJson.encode(page).size<=524288,"resource_limit");return page
                }
                fun pageChild(depth:Int,children:List<Obj>):Obj {
                    val page=makePage(depth,children);val r=append(LmtrFraming.INDEX,LmtrJson.encode(page))
                    return linkedMapOf("record" to RecordRef(r.offset,r.length,r.ordinal,page["first"].d(),page["last"].d(),r.refHash).json(),
                        "time_first_ns" to page["time_first_ns"],"time_last_ns" to page["time_last_ns"],"stream_ids" to page["stream_ids"],
                        "_chunks" to page["chunks"],"_entries" to page["entries"])
                }
                fun push(initialDepth:Int,initial:Obj) {
                    var depth=initialDepth;var child=initial
                    while(true) {
                        demand(depth<=7,"resource_limit");while(levels.size<=depth)levels.add(mutableListOf())
                        val pending=levels[depth]
                        if(pending.isNotEmpty() && (pending.size==256 || try { LmtrJson.encode(makePage(depth,pending+child)).size>524288 } catch(e:LmtrFailure){ if(e.code=="resource_limit")true else throw e })) {
                            val promoted=pageChild(depth,pending.toList());pending.clear();
                            // Recursively promoting needs no duration-sized reference list.
                            pending.add(child);depth++;child=promoted;continue
                        }
                        pending.add(child);return
                    }
                }
                fun snapshotRoot():Obj? {
                    var carry=emptyList<Obj>();var depth=0
                    while(depth<levels.size || carry.isNotEmpty()) {
                        val pending=if(depth<levels.size)levels[depth].toList() else emptyList()
                        val children=pending+carry
                        if(children.isEmpty()){depth++;continue}
                        // Snapshot pages do not mutate rolling pending state. A
                        // full upper level plus a lower snapshot may have 257
                        // children; split it before promotion rather than failing
                        // a valid recording at the first multi-level checkpoint.
                        if(depth>0 && children.size==1 && levels.drop(depth+1).all { it.isEmpty() })return children.single()["record"].obj()
                        demand(depth<=7,"resource_limit")
                        carry=partitionIndexChildren(children) { group ->
                            try { makePage(depth,group);true }
                            catch(e:LmtrFailure) { if(e.code=="resource_limit")false else throw e }
                        }.map { pageChild(depth,it) }
                        depth++
                    }
                    return null
                }

                fun checkpoint():Pair<Record,Obj> {
                    val root=snapshotRoot();val value=linkedMapOf<String,Any?>("recording_id" to header["recording_id"],"root" to root,
                        "last_chunk" to lastChunk,"previous_checkpoint" to previousCheckpoint,"summary" to summary.json())
                    LmtrMetadata.local("Checkpoint",value);val r=append(LmtrFraming.CHECKPOINT,LmtrJson.encode(value))
                    val s=summary.json();previousCheckpoint=RecordRef(r.offset,r.length,r.ordinal,s["first_entry"]?.d(),s["last_entry"]?.d(),r.refHash).json()
                    sinceCheckpoint=0;return r to value
                }
                for (item in plan["chunks"].arr().map { it.obj() }) {
                    val original=item["metadata"].obj();val recipes=item["payload_recipes"].obj()
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
                    LmtrChunk.decode(body,header);state.accept(meta)
                    val r=append(LmtrFraming.CHUNK,body);summary.accept(meta)
                    val first=entries.first();val last=entries.last()
                    val ref=RecordRef(r.offset,r.length,r.ordinal,first["entry_id"].d(),last["entry_id"].d(),r.refHash).json();lastChunk=ref
                    val segments=meta["segments"].arr().map { it.obj() }.associateBy { it["id"].text() };val ids=sortedSetOf<String>()
                    for(e in entries)if(e["stream_id"]!=null)ids.add(e["stream_id"].text())else ids.addAll(segments.getValue(e["segment_id"].text())["streams"].arr().map { it.obj()["stream_id"].text() })
                    push(0,linkedMapOf("record" to ref,"time_first_ns" to first["timeline_ns"],"time_last_ns" to last["timeline_ns"],"stream_ids" to ids.toList(),"_chunks" to "1","_entries" to entries.size.toString()))
                    sinceCheckpoint++;if(sinceCheckpoint==32)checkpoint()
                }
                demand(state.activeSegment==null,"invalid_metadata")
                val (cpRecord,cp)=checkpoint();val s=summary.json()
                val end=linkedMapOf<String,Any?>("recording_id" to header["recording_id"],"root" to cp["root"],
                    "checkpoint" to RecordRef(cpRecord.offset,cpRecord.length,cpRecord.ordinal,s["first_entry"]?.d(),s["last_entry"]?.d(),cpRecord.refHash).json(),
                    "summary" to s,"status" to "completed","reason" to "synthetic_fixture_stop","finalization_time" to (plan["finalization_time"]?:header["creation_time"]),"error" to null)
                LmtrMetadata.local("End",end);append(LmtrFraming.END,LmtrJson.encode(end))
            }
            // Directory persistence/platform close failures are not certified by
            // this synthetic-plan harness. Production backend admission is R4 work.
            return LmtrReader(destination).use { it.check(full=true) }
        } catch(e:Exception) { throw e } // Preserve this owned unfinished file as failure evidence.
    }

    /** Split a snapshot frontier by fanout and exact body admission, preserving order. */
    internal fun partitionIndexChildren(children: List<Obj>, fits: (List<Obj>) -> Boolean): List<List<Obj>> {
        val groups=mutableListOf<List<Obj>>();var pending=mutableListOf<Obj>()
        for(child in children) {
            if(pending.isNotEmpty() && (pending.size==256 || !fits(pending+child))) {
                groups.add(pending.toList());pending=mutableListOf()
            }
            demand(fits(listOf(child)),"resource_limit")
            pending.add(child)
        }
        if(pending.isNotEmpty())groups.add(pending.toList())
        return groups
    }

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
