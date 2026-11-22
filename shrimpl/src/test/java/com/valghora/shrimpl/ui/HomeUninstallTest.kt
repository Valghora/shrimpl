package com.valghora.shrimpl.ui

import android.os.Looper
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.install.Availability
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.install.Installer
import com.valghora.shrimpl.ui.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * docs/SHRIMPL_SPEC.md R2.2: Uninstall from the app list keeps the app, then offers to remove it from
 * Shrimpl; one that didn't go offers nothing.
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

    private val model by lazy {
        TestApps.save()
        HomeViewModel(TestApps.app.container)
    }

    /** Runs the main looper until [done], since the view model and the store work off it. */
    private fun settle(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun appIds() = model.records.value.map { it.appId }.sorted()

    @Test
    fun anUninstalledAppIsOfferedForRemovalAndRemoveTakesItOff() {
        val installer = FakeInstaller(InstallResult.Success)
        model.uninstall(TestApps.app, TestApps.example, installer)
        settle { model.offerRemove.value != null }
        assertEquals(listOf(TestApps.example.packageName), installer.uninstalled)
        assertEquals(TestApps.example.appId, model.offerRemove.value)
        assertEquals("the app stays until the offer is answered", listOf(TestApps.example.appId, TestApps.squoosh.appId), appIds())

        model.dismissRemoveOffer()
        model.remove(TestApps.example)
        settle { TestApps.example.appId !in appIds() }
        assertEquals(listOf(TestApps.squoosh.appId), appIds())
    }

    @Test
    fun aCancelledUninstallOffersNothing() {
        val installer = FakeInstaller(InstallResult.Cancelled)
        model.uninstall(TestApps.app, TestApps.example, installer)
        settle { installer.uninstalled.isNotEmpty() }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(model.offerRemove.value)
        assertEquals(listOf(TestApps.example.appId, TestApps.squoosh.appId), appIds())
    }
}
