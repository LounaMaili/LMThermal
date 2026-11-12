package org.lmthermal.lmtr

import java.io.File
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory

/** Fixed host probe, never a throughput assertion or ART/physical admission claim.
 * Input bytes are read-only and each iteration repeats one COMPLETE validation;
 * warmups, loop counts and operation boundaries are identical across candidates.
 */
object LmtrValidationBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        RandomAccessFile(File(args[0]), "r").use { source ->
            val headerRecord = LmtrFraming.read(source, 0)
            val header = LmtrJson.parse(headerRecord.body)
            var offset = headerRecord.length
            var selected: Record? = null
            while (offset < source.length()) {
                val record = LmtrFraming.read(source, offset)
                if (record.type == LmtrFraming.CHUNK) { selected = record; break }
                offset += record.length
            }
            val body = requireNotNull(selected).body
            repeat(20) { LmtrChunk.validate(body, header) }
            val mx = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
            val thread = Thread.currentThread().id
            val allocationsSupported = mx?.isThreadAllocatedMemorySupported == true
            if (allocationsSupported) mx!!.isThreadAllocatedMemoryEnabled = true
            val before = if (allocationsSupported) mx!!.getThreadAllocatedBytes(thread) else 0L
            val samples = LongArray(100)
            for (i in samples.indices) {
                val start = System.nanoTime(); LmtrChunk.validate(body, header)
                samples[i] = System.nanoTime()-start
            }
            val allocated = if (allocationsSupported) mx!!.getThreadAllocatedBytes(thread)-before else null
            val ordered = samples.sorted()
            println(LmtrJson.encode(mapOf("input_body_sha256" to LmtrChunk.sha(body),
                "body_bytes" to body.size,"warmup" to 20,"iterations" to samples.size,
                "sum_ns" to samples.sum().toString(),"p50_ns" to ordered[49].toString(),
                "p95_ns" to ordered[94].toString(),"max_ns" to ordered.last().toString(),
                "thread_allocated_bytes" to allocated?.toString(),
                "scope" to "HotSpot complete synchronous CHUNK validation; not recorder throughput"
            )).toString(Charsets.UTF_8))
        }
    }
}
