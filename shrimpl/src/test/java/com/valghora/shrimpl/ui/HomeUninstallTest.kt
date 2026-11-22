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

/**
 * docs/SHRIMPL_SPEC.md R2.2: an uninstalled app leaves the list unless the user chose to keep it; one
 * that didn't go stays.
 */
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

    private val both by lazy { listOf(TestApps.example.appId, TestApps.squoosh.appId) }

    /** The apps listed once an uninstall of Example ends with [result], the user having chosen [keep]. */
    private fun appIdsAfter(result: InstallResult, keep: Boolean): List<String> {
        TestApps.save()
        val model = HomeViewModel(TestApps.app.container)
        val installer = FakeInstaller(result)
        model.uninstall(TestApps.app, TestApps.example, keep, installer)
        val removing = result == InstallResult.Success && !keep
        // The view model and the store work off the main looper; run it until the outcome settles.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val ids = model.records.value.map { it.appId }
            if (installer.uninstalled.isNotEmpty() && (!removing || TestApps.example.appId !in ids)) break
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(TestApps.example.packageName), installer.uninstalled)
        return model.records.value.map { it.appId }.sorted()
    }

    @Test
    fun anUninstalledAppLeavesTheList() {
        assertEquals(listOf(TestApps.squoosh.appId), appIdsAfter(InstallResult.Success, keep = false))
    }

    @Test
    fun anAppTheUserKeepsStaysInTheList() {
        assertEquals(both, appIdsAfter(InstallResult.Success, keep = true))
    }

    @Test
    fun aCancelledUninstallKeepsTheApp() {
        assertEquals(both, appIdsAfter(InstallResult.Cancelled, keep = false))
    }
}
