package com.valghora.shrimpl.webapp

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valghora.shrimpl.core.DarkMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dark mode (docs/TEMPLATE_SPEC.md T6.21): the page hears the app's theme in `prefers-color-scheme`, a
 * light site gets Dark Reader, and a site with its own dark design is left as it is. How a darkened site
 * looks is confirmed on the owner's devices (docs/TESTING.md).
 */
@RunWith(AndroidJUnit4::class)
class DarkModeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A Dark mode choice an earlier test made in the panel would override the test's profile. */
    @Before
    @After
    fun forgetThePanelsChoice() {
        context.getSharedPreferences("${WebApp.profile.appId}_session", Context.MODE_PRIVATE).edit { remove("dark_mode") }
    }

    private fun withDarkMode(mode: DarkMode, body: String = "", check: (PageDriver) -> Unit) =
        withProfile(WebApp.profile.copy(darkMode = mode)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
                check(PageDriver(scenario).also { it.loadTestPage(body) })
            }
        }

    @Test
    fun darkModeOnTellsThePageAndDarkensALightSite() = withDarkMode(DarkMode.DARK) { page ->
        page.awaitJs("matchMedia('(prefers-color-scheme: dark)').matches", "the page to hear the dark theme")
        page.awaitJs("document.querySelector('style.darkreader') !== null", "Dark Reader to darken the page")
    }

    @Test
    fun aSiteWithItsOwnDarkDesignIsLeftAsItIs() = withDarkMode(
        DarkMode.DARK,
        "<style>html { background-color: rgb(17, 17, 17); color: #eee; }</style>",
    ) { page ->
        page.awaitJs("matchMedia('(prefers-color-scheme: dark)').matches", "the page to hear the dark theme")
        Thread.sleep(1_000) // Dark Reader would have started by now.
        assertEquals("false", page.js("document.querySelector('style.darkreader') !== null"))
        assertEquals("rgb(17, 17, 17)", page.js("getComputedStyle(document.documentElement).backgroundColor"))
    }

    @Test
    fun darkModeOffKeepsTheSiteLight() = withDarkMode(DarkMode.LIGHT) { page ->
        Thread.sleep(1_000)
        assertEquals("false", page.js("matchMedia('(prefers-color-scheme: dark)').matches"))
        assertEquals("false", page.js("document.querySelector('style.darkreader') !== null"))
    }

    /** The page's scripts can't reach Dark Reader: it runs inside the app's script, not as a global. */
    @Test
    fun darkReaderStaysOutOfThePagesGlobals() = withDarkMode(DarkMode.DARK) { page ->
        page.awaitJs("document.querySelector('style.darkreader') !== null", "Dark Reader to darken the page")
        assertEquals("true", page.js("typeof window.DarkReader === 'undefined'"))
    }
}
