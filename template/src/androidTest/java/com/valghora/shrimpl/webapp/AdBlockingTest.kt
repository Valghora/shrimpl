package com.valghora.shrimpl.webapp

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valghora.shrimpl.core.AdBlocking
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ad blocking (docs/TEMPLATE_SPEC.md T6.19): with a list in the app's own copy, the page's request for a
 * blocked URL gets the empty 403 and an allowed one goes out. The list is written straight into the app's
 * copy, as Shrimpl would hand it over (docs/SHRIMPL_SPEC.md R15.3), so no network or Shrimpl is needed:
 * the blocked answer comes from the app, and the allowed request only has to be anything but that.
 */
@RunWith(AndroidJUnit4::class)
class AdBlockingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.noBackupFilesDir, "adblock")

    @After
    fun forgetTheList() {
        dir.deleteRecursively()
    }

    @Test
    fun aBlockedRequestGetsTheEmpty403AndAnAllowedOneGoesOut() {
        dir.mkdirs()
        File(dir, "easylist.txt").writeText("! Title: test\n||example.com/ads/\n/banner-*.png\n")
        File(dir, "easylist.version").writeText("1")
        withProfile(WebApp.profile.copy(adBlocking = AdBlocking.ADS)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                val page = PageDriver(scenario).also { it.loadTestPage() }
                // The filter is built off the main thread once the activity starts.
                fun answer(url: String) = page.runAsync(
                    "const r = await fetch('$url', { cache: 'no-store' }); window.__r = r.status + ' ' + r.statusText;",
                )
                awaitUntil("the filter to block", 20_000) { answer("https://example.com/ads/a.js") == "403 Blocked" }
                assertEquals("403 Blocked", answer("https://example.com/img/banner-728.png"))
                assertNotEquals("an allowed request was blocked", "403 Blocked", answer("https://example.com/content/a.js"))
            }
        }
    }
}
