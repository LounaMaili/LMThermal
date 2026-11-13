package org.lmthermal.recorder

import org.lmthermal.lmtr.*
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Finite host attribution from an existing private recording, never a camera or ART admission.
 * Reconstruct one complete original CHUNK, including fresh statistics, then run both
 * full boundaries and actual footer-sync append. Only host test sources reference JDK
 * counters. All output files are new, private, intentionally unfinished prefix files.
 */
object HostWriterPhaseProbe {
    private data class Input(val header:Obj,val body:ByteArray,val entries:List<PendingEntry>)
    private data class Measurement(val wall:Long,val cpu:Long,val allocated:Long) {
        fun json():Obj=mapOf("wall_ns" to wall.toString(),"cpu_ns" to cpu.toString(),"allocated_bytes" to allocated.toString())
    }
    private fun sha(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val scratch=ByteArray(65536);while(true){val n=stream.read(scratch);if(n<0)break;digest.update(scratch,0,n)} }
        return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    private fun input(file:File):Input=RandomAccessFile(file,"r").use { source ->
        val header=LmtrJson.parse(LmtrFraming.read(source,0).body)
        var at=0L;var body:ByteArray?=null;var largest=-1L
        while(at<source.length()) {
            val record=LmtrFraming.read(source,at)
            if(record.type==LmtrFraming.CHUNK) {
                val bytes=LmtrChunk.metadata(record.body).first["blocks"].arr().sumOf { it.obj()["decoded"].d() }
                if(bytes>largest){body=record.body;largest=bytes}
            }
            at+=record.length
        }
        val original=checkNotNull(body);val decoded=LmtrChunk.decode(original,header)
        val segments=decoded.metadata["segments"].arr().map {it.obj()}.associateBy {it["id"].text()}
        val contexts=decoded.metadata["contexts"].arr().map {it.obj()}.associateBy {it["id"].text()}
        val placement=setOf("kind","block_id","offset","length","hash","parent_payload_id")
        val entries=decoded.metadata["entries"].arr().map { value ->
            val entry=value.obj()
            val payloads=entry["payloads"]?.arr()?.map { item ->
                val p=item.obj()
                RecordingPayload(p.filterKeys {it !in placement},decoded.payloads.getValue(entry["entry_id"].text()+":"+p["id"].text()),
                    if(p["kind"]=="view")p["offset"].d() else null)
            } ?: emptyList()
            // Recompute the normal encoder's statistics, instead of giving it a prebuilt cache.
            val facts=if(entry["kind"]=="observation")entry.filterKeys {it!="payloads"}+
                ("measurement" to entry["measurement"].obj().filterKeys {it!="statistics"}) else entry
            PendingEntry(facts,segments.getValue(entry["segment_id"].text()),entry["context_id"]?.let {contexts.getValue(it.text())},payloads)
        }
        Input(header,original,entries)
    }

    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val source=File(args[0]);val root=File(args[1]);require(source.isFile && !root.exists());check(root.mkdirs())
        val before=sha(source);val data=input(source)
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported && bean.isThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled=true;bean.isThreadCpuTimeEnabled=true
        val thread=Thread.currentThread().id
        fun allocated()=bean.getThreadAllocatedBytes(thread)
        fun cpu()=bean.currentThreadCpuTime
        fun <T> measure(action:()->T):Pair<T,Measurement> {
            val a=allocated();val c=cpu();val t=System.nanoTime();val value=action()
            val end=System.nanoTime();val ec=cpu();val ea=allocated()
            return value to Measurement(end-t,ec-c,ea-a)
        }
        fun memory():Obj {
            val runtime=Runtime.getRuntime()
            val rss=File("/proc/self/status").takeIf {it.isFile}?.useLines { lines ->
                lines.firstOrNull {it.startsWith("VmRSS:")}?.split(Regex("\\s+"))?.get(1)?.toLong()?.times(1024)
            }
            return mapOf("java_occupied_bytes" to (runtime.totalMemory()-runtime.freeMemory()).toString(),
                "rss_bytes" to rss?.toString(),"scope" to "host point sample; setup/body/owners retained; not a live-heap or Pixel bound")
        }
        val overhead=(0 until 32).map {measure {Unit}.second.json()}
        val rows=mutableListOf<Obj>()
        for(codec in 0..1)for(sample in -4 until 8) {
            val header=data.header+("codecs" to listOf(codec))
            var codecNs=0L;var codecCalls=0
            val occupiedBefore=memory()
            val (body,encode)=measure {RecordingChunk.encode(header,data.entries,codec,134217728,
                codecMetric={ns,_,_->codecNs+=ns;codecCalls++})}
            val (_,first)=measure {LmtrChunk.validate(body,header)}
            val (_,second)=measure {LmtrChunk.validate(body,header)}
            val folder=File(root,"codec-$codec-sample-${sample+4}");check(folder.mkdir())
            val file=File(folder,"prefix.lmtr");val backend=LocalRecorderBackend(file)
            var chunkStart=0L;var chunkEnd=0L;var allocatedAtSink=0L;var allocatedAfterSink=0L
            var ackAt=0L;var allocatedAtAck=0L;var sinkCpu=0L
            val session=LmtrAppendSession(header,LmtrRecordSink { type,ordinal,previous,bytes ->
                if(type==LmtrFraming.CHUNK){allocatedAtSink=allocated();chunkStart=System.nanoTime()}
                val (record,measurement)=measure {backend.append(type,ordinal,previous,bytes)}
                if(type==LmtrFraming.CHUNK){chunkEnd=System.nanoTime();allocatedAfterSink=allocated();sinkCpu=measurement.cpu}
                record
            },onChunkAcknowledged={
                check(chunkEnd>chunkStart);ackAt=System.nanoTime();allocatedAtAck=allocated()
            })
            val startAllocation=allocated();val started=System.nanoTime()
            val (_,append)=measure {session.appendChunk(body)}
            val returned=System.nanoTime();val returnedAllocation=allocated()
            backend.close()
            check(ackAt>=chunkEnd && returned>=ackAt)
            val meta=LmtrChunk.metadata(body).first
            val exact=codec==1 && body.contentEquals(data.body)
            if(codec==1)check(exact) {"Re-encoding original DEFLATE CHUNK changed bytes"}
            val row=mapOf("codec" to codec,"sample" to sample,"warmup" to (sample<0),
                "encode_including_first_validation" to encode.json(),"separate_full_validation_1" to first.json(),
                "separate_full_validation_2" to second.json(),"append_session_including_validation_backend_ack_index" to append.json(),
                "pre_backend_including_second_boundary" to mapOf("wall_ns" to (chunkStart-started).toString(),"allocated_bytes" to (allocatedAtSink-startAllocation).toString()),
                "backend_framing_and_footer_sync" to mapOf("wall_ns" to (chunkEnd-chunkStart).toString(),"cpu_ns" to sinkCpu.toString(),"allocated_bytes" to (allocatedAfterSink-allocatedAtSink).toString()),
                "after_sync_to_ack" to mapOf("wall_ns" to (ackAt-chunkEnd).toString(),"allocated_bytes" to (allocatedAtAck-allocatedAfterSink).toString()),
                "after_ack_to_return" to mapOf("wall_ns" to (returned-ackAt).toString(),"allocated_bytes" to (returnedAllocation-allocatedAtAck).toString()),
                "codec_call_inclusive_ns" to codecNs.toString(),"codec_calls" to codecCalls,
                "entries" to meta["entries"].arr().size,"body_bytes" to body.size,
                "decoded_block_bytes" to meta["blocks"].arr().sumOf {it.obj()["decoded"].d()}.toString(),
                "deflate_same_original_body" to if(codec==1)exact else null,
                "file" to root.toPath().relativize(file.toPath()).toString(),"file_sha256" to sha(file),
                "intentionally_missing_END" to true,"occupied_before" to occupiedBefore,"occupied_after" to memory())
            rows.add(row)
            // Preserve each completed sample immediately; a later failure cannot erase it.
            File(root,"phases.json").writeBytes(LmtrJson.encode(mapOf("source_sha256" to before,"rows" to rows,
                "source_unchanged" to (sha(source)==before),"clock_counter_empty_overhead" to overhead,
                "scope" to "host standalone boundaries; encoder/codec inclusive; append subintervals disjoint; not physical attribution",
                "java" to System.getProperty("java.runtime.version"),"forced_gc" to false)))
        }
        check(sha(source)==before)
    }
}
