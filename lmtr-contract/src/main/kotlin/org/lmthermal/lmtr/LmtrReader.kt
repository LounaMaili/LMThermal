package org.lmthermal.lmtr

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read-only selected/full access. Scope is explicit; opening never certifies unvisited payloads. */
class LmtrReader(path: File, private val workingBudget: Long = 134217728L) : Closeable {
    private val file = RandomAccessFile(path, "r")
    val header: Obj
    private val pages = linkedMapOf<Long, Pair<Record, Obj>>()
    var recordsRead = 0L; private set
    var chunksDecoded = 0L; private set
    private val hashes = linkedMapOf<String,String>()
    private val hashReportLimit = 65536

    init {
        try {
        val record = read(0); demand(record.type == LmtrFraming.HEADER, "invalid_container")
        header = LmtrJson.parse(record.body)
        // Registry rejection precedes a local schema's restricted enum for these
        // known integer fields, so unassigned codec is not called a benign gap.
        for (codec in header["codecs"].arr()) demand(codec.i() in 0..1,"unsupported_codec")
        LmtrMetadata.header(header)
        } catch(e:Exception) { file.close(); throw e }
    }

    override fun close() { file.close() }

    private fun read(offset: Long): Record {
        recordsRead++
        try { return LmtrFraming.read(file, offset, minOf(workingBudget, LmtrFraming.MAX_RECORD_BYTES-112)) }
        catch(e:LmtrFailure) {
            if(e.code=="committed_payload_integrity") {
                val info=LmtrFraming.inspect(file,offset)
                if(info.type in listOf(LmtrFraming.INDEX,LmtrFraming.CHECKPOINT,LmtrFraming.END))throw LmtrFailure("invalid_container")
            }
            throw e
        }
    }

    private fun obj(record: Record, shape: String): Obj {
        val value = LmtrJson.parse(record.body); LmtrMetadata.local(shape,value); return value
    }

    private fun reference(value: Any?): RecordRef {
        val r=value.obj();LmtrMetadata.local("Ref",r)
        return RecordRef(r["offset"].d(),r["length"].d(),r["ordinal"].d(),r["first"]?.d(),r["last"]?.d(),r["hash"].text())
    }

    private fun resolve(ref: RecordRef, owner: Record, type: Int): Record {
        demand(ref.ordinal < owner.ordinal && LmtrChunk.add(ref.offset,ref.length) <= owner.offset,"invalid_container")
        val record=read(ref.offset)
        demand(record.type==type && record.length==ref.length && record.ordinal==ref.ordinal && record.refHash==ref.hash,"invalid_container")
        return record
    }

    private fun chunk(record: Record): ChunkData {
        chunksDecoded++
        val data=LmtrChunk.decode(record.body,header,workingBudget)
        for ((key,hash) in data.logicalHashes) {
            demand(hashes.size < hashReportLimit || key in hashes,"local_resource_limit");hashes[key]=hash
        }
        return data
    }

    private fun terminal(): Pair<Record,Obj> {
        demand(file.length()>=112,"invalid_container")
        val footer=file.length()-72;file.seek(footer+8);val bytes=ByteArray(8);file.readFully(bytes)
        val length=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
        demand(length in 112..LmtrFraming.MAX_RECORD_BYTES && length<=file.length(),"invalid_container")
        val end=read(file.length()-length);demand(end.type==LmtrFraming.END && end.offset+end.length==file.length(),"invalid_container")
        val value=obj(end,"End")
        demand(value["recording_id"]==header["recording_id"],"invalid_metadata")
        val status=value["status"].text()
        demand(if(status=="failed")value["error"]!=null else value["error"]==null,"invalid_metadata")
        val cpRef=reference(value["checkpoint"]);val cpRecord=resolve(cpRef,end,LmtrFraming.CHECKPOINT);val cp=obj(cpRecord,"Checkpoint")
        checkpoint(cpRecord,cp)
        demand(LmtrJson.encode(cp["summary"]).contentEquals(LmtrJson.encode(value["summary"])) &&
            LmtrJson.encode(cp["root"]).contentEquals(LmtrJson.encode(value["root"])),"invalid_metadata")
        val summary=value["summary"].obj()
        demand(cpRef.first==summary["first_entry"]?.d() && cpRef.last==summary["last_entry"]?.d(),"invalid_container")
        return end to value
    }

