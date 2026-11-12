package org.lmthermal.recorder

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.lmtr.*
import java.io.File
import java.nio.file.Files

/** Narrow compatibility is safer than merging distinct qualified evidence to improve a metric. */
class OverloadLossRunTest {
    private fun stream(session:String="a")=FrozenJson(mapOf("session_id" to session))
    @Test fun exactRunRetainsAllWitnessesAndCannotCrossCapacity() {
        val s=stream();val run=OverloadLossRun("primary",s,"writer_overload",100,1000)
        for(i in 1..31)assertTrue(run.extend("primary",s,"writer_overload",100L+i,1000L+i,null))
        assertFalse(run.extend("primary",s,"writer_overload",132,1032,null))
        assertEquals(32,run.count);val facts=run.facts()
        assertEquals("100",facts["observation_first"]);assertEquals("131",facts["observation_last"])
        val data=facts["extensions"].obj()[OverloadLossRun.NAMESPACE].obj()["data"].obj()
        assertEquals((1000L..1031L).map(Long::toString),data["intake_monotonic_ns"].arr())
    }
    @Test fun incompatibleSequenceStreamSessionCategoryQualificationAndClockCannotMerge() {
        val s=stream()
        fun refuses(id:String="primary",definition:FrozenJson=s,kind:String="writer_overload",
                    sequence:Long=1,witness:Long=1001,facts:FrozenJson?=null) {
            val run=OverloadLossRun("primary",s,"writer_overload",0,1000)
            assertFalse(run.extend(id,definition,kind,sequence,witness,facts));assertEquals(1,run.count)
        }
        refuses(sequence=2);refuses(id="other");refuses(definition=stream("b"))
        refuses(kind="preparation_overload");refuses(kind="source_loss");refuses(witness=999)
        refuses(facts=FrozenJson(mapOf("evidence" to "explicit_qualification")))
        assertFalse(OverloadLossRun.eligible("source_loss",null))
    }
    @Test fun freshAgedFullReserveOverflowAndFailedAppendKeepTruthfulRecovery() {
        val root=Files.createTempDirectory("loss-run-test-").toFile()
        try {
            for(codec in 0..1) {
                val fresh=RecorderBacklogProbe.runCase(File(root,"fresh-$codec"),codec,false,coalescing=true)
                val aged=RecorderBacklogProbe.runCase(File(root,"aged-$codec"),codec,true,coalescing=true,concurrentStop=true,withTrace=true)
                assertTrue(aged["chunks"].d()<50);assertTrue(fresh["finalized"]==true)
                val overflow=RecorderBacklogProbe.runCase(File(root,"overflow-$codec"),codec,true,overflow=true,coalescing=true)
                assertFalse(overflow["finalized"] as Boolean)
                val failed=RecorderBacklogProbe.runCase(File(root,"failed-$codec"),codec,true,failLastGap=true,coalescing=true)
                assertFalse(failed["finalized"] as Boolean)
            }
        } finally {check(root.deleteRecursively())}
    }
}

/** Explicit on-demand prototype outputs; original reference output is never rewritten. */
object LossRunProbe {
    @JvmStatic fun main(args:Array<String>) {
        val root=File(args.single());check(root.mkdirs())
        for(codec in 0..1) {
            RecorderBacklogProbe.runCase(File(root,"fresh-$codec"),codec,false,coalescing=true)
            RecorderBacklogProbe.runCase(File(root,"aged-$codec"),codec,true,coalescing=true,concurrentStop=true,withTrace=true)
            RecorderBacklogProbe.runCase(File(root,"overflow-$codec"),codec,true,overflow=true,coalescing=true)
            RecorderBacklogProbe.runCase(File(root,"gap-sync-failure-$codec"),codec,true,failLastGap=true,coalescing=true)
        }
    }
}
