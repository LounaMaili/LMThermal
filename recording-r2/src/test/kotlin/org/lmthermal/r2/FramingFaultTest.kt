package org.lmthermal.r2

import org.junit.Test
import org.junit.Assert.*
import org.lmthermal.exchange.LmtxJson
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FramingFaultTest {
    private fun file() = File.createTempFile("r2-framing", ".r2proto").also { it.delete(); it.deleteOnExit() }
    private fun rejects(action: () -> Unit) { try { action(); fail("invalid input accepted") } catch (_: IllegalArgumentException) {} }
    @Test fun maliciousLengthsRecordTypesAndOrdinalsRejectBeforeAllocation() {
        for (field in listOf("length", "type", "ordinal")) {
            val source = file(); Synthetic.write(source, Profile.FULL, Deflate1, 3)
            val data = source.readBytes(); val bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            when (field) { "length" -> bytes.putLong(24, Long.MAX_VALUE); "type" -> bytes.putShort(10, 999); "ordinal" -> bytes.putLong(16, 1) }
            source.writeBytes(data); rejects { PrototypeReader(source).close() }
        }
    }
    @Test fun damagedFinalIndexAndTornCheckpointRecoverEarlierCommits() {
        val source = file(); Synthetic.write(source, Profile.FULL, Deflate1)
        val all = mutableListOf<RecordReader.Record>(); RecordReader(source).use { reader -> var at=0L
            while (at<reader.size) { val r=reader.record(at); all+=r; at+=r.ref.length } }
        val data=source.readBytes()
        val end=all.last(); val root=LmtxJson.decode(data.copyOfRange((end.ref.offset+40).toInt(),(end.ref.offset+40+end.bodyLength).toInt()))["root"].objectMap()
        val broken=file(); val damaged=data.copyOf(); damaged[root.long("offset").toInt()+41]=(damaged[root.long("offset").toInt()+41].toInt() xor 1).toByte(); broken.writeBytes(damaged)
        PrototypeReader(broken).use { reader -> assertFalse(reader.complete); var count=0; reader.forEachChunk { count+=it.entries.size }; assertEquals(60,count) }
        val checkpoint=all.first { it.type==RecordType.CHECKPOINT }
        for (cut in listOf(checkpoint.ref.offset+41,checkpoint.ref.offset+checkpoint.ref.length-1)) {
            val torn=file(); torn.writeBytes(data.copyOf(cut.toInt()))
            PrototypeReader(torn).use { reader -> assertFalse(reader.complete); var count=0; reader.forEachChunk { count+=it.entries.size }; assertEquals(60,count) }
        }
    }
    @Test fun invalidContextReferenceAndParentHashReject() {
        val encoded=ChunkEncoder(Profile.FULL,Stored).encode(listOf(Synthetic.frame(0,3,2)))
        for (field in listOf("context","parent_hash")) {
            val metadata=LmtxJson.decode(encoded.parts[1]).toMutableMap(); val entry=metadata.list("entries").single().objectMap().toMutableMap()
            if(field=="context") entry["context"]=3 else {
                val payloads=entry["payloads"].objectMap().toMutableMap(); val parent=payloads["acquisition"].objectMap().toMutableMap()
                parent["hash"]="0".repeat(64); payloads["acquisition"]=parent; entry["payloads"]=payloads }
            metadata["entries"]=listOf(entry); val bytes=encoded.parts.drop(2).fold(byteArrayOf()) { a,b -> a+b }
            rejects { ChunkDecoder().decodeMetadata(metadata,bytes.size) { off,n -> bytes.copyOfRange(off.toInt(),off.toInt()+n) } }
        }
    }
    @Test fun timeCountByteAndFinalStopChunkPoliciesRemainBounded() {
        for (mode in listOf("time","entries","bytes","low_rate","mask_context")) {
            val source=file(); RecordWriter(FileSink(source)).use { records -> PrototypeRecorder(records,Profile.FULL,Stored).use { writer ->
                val count=if(mode=="bytes") 30 else 70
                repeat(count) { i -> val frame=Synthetic.frame(i.toLong(),if(mode=="bytes") 384 else 13,if(mode=="bytes")288 else 7,mode=="mask_context")
                    writer.accept(frame.copy(relativeNs=i*(if(mode=="low_rate")2000000000L else if(mode=="entries")100L else 40000000L))) }
                writer.finish(); assertTrue(writer.committedChunks>=2); assertTrue(writer.maxChunkBytes<=Bounds.CHUNK) }
            }; PrototypeReader(source).use { assertTrue(it.complete) }
        }
    }
}
