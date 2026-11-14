package org.lmthermal.core

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.Ht301ThermalMeasurement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Shared numerical semantics and source ownership are unchanged by the optional display path. */
class ImmutableCelsiusRenderTest {
    private fun source():Ht301ThermalMeasurement {
        val bytes=javaClass.getResourceAsStream("/thermometry/warm-hand-settled.raw")!!.use {it.readBytes()}
        return Ht301ThermalMeasurement(NativeEquivalentThermometry.measure(Ht301Frame.parse(bytes)))
    }
    @Test fun htImmutableAndGenericFallbackHaveExactColorsAndRangesForEveryPaletteAndMode() {
        val source=source();val original=source.matrix();val raw=source.evidence.raw14()
        val fallback=object:ThermalMeasurement by source {}
        assertFalse(fallback is ImmutableCelsiusRenderProvider)
        for(palette in CelsiusPalette.entries)for(automatic in listOf(true,false)) {
            val settings=CelsiusPresentationSettings(palette,automatic)
            val direct=CelsiusRenderer.render(source,settings);val generic=CelsiusRenderer.render(fallback,settings)
            assertEquals(generic.range,direct.range);assertArrayEquals(generic.argb(),direct.argb())
            assertArrayEquals(original,source.matrix(),0f);assertArrayEquals(raw,source.evidence.raw14())
        }
    }
    @Test fun optInProviderDoesNotNeedThePublicMatrixCopy() {
        val source=source();val values=source.matrix();var computations=0
        val provider=object:ThermalMeasurement by source,ImmutableCelsiusRenderProvider {
            override fun matrix():FloatArray=error("Copy-only API must not be requested on this explicit path")
            override fun renderCelsius(settings:CelsiusPresentationSettings):CelsiusRender {
                computations++;return CelsiusRenderer.render(values,geometry,settings)
            }
        }
        CelsiusRenderer.render(provider,CelsiusPresentationSettings());assertEquals(1,computations)
    }
    @Test fun wrapperChangingMatrixDoesNotInheritAnotherOwnersOptInCapability() {
        val source=source();var copies=0;val replacement=FloatArray(source.geometry.pixelCount){44.25f}
        val wrapper=object:ThermalMeasurement by source {
            override fun matrix():FloatArray {copies++;return replacement.copyOf()}
        }
        val settings=CelsiusPresentationSettings()
        val actual=CelsiusRenderer.render(wrapper,settings)
        assertEquals(1,copies);assertArrayEquals(CelsiusRenderer.render(replacement,source.geometry,settings).argb(),actual.argb())
    }
    @Test fun displayTransferAndPublicMatrixMutationsCannotChangeSourceEvidence() {
        val source=source();val original=source.matrix()
        source.matrix().fill(-999f);source.evidence.raw14().fill(0)
        val first=CelsiusRenderer.render(source,CelsiusPresentationSettings());val independent=first.argb()
        val transferred=first.takeArgb();transferred.fill(0)
        assertArrayEquals(original,source.matrix(),0f)
        assertArrayEquals(independent,CelsiusRenderer.render(source,CelsiusPresentationSettings()).argb())
        assertThrows(IllegalStateException::class.java){first.argb()}
    }
    @Test fun explicitProviderStillUsesFullValidationEvenForLockedBounds() {
        val source=source();val values=source.matrix();values[values.lastIndex]=Float.NaN
        val provider=object:ThermalMeasurement by source,ImmutableCelsiusRenderProvider {
            override fun renderCelsius(settings:CelsiusPresentationSettings)=CelsiusRenderer.render(values,geometry,settings)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CelsiusRenderer.render(provider,CelsiusPresentationSettings(automatic=false))
        }
    }
    @Test fun invalidMeasurementCannotInvokeAnOptInProvider() {
        val source=source();var invoked=false
        val provider=object:ThermalMeasurement by source,ImmutableCelsiusRenderProvider {
            override val validity=MeasurementValidity.INVALID
            override fun renderCelsius(settings:CelsiusPresentationSettings):CelsiusRender {
                invoked=true;error("Invalid measurement")
            }
        }
        assertThrows(IllegalArgumentException::class.java){CelsiusRenderer.render(provider,CelsiusPresentationSettings())}
        assertFalse(invoked)
    }
    @Test fun concurrentRendersOnlyShareImmutableEvidenceAndProduceIndependentPixels() {
        val source=source();val original=source.matrix();val start=CountDownLatch(1)
        val error=AtomicReference<Throwable?>();val images=Array(2){arrayOfNulls<IntArray>(4)}
        val workers=(0..1).map {worker->Thread {
            try {check(start.await(5,TimeUnit.SECONDS));repeat(4) {images[worker][it]=CelsiusRenderer.render(source,CelsiusPresentationSettings()).takeArgb()}}
            catch(t:Throwable){error.compareAndSet(null,t)}
        }.apply {start()}}
        start.countDown();workers.forEach {it.join(10000);assertFalse(it.isAlive)};error.get()?.let {throw it}
        assertArrayEquals(original,source.matrix(),0f)
        assertNotSame(images[0][0],images[1][0]);assertArrayEquals(images[0][0],images[1][0])
        images[0][0]!!.fill(0);assertFalse(images[1][0]!!.all {it==0})
    }
}
