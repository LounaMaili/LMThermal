package org.lmthermal.field

import org.junit.Assert.*
import org.junit.Test
import org.lmthermal.camera.ht301.*
import org.lmthermal.camera.*
import org.lmthermal.core.*
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/** Independent old-sort reference plus original fixtures; no approximate percentile tolerance. */
class PresentationParityTest {
    private fun check(values: FloatArray, geometry: NativeImageGeometry) {
        val original = values.copyOf()
        val old = runCatching { PresentationReference.range(values, geometry) }
        val new = runCatching { CelsiusRenderer.autoRange(values, geometry) }
        assertEquals(old.isSuccess, new.isSuccess)
        if (old.isSuccess) {
            assertEquals(old.getOrThrow().lower.toRawBits(), new.getOrThrow().lower.toRawBits())
            assertEquals(old.getOrThrow().upper.toRawBits(), new.getOrThrow().upper.toRawBits())
            for (value in values) assertEquals(CelsiusRenderer.level(value.toDouble(), old.getOrThrow()),
                CelsiusRenderer.level(value.toDouble(), new.getOrThrow()))
        }
        for (i in values.indices) assertEquals(original[i].toRawBits(), values[i].toRawBits())
    }
    @Test fun automaticPercentilesPreserveSortedFloatBitsAcrossRanksTiesAndSignedZeros() {
        val random = Random(9173)
        for (size in listOf(1,2,3,49,50,51,100,257,4096)) repeat(20) {
            val values = FloatArray(size) {
                when (random.nextInt(5)) { 0 -> -0f; 1 -> 0f; 2 -> 25f; else -> (random.nextDouble()*200-100).toFloat() }
            }
            check(values, NativeImageGeometry(size,1))
        }
    }
    @Test fun fullSizeOrderedReversePlateauAndExtremeFinitePlanesMatchOriginalSort() {
        val geometry = Ht301ModuleProfile.GEOMETRY
        val planes = listOf(FloatArray(geometry.pixelCount) { it.toFloat() },
            FloatArray(geometry.pixelCount) { (geometry.pixelCount-it).toFloat() },
            FloatArray(geometry.pixelCount) { if (it%2==0) -0f else 0f },
            FloatArray(geometry.pixelCount) { (it%7).toFloat() },
            FloatArray(geometry.pixelCount) { if (it%2==0) -Float.MAX_VALUE else Float.MAX_VALUE })
        planes.forEach { check(it,geometry) }
    }
    @Test fun nonfiniteAndWrongGeometryStillRejectBeforeRangeCalculation() {
        for (bad in listOf(Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { CelsiusRenderer.autoRange(floatArrayOf(bad),NativeImageGeometry(1,1)) }
        }
        assertThrows(IllegalArgumentException::class.java) { CelsiusRenderer.autoRange(FloatArray(2),NativeImageGeometry(1,1)) }
    }
    private fun source(): Ht301ThermalMeasurement = Ht301ThermalMeasurement(NativeEquivalentThermometry.measure(
        Ht301Frame.parse(javaClass.getResourceAsStream("/fixtures/radiometric-room-settled.raw")!!.readBytes())))
    @Test fun pixelTransferIsSingleOwnerAndPublicCopiesCannotMutateSourceOrTransferredPixels() {
        val measurement=source();val before=measurement.matrix()
        val render=CelsiusRenderer.render(measurement,CelsiusPresentationSettings())
        val copy=render.argb();val expected=copy.copyOf();copy.fill(0)
        val transferred=render.takeArgb();assertArrayEquals(expected,transferred)
        assertThrows(IllegalStateException::class.java){render.takeArgb()}
        assertThrows(IllegalStateException::class.java){render.argb()}
        transferred.fill(0);assertArrayEquals(before,measurement.matrix(),0f)
    }
    @Test fun simultaneousTransfersPermitExactlyOneOwner() {
        val render=CelsiusRenderer.render(source(),CelsiusPresentationSettings());val owners=AtomicInteger()
        val threads=List(8){Thread {runCatching {render.takeArgb()}.onSuccess {owners.incrementAndGet()} }}
        threads.forEach {it.start()};threads.forEach {it.join()};assertEquals(1,owners.get())
    }
    @Test fun moduleOwnedRoiMatchesCopiedPlaneForFullCornersAndInteriorWithoutChangingEvidence() {
        val measurement=source();val before=measurement.matrix();val raw=measurement.evidence.source.transportBytes()
        for (rect in listOf(NativeRect(0,0,384,288),NativeRect(0,0,1,1),NativeRect(383,287,384,288),NativeRect(100,80,220,190))) {
            assertEquals(RoiStatistics.calculate(measurement.geometry,measurement.matrix(),rect),measurement.roiStatistics(rect))
        }
        measurement.matrix().fill(0f);assertArrayEquals(before,measurement.matrix(),0f)
        assertArrayEquals(raw,measurement.evidence.source.transportBytes())
        assertThrows(IllegalArgumentException::class.java){measurement.roiStatistics(NativeRect(0,0,385,288))}
    }
    @Test fun delegatedGenericWrapperStillUsesItsOwnMaskAndFullPlaneValidation() {
        val base=source();val rect=NativeRect(0,0,1,1)
        val masked:ThermalMeasurement=object:ThermalMeasurement by base {
            override fun matrix()=base.matrix().apply {this[0]=Float.NaN}
            override fun validityMask()=ByteArray(geometry.pixelCount){1}.apply {this[0]=0}
        }
        assertFalse(masked is ImmutableRoiProvider)
        assertEquals(0,masked.roiStatistics(rect).validPixelCount)
        val invalid=object:ThermalMeasurement by base {
            override fun matrix()=base.matrix().apply {this[lastIndex]=Float.NaN}
        }
        assertThrows(IllegalArgumentException::class.java){invalid.roiStatistics(rect)}
    }
}
