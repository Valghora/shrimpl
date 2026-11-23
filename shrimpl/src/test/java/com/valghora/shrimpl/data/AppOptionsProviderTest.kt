package com.valghora.shrimpl.data

import android.content.Context
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import com.valghora.shrimpl.core.records.AppOptionsSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The App options provider saves for, and answers the live settings of, only a web app Shrimpl built
 * (docs/SHRIMPL_SPEC.md R11.9, R11.10): any other caller is turned down, and other calls get no answer.
 */
@RunWith(RobolectricTestRunner::class)
class AppOptionsProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = Robolectric.setupContentProvider(AppOptionsProvider::class.java)

    @Test
    fun aCallerThatIsntOneOfShrimplsAppsIsTurnedDown() {
        // The test calls as Shrimpl itself, which no record names.
        val answer = provider.call(AppOptionsSync.METHOD, null, bundleOf(AppOptionsSync.KEY_LINK_MENU to true))
        assertEquals(false, answer?.getBoolean(AppOptionsSync.KEY_SAVED))
        assertEquals(emptyList<Any>(), context.packageManager.getPackagesForUid(android.os.Process.myUid()).orEmpty().filter { it.startsWith("com.valghora.shrimpl.app.") })
    }

    @Test
    fun aCallerThatIsntOneOfShrimplsAppsGetsNoSettings() {
        assertNull(provider.call(AppOptionsSync.METHOD_SETTINGS, null, null))
    }

    @Test
    fun anyOtherCallGetsNoAnswer() {
        assertNull(provider.call("something_else", null, bundleOf()))
        assertNull(provider.call(AppOptionsSync.METHOD, null, null))
    }
}