    private fun checkpoint(record: Record, cp: Obj) {
        demand(cp["recording_id"]==header["recording_id"],"invalid_metadata")
        val summary=cp["summary"].obj();val count=summary["chunks"].d()
        val empty=count==0L
        demand((cp["root"]==null)==empty && (cp["last_chunk"]==null)==empty,"invalid_metadata")
        if (empty) {
            demand(summary["entries"].d()==0L && summary["first_entry"]==null && summary["last_entry"]==null && summary["first_timeline_ns"]==null && summary["last_timeline_ns"]==null,"invalid_metadata")
        } else {
            demand(summary["first_entry"].d()==0L && summary["last_entry"].d()==summary["entries"].d()-1,"invalid_metadata")
            val root=reference(cp["root"]);val (r,p)=page(root,record)
            demand(root.first==summary["first_entry"].d() && root.last==summary["last_entry"].d() && p["chunks"].d()==count && p["entries"].d()==summary["entries"].d(),"invalid_metadata")
            val last=reference(cp["last_chunk"])
            demand(last.last==summary["last_entry"].d(),"invalid_container")
            // Verify framing/reference identity without eagerly decoding the last
            // chunk; a lazy open certifies navigation, not its numerical payloads.
            val inspected=LmtrFraming.inspect(file,last.offset)
            demand(inspected.type==LmtrFraming.CHUNK && inspected.ordinal==last.ordinal && last.ordinal<record.ordinal && inspected.length==last.length && inspected.refHash==last.hash && LmtrChunk.add(last.offset,last.length)<=record.offset,"invalid_container")
            demand(r.offset<record.offset,"invalid_container")
        }
        cp["previous_checkpoint"]?.let { prior ->
            val ref=reference(prior); val inspected=LmtrFraming.inspect(file,ref.offset)
            demand(inspected.type==LmtrFraming.CHECKPOINT && inspected.refHash==ref.hash && inspected.ordinal==ref.ordinal && inspected.length==ref.length && ref.ordinal<record.ordinal && LmtrChunk.add(ref.offset,ref.length)<=record.offset,"invalid_container")
        }
    }

    private fun page(ref: RecordRef, owner: Record): Pair<Record,Obj> {
        val cached=pages[ref.offset]
        val pair=cached ?: resolve(ref,owner,LmtrFraming.INDEX).let { it to obj(it,"IndexPage") }
        val (record,page)=pair
        demand(record.refHash==ref.hash && record.ordinal==ref.ordinal && record.ordinal<owner.ordinal && record.length==ref.length && LmtrChunk.add(record.offset,record.length)<=owner.offset,"invalid_container")
        demand(ref.first==page["first"].d() && ref.last==page["last"].d(),"invalid_container")
        val children=page["children"].arr().map { it.obj() };val depth=page["depth"].i();demand(depth in 0..7,"resource_limit")
        var previousLast:Long?=null;var previousTime=-1L;var entries=0L
        val streams=sortedSetOf<String>()
        for (child in children) {
            val childRef=reference(child["record"])
            val first=childRef.first ?: throw LmtrFailure("invalid_container");val last=childRef.last ?: throw LmtrFailure("invalid_container")
            demand(first<=last && (previousLast==null || first==LmtrChunk.add(previousLast,1)),"invalid_container")
            val t1=child["time_first_ns"].d();val t2=child["time_last_ns"].d();demand(t1<=t2 && t1>=previousTime,"invalid_metadata")
            val ids=child["stream_ids"].arr().map { it.text() };demand(ids==ids.sorted().distinct() && ids.all { id -> header["streams"].arr().any { it.obj()["id"]==id } },"invalid_metadata")
            streams.addAll(ids);previousLast=last;previousTime=t2;entries=LmtrChunk.add(entries,last-first+1)
            demand(childRef.ordinal<record.ordinal && LmtrChunk.add(childRef.offset,childRef.length)<=record.offset,"invalid_container")
        }
        demand(page["first"].d()==reference(children.first()["record"]).first && page["last"].d()==previousLast && page["entries"].d()==entries,"invalid_metadata")
        demand(page["time_first_ns"].d()==children.first()["time_first_ns"].d() && page["time_last_ns"].d()==previousTime && page["stream_ids"].arr().map { it.text() }==streams.toList(),"invalid_metadata")
        // At most two pages/level. The schema's 512 KiB body ceiling keeps this
        // cache under 8 MiB; parsed metadata overhead is separately disclosed.
        if(cached==null) {
            while(pages.values.count { it.second["depth"].i()==depth }>=2) pages.remove(pages.entries.first { it.value.second["depth"].i()==depth }.key)
            pages[ref.offset]=pair
        }
        return pair
    }

