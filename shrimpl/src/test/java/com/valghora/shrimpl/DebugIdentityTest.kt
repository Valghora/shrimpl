package com.valghora.shrimpl

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * docs/SHRIMPL_SPEC.md R12.3: the debug build (the variant these tests run) has its own app ID and
 * name, so it installs beside a release instead of failing to update it.
 */
@RunWith(RobolectricTestRunner::class)
class DebugIdentityTest {
    @Test
    fun theDebugBuildInstallsBesideARelease() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.valghora.shrimpl.debug", BuildConfig.APPLICATION_ID)
        assertEquals("com.valghora.shrimpl.debug", context.packageName)
        assertEquals("Shrimpl Debug", context.getString(R.string.app_name))
    }
}
