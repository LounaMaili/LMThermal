package org.lmthermal.lmtr

import java.io.RandomAccessFile

/** A serialized append boundary. Success means the complete footer was synchronized.
 * Any failure poisons its owner: callers must preserve the file and never append a repair END.
 * Injection is for backend tests; production uses the existing R3 local-file primitive.
 */
fun interface LmtrRecordSink {
    fun append(type: Int, ordinal: Long, previous: Long, body: ByteArray): Record
}

/** Incremental contract writer shared by fixture publication and the isolated R4 engine.
 * Exactly one worker owns it. Rolling index state is bounded, not proportional to duration.
 * No camera, synthetic recipe, timing or admission policy belongs in this class.
 */
class LmtrAppendSession(header: Obj, private val sink: LmtrRecordSink,
    private val onChunkAcknowledged:(Obj)->Unit = {},
    private val onCheckpointAcknowledged:(Obj)->Unit = {},
    private val validationBudget:Long = 134217728L) {
    constructor(header: Obj, file: RandomAccessFile): this(header, LmtrRecordSink { type, ordinal, previous, body ->
        LmtrFraming.append(file, type, ordinal, previous, body)
    })
    private val header=LmtrJson.parse(LmtrJson.encode(header))
    private var ordinal=0L
    private var previous=LmtrFraming.SENTINEL
    private var poisoned=false
    private var finished=false
    val uncheckpointedChunks: Int get() = sinceCheckpoint
    val acknowledgedSummary: Obj get() = summary.json()
    init { LmtrMetadata.header(this.header);append(LmtrFraming.HEADER,LmtrJson.encode(this.header)) }
    /** Validation can partially advance journal state; any failed operation ends this owner. */
    private fun <T> guarded(action:()->T):T {
        check(!poisoned && !finished)
        return try { action() } catch(failure:Exception) { poisoned=true;throw failure }
    }
    private fun append(type:Int,body:ByteArray):Record {
        check(!poisoned && !finished)
        try {
            val record=sink.append(type,ordinal,previous,body)
            previous=record.offset;ordinal=LmtrChunk.add(ordinal,1);return record
        } catch(failure:Exception) { poisoned=true;throw failure }
    }
    private val levels=mutableListOf<MutableList<Obj>>();private val state=JournalState();private val summary=SummaryAccumulator(this.header)
    private var lastChunk:Obj?=null;private var previousCheckpoint:Obj?=null;private var sinceCheckpoint=0
    private fun makePage(depth:Int,children:List<Obj>):Obj {
        demand(depth in 0..7 && children.isNotEmpty() && children.size<=256,"resource_limit")
        val refs=children.map { it["record"].obj() };val streamIds=children.flatMap { it["stream_ids"].arr().map { v->v.text() } }.sorted().distinct()
        val page=linkedMapOf<String,Any?>("depth" to depth,"children" to children,"first" to refs.first()["first"],"last" to refs.last()["last"],
            "time_first_ns" to children.first()["time_first_ns"],"time_last_ns" to children.last()["time_last_ns"],"stream_ids" to streamIds,
            "chunks" to children.fold(0L){n,c->LmtrChunk.add(n,c["_chunks"].d())}.toString(),
            "entries" to children.fold(0L){n,c->LmtrChunk.add(n,c["_entries"].d())}.toString())
        page["children"]=children.map { it.filterKeys { k->!k.startsWith("_") } }
        LmtrMetadata.local("IndexPage",page);demand(LmtrJson.encode(page).size<=524288,"resource_limit");return page
    }
    private fun pageChild(depth:Int,children:List<Obj>):Obj {
        val page=makePage(depth,children);val r=append(LmtrFraming.INDEX,LmtrJson.encode(page))
        return linkedMapOf("record" to RecordRef(r.offset,r.length,r.ordinal,page["first"].d(),page["last"].d(),r.refHash).json(),
            "time_first_ns" to page["time_first_ns"],"time_last_ns" to page["time_last_ns"],"stream_ids" to page["stream_ids"],
            "_chunks" to page["chunks"],"_entries" to page["entries"])
    }
    private fun push(initialDepth:Int,initial:Obj) {
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
    private fun snapshotRoot():Obj? {
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

    /** Publish a fresh immutable root for acknowledged chunks, never queued observations. */
    fun checkpoint():Pair<Record,Obj> = guarded {
        val root=snapshotRoot();val value=linkedMapOf<String,Any?>("recording_id" to header["recording_id"],"root" to root,
            "last_chunk" to lastChunk,"previous_checkpoint" to previousCheckpoint,"summary" to summary.json())
        LmtrMetadata.local("Checkpoint",value);val r=append(LmtrFraming.CHECKPOINT,LmtrJson.encode(value))
        val s=summary.json();previousCheckpoint=RecordRef(r.offset,r.length,r.ordinal,s["first_entry"]?.d(),s["last_entry"]?.d(),r.refHash).json()
        sinceCheckpoint=0;onCheckpointAcknowledged(value);r to value
    }
    /** Validate all closure/payload data before acknowledging a CHUNK. */
    fun appendChunk(body: ByteArray) = guarded {
        val meta=LmtrChunk.validate(body,header,validationBudget)
        state.accept(meta)
        val r=append(LmtrFraming.CHUNK,body);summary.accept(meta)
        // Source acknowledgment precedes any potentially delayed index/checkpoint I/O.
        onChunkAcknowledged(summary.json())
        val entries=meta["entries"].arr().map { it.obj() };val first=entries.first();val last=entries.last()
        val ref=RecordRef(r.offset,r.length,r.ordinal,first["entry_id"].d(),last["entry_id"].d(),r.refHash).json();lastChunk=ref
        val segments=meta["segments"].arr().map { it.obj() }.associateBy { it["id"].text() };val ids=sortedSetOf<String>()
        for(e in entries)if(e["stream_id"]!=null)ids.add(e["stream_id"].text())else ids.addAll(segments.getValue(e["segment_id"].text())["streams"].arr().map { it.obj()["stream_id"].text() })
        push(0,linkedMapOf("record" to ref,"time_first_ns" to first["timeline_ns"],"time_last_ns" to last["timeline_ns"],"stream_ids" to ids.toList(),"_chunks" to "1","_entries" to entries.size.toString()))
        sinceCheckpoint++;if(sinceCheckpoint==32)checkpoint()
    }
    /** Terminal commit; the backend owner must still close and independently reopen. */
    fun finish(status: String, reason: String, finalizationTime: Obj, error: Obj? = null) = guarded {
        demand(state.activeSegment==null,"invalid_metadata")
        val (cpRecord,cp)=checkpoint();val s=summary.json()
        // Earlier creation UTC is not evidence of this Stop/publication.
        // Missing finalization-clock input must remain explicitly unknown.
        val end=linkedMapOf<String,Any?>("recording_id" to header["recording_id"],"root" to cp["root"],
            "checkpoint" to RecordRef(cpRecord.offset,cpRecord.length,cpRecord.ordinal,s["first_entry"]?.d(),s["last_entry"]?.d(),cpRecord.refHash).json(),
            "summary" to s,"status" to status,"reason" to reason,"finalization_time" to finalizationTime,"error" to error)
        LmtrMetadata.local("End",end);append(LmtrFraming.END,LmtrJson.encode(end))
        finished=true
    }
    companion object {
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

    }

}