    private fun childMatches(child: Obj, meta: Obj) {
        val entries=meta["entries"].arr().map { it.obj() };val ref=reference(child["record"])
        demand(ref.first==entries.first()["entry_id"].d() && ref.last==entries.last()["entry_id"].d() && child["time_first_ns"].d()==entries.first()["timeline_ns"].d() && child["time_last_ns"].d()==entries.last()["timeline_ns"].d(),"invalid_container")
        val segments=meta["segments"].arr().map { it.obj() }.associateBy { it["id"].text() }
        val ids=sortedSetOf<String>()
        for(e in entries) if(e["stream_id"]!=null)ids.add(e["stream_id"].text()) else ids.addAll(segments.getValue(e["segment_id"].text())["streams"].arr().map { it.obj()["stream_id"].text() })
        demand(child["stream_ids"].arr().map { it.text() }==ids.toList(),"invalid_metadata")
    }

    /**
     * Full verification checks every physical immutable page, including historical snapshots
     * which no longer belong to the final root. Each direct child is bounded by fanout; checking
     * metadata here avoids recursively decoding the same source planes at every checkpoint.
     */
    private fun verifyPhysicalPage(record: Record) {
        val page=obj(record,"IndexPage");val depth=page["depth"].i()
        var chunks=0L;var entries=0L;var previousLast:Long?=null;var previousTime:Long?=null
        val streams=sortedSetOf<String>();val children=page["children"].arr().map { it.obj() }
        for(child in children) {
            val ref=reference(child["record"])
            val first=ref.first ?: throw LmtrFailure("invalid_container")
            val last=ref.last ?: throw LmtrFailure("invalid_container")
            demand(first<=last && (previousLast==null || first==LmtrChunk.add(previousLast,1)),"invalid_container")
            val start=child["time_first_ns"].d();val end=child["time_last_ns"].d()
            demand(start<=end && (previousTime==null || start>=previousTime),"invalid_metadata")
            val actual=resolve(ref,record,if(depth==0)LmtrFraming.CHUNK else LmtrFraming.INDEX)
            if(depth==0) {
                val (meta,_)=LmtrChunk.metadata(actual.body);LmtrMetadata.chunk(meta,header);childMatches(child,meta)
                chunks=LmtrChunk.add(chunks,1);entries=LmtrChunk.add(entries,meta["entries"].arr().size.toLong())
            } else {
                val lower=obj(actual,"IndexPage")
                demand(lower["depth"].i()==depth-1 && ref.first==lower["first"].d() && ref.last==lower["last"].d(),"invalid_container")
                demand(start==lower["time_first_ns"].d() && end==lower["time_last_ns"].d() && child["stream_ids"]==lower["stream_ids"],"invalid_metadata")
                chunks=LmtrChunk.add(chunks,lower["chunks"].d());entries=LmtrChunk.add(entries,lower["entries"].d())
            }
            streams.addAll(child["stream_ids"].arr().map { it.text() });previousLast=last;previousTime=end
        }
        demand(page["first"].d()==reference(children.first()["record"]).first && page["last"].d()==previousLast &&
            page["time_first_ns"].d()==children.first()["time_first_ns"].d() && page["time_last_ns"].d()==previousTime &&
            page["stream_ids"].arr().map { it.text() }==streams.toList() && page["chunks"].d()==chunks && page["entries"].d()==entries,"invalid_metadata")
    }

    private fun select(ref: RecordRef, owner: Record, entry: Long): ChunkData {
        val (record,p)=page(ref,owner);demand(entry in p["first"].d()..p["last"].d(),"invalid_metadata")
        val child=p["children"].arr().map { it.obj() }.first { c -> val r=reference(c["record"]);entry in r.first!!..r.last!! }
        val r=reference(child["record"])
        if(p["depth"].i()>0) {
            val (_,lower)=page(r,record);demand(lower["depth"].i()==p["depth"].i()-1 && lower["time_first_ns"].d()==child["time_first_ns"].d() && lower["time_last_ns"].d()==child["time_last_ns"].d() && lower["stream_ids"]==child["stream_ids"],"invalid_container")
            return select(r,record,entry)
        }
        val data=chunk(resolve(r,record,LmtrFraming.CHUNK));childMatches(child,data.metadata);return data
    }

