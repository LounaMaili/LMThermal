package org.lmthermal.exchange

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.*
import org.lmthermal.core.*
import org.lmthermal.camera.ht301.*

/** Deliberately synthetic/camera-free corpus. HT-rich evidence reuses an already sanitized, provenance-tracked frame. */
object LmtxTestSupport {
    val producer = CaptureProducer("org.lmthermal.conformance", "1.0")
    val clock = CaptureClock(reason = "synthetic_example_has_no_event_time")
    val grid = NativeImageGeometry(2, 2)
    fun png(width: Int, height: Int): ByteArray {
        val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        fun chunk(type: String, payload: ByteArray) {
            out.writeInt(payload.size); out.writeBytes(type); out.write(payload)
            val crc = CRC32(); crc.update(type.toByteArray()); crc.update(payload); out.writeInt(crc.value.toInt())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(0).put(0).put(0).put(0).array())
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { z -> repeat(height) { y -> z.write(0); repeat(width) { x -> z.write((x + y) % 256) } } }
        chunk("IDAT", compressed.toByteArray()); chunk("IEND", byteArrayOf()); return bytes.toByteArray()
    }
    fun preview(geometry: NativeImageGeometry) = EvidencePayload("preview", "preview/thermal.png", "preview", "image/png",
        OwnedMember(png(geometry.width, geometry.height)), mapOf("image" to mapOf("width_px" to geometry.width,
            "height_px" to geometry.height, "coordinate_space" to "native", "orientation" to "stored_pixels"), "origin" to "synthetic_gradient"))
    fun capture(geometry: NativeImageGeometry = grid, values: FloatArray? = floatArrayOf(20f, -0.0f, 31f, 24f), mask: ByteArray? = null,
        roi: NativeRect? = NativeRect(0, 0, geometry.width, geometry.height), evidence: SourceExportEvidence = SourceExportEvidence(),
        preview: EvidencePayload? = null, creation: CaptureClock = clock, id: String = "dba2f0a6-ecb2-4a92-9049-f07f0e094b72") =
        LmtxCapture(geometry, CaptureSource("synthetic.contract-example", "invented-conformance", "simulated"), producer,
            creation, captureId = id, temperature = values?.let { CapturedTemperature(geometry, it, mask,
                mapOf("kind" to "simulated", "physical_accuracy" to "not_applicable")) },
            preview = preview, evidence = evidence, roi = roi,
            capabilities = mapOf("temperature" to if (values != null) "supported" else "unsupported"))
    fun ht(): LmtxCapture {
        val raw = javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use { it.readBytes() }
        val m = NativeEquivalentThermometry.measure(Ht301Frame.parse(raw))
        val evidence = Ht301ThermalMeasurement(m).exportEvidence()
        return LmtxCapture(Ht301ModuleProfile.GEOMETRY, CaptureSource("ht301", Ht301ModuleProfile.metadata.modelId, "device"), producer, clock,
            captureId = "ca8a1d7b-f38c-45aa-b94b-c7777fa31001", temperature = CapturedTemperature(Ht301ModuleProfile.GEOMETRY,
                m.matrix(), provenance = evidence.temperatureProvenance()!!), evidence = evidence, roi = NativeRect(170, 130, 210, 160))
    }
    fun members(file: File): Map<String, ByteArray> = ZipFile(file).use { zip -> zip.entries().asSequence().associate {
        it.name to zip.getInputStream(it).use { stream -> stream.readBytes() } } }
    fun archive(file: File, members: Map<String, ByteArray>, method: Int = ZipEntry.DEFLATED) {
        ZipOutputStream(file.outputStream()).use { zip -> members.forEach { (name, bytes) ->
            val entry = ZipEntry(name); entry.time = 315532800000L; entry.method = method
            if (method == ZipEntry.STORED) { entry.size = bytes.size.toLong(); entry.crc = CRC32().apply { update(bytes) }.value }
            zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
        } }
    }
    fun floatBits(bytes: ByteArray): List<Int> = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let { b ->
        List(bytes.size / 4) { b.int } }
}
