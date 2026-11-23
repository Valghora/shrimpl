package com.valghora.shrimpl.install

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** docs/SHRIMPL_SPEC.md R6.1: the shell service takes calls from Shrimpl only. */
@RunWith(RobolectricTestRunner::class)
class ShellServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val service = ShellService(context)

    @Test
    fun shrimplItselfMayCall() {
        service.checkCaller(context.applicationInfo.uid)
    }

    @Test
    fun anotherAppIsRefused() {
        assertThrows(SecurityException::class.java) { service.checkCaller(context.applicationInfo.uid + 1) }
    }

    @Test
    fun theShellItselfIsRefused() {
        assertThrows(SecurityException::class.java) { service.checkCaller(Process.SHELL_UID) }
    }
}
