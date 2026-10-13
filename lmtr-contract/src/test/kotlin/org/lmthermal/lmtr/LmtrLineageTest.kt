package org.lmthermal.lmtr

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

/** Frozen approach B and single-file identity cannot silently invent legacy dataset provenance. */
class LmtrLineageTest {
    private fun header():Obj = LmtrJson.parse(javaClass.getResourceAsStream("/lmtr/plans/frozen-single-file-reprocessing.json")!!.use { it.readBytes() })["header"].obj()

    private fun reject(expected:String, action:()->Unit) {
        try { action();fail("invalid lineage must reject") }
        catch(failure:LmtrFailure) { assertEquals(expected,failure.code) }
    }

    @Test fun knownMultiFileDatasetCannotBecomeSingleParentFile() {
        val original=header();val lineage=original["lineage"].obj()
        val parents=lineage["parents"].arr().map { it.obj()+("format_id" to "lmthermal-radiometric-recording") }
        reject("unsupported_required_feature") { LmtrMetadata.header(original+("lineage" to (lineage+("parents" to parents)))) }
    }

    @Test fun recordingCannotNameItselfAsImmutableParent() {
        val original=header();val lineage=original["lineage"].obj()
        val parents=lineage["parents"].arr().map { it.obj()+("recording_id" to original["recording_id"]) }
        reject("invalid_metadata") { LmtrMetadata.header(original+("lineage" to (lineage+("parents" to parents)))) }
    }

    @Test fun nullParentEntryRequiresNonemptyNamespacedReferenceEvidence() {
        val source=File(javaClass.getResource("/lmtr/files/frozen-single-file-reprocessing.lmtr")!!.toURI())
        val metadata=RandomAccessFile(source,"r").use { file ->
            val head=LmtrFraming.read(file,0)
            LmtrChunk.metadata(LmtrFraming.read(file,head.length).body).first
        }
        for(empty in listOf(false,true)) {
            val entries=metadata["entries"].arr().map { value ->
                val entry=value.obj()
                if(!entry.containsKey("parent_entry"))entry else {
                    val replacement=entry.toMutableMap()
                    replacement["parent_entry"]=entry["parent_entry"].obj()+("entry_id" to null)
                    if(empty)replacement["extensions"]=emptyMap<String,Any?>() else replacement.remove("extensions")
                    replacement
                }
            }
            reject("invalid_metadata") { LmtrMetadata.chunk(metadata+("entries" to entries),header()) }
        }
    }
}
