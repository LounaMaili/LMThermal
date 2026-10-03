package org.lmthermal.exchange

import java.io.*
import java.security.MessageDigest
import java.util.zip.*
import kotlin.math.abs
import org.lmthermal.core.*

/** Reopen checker for original acquisition exports; not a Desktop importer or derivative editor.
 * Local numeric/image allocation ceiling is 4M pixels / 16MiB image member, explicitly resource_limit.
 * Every member is streamed/hash/CRC checked first, including unknown optional inventory entries.
 */
object LmtxChecker {
    fun check(file: File): JsonObject {
        val records = LmtxContainer.inspect(file).associateBy { it.name }
        try { ZipFile(file).use { zip ->
            fun bounded(name: String, limit: Int): ByteArray {
                val record = records[name] ?: throw LmtxException("missing_payload", name)
                demand(record.size <= limit, "resource_limit")
                return zip.getInputStream(zip.getEntry(name)).use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        demand(output.size().toLong() + n <= limit, "resource_limit"); output.write(buffer, 0, n) }
                    output.toByteArray()
                }.also { demand(it.size.toLong() == record.size, "integrity_mismatch") }
            }
            val manifest = LmtxJson.decode(bounded("manifest.json", LmtxJson.MAX_BYTES))
            val descriptors = LmtxSchema.validate(manifest)
            demand(records.keys == descriptors.map { it["member"].text() }.toSet() + "manifest.json", "missing_payload")
            var total = 0L
            for ((name, record) in records) {
                val digest = MessageDigest.getInstance("SHA-256"); val crc = CRC32(); var count = 0L
                zip.getInputStream(zip.getEntry(name)).use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) { val n = stream.read(buffer); if (n < 0) break
                        count += n; total += n
                        demand(count <= 134_217_728 && total <= 536_870_912, "resource_limit")
                        digest.update(buffer, 0, n); crc.update(buffer, 0, n)
                    }
                }
                demand(count == record.size && crc.value == record.crc, "integrity_mismatch")
                if (name != "manifest.json") {
                    val p = descriptors.single { it["member"] == name }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    demand(count == p["byte_length"].integer().toLong() && hash == p["sha256"], "integrity_mismatch")
                }
            }
            descriptors.forEach { p ->
                when (p["role"]) {
                    "extension_json" -> LmtxJson.decode(bounded(p["member"].text(), LmtxJson.MAX_BYTES))
                    "preview", "visible_image" -> {
                        demand(p["media_type"] == "image/png", "unsupported_required_feature", "This self-checker supports PNG, not JPEG")
                        checkPng(bounded(p["member"].text(), 16_777_216), p["image"].obj())
                    }
                }
            }
            val temperature = descriptors.singleOrNull { it["role"] == "temperature" }
            if (temperature != null) {
                val geometry = LmtxSchema.geometry(manifest)
                demand(geometry.pixelCount <= 4_194_304, "resource_limit", "Local 4M pixel analysis limit")
                val values = FloatArray(geometry.pixelCount)
                zip.getInputStream(zip.getEntry(temperature["member"].text())).use { input ->
                    for (i in values.indices) {
                        var bits = 0
                        for (shift in 0..24 step 8) { val b = input.read(); demand(b >= 0, "invalid_payload"); bits = bits or (b shl shift) }
                        values[i] = Float.fromBits(bits)
                    }
                }
                val mask = descriptors.singleOrNull { it["role"] == "temperature_validity" }?.let {
                    bounded(it["member"].text(), geometry.pixelCount) }
                demand(values.indices.all { i -> if (mask == null || mask[i].toInt() == 1) values[i].isFinite()
                    else mask[i].toInt() == 0 && values[i].toRawBits() == 0 }, "invalid_payload")
                val full = RoiStatistics.calculate(geometry, values, NativeRect(0, 0, geometry.width, geometry.height), mask)
                val m = manifest["measurement"].obj()
                val validity = when (full.validPixelCount) { 0 -> "no_valid_pixels"; geometry.pixelCount -> "all_valid"; else -> "partially_valid" }
                demand(m["validity"] == validity, "invalid_payload")
                m["extrema"]?.let { statistics(it.obj(), full, temperature["id"].text()) }
                val analysis = manifest["analysis"]?.obj()
                analysis?.get("shapes")?.array()?.forEach { item -> val shape = item.obj()
                    if (shape["type"] == "rectangle" && shape.containsKey("statistics")) statistics(shape["statistics"].obj(),
                        RoiStatistics.calculate(geometry, values, LmtxSchema.rectangle(shape), mask), temperature["id"].text()) }
                analysis?.get("points")?.array()?.forEach { item -> val point = item.obj()
                    point["temperature_c"]?.let { saved -> val offset = geometry.offset(geometry.pixel(point["x_px"].integer(), point["y_px"].integer()))
                        demand(mask == null || mask[offset].toInt() == 1, "invalid_payload"); near(saved.number(), values[offset].toDouble()) } }
            }
            return manifest
        } } catch (e: LmtxException) { throw e }
        catch (_: Exception) { throw LmtxException("invalid_payload", "Cannot validate payload") }
    }
    private fun near(saved: Double, calculated: Double) {
        demand(abs(saved - calculated) <= maxOf(1e-4, 1e-6 * abs(calculated)), "invalid_payload")
    }
    private fun statistics(saved: JsonObject, calculated: RoiStatistics, temperatureId: String) {
        demand(saved["temperature_payload_id"] == temperatureId && saved["method"] == RoiStatistics.METHOD && saved["unit"] == "Cel")
        demand(saved["pixel_count"].integer() == calculated.pixelCount && saved["valid_pixel_count"].integer() == calculated.validPixelCount, "invalid_payload")
        if (calculated.validPixelCount == 0) demand(listOf("min", "max", "mean", "min_xy", "max_xy").none { saved.containsKey(it) }, "invalid_payload")
        else {
            near(saved["min"].number(), calculated.minC!!.toDouble()); near(saved["max"].number(), calculated.maxC!!.toDouble())
            near(saved["mean"].number(), calculated.meanC!!)
            demand(saved["min_xy"].array().map { it.integer() } == listOf(calculated.minPixel!!.x, calculated.minPixel.y), "invalid_payload")
            demand(saved["max_xy"].array().map { it.integer() } == listOf(calculated.maxPixel!!.x, calculated.maxPixel.y), "invalid_payload")
        }
    }
    /** PNG structural/CRC and bounded zlib scanline validation; dimensions are not inferred from a filename. */
    private fun checkPng(bytes: ByteArray, descriptor: JsonObject) {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val signature = ByteArray(8); input.readFully(signature)
        demand(signature.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)), "invalid_payload")
        var header = false; var ended = false; var idat = false; var afterIdat = false; var rowBytes = 0L; var expected = 0L; var inflated = 0L
        val inflater = Inflater(); val output = ByteArray(8192)
        try {
            while (input.available() > 0) {
                val length = input.readInt(); demand(length >= 0 && length <= input.available() - 8, "invalid_payload")
                val type = ByteArray(4); input.readFully(type); val kind = type.toString(Charsets.US_ASCII)
                val data = ByteArray(length); input.readFully(data); val crc = CRC32(); crc.update(type); crc.update(data)
                demand(input.readInt().toLong() and 0xffffffffL == crc.value, "integrity_mismatch")
                demand(header || kind == "IHDR", "invalid_payload"); demand(!ended, "invalid_payload")
                when (kind) {
                    "IHDR" -> {
                        demand(!header && length == 13, "invalid_payload"); header = true
                        val h = DataInputStream(ByteArrayInputStream(data)); val width = h.readInt(); val height = h.readInt()
                        demand(width == descriptor["width_px"].integer(1) && height == descriptor["height_px"].integer(1), "invalid_payload")
                        demand(width.toLong() * height <= 4_194_304, "resource_limit")
                        val depth = h.readUnsignedByte(); val color = h.readUnsignedByte()
                        val channels = mapOf(0 to 1, 2 to 3, 3 to 1, 4 to 2, 6 to 4)[color]
                        demand(channels != null && depth in (if (color == 3) setOf(1, 2, 4, 8) else if (color == 0) setOf(1, 2, 4, 8, 16) else setOf(8, 16)), "invalid_payload")
                        demand(width.toLong() * height * channels!! * (if (depth == 16) 2 else 1) <= 134_217_728, "resource_limit")
                        rowBytes = (width.toLong() * channels * depth + 7) / 8 + 1; expected = rowBytes * height
                        demand(h.readUnsignedByte() == 0 && h.readUnsignedByte() == 0, "invalid_payload")
                        demand(h.readUnsignedByte() == 0, "unsupported_required_feature", "Self-checker supports non-interlaced PNG")
                    }
                    "IDAT" -> {
                        demand(!afterIdat && !inflater.finished(), "invalid_payload"); idat = true; inflater.setInput(data)
                        while (!inflater.needsInput() && !inflater.finished()) {
                            val n = inflater.inflate(output)
                            demand(n > 0, "invalid_payload")
                            for (i in 0 until n) if ((inflated + i) % rowBytes == 0L) demand(output[i].toInt() in 0..4, "invalid_payload")
                            inflated += n; demand(inflated <= expected, "resource_limit")
                        }
                        demand(inflater.remaining == 0, "invalid_payload")
                    }
                    "IEND" -> { demand(length == 0 && idat && inflater.finished() && inflated == expected, "invalid_payload"); ended = true }
                    else -> { if (idat) afterIdat = true
                        demand(kind != "eXIf", "unsupported_required_feature", "Orientation metadata must be removed by this producer")
                        demand(kind.first().isLowerCase() || kind == "PLTE", "invalid_payload") }
                }
            }
            demand(ended, "invalid_payload")
        } finally { inflater.end() }
    }
}
