package com.valghora.shrimpl.ui

import android.os.Looper
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.install.Availability
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.install.Installer
import com.valghora.shrimpl.ui.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/** docs/SHRIMPL_SPEC.md R2.2: Uninstall from the app list removes the app from it, unless it didn't go. */
@RunWith(RobolectricTestRunner::class)
class HomeUninstallTest {
    /** An install method whose uninstall ends with [result]. */
    private class FakeInstaller(private val result: InstallResult) : Installer {
        val uninstalled = mutableListOf<String>()
        override val method = InstallMethod.SESSION
        override suspend fun availability() = Availability.AVAILABLE
        override suspend fun install(apk: File) = InstallResult.Success
        override suspend fun uninstall(packageName: String): InstallResult = result.also { uninstalled += packageName }
    }

    private fun appIdsAfterUninstall(result: InstallResult): List<String> {
        TestApps.save()
        val model = HomeViewModel(TestApps.app.container)
        val installer = FakeInstaller(result)
        model.uninstall(TestApps.app, TestApps.example, installer)
        // The store writes off the main thread; wait for the list to settle.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (installer.uninstalled.isNotEmpty() && (result != InstallResult.Success || model.records.value.none { it.appId == TestApps.example.appId })) break
            Thread.sleep(20)
        }
        assertEquals(listOf(TestApps.example.packageName), installer.uninstalled)
        return model.records.value.map { it.appId }.sorted()
    }

    @Test
    fun anUninstalledAppLeavesTheList() {
        assertEquals(listOf(TestApps.squoosh.appId), appIdsAfterUninstall(InstallResult.Success))
    }

    @Test
    fun aCancelledUninstallKeepsTheApp() {
        assertEquals(listOf(TestApps.example.appId, TestApps.squoosh.appId), appIdsAfterUninstall(InstallResult.Cancelled))
    }
}
