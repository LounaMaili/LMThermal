package org.lmthermal.r2

import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import java.util.ArrayDeque

/** One dedicated preparation worker retaining proven owned evidence, not a USB buffer.
 * Handoff is nonblocking and bounded by the actual retained source arrays (not output profile).
 * One pending overload range is explicit; source gaps are counted separately by the observer.
 */
class Ht301Preparation(private val writer: BoundedRecorder, private val profile: Profile,
    private val originMs: Long, private val costs: StageCosts) {
    private data class Item(val measurement: Ht301ThermalMeasurement?, val gap: OwnedObservation?, val relativeNs: Long, val queuedNs: Long = System.nanoTime()) {
        val bytes get() = if (measurement != null) SOURCE_ARRAY_BYTES else gap!!.bytes
        val sequence get() = measurement?.sequence ?: gap!!.sequence
        val end get() = measurement?.sequence ?: gap!!.frame.gapEnd
    }
    private val monitor = Object()
    private val queue = ArrayDeque<Item>()
    private var bytes = 0L
    private var pending: OwnedObservation? = null
    private var stopping = false
    @Volatile var failure: String? = null; private set
    @Volatile var drops = 0L; private set
    @Volatile var lostSourceGapSequences = 0L; private set
    @Volatile var maxBytes = 0L; private set
    @Volatile var maxDepth = 0; private set
    @Volatile var inFlightBytes = 0L; private set
    private val worker = Thread({
        try {
            costs.attached {
                while (true) {
                    val pair = synchronized(monitor) {
                        while (queue.isEmpty() && !stopping) monitor.wait()
                        if (queue.isEmpty()) null else {
                            val item = queue.removeFirst(); bytes -= item.bytes; inFlightBytes = item.bytes
                            // Pending overload follows every earlier queued source item.
                            item
                        }
                    } ?: break
                    costs.record("preparation_queue_wait", System.nanoTime() - pair.queuedNs)
                    val frame = pair.measurement?.let { StageCosts.timed("freeze_total") { OwnedObservation.ht301(it, profile, originMs) } } ?: pair.gap!!
                    writer.offer(frame); inFlightBytes = 0
                }
                synchronized(monitor) { pending }?.let(writer::offer)
            }
        } catch (error: Exception) { failure = error.javaClass.simpleName + ":" + error.message }
        finally { inFlightBytes = 0 }
    }, "r2-preparation").apply { start() }

    fun offer(measurement: Ht301ThermalMeasurement): Boolean = offer(Item(measurement, null, (measurement.receivedMonotonicMs - originMs).coerceAtLeast(0) * 1000000))
    fun offer(gap: OwnedObservation): Boolean = offer(Item(null, gap, gap.frame.relativeNs))
    private fun offer(item: Item): Boolean = synchronized(monitor) {
        if (stopping || failure != null) return false
        val extra = item.bytes + (pending?.bytes ?: 0)
        if (bytes + extra > Bounds.QUEUE || queue.size + (if (pending == null) 1 else 2) > 128) {
            if (item.measurement != null) drops++ else lostSourceGapSequences += item.end - item.sequence + 1
            pending = pending?.gap("preparation_overload", item.end)
                ?: OwnedObservation.gap(item.sequence, item.end, item.relativeNs, "preparation_overload")
            return false
        }
        pending?.let { queue.addLast(Item(null, it, it.frame.relativeNs)); bytes += it.bytes }; pending = null
        queue.addLast(item); bytes += item.bytes
        maxBytes = maxOf(maxBytes, bytes); maxDepth = maxOf(maxDepth, queue.size); monitor.notifyAll(); true
    }
    fun backlog(): Long = synchronized(monitor) { bytes }
    /** Drain preparation first; only then may the writer finalize its remaining queue. */
    fun stop(timeoutMs: Long = 15000): Boolean {
        synchronized(monitor) { stopping = true; monitor.notifyAll() }; worker.join(timeoutMs)
        return !worker.isAlive && failure == null
    }
    companion object {
        // Owned frame + IntArray indices + FloatArray temperatures. Scalars/map/object headers
        // are reported separately; this is a conservative array budget, not process-memory proof.
        const val SOURCE_ARRAY_BYTES = 224256L + 442368L + 442368L
    }
}
