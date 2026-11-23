package com.valghora.shrimpl.webapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valghora.shrimpl.core.AdBlocking
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ad blocking (docs/TEMPLATE_SPEC.md T6.19): with a list in the app's own copy, the page's request for a
 * blocked URL gets the empty 403 and an allowed one goes out. The list is written straight into the app's
 * copy, as Shrimpl would hand it over (docs/SHRIMPL_SPEC.md R15.3), so no network or Shrimpl is needed:
 * the blocked answer comes from the app, and the allowed request only has to be anything but that.
 * Element hiding and ad popups (T6.20) use the same list.
 */
@RunWith(AndroidJUnit4::class)
class AdBlockingTest {
    @get:Rule
    val intents = IntentsRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.noBackupFilesDir, "adblock")

    /**
     * The ad blocker is one per process and builds its filter once, so each test starts it afresh: the
     * fields are cleared through reflection, as withProfile swaps the profile, leaving production code as
     * it is.
     */
    @Before
    @After
    fun forgetTheListAndTheFilter() {
        dir.deleteRecursively()
        for (name in listOf("engine", "hidingScript")) {
            AdBlocker::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
        }
    }

    @Test
    fun aBlockedRequestGetsTheEmpty403AndAnAllowedOneGoesOut() {
        writeList("||example.com/ads/\n/banner-*.png")
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

    private fun writeList(rules: String) {
        dir.mkdirs()
        File(dir, "easylist.txt").writeText("! Title: test\n$rules\n")
        File(dir, "easylist.version").writeText("1")
    }

    /**
     * T6.22: Hide app banners works with Ad blocking Off, applying the app banners list's hiding rules and
     * its style rules, which undo the scroll lock an overlay leaves behind.
     */
    @Test
    fun hideAppBannersHidesTheOverlayAndUndoesItsScrollLock() {
        dir.mkdirs()
        File(dir, "app-banners.txt").writeText(
            "! Title: test\n##.get-app\nexample.com#${'$'}#.upsell-sheet { display: none !important; }\n" +
                "example.com#${'$'}#body.scroll-locked { overflow: auto !important; }\n",
        )
        File(dir, "app-banners.version").writeText("1")
        withProfile(WebApp.profile.copy(adBlocking = AdBlocking.OFF, hideAppBanners = true)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                val page = PageDriver(scenario).also {
                    it.loadTestPage(
                        "<style>.scroll-locked { overflow: hidden; }</style>" +
                            "<div class=\"get-app\" id=\"g\">Get the app</div><div class=\"upsell-sheet\" id=\"u\">Open in app</div>" +
                            "<p id=\"text\">article</p><script>document.body.classList.add('scroll-locked')</script>",
                    )
                }
                fun display(id: String) = page.js("getComputedStyle(document.getElementById('$id')).display")
                awaitUntil("the app banner to be hidden", 20_000) { display("g") == "none" }
                assertEquals("a style rule hides too", "none", display("u"))
                assertEquals("the scroll lock stayed", "auto", page.js("getComputedStyle(document.body).overflowY"))
                assertNotEquals("the page's content was hidden", "none", display("text"))
            }
        }
    }

    @Test
    fun adSlotsAreHiddenAndTheRestOfThePageIsNot() {
        writeList("##.ad-slot\n###sponsor\nexample.com##.house-ad\nexample.com#@#.kept-ad\n##.kept-ad")
        withProfile(WebApp.profile.copy(adBlocking = AdBlocking.ADS)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                val page = PageDriver(scenario).also {
                    it.loadTestPage(
                        "<div class=\"ad-slot\" id=\"a\">ad</div><div id=\"sponsor\">ad</div>" +
                            "<div class=\"house-ad\" id=\"h\">ad</div><div class=\"kept-ad\" id=\"k\">kept</div>" +
                            "<p id=\"text\">article</p>",
                    )
                }
                fun display(id: String) = page.js("getComputedStyle(document.getElementById('$id')).display")
                awaitUntil("the ad slot to be hidden", 20_000) { display("a") == "none" }
                assertEquals("none", display("sponsor"))
                assertEquals("a rule for the app's own domain hides too", "none", display("h"))
                assertNotEquals("the app's domain shows what its exception keeps", "none", display("k"))
                assertNotEquals("the page's content was hidden", "none", display("text"))
            }
        }
    }

    @Test
    fun anAdPopupNeverOpensButAnotherPopupStillDoes() {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        writeList("||popads.example^\$popup\n||example.com/ads/")
        withProfile(WebApp.profile.copy(adBlocking = AdBlocking.ADS)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                val page = PageDriver(scenario).also { it.loadTestPage() }
                // The filter is built off the main thread; a blocked request shows it's ready.
                awaitUntil("the filter to be ready", 20_000) {
                    page.runAsync("const r = await fetch('https://example.com/ads/x', { cache: 'no-store' }); window.__r = String(r.status);") == "403"
                }
                page.js("window.open('https://popads.example/offer'); 0")
                page.js("window.open('https://elsewhere.example/page'); 0")
                awaitUntil("the other popup to reach the browser", 15_000) {
                    Intents.getIntents().any { it.action == Intent.ACTION_VIEW && it.dataString == "https://elsewhere.example/page" }
                }
                assertFalse(
                    "the ad popup reached the browser",
                    Intents.getIntents().any { it.dataString?.startsWith("https://popads.example") == true },
                )
            }
        }
    }
}
