package org.lmthermal.lmtr

import java.io.File
import java.security.MessageDigest

/** Test-only controlled polls: no camera, CLI production change or universal latency claim. */
object LmtrCancellationProbe {
    /** Hash the bounded test source in pages, outside the measured cancellation interval. */
    private fun hash(source:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        source.inputStream().use { stream ->
            val buffer=ByteArray(65536)
            while(true) { val length=stream.read(buffer);if(length<0)break;digest.update(buffer,0,length) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Count live descriptors referencing this source on Linux; unavailable elsewhere. */
    private fun descriptors(source:File):Int? {
        val directory=File("/proc/self/fd")
        if(!directory.isDirectory)return null
        return directory.listFiles()?.count {
            try { it.canonicalPath==source.canonicalPath } catch(_:Exception) { false }
        }
    }

    @JvmStatic fun main(args:Array<String>) {
        require(args.size==3)
        val source=File(args[0]);val mode=args[1];val stopPoll=args[2].toInt()
        val before=hash(source);val descriptorsBefore=descriptors(source)
        var requested=0L;var recordsAtRequest=0L;var polls=0;var additional=0L;var sampledHeap=0L
        var result:Obj=emptyMap()
        LmtrReader(source).use { reader ->
            fun cancelled():Boolean {
                val runtime=Runtime.getRuntime();sampledHeap=maxOf(sampledHeap,runtime.totalMemory()-runtime.freeMemory())
                if(++polls!=stopPoll)return false
                requested=System.nanoTime();recordsAtRequest=reader.recordsRead
                return true
            }
            result=if(mode=="salvage")reader.salvage(::cancelled) else {
                try { reader.check(full=true,recover=mode=="recovery",cancelled=::cancelled);error("cancel not observed") }
                catch(failure:LmtrFailure) { require(failure.code=="cancelled");mapOf("status" to "cancelled","integrity_scope" to "none") }
            }
            additional=reader.recordsRead-recordsAtRequest
        }
        val latency=System.nanoTime()-requested
        require(requested>0 && additional==0L)
        val after=hash(source);require(before==after)
        val descriptorsAfter=descriptors(source);require(descriptorsBefore==descriptorsAfter)
        println(LmtrJson.encode(mapOf("runtime" to System.getProperty("java.runtime.version"),"mode" to mode,
            "request_point" to "before record access at poll $stopPoll","polls" to polls,
            "additional_record_reads" to additional.toString(),"observed_latency_ns" to latency.toString(),
            "sampled_peak_heap_bytes" to sampledHeap.toString(),"descriptors_before" to descriptorsBefore,
            "descriptors_after" to descriptorsAfter,"source_sha256" to before,"immutable" to true,"result" to result,
            "limitations" to "cooperative record boundaries; synchronous hash/decompression not interrupted; no hard real-time deadline")).toString(Charsets.UTF_8))
    }
}
