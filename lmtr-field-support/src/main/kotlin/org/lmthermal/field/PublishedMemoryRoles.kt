package org.lmthermal.field

import org.lmthermal.camera.CameraSessionState
import org.lmthermal.camera.ThermalMeasurement
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement

/** Ephemeral published-role reads only. Never calls matrix/raw/cursor/bitmap copying APIs,
 * stores objects, infers retained roots, or adds aliases into a supposed unique byte total.
 */
object PublishedMemoryRoles {
    /** Known HT wrappers expose the same immutable evidence owner; generic sharing stays unknown. */
    private fun identity(m:ThermalMeasurement):Any=(m as? Ht301ThermalMeasurement)?.evidence ?: m
    fun fill(source:CameraSessionState<*>,rendered:ThermalMeasurement?,roi:ThermalMeasurement?,
        bitmapBytes:Long?,legendBytes:Long?,v:LongArray) {
        val m=source.measurement
        v[2]=m?.sequence ?: -1;v[3]=rendered?.sequence ?: -1;v[4]=roi?.sequence ?: -1
        v[5]=if(m==null || rendered==null)-1 else if(identity(m)===identity(rendered))1 else 0
        v[6]=if(m==null || roi==null)-1 else if(identity(m)===identity(roi))1 else 0
        v[7]=if(rendered==null || roi==null)-1 else if(identity(rendered)===identity(roi))1 else 0
        v[8]=bitmapBytes ?: -1;v[9]=legendBytes ?: -1
        v[10]=m?.geometry?.pixelCount?.toLong()?.times(4) ?: -1
        v[11]=source.statistics.received;v[12]=source.statistics.replaced;v[13]=source.statistics.malformed
    }
    /** Discard ambiguous role values across invalidation; retain the two generation witnesses. */
    fun qualify(v:LongArray,before:Long?,after:Long?):MemoryProbeStatus {
        if(before==null || before!=after) {
            v.fill(-1);v[0]=before ?: -1;v[1]=after ?: -1;return MemoryProbeStatus.SOURCE_CHANGED
        }
        v[0]=before;v[1]=after ?: -1;return MemoryProbeStatus.OK
    }
}
