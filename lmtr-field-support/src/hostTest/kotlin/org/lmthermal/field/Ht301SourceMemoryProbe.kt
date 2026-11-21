package org.lmthermal.field

import com.sun.management.ThreadMXBean
import org.lmthermal.camera.CameraSourceIdentity
import org.lmthermal.camera.ht301.Ht301AcquiredObservation
import org.lmthermal.camera.ht301.Ht301ModuleProfile
import org.lmthermal.core.Ht301Frame
import org.lmthermal.core.MeasurementGate
import org.lmthermal.core.SessionState
import org.lmthermal.lmtr.LmtrReader
import org.lmthermal.recorder.Admission
import org.lmthermal.recorder.FiveHertzSampling
import org.lmthermal.recorder.LocalRecorderBackend
import org.lmthermal.recorder.RecorderEngine
import org.lmthermal.recorder.RecordingProfile
import org.lmthermal.recorder.RecordingStopDeadline
import org.lmthermal.recording.Ht301RecordingAdapter
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Compare the same bytecode against both core jars using only the existing sanitized fixtures. */
object Ht301SourceMemoryProbe {
    private val fixtures = listOf("/fixtures/radiometric-room-settled.raw", "/thermometry/warm-hand-settled.raw")
        .map { javaClass.getResourceAsStream(it)!!.use { input -> input.readBytes() } }
    private val identity = CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin = "simulated"),
        "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222")
    private var readFrame: (ByteArray) -> Ht301Frame = Ht301Frame.Companion::parse
    @Volatile private var latest: Ht301AcquiredObservation? = null

    /** Use the real parser, current-frame measurement gate and immutable source wrapper. */
    private fun source(sequence: Long, receipt: Long): Ht301AcquiredObservation {
        val frame = readFrame(fixtures[(sequence % 2).toInt()])
        val state = SessionState.RADIOMETRIC_READY
        val inspection = frame.inspect()
        val result = MeasurementGate.evaluate(state, frame, sequence, receipt / 1_000_000)
        check(result.measurement != null)
        return Ht301AcquiredObservation(identity, sequence, sequence, receipt, frame, inspection, state, result)
    }

    /** Count cumulative caller-thread allocations. Keep just the latest owner, as in acquisition. */
    private fun allocations() {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        repeat(96) { latest = source(it.toLong(), it * 40_000_000L) }
        println("java=${System.getProperty("java.runtime.version")} retained_array_bytes=${latest!!.retainedBytes}")
        repeat(6) {
            val before = bean.getThreadAllocatedBytes(thread)
            val start = System.nanoTime()
            repeat(16) { latest = source(it.toLong(), it * 40_000_000L) }
            val elapsed = System.nanoTime() - start
            val allocated = bean.getThreadAllocatedBytes(thread) - before
            println("frames=16 allocated_bytes=$allocated elapsed_ns=$elapsed")
        }
    }

    /** Bound waits for the worker stages while a virtual source holds its time fixed. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Synthetic replay worker did not drain" }
            Thread.sleep(2)
        }
    }

    /** One deterministic Analysis/DEFLATE replay per build. Virtual cadence checks selection and bytes. */
    private fun replay(destination: File) {
        check(!destination.exists())
        val clock = AtomicLong(0)
        val header = FieldClock(clock::get, identity.receiptClockId).header() +
            ("recording_id" to "33333333-3333-4333-8333-333333333333")
        val profile = RecordingProfile.ANALYSIS
        val adapter = Ht301RecordingAdapter(identity, profile)
        val seconds = FieldPeriodicDuration.REGRESSION_SECONDS
        val plan = FieldPreflight(profile, seconds, null)
        val cutoff = FieldPeriodicDuration.deadline(0, seconds)
        val recorder = RecorderEngine(header, listOf(adapter.stream.value()), profile, plan.limits,
            LocalRecorderBackend(destination), plan.codec, now = clock::get,
            sampling = FiveHertzSampling(0), stopDeadline = cutoff)
        try {
            repeat(250) { sequence ->
                val receipt = sequence * 40_000_000L
                clock.set(receipt)
                val admission = recorder.admit(adapter.offer(source(sequence.toLong(), receipt)))
                if (sequence % 5 == 0) {
                    check(admission == Admission.ACCEPTED)
                    await { recorder.snapshot().let {
                        it.preparationQueuedBytes + it.preparationInFlightBytes +
                            it.writerQueuedBytes + it.writerInFlightBytes == 0L
                    } }
                } else check(admission == Admission.NOT_SELECTED)
            }
            clock.set(cutoff.monotonicNs)
            recorder.wakeTimers()
            val result = checkNotNull(recorder.awaitStop(30_000))
            await(recorder::workersTerminated)
            val counts = recorder.snapshot()
            val periodic = checkNotNull(recorder.periodicSnapshot())
            check(result.finalized && result.reason == RecordingStopDeadline.REASON)
            check(periodic.ledgerComplete && periodic.selected == 50L && periodic.notSelected == 200L)
            check(counts.accepted == 50L && counts.acknowledged == 50L)
            check(counts.preparationDrops == 0L && counts.writerDrops == 0L && periodic.missedJournaled == 0L)
            LmtrReader(destination).use { check(it.check(full = true)["status"] == "valid") }
            println("Analysis codec=${plan.codec} selected=${periodic.selected} ack=${counts.acknowledged} bytes=${destination.length()}")
        } finally {
            recorder.stop()
            recorder.awaitStop(30_000)
        }
    }

    /** Simulate one fresh JNI allocation on both arms. Bind the factory outside the measured path. */
    private fun simulateJni(handoff: Boolean): (ByteArray) -> Ht301Frame {
        val frameClass = Ht301Frame::class.java
        val signature = MethodType.methodType(frameClass, ByteArray::class.java)
        val lookup = MethodHandles.lookup()
        val factory = if (handoff) {
            val method = frameClass.getDeclaredMethod("takeFreshNativePayload", ByteArray::class.java)
            MethodHandles.privateLookupIn(frameClass, lookup).unreflect(method).asType(signature)
        } else {
            lookup.findVirtual(Ht301Frame.Companion::class.java, "parse", signature).bindTo(Ht301Frame.Companion)
        }
        return { fixture -> factory.invokeExact(fixture.copyOf()) as Ht301Frame }
    }

    /** Measure by default, or write a new synthetic recording on explicit request. */
    @JvmStatic fun main(args: Array<String>) {
        val mode = args.firstOrNull()
        val replayArgs = when (mode) {
            "--jni-copy", "--jni-handoff" -> {
                readFrame = simulateJni(mode == "--jni-handoff")
                args.drop(1)
            }
            else -> args.toList()
        }
        require(replayArgs.size <= 1)
        if (replayArgs.isEmpty()) allocations() else replay(File(replayArgs.single()))
    }
}
