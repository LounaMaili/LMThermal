package org.lmthermal.r2

import org.lmthermal.r2.R2Json as LmtxJson
import java.io.File
import java.io.RandomAccessFile

/** Test-only sparse forward logical sink. Zero payload holes stand for exact real zero bytes.
 * This is large-offset tooling, NOT a proposed writer/backend dependency on seek support.
 */
object SparseStress {
    fun write(file: File): Map<String, Any?> {
        require(!file.exists()); file.parentFile?.mkdirs()
        val source=RandomAccessFile(file,"rw")
        val sink=object: AppendSink {
            override fun write(bytes: ByteArray) {
                val zero=bytes.size>=1048576 && bytes.all { it==0.toByte() }
                if(zero) source.seek(Bounds.add(source.filePointer,bytes.size.toLong())) else source.write(bytes)
            }
            override fun sync() { source.fd.sync() }
            override fun close() { source.close() }
        }
        val temp=ByteArray(2048*2048*4); val raw=ByteArray(2048*2048*2); val acq=ByteArray(raw.size+64)
        val runtime=Runtime.getRuntime(); var peak=0L; var chunks=0L
        RecordWriter(sink).use { records -> PrototypeRecorder(records,Profile.FULL,Stored).use { writer ->
            repeat(180) { i -> writer.accept(Observation(i.toLong(),null,i*2000000000L,2048,2048,temp,native=raw,acquisition=acq,
                context=mapOf("provenance" to "sparse-zero-r2-stress"),nativeEncoding="org.lmthermal.r2.synthetic"))
                writer.seal(); peak=maxOf(peak,runtime.totalMemory()-runtime.freeMemory()) }
            writer.finish(); chunks=writer.committedChunks } }
        require(file.length()>4294967296L)
        var read=0L
        PrototypeReader(file).use { reader -> val before=reader.records.bytesRead
            require(reader.seek(179)!!.entries.single()["sequence"]=="179"); read=reader.records.bytesRead-before }
        return mapOf("logical_file_bytes" to file.length(),"chunks" to chunks,"peak_java_used" to peak,
            "seek_validation_read_bytes" to read,"offset_over_4gib" to true,"sparse_test_only" to true)
    }
}
