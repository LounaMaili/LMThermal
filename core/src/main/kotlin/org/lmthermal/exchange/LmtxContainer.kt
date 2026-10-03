package org.lmthermal.exchange

import java.io.File
import java.io.RandomAccessFile

/** Validates physical ZIP records before ZipFile can allocate/read payloads. Offsets/ranges are checked independently.
 * This is a camera-free conformance checker, not an extractor; no archive member becomes a filesystem path.
 */
object LmtxContainer {
    data class Entry(val name: String, val size: Long, val compressed: Long, val crc: Long, val method: Int, val offset: Long)
    fun safePath(name: String) {
        demand(name.length <= 240 && name.isNotEmpty() && name.all { it.code in 32..126 }, "unsafe_path")
        val parts = name.split('/')
        val reserved = setOf("con", "prn", "aux", "nul") + (1..9).flatMap { listOf("com" + it, "lpt" + it) }
        demand(parts.all { it.matches(Regex("[a-z0-9][a-z0-9._-]*")) && !it.contains("..") &&
            !it.endsWith('.') && it.substringBefore('.') !in reserved }, "unsafe_path")
        demand(name == "manifest.json" || parts.first() in setOf("data", "preview", "extensions") && parts.size > 1, "unsafe_path")
    }
    fun inspect(file: File): List<Entry> {
        demand(file.length() in 22..268_435_456, "resource_limit")
        try { RandomAccessFile(file, "r").use { input ->
            fun u16(): Int = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)
            fun extra(n: Int) {
                val end = input.filePointer + n
                while (input.filePointer < end) { demand(end - input.filePointer >= 4, "invalid_container")
                    val id = u16(); val length = u16(); demand(id != 1 && input.filePointer + length <= end, "invalid_container")
                    input.seek(input.filePointer + length) }
            }
            fun name(n: Int): String {
                demand(n in 1..240, "unsafe_path"); val bytes = ByteArray(n); input.readFully(bytes)
                demand(bytes.all { it.toInt() in 32..126 }, "unsafe_path")
                return bytes.toString(Charsets.US_ASCII).also(::safePath)
            }
            var eocd = -1L
            for (position in file.length() - 22 downTo maxOf(0, file.length() - 65557)) {
                input.seek(position)
                if (u32() == 0x06054b50L) { input.seek(position + 20)
                    if (position + 22 + u16() == file.length()) { eocd = position; break } }
            }
            demand(eocd >= 0, "invalid_container")
            input.seek(eocd + 4); demand(u16() == 0 && u16() == 0, "invalid_container")
            val diskCount = u16(); val count = u16(); demand(count == diskCount && count in 1..256, "resource_limit")
            val directorySize = u32(); val directoryOffset = u32()
            demand(directorySize <= 2_097_152, "resource_limit")
            demand(directoryOffset + directorySize == eocd, "invalid_container")
            input.seek(directoryOffset)
            data class Record(val entry: Entry, val flags: Int)
            val records = mutableListOf<Record>()
            repeat(count) {
                demand(u32() == 0x02014b50L, "invalid_container")
                val madeBy = u16(); val needed = u16(); val flags = u16(); val method = u16()
                demand(needed <= 20 && flags and 0xf7f1 == 0 && method in setOf(0, 8), "invalid_container")
                u32(); val crc = u32(); val compressed = u32(); val size = u32()
                demand(size <= 134_217_728, "resource_limit")
                demand(compressed != 0xffffffffL && size != 0xffffffffL, "invalid_container")
                val nameLength = u16(); val extraLength = u16(); val commentLength = u16()
                demand(u16() == 0, "invalid_container"); u16(); val attributes = u32(); val offset = u32()
                val unixMode = (attributes ushr 16).toInt()
                demand(attributes and 0x18L == 0L && (madeBy ushr 8 != 3 ||
                    unixMode and 0xf000 in setOf(0, 0x8000) && unixMode and 0x49 == 0), "invalid_container")
                val memberName = name(nameLength); extra(extraLength); input.seek(input.filePointer + commentLength)
                demand(input.filePointer <= eocd, "invalid_container")
                records += Record(Entry(memberName, size, compressed, crc, method, offset), flags)
            }
            demand(input.filePointer == eocd, "invalid_container")
            val names = records.map { it.entry.name }
            demand(names.toSet().size == count, "unsafe_path")
            demand(names.none { n -> names.any { it.startsWith(n + "/") } }, "unsafe_path")
            demand(names.count { it == "manifest.json" } == 1, "invalid_container")
            demand(records.sumOf { it.entry.size } <= 536_870_912, "resource_limit")
            var end = 0L
            for (record in records.sortedBy { it.entry.offset }) {
                val e = record.entry
                demand(e.offset >= end && (end != 0L || e.offset == 0L), "invalid_container") // No preamble or overlapping members.
                input.seek(e.offset); demand(u32() == 0x04034b50L, "invalid_container")
                demand(u16() <= 20 && u16() == record.flags && u16() == e.method, "invalid_container")
                u32(); val crc = u32(); val compressed = u32(); val size = u32(); val n = u16(); val x = u16()
                demand(name(n) == e.name, "invalid_container"); extra(x)
                if (record.flags and 8 == 0) demand(crc == e.crc && compressed == e.compressed && size == e.size, "invalid_container")
                else demand((crc == 0L || crc == e.crc) && (compressed == 0L || compressed == e.compressed) &&
                    (size == 0L || size == e.size), "invalid_container")
                end = input.filePointer + e.compressed; demand(end <= directoryOffset, "invalid_container")
                if (record.flags and 8 != 0) {
                    input.seek(end); var descriptorCrc = u32()
                    if (descriptorCrc == 0x08074b50L) descriptorCrc = u32()
                    demand(descriptorCrc == e.crc && u32() == e.compressed && u32() == e.size, "invalid_container")
                    end = input.filePointer
                }
            }
            demand(end == directoryOffset, "invalid_container")
            return records.map { it.entry }
        } } catch (e: LmtxException) { throw e }
        catch (_: Exception) { throw LmtxException("invalid_container", "Invalid ZIP records") }
    }
}
