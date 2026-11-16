package com.valghora.shrimpl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/SHRIMPL_SPEC.md R6.2: a saved install method reads back, an earlier privileged choice as Shevery. */
class InstallMethodTest {
    @Test
    fun savedMethodsReadBack() {
        InstallMethod.entries.forEach { assertEquals(it, installMethodOf(it.name)) }
    }

    @Test
    fun theEarlierPrivilegedInstallerReadsAsShevery() {
        assertEquals(InstallMethod.SHEVERY, installMethodOf("SHIZUKU"))
    }

    @Test
    fun anUnknownNameIsNone() {
        assertNull(installMethodOf("FLOPPY"))
    }
}
