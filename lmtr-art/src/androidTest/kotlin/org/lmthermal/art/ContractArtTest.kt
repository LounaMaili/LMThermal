package org.lmthermal.art

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.RecordingInteropProbe
import java.io.File
import java.util.zip.CRC32

/** Actual ART contract execution. Assets are unchanged canonical inputs, not generated fixtures. */
class ContractArtTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun asset(name:String)=instrumentation.context.assets.open("lmtr/$name").use { it.readBytes() }
    private fun equalJson(a:Any?,b:Any?) = assertArrayEquals(LmtrJson.encode(a),LmtrJson.encode(b))

    @Test fun exactCanonicalJsonAndBinaryEvidenceHaveDistinctAuthority() {
        val input="{\"z\":-0.0,\"a\":9007199254740993,\"n\":1.2345678901234567890123456789}".toByteArray()
        assertEquals("{\"a\":9007199254740993,\"n\":1.2345678901234567890123456789,\"z\":0}",String(LmtrJson.encode(LmtrJson.parse(input))))
        assertEquals(0x80000000.toInt(),Float.fromBits(0x80000000.toInt()).toRawBits())
        assertEquals(0x7fc00042,Float.fromBits(0x7fc00042).toRawBits())
    }
    @Test fun hashesAndCodecBoundariesRunOnArt() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",LmtrChunk.sha("abc".toByteArray()))
        assertEquals(0xcbf43926L,CRC32().apply { update("123456789".toByteArray()) }.value)
        val bytes=ByteArray(4096){(it*7).toByte()}
        for(codec in 0..1)assertArrayEquals(bytes,LmtrCodec.decode(LmtrCodec.encode(bytes,codec),codec,bytes.size.toLong(),1048576))
    }
    @Test fun allFiftyOneCanonicalFilesRetainExpectedMeaningAndSourceBytes() {
        val manifest=LmtrJson.parse(asset("manifest.json"));val cases=manifest["cases"].arr().map { it.obj() }
        assertEquals(51,cases.size)
        val root=File(instrumentation.targetContext.cacheDir,"canonical-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        for(case in cases) {
            val bytes=asset(case["file"].text());assertEquals(case["sha256"],LmtrChunk.sha(bytes))
            val file=File(root,case["id"].text()+".lmtr").apply { writeBytes(bytes) };val expected=case["expected"].obj()
            if(expected["status"]=="error") {
                val failure=assertThrows(LmtrFailure::class.java){LmtrReader(file).use { it.check(full=true,recover=case["recover"]==true) }}
                assertEquals(case["id"].text(),expected["error"],failure.code)
            } else {
                val actual=LmtrReader(file).use { it.check(full=true,recover=expected["status"]=="recovered") }
                for(field in listOf("status","integrity_scope","summary","logical_hashes","declared_end_status"))equalJson(expected[field],actual[field])
            }
            assertArrayEquals(bytes,file.readBytes())
        }
    }
    @Test fun authoritativeSourceAdapterProducesSyntheticReadback() {
        org.lmthermal.recording.CameraRecordingStressProbe.main(arrayOf(
            File(instrumentation.targetContext.filesDir,"r4/source/generated").absolutePath,"3"))
    }
    @Test fun realEngineOutputsArePreservedForIndependentHostReadback() {
        // Only synthetic data enters this explicitly exported directory; no camera is instantiated.
        RecordingInteropProbe.main(arrayOf(File(instrumentation.targetContext.filesDir,"r4/generated").absolutePath))
        val environment=mapOf("sdk" to android.os.Build.VERSION.SDK_INT,"abis" to android.os.Build.SUPPORTED_ABIS.toList(),
            "model" to android.os.Build.MODEL,"runtime" to System.getProperty("java.vm.name"),
            "accuracy_warning" to "Native-equivalent temperatures; absolute physical accuracy not yet independently validated.")
        File(instrumentation.targetContext.filesDir,"r4/environment.json").writeBytes(LmtrJson.encode(environment))
    }
}
