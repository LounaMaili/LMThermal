package org.lmthermal.lmtr

import org.junit.Assert.*
import org.junit.Test

/** Independent canonical-byte hashes guard precision, UTF-8 and leading-zero hex output. */
class LmtrMetadataDigestTest {
    @Test fun canonicalMetadataHashRetainsExactBytesAndLeadingZeroNibbles() {
        assertEquals("5bff452c5ed93f2e87a23984db5a15050c6477335fdec955b70063bb2d692bf1", LmtrMetadata.hash(LmtrJson.parse("""{"x":0}""".toByteArray(Charsets.UTF_8))))
        assertEquals("9f9007df3f98548d2ab748f9a7f798d5526b70f76fb9e267b8386b4cc1327a04", LmtrMetadata.hash(LmtrJson.parse("""{"x":1.0000000000000000000000000000000000001}""".toByteArray(Charsets.UTF_8))))
        assertEquals("0e765e2b05546690596c120a6dbca2bcb6f40e2db95bddd160be5520e6a0f567", LmtrMetadata.hash(LmtrJson.parse("""{"a":"é","b":"𐀀"}""".toByteArray(Charsets.UTF_8))))
        assertEquals("004aa5b2289f3770c0c346a16342e1fb0fd0b1fce5e64b595729f133bb330846", LmtrMetadata.hash(LmtrJson.parse("""{"iteration":281}""".toByteArray(Charsets.UTF_8))))
    }
}
