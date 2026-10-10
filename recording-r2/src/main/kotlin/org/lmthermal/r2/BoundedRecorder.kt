package org.lmthermal.r2

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

/** Nonblocking bounded handoff. One compressed pending gap range cannot grow with run duration.
 * Writer work, compression and sync run on their own thread; queue acceptance is never a saved count.
 */
class BoundedRecorder(file: File, profile: Profile, codec: BlockCodec,
    private val costs: StageCosts? = null, preferredChunkBytes: Int = Bounds.TARGET,
    sinkFactory: (File) -> AppendSink = ::FileSink) : Closeable {
    private data class Item(val gap: ProducerEntry?, val frame: ProducerEntry, val queuedNs: Long = System.nanoTime()) {
        val bytes get() = Bounds.add(frame.frame.bytes,gap?.frame?.bytes ?: 0L)
    }
    private val monitor = Object()
    private val queue = ArrayDeque<Item>()
    private var queueBytes = 0L
    private var gap: ProducerEntry? = null
    private var stopping = false
    private var reason = "user_stop"
    @Volatile var failure: String? = null; private set
    @Volatile var inFlightBytes = 0L; private set
    @Volatile var maxBytes = 0L; private set
    @Volatile var maxDepth = 0; private set
    @Volatile var drops = 0L; private set
    @Volatile var intake = 0L; private set
    @Volatile var drained = 0L; private set
    @Volatile var codecCpuNs = 0L; private set
    val recorder: PrototypeRecorder
    private val worker: Thread
    init {
        require(preferredChunkBytes in Bounds.MIB..Bounds.TARGET)
        require(!file.exists()) { "refuse_overwrite" }; file.parentFile?.mkdirs()
        recorder = PrototypeRecorder(RecordWriter(sinkFactory(file)), profile, codec, preferredChunkBytes)
        worker = Thread({
            try {
                while (true) {
                    val item = synchronized(monitor) {
                        while (queue.isEmpty() && !stopping) monitor.wait()
                        if (queue.isEmpty()) null else queue.removeFirst().also { queueBytes -= it.bytes; inFlightBytes = it.bytes }
                    } ?: break
                    costs?.record("writer_queue_wait", System.nanoTime() - item.queuedNs)
                    fun service() = StageCosts.timed("writer_service") { item.gap?.let(recorder::acceptEntry); recorder.acceptEntry(item.frame); drained++ }
                    if (costs == null) service() else costs.attached { service() }; inFlightBytes = 0
                }
                synchronized(monitor) { gap }?.let(recorder::acceptEntry)
                if (costs == null) recorder.finish(reason) else costs.attached { recorder.finish(reason) }
            } catch (error: Exception) { failure = error.javaClass.simpleName + ":" + (error.message ?: "write_failure") }
            finally { inFlightBytes = 0; try { recorder.close() } catch (error: IOException) { failure = "close_failed:" + error.message } }
        }, "r2-bounded-writer").apply { start() }
    }
    fun offer(frame: Observation): Boolean = offerEntry(ProducerEntry(frame))
    fun offer(frame: OwnedObservation): Boolean = offerEntry(ProducerEntry(frame.frame, frame))
    private fun offerEntry(frame: ProducerEntry): Boolean = synchronized(monitor) {
        if (stopping || failure != null) return false
        intake++
        val item=Item(gap,frame)
        if (Bounds.add(queueBytes,item.bytes) > Bounds.QUEUE || queue.size >= 128) {
            drops++
            gap = (gap ?: frame).gap("writer_overload", frame.frame.gapEnd)
            return false
        }
        queue.addLast(item); gap = null; queueBytes += item.bytes
        maxBytes = maxOf(maxBytes, queueBytes); maxDepth = maxOf(maxDepth, queue.size); monitor.notifyAll(); true
    }
    fun backlog(): Long = synchronized(monitor) { queueBytes }
    fun stop(interruption: String = "user_stop", timeoutMs: Long = 15000): Boolean {
        synchronized(monitor) { stopping = true; reason = interruption; monitor.notifyAll() }
        worker.join(timeoutMs)
        return !worker.isAlive && failure == null && recorder.finalized
    }
    override fun close() { stop() }
}

/** Capacity is an observation, not a promise. Unknown capacity alone never blocks a supported sink. */
object StoragePolicy {
    data class State(val allowStart: Boolean, val label: String, val warning: String?)
    fun start(available: Long?, startup: Long, reserve: Long): State {
        require(startup >= 0 && reserve >= 0)
        if (available == null) return State(true, "unknown", "Capacity unknown; storage may refuse writes")
        require(available >= 0)
        return if (available < Bounds.add(startup, reserve)) State(false, "insufficient", "Startup capacity plus reserve unavailable")
        else State(true, "available", null)
    }
    fun running(available: Long?, reserve: Long): State = if (available == null) State(true, "unknown", null)
        else if (available < reserve) State(true, "low", "Low storage; prepare to stop") else State(true, "available", null)
}
