package org.lmthermal.recording

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.core.*
import org.lmthermal.lmtr.*
import org.lmthermal.recorder.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Preparation policy changes which owners are built, never the source's matrix, context or reasons. */
class Ht301ProfilePreparationTest {
    private fun source(ready:Boolean=true,bytes:ByteArray?=null):Ht301AcquiredObservation {
        val input=bytes ?:javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.use {it.readBytes()}
        val frame=Ht301Frame.parse(input)
        val state=if(ready)SessionState.RADIOMETRIC_READY else SessionState.RAW14_UNSETTLED
        return Ht301AcquiredObservation(CameraSourceIdentity(Ht301ModuleProfile.metadata.copy(sourceOrigin="simulated")),
            7,11,42000000,frame,frame.inspect(),state,MeasurementGate.evaluate(state,frame,11,42))
    }
    @Test fun analysisBuildsOnlyTemperatureWhileNativeAndFullRemainExplicitIndependentChoices() {
        val source=source()
        for((profile,roles) in listOf(RecordingProfile.ANALYSIS to setOf("temperature"),
            RecordingProfile.NATIVE to setOf("temperature","native_samples"),
            RecordingProfile.FULL to setOf("temperature","native_samples","acquisition"))) {
            val adapter=Ht301RecordingAdapter(source.identity,profile)
            val frozen=adapter.offer(source).materialize()
            assertEquals(roles,frozen.payloads.map {it.role}.toSet())
            assertEquals(profile,adapter.preparationProfile)
            assertEquals(3L*1048576,adapter.maximumCharge)
            assertEquals(source.receivedMonotonicNs,adapter.offer(source).sourceWitness!!.receiptNs)
        }
    }
    @Test fun profileAwareAndEagerFullSelectionHaveExactSameFactsContextAndSelectedBytes() {
        for(ready in listOf(false,true))for(profile in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL)) {
            val source=source(ready)
            val eager=RecordingProfiles.select(Ht301RecordingAdapter(source.identity,RecordingProfile.FULL).offer(source).materialize(),profile)
            val aware=RecordingProfiles.select(Ht301RecordingAdapter(source.identity,profile).offer(source).materialize(),profile)
            assertArrayEquals(LmtrJson.encode(eager.context.value()),LmtrJson.encode(aware.context.value()))
            assertArrayEquals(LmtrJson.encode(eager.facts.value()),LmtrJson.encode(aware.facts.value()))
            assertEquals(eager.payloads.map {it.role},aware.payloads.map {it.role})
            for((a,b) in eager.payloads.zip(aware.payloads))assertArrayEquals(a.bytes(),b.bytes())
        }
    }
    @Test fun callerMutationAndConcurrentFreezingCannotChangeTheImmutableMatrixOrInterpretation() {
        val source=source();val adapter=Ht301RecordingAdapter(source.identity,RecordingProfile.ANALYSIS)
        val original=source.result.measurement!!.matrix()
        val expected=ByteBuffer.allocate(original.size*4).order(ByteOrder.LITTLE_ENDIAN).apply {for(v in original)putInt(v.toRawBits())}.array()
        original.fill(Float.NaN)
        val pool=Executors.newFixedThreadPool(4)
        try {
            val futures=(0 until 4).map {pool.submit<Boolean> {
                repeat(8) {
                    val prepared=adapter.offer(source).materialize()
                    assertArrayEquals(expected,prepared.payloads.single().bytes())
                    val copy=prepared.payloads.single().bytes();copy.fill(0)
                    val facts=prepared.facts.value().toMutableMap();facts["measurement"]=null
                    assertEquals("available",prepared.facts.value()["measurement"].obj()["status"])
                    assertArrayEquals(expected,prepared.payloads.single().bytes())
                };true
            }}
            for(f in futures)assertTrue(f.get(20,TimeUnit.SECONDS))
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS))}
        assertArrayEquals(expected,adapter.offer(source).materialize().payloads.single().bytes())
    }
    @Test fun unavailableReasonAndDetailedGateReasonArePreservedWithCurrentContext() {
        val source=source(false)
        val frozen=Ht301RecordingAdapter(source.identity,RecordingProfile.ANALYSIS).offer(source).materialize()
        assertTrue(frozen.payloads.isEmpty())
        assertEquals(source.unavailableReason,frozen.facts.value()["measurement"].obj()["reason"])
        assertEquals(source.result.reason,frozen.facts.value()["source_evidence"].obj()["measurement_reason"])
        assertEquals("known",frozen.context.value()["settings"].obj()["status"])
        assertEquals("omitted",RecordingProfiles.select(frozen,RecordingProfile.ANALYSIS).facts.value()["retention"].obj()["native_samples"].obj()["status"])
    }
    @Test fun nonfiniteSettingsCannotBecomeAnAvailableAnalysisMatrixOrNonfiniteJson() {
        val bytes=source().transportBytes()!!
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(Ht301Layout.PARAMETERS,0x7fc00042)
        val source=source(bytes=bytes)
        assertNull(source.result.measurement)
        val analysis=Ht301RecordingAdapter(source.identity,RecordingProfile.ANALYSIS).offer(source).materialize()
        val full=Ht301RecordingAdapter(source.identity,RecordingProfile.FULL).offer(source).materialize()
        assertTrue(analysis.payloads.isEmpty())
        assertEquals("unavailable",analysis.facts.value()["measurement"].obj()["status"])
        assertArrayEquals(LmtrJson.encode(full.facts.value()),LmtrJson.encode(analysis.facts.value()))
        assertEquals("unavailable",analysis.context.value()["settings"].obj()["status"])
        assertArrayEquals(bytes,full.payloads.single {it.role=="acquisition"}.bytes())
        LmtrJson.parse(LmtrJson.encode(analysis.context.value()))
    }
}
