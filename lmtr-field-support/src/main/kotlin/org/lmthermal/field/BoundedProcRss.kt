package org.lmthermal.field

import java.io.InputStream
import java.io.IOException

/** Private reusable scratch for /proc status, never an image or shared source buffer.
 * Finite scanning rejects truncation, duplicate RSS, overflow and zero-progress reads.
 */
class BoundedProcRss {
    companion object {
        const val BUFFER_BYTES=1024;const val LINE_BYTES=128;const val MAX_BYTES=65536
        private val PREFIX=byteArrayOf(86,109,82,83,83,58) // ASCII VmRSS:
    }
    private val buffer=ByteArray(BUFFER_BYTES)
    private val line=ByteArray(LINE_BYTES)

    /** Linux VmRSS is KiB; no Android dumpsys category is substituted for it. */
    fun read(input:InputStream):Long {
        var total=0;var length=0;var tooLong=false;var result:Long?=null;var zeroReads=0
        fun consume() {
            val prefix=PREFIX
            if(length>=prefix.size && prefix.indices.all {line[it]==prefix[it]}) {
                if(tooLong || result!=null)throw IOException("ambiguous RSS")
                var i=prefix.size
                while(i<length && (line[i]==32.toByte() || line[i]==9.toByte()))i++
                val first=i;var value=0L
                while(i<length && line[i] in 48..57) {
                    val digit=line[i]-48
                    if(value>(Long.MAX_VALUE/1024-digit)/10)throw IOException("RSS overflow")
                    value=value*10+digit;i++
                }
                if(i==first)throw IOException("RSS number missing")
                while(i<length && (line[i]==32.toByte() || line[i]==9.toByte()))i++
                if(i+2>length || line[i]!=107.toByte() || line[i+1]!=66.toByte())throw IOException("RSS unit")
                i+=2;while(i<length && (line[i]==32.toByte() || line[i]==9.toByte()))i++
                if(i!=length)throw IOException("RSS suffix")
                result=value*1024
            }
            length=0;tooLong=false
        }
        while(true) {
            val n=input.read(buffer)
            if(n<0)break
            if(n==0){if(++zeroReads==4)throw IOException("no progress");continue}
            zeroReads=0;total+=n;if(total>MAX_BYTES)throw IOException("status byte budget")
            for(i in 0 until n) {
                val b=buffer[i]
                if(b==10.toByte())consume()
                else if(length<LINE_BYTES)line[length++]=b else tooLong=true
            }
        }
        // A partial final line is not a complete status file, even when VmRSS was earlier valid.
        if(length!=0 || tooLong)throw IOException("truncated status")
        return result ?: throw IOException("RSS unavailable")
    }
}
