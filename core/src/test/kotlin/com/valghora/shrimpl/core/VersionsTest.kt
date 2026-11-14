package com.valghora.shrimpl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/SHRIMPL_SPEC.md R11.8: major.minor versions as codes that order, old whole numbers included. */
class VersionsTest {
    @Test
    fun versionsBecomeCodesAndBack() {
        assertEquals(100, Versions.code("1.0"))
        assertEquals(112, Versions.code("1.12"))
        assertEquals(199, Versions.code("1.99"))
        assertEquals(200, Versions.code("2.0"))
        assertEquals("1.0", Versions.name(100))
        assertEquals("1.12", Versions.name(112))
        assertEquals("2.0", Versions.name(200))
    }

    @Test
    fun theMinorVersionRunsZeroTo99() {
        assertNull("no minor 100", Versions.code("1.100"))
        assertNull(Versions.code("0.5"))
        assertNull(Versions.code("1"))
        assertNull(Versions.code("1.2.3"))
        assertNull(Versions.code("a.b"))
        assertNull(Versions.code("1.-1"))
    }

    @Test
    fun everyVersionIsNewerThanTheOldWholeNumbers() {
        assertTrue("template 19, the last before R11.8, is older than 1.0", 19 < Versions.code("1.0")!!)
        assertTrue(Versions.code("1.99")!! < Versions.code("2.0")!!)
        assertTrue("1.10 is after 1.9", Versions.code("1.9")!! < Versions.code("1.10")!!)
    }

    @Test
    fun webAppVersionNamesCarryTheTemplateVersion() {
        assertEquals("1.0.12", Versions.webAppVersionName(100, 12))
        assertEquals(100, Versions.templateCodeOf("1.0.12"))
        assertEquals(112, Versions.templateCodeOf("1.12.3"))
        assertEquals("a name from before R11.8", 19, Versions.templateCodeOf("19.12"))
        assertNull(Versions.templateCodeOf("x"))
        assertNull(Versions.templateCodeOf("1.0.x"))
        assertNull(Versions.templateCodeOf("1.200.3"))
        assertNull(Versions.templateCodeOf("19.x"))
        assertNull(Versions.templateCodeOf("1.2.3.4"))
        assertNull("not an old template number", Versions.templateCodeOf("150.2"))
    }
}
