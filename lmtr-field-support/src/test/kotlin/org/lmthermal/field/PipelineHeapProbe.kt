package org.lmthermal.field

import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import org.lmthermal.core.*
import org.lmthermal.lmtr.LmtrJson
import org.lmthermal.lmtr.Obj
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Camera-free attribution of unchanged acquisition math versus presentation allocations.
 * This is synchronous fixture replay, not USB/Compose throughput or a recorder admission.
 * The optional single GC per phase is explicitly diagnostic, after the measured window.
 * Weak owners distinguish retained generations from garbage without claiming a universal bound.
 */
object PipelineHeapProbe {
    @JvmStatic fun main(args: Array<String>) {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val method = Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        run(File(args[0]), paced = false, platformSample = { emptyMap() },
            threadAllocation = { (method.invoke(bean, Thread.currentThread().id) as Number).toLong() }, diagnosticGc = { System.gc(); Thread.sleep(200) })
    }

    /** Each invocation requires a new directory and preserves every earlier output. */
    fun run(root: File, paced: Boolean, platformSample: () -> Obj,
        bitmap: ((IntArray) -> Any)? = null, threadAllocation: (() -> Long)? = null, diagnosticGc: (() -> Unit)? = null) {
        check(root.mkdirs())
        val input = javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes()
        val reports = mutableListOf<Obj>()
        for (phase in listOf("acquisition", "acquisition_colors", "acquisition_colors_roi_bitmap")) {
            val owners = ArrayList<WeakReference<RadiometricMeasurement>>()
            val retained = ArrayDeque<RadiometricMeasurement>()
            var displayedBitmap: Any? = null
            var lastHash: String? = null
            var peak = 0L
            var missedDeadlines = 0
            val start = platformSample() + occupied()
            val allocations = linkedMapOf<String, Long>()
            val begin = System.nanoTime()
            repeat(120) { index ->
                fun <T> attributed(name: String, action: () -> T): T {
                    val before = threadAllocation?.invoke()
                    val result = action()
                    val after = threadAllocation?.invoke()
                    if (before != null && after != null)
                        allocations[name] = (allocations[name] ?: 0) + after - before
                    return result
                }
                val measurement = attributed("parse_inspect_measure") {
                    val frame = Ht301Frame.parse(input)
                    frame.inspect()
                    // The real session uses this identical SHA input for liveness.
                    frame.imageDigest()
                    MeasurementGate.evaluate(SessionState.RADIOMETRIC_READY, frame, index.toLong(), index * 40L).measurement!!
                }
                owners.add(WeakReference(measurement))
                retained.addLast(measurement)
                // Deliberately retain three distinct generations, not an unbounded observer history.
                if (retained.size > 3) retained.removeFirst()
                val generic = Ht301ThermalMeasurement(measurement)
                if (phase != "acquisition") {
                    val colors = attributed("render_and_argb_copy") {
                        CelsiusRenderer.render(generic, CelsiusPresentationSettings()).argb()
                    }
                    if (phase.endsWith("bitmap")) {
                        displayedBitmap = attributed("bitmap") { bitmap?.invoke(colors) }
                        attributed("full_roi") { RoiStatistics.calculate(generic.geometry, generic.matrix(),
                            NativeRect(0, 0, 384, 288)) }
                    }
                }
                // Only the first/last output is hashed; hashing is outside attributed operations.
                if (index == 0 || index == 119) {
                    val matrix = measurement.matrix()
                    val bytes = ByteBuffer.allocate(matrix.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    matrix.forEach { bytes.putInt(it.toRawBits()) }
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it.toInt() and 255) }
                    check(lastHash == null || lastHash == hash)
                    lastHash = hash
                }
                peak = maxOf(peak, (occupied()["java_occupied_bytes"] as Number).toLong())
                if (paced) {
                    val remaining = begin + (index + 1) * 40_000_000L - System.nanoTime()
                    if (remaining > 0) Thread.sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
                    else missedDeadlines++
                }
            }
            val windowElapsed = System.nanoTime() - begin
            val end = platformSample() + occupied()
            val aliveBefore = owners.count { it.get() != null }
            diagnosticGc?.invoke() // Never available to physical Start or the production admission gate.
            val afterGc = platformSample() + occupied()
            val aliveAfter = owners.count { it.get() != null }
            // Keep these owners strongly reachable through the after-GC sample; then release them.
            check(retained.size == 3 && retained.all { it.matrix().size == Ht301Layout.IMAGE_WORDS })
            reports.add(mapOf("phase" to phase, "frames" to 120, "paced_25_hz" to paced,
                "start" to start, "end" to end, "sampled_peak_occupied_bytes" to peak,
                "window_elapsed_ns" to windowElapsed, "missed_40ms_deadlines" to missedDeadlines,
                "attributed_thread_bytes" to allocations, "weak_owners_before_diagnostic_gc" to aliveBefore,
                "weak_owners_after_diagnostic_gc" to aliveAfter, "strong_owner_generations" to retained.size,
                "after_diagnostic_gc" to afterGc, "diagnostic_gc_requested" to (diagnosticGc != null),
                "bitmap_created" to (displayedBitmap != null), "float32_sha256" to lastHash))
            retained.clear()
        }
        check(reports.map { it["float32_sha256"] }.distinct().size == 1)
        File(root, "pipeline.json").writeBytes(LmtrJson.encode(mapOf("phases" to reports,
            "physical" to false, "recorder_created" to false,
            "scope" to "fixture_synchronous_replay_not_compose_or_usb_or_admission",
            "profiler_overhead" to "start_end_platform_probes_and_first_last_hashes_not_subtracted; host_thread_attribution_includes_counter_read_overhead")))
    }
    /** Occupied bytes include uncollected garbage; the diagnostic GC is only a reclaimability observation. */
    private fun occupied(): Obj = Runtime.getRuntime().let {
        mapOf("java_max_bytes" to it.maxMemory(), "java_occupied_bytes" to it.totalMemory() - it.freeMemory())
    }
}
