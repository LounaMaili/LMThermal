package org.lmthermal.r2

/** Optional test-prototype profiling. Samples are bounded; allocation figures are explicit
 * byte-array lower bounds, not a substitute for Android process/allocation measurements.
 * Thread-local attachment separates intake and writer CPU without a global active recorder.
 */
class StageCosts(private val cpuNow: () -> Long = { 0L }) {
    private data class Row(var count: Long = 0, var wall: Long = 0, var cpu: Long = 0,
        var copied: Long = 0, var allocated: Long = 0, val samples: MutableList<Long> = mutableListOf(),
        val threads: MutableSet<String> = linkedSetOf())
    private val rows = linkedMapOf<String, Row>()
    fun record(stage: String, wall: Long, cpu: Long = 0, copied: Long = 0, allocated: Long = 0) = synchronized(rows) {
        val row = rows.getOrPut(stage) { Row() }; row.count++; row.wall += wall; row.cpu += cpu
        row.copied += copied; row.allocated += allocated
        if (row.samples.size < 20000) row.samples += wall
        row.threads += Thread.currentThread().name
    }
    fun <T> measure(stage: String, copied: Long = 0, allocated: Long = 0, work: () -> T): T {
        val wall = System.nanoTime(); val cpu = cpuNow()
        try { return work() } finally { record(stage, System.nanoTime() - wall, cpuNow() - cpu, copied, allocated) }
    }
    fun <T> attached(work: () -> T): T {
        val previous = local.get(); local.set(this)
        try { return work() } finally { local.set(previous) }
    }
    fun snapshot(): Map<String, Any?> = synchronized(rows) { rows.mapValues { (_, row) ->
        val sorted = row.samples.sorted()
        mapOf("count" to row.count, "wall_ns" to row.wall, "thread_cpu_ns" to row.cpu,
            "copied_bytes_lower_bound" to row.copied, "allocated_bytes_lower_bound" to row.allocated,
            "wall_ns_p50" to sorted.getOrNull((sorted.size * .5).toInt()),
            "wall_ns_p95" to sorted.getOrNull((sorted.size * .95).toInt()),
            "wall_ns_max" to sorted.lastOrNull(), "threads" to row.threads.toList())
    } }
    companion object {
        private val local = ThreadLocal<StageCosts?>()
        fun <T> timed(stage: String, copied: Long = 0, allocated: Long = 0, work: () -> T): T =
            local.get()?.measure(stage, copied, allocated, work) ?: work()
    }
}
