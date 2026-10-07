package org.lmthermal.app.r2

import android.os.ParcelFileDescriptor
import android.os.StatFs
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.lmthermal.exchange.LmtxJson
import org.lmthermal.r2.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Synthetic artifacts are R2 interoperability packets, explicitly NOT the canonical R3 corpus. */
@RunWith(AndroidJUnit4::class)
class R2PacketDeviceTest {
    @Test fun androidWritesExactCrossPlatformPacketAndReopensCanonicalFileReadOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "captures/r2-packet-" + System.currentTimeMillis()).apply { mkdirs() }
        val cases = mutableListOf<Map<String, Any?>>()
        for (codec in listOf<BlockCodec>(Stored, Deflate1, NativeZstd)) for (profile in Profile.entries) {
            val file = File(directory, profile.name.lowercase() + "-" + codec.name + ".r2proto")
            RecordWriter(FileSink(file)).use { records -> PrototypeRecorder(records, profile, codec).use { writer ->
                repeat(6) { index -> writer.accept(if (index == 2) Observation(index.toLong(), null, index * 40000000L, 384, 288, null, reason = "explicit-synthetic-gap")
                    else Synthetic.frame(index.toLong(), masked = index % 11 == 0)) }; writer.finish() } }
            val hash = hex(sha(file.readBytes()))
            PrototypeReader(file, mapOf(0 to Stored, 1 to Deflate1, 2 to NativeZstd)).use { reader ->
                assertTrue(reader.complete); var frames = 0; var gaps = 0
                reader.forEachChunk { decoded -> decoded.entries.zip(decoded.bytes).forEach { (entry, bytes) ->
                    if (entry["reason"] == null) {
                        val sequence = entry.getValue("sequence").toString().toLong()
                        val expected = Synthetic.frame(sequence, masked = sequence % 11 == 0L)
                        assertArrayEquals(expected.temperature, bytes["temperature"])
                        if (profile != Profile.ANALYSIS) assertArrayEquals(expected.native, bytes["native"])
                        if (profile == Profile.FULL) assertArrayEquals(expected.acquisition, bytes["acquisition"])
                        frames++
                    } else { assertTrue(bytes.isEmpty()); gaps++ }
                } }; assertEquals(5, frames); assertEquals(1, gaps)
            }
            cases += mapOf("file" to file.name, "sha256" to hash, "expect" to "complete", "profile" to profile.name, "frames" to 5, "gaps" to 1)
            if (profile == Profile.FULL) {
                val bytes = file.readBytes(); val endOffset = RecordReader(file).use { it.last().ref.offset.toInt() }
                val missing = File(directory, "missing-end-" + codec.name + ".r2proto").apply { writeBytes(bytes.copyOf(endOffset)) }
                cases += mapOf("file" to missing.name, "sha256" to hex(sha(missing.readBytes())), "expect" to "recovered", "frames" to 5, "gaps" to 1, "profile" to profile.name)
                val broken = bytes.copyOf(); val firstChunk = RecordReader(file).use { it.record(0).ref.length.toInt() }
                broken[firstChunk + 100] = (broken[firstChunk + 100].toInt() xor 1).toByte()
                val corrupt = File(directory, "corrupt-" + codec.name + ".r2proto").apply { writeBytes(broken) }
                cases += mapOf("file" to corrupt.name, "sha256" to hex(sha(broken)), "expect" to "integrity_failure")
            }
            assertEquals(hash, hex(sha(file.readBytes())))
        }
        val canonical = File(directory, "full-deflate-1.r2proto")
        val original = hex(sha(canonical.readBytes()))
        ParcelFileDescriptor.open(canonical, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream -> assertEquals(original, hex(sha(stream.readBytes()))) }
        }
        // The granted operation is read-only; no second source file is needed for access.
        ParcelFileDescriptor.open(canonical, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            try { android.system.Os.write(descriptor.fileDescriptor, byteArrayOf(1), 0, 1); fail("read-only descriptor writable") }
            catch (_: android.system.ErrnoException) { }
        }
        val capacity = StatFs(directory.path).availableBytes
        assertFalse(StoragePolicy.start(2, 3, 1).allowStart)
        assertTrue(StoragePolicy.start(null, 3, 1).allowStart)
        assertEquals("low", StoragePolicy.running(1, 2).label)
        val manifest = mapOf("artifact" to "noncanonical-r2-synthetic-packet", "producer" to "Android test APK on Pixel 8",
            "cases" to cases, "app_private" to mapOf("available_bytes" to capacity, "statfs" to true, "file_sync" to true,
                "read_only_no_copy_descriptor" to true, "source_immutable" to (original == hex(sha(canonical.readBytes())))),
            "warning" to "Native-equivalent temperatures; absolute physical accuracy not yet independently validated.")
        File(directory, "packet.json").writeBytes(LmtxJson.encode(manifest))
        File(context.filesDir, "r2-packet-location.txt").writeText(directory.name)
    }
    @Test fun codecExpansionTrailingAndDictionaryInputsReject() {
        val original = ByteArray(10000) { (it % 173).toByte() }
        for (codec in listOf<BlockCodec>(Stored, Deflate1, NativeZstd)) {
            val encoded = codec.encode(original); assertArrayEquals(original, codec.decode(encoded, original.size))
            try { codec.decode(encoded, 5); fail("expansion accepted") } catch (_: IllegalArgumentException) { }
            try { codec.decode(encoded + byteArrayOf(1), original.size); fail("trailing bytes accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
