package com.valghora.shrimpl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/SHRIMPL_SPEC.md R11.8: major.minor.patch versions as codes that order, earlier forms included. */
class VersionsTest {
    @Test
    fun versionsBecomeCodesAndBack() {
        assertEquals(100, Versions.code("1.0.0"))
        assertEquals(123, Versions.code("1.2.3"))
        assertEquals(199, Versions.code("1.9.9"))
        assertEquals(200, Versions.code("2.0.0"))
        assertEquals("1.0.0", Versions.name(100))
        assertEquals("1.2.3", Versions.name(123))
        assertEquals("2.0.0", Versions.name(200))
    }

    @Test
    fun eachChangeRaisesTheLastPartAndCarriesAtNine() {
        assertEquals("1.1.9 then 1.2.0", Versions.code("1.1.9")!! + 1, Versions.code("1.2.0"))
        assertEquals(Versions.code("1.2.0")!! + 1, Versions.code("1.2.1"))
        assertTrue(Versions.code("1.9.9")!! < Versions.code("2.0.0")!!)
    }

    @Test
    fun minorAndPatchRunZeroToNine() {
        assertNull(Versions.code("1.10.0"))
        assertNull(Versions.code("1.0.10"))
        assertNull(Versions.code("0.5.0"))
        assertNull(Versions.code("1.0"))
        assertNull(Versions.code("1.2.3.4"))
        assertNull(Versions.code("a.b.c"))
        assertNull(Versions.code("1.-1.0"))
    }

    @Test
    fun theCodesAreThoseOfEveryEarlierForm() {
        assertTrue("template 19, the last whole number, is older than 1.0.0", 19 < Versions.code("1.0.0")!!)
        assertEquals("Shrimpl 1.1 under major.minor is 1.0.1", "1.0.1", Versions.name(101))
    }

    @Test
    fun webAppVersionNamesCarryTheTemplateVersion() {
        assertEquals("1.0.0.12", Versions.webAppVersionName(100, 12))
        assertEquals(123, Versions.templateCodeOf("1.2.3.12"))
        assertEquals("a major.minor name", 112, Versions.templateCodeOf("1.12.3"))
        assertEquals("a name from before either", 19, Versions.templateCodeOf("19.12"))
        assertNull(Versions.templateCodeOf("x"))
        assertNull(Versions.templateCodeOf("1.0.0.x"))
        assertNull(Versions.templateCodeOf("1.10.0.3"))
        assertNull(Versions.templateCodeOf("1.200.3"))
        assertNull(Versions.templateCodeOf("0.5.3"))
        assertNull(Versions.templateCodeOf("19.x"))
        assertNull(Versions.templateCodeOf("1.2.3.4.5"))
        assertNull("not an old template number", Versions.templateCodeOf("150.2"))
    }
}