    private fun verifyTree(ref: RecordRef, owner: Record, state: JournalState? = null, summary: SummaryAccumulator? = null): Pair<Long,Long> {
        val (record,p)=page(ref,owner);var chunks=0L;var entries=0L
        for(child in p["children"].arr().map { it.obj() }) {
            val r=reference(child["record"])
            val counts=if(p["depth"].i()==0) {
                val chunkRecord=resolve(r,record,LmtrFraming.CHUNK);val (meta,_)=LmtrChunk.metadata(chunkRecord.body)
                LmtrMetadata.chunk(meta,header);childMatches(child,meta)
                if(state!=null && summary!=null) { val data=chunk(chunkRecord);state.accept(data.metadata);summary.accept(data.metadata) }
                1L to meta["entries"].arr().size.toLong()
            } else {
                val (_,lower)=page(r,record);demand(lower["depth"].i()==p["depth"].i()-1 && lower["time_first_ns"]==child["time_first_ns"] && lower["time_last_ns"]==child["time_last_ns"] && lower["stream_ids"]==child["stream_ids"],"invalid_container")
                verifyTree(r,record,state,summary)
            }
            chunks=LmtrChunk.add(chunks,counts.first);entries=LmtrChunk.add(entries,counts.second)
        }
        demand(chunks==p["chunks"].d() && entries==p["entries"].d(),"invalid_metadata")
        return chunks to entries
    }

    /** Normal open, selected access or complete physical/index/payload verification. */
    fun check(full: Boolean = false, entry: Long? = null, recover: Boolean = false): Obj {
        val terminal=try { terminal() } catch(e: LmtrFailure) { if(recover)return recovery(full) else throw e }
        val (end,value)=terminal;val summary=value["summary"].obj()
        if(entry!=null) { demand(entry>=0 && summary["entries"].d()>entry,"invalid_metadata");select(reference(value["root"]),end,entry) }
        if(full) {
            val state=JournalState();val actual=SummaryAccumulator(header);var offset=0L;var ordinal=0L;var previous=LmtrFraming.SENTINEL
            var sinceCheckpoint=0;var sawEnd=false
            while(offset<file.length()) {
                val record=read(offset);demand(record.ordinal==ordinal && record.previous==previous,"invalid_container")
                when(record.type) {
                    LmtrFraming.HEADER -> demand(offset==0L,"invalid_container")
                    LmtrFraming.CHUNK -> { demand(!sawEnd && sinceCheckpoint<32,"invalid_container");val data=chunk(record);state.accept(data.metadata);actual.accept(data.metadata);sinceCheckpoint++ }
                    LmtrFraming.INDEX -> verifyPhysicalPage(record)
                    LmtrFraming.CHECKPOINT -> { val cp=obj(record,"Checkpoint");checkpoint(record,cp);demand(LmtrJson.encode(cp["summary"]).contentEquals(LmtrJson.encode(actual.json())),"invalid_metadata");sinceCheckpoint=0 }
                    LmtrFraming.END -> { demand(state.activeSegment==null && offset==end.offset && LmtrChunk.add(offset,record.length)==file.length(),"invalid_container");sawEnd=true }
                }
                previous=offset;offset=LmtrChunk.add(offset,record.length);ordinal=LmtrChunk.add(ordinal,1)
            }
            demand(sawEnd && LmtrJson.encode(actual.json()).contentEquals(LmtrJson.encode(summary)),"invalid_metadata")
            value["root"]?.let { verifyTree(reference(it),end) }
        }
        return report("valid",if(full)"full" else if(entry!=null)"selected_chunks" else "opened_records",summary,value["status"].text(),false)
    }

    /** Locate a CRC-verified footer in the bounded tail without allocating the whole tail. */
    private fun candidate(): RecordEnvelope {
        val minimum=maxOf(0L,file.length()-68157512L);var end=file.length();var attempts=0
        val marker="LMTRCMT1".toByteArray(Charsets.US_ASCII)
        while(end>minimum) {
            val start=maxOf(minimum,end-65536);val length=(end-start).toInt();val data=ByteArray(length);file.seek(start);file.readFully(data)
            for(i in length-8 downTo 0) if(marker.indices.all { k -> data[i+k] == marker[k] }) {
                attempts++;demand(attempts<=1024,"recovery_limit")
                val footer=start+i; if(file.length()-footer<72)continue
                try {
                    file.seek(footer+8);val raw=ByteArray(8);file.readFully(raw);val total=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).long
                    if(total !in 112..LmtrFraming.MAX_RECORD_BYTES || total>footer+72)continue
                    val info=LmtrFraming.inspect(file,footer+72-total)
                    if(info.offset+info.length==footer+72)return info
                } catch(_: LmtrFailure) { /* False marker is not a trusted reference. */ }
            }
            if(start==minimum)break
            end=start+7 // Footer marker spanning a buffer boundary remains visible.
        }
        throw LmtrFailure("recovery_limit")
    }

    /** Bounded suffix recovery: committed CHUNK corruption never becomes a normal gap. */
    private fun recovery(full: Boolean): Obj {
        var prefixState = JournalState(); var prefixActual = SummaryAccumulator(header)
        var current=candidate();val chunks=mutableListOf<Record>();var records=0;var boundary:Long?=null
        var baseSummary:Obj?=null;var baseLast:Long?=null;var baseTime:Long?=null
        while(true) {
            records++;demand(records<=1024,"recovery_limit")
            val record=try { read(current.offset) } catch(e: LmtrFailure) {
                if(current.type==LmtrFraming.CHUNK || current.type==LmtrFraming.HEADER)throw e
                null // Only framing-verified navigation is rebuildable.
            }
            if(record!=null && record.type==LmtrFraming.CHUNK) { demand(chunks.size<64,"recovery_limit");chunk(record);chunks.add(record) }
            if(record!=null && record.type==LmtrFraming.CHECKPOINT) {
                try { val cp=obj(record,"Checkpoint");checkpoint(record,cp)
                    val candidateState=JournalState();val candidateActual=SummaryAccumulator(header)
                    cp["root"]?.let { verifyTree(reference(it),record,if(full)candidateState else null,if(full)candidateActual else null) }
                    if(full)demand(LmtrJson.encode(cp["summary"]).contentEquals(LmtrJson.encode(candidateActual.json())),"invalid_metadata")
                    prefixState=candidateState;prefixActual=candidateActual
                    baseSummary=cp["summary"].obj();baseLast=baseSummary["last_entry"]?.d();baseTime=baseSummary["last_timeline_ns"]?.d();boundary=LmtrChunk.add(record.offset,record.length);break
                } catch(e:LmtrFailure) { if(e.code=="unsupported_required_feature" || e.code=="unsupported_codec" || e.code=="local_resource_limit")throw e }
            }
            if(current.offset==0L) { boundary=read(0).length;break }
            current=LmtrFraming.inspect(file,current.previous)
        }
        var last=baseLast;var time=baseTime;var suffixEntries=0L
        for(record in chunks.asReversed()) {
            val data=chunk(record);val entries=data.metadata["entries"].arr().map { it.obj() }
            demand(entries.first()["entry_id"].d()==(last?.let { LmtrChunk.add(it,1) } ?: 0L),"invalid_metadata")
            demand(time==null || entries.first()["timeline_ns"].d()>=time,"invalid_metadata")
            if(full){prefixState.accept(data.metadata);prefixActual.accept(data.metadata)}
            last=entries.last()["entry_id"].d();time=entries.last()["timeline_ns"].d();suffixEntries=LmtrChunk.add(suffixEntries,entries.size.toLong())
            boundary=maxOf(boundary!!,LmtrChunk.add(record.offset,record.length))
        }
        return linkedMapOf("status" to "recovered", "reason" to "missing_or_torn_end", "integrity_scope" to if(full)"full_verified_prefix" else "selected_chunks",
            "unknown_tail" to true, "declared_end_status" to null,"verified_prefix_bytes" to boundary!!.toString(),
            "last_verified_entry" to last?.toString(),"last_verified_timeline_ns" to time?.toString(),
            "declared_checkpoint_summary" to baseSummary,"summary" to if(full)prefixActual.json() else null,"verified_suffix_entries" to suffixEntries.toString(),
            "logical_hashes" to hashes.toMap(),"records_read" to recordsRead.toString(),"chunks_decoded" to chunksDecoded.toString())
    }

    private fun report(status:String,scope:String,summary:Obj,endStatus:String,unknownTail:Boolean):Obj = linkedMapOf(
        "status" to status,"integrity_scope" to scope,"declared_end_status" to endStatus,"unknown_tail" to unknownTail,
        "summary" to summary,"logical_hashes" to hashes.toMap(),"records_read" to recordsRead.toString(),"chunks_decoded" to chunksDecoded.toString(),
        "accuracy_warning" to "Native-equivalent temperatures; absolute physical accuracy not yet independently validated.")
}
