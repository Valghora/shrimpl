package com.valghora.shrimpl.webapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pop-up windows in the real WebView (docs/TEMPLATE_SPEC.md T6.6): one the page opens on its own site
 * shows in a dialog at 90 % of the window and closes when the page closes it; one to another site goes to
 * the browser instead, with no dialog. Pages may open windows without a gesture (T6.1), so a script does.
 */
@RunWith(AndroidJUnit4::class)
class PopupsTest {
    @get:Rule
    val intents = IntentsRule()

    /** The popup's WebView in a dialog window, or null while there is none. */
    private fun popupView(): View? = try {
        var found: View? = null
        onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).check { view, _ -> found = view }
        found
    } catch (_: NoMatchingRootException) {
        null
    } catch (_: RuntimeException) {
        // Espresso reports a missing view in an existing root as NoMatchingViewException.
        null
    }

    @Test
    fun aPopupOnTheAppsOwnSiteOpensInADialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val page = PageDriver(scenario).also { it.loadTestPage() }
            page.js("window.__popup = window.open('https://example.com/popup'); 0")
            awaitUntil("the popup's dialog", 15_000) { popupView() != null }

            var windowWidth = 0
            var windowHeight = 0
            scenario.onActivity {
                windowWidth = it.window.decorView.width
                windowHeight = it.window.decorView.height
            }
            val popup = requireNotNull(popupView())
            // The dialog is 90 % of the root, which is the window less its system bar insets.
            assertTrue("popup width ${popup.width} of window $windowWidth", popup.width in (windowWidth * 0.75).toInt()..(windowWidth * 0.92).toInt())
            assertTrue("popup height ${popup.height} of window $windowHeight", popup.height in (windowHeight * 0.6).toInt()..(windowHeight * 0.92).toInt())
            assertFalse("a popup on the app's own site went to the browser", Intents.getIntents().any { it.action == Intent.ACTION_VIEW })

            page.js("window.__popup.close(); 0")
            awaitUntil("the popup's dialog to close", 15_000) { popupView() == null }
        }
    }

    @Test
    fun aPopupToAnotherSiteOpensInTheBrowser() {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val page = PageDriver(scenario).also { it.loadTestPage() }
            page.js("window.open('https://elsewhere.example/popup?x=1'); 0")
            awaitUntil("the browser to be asked to open the popup", 15_000) {
                Intents.getIntents().any { it.action == Intent.ACTION_VIEW && it.dataString == "https://elsewhere.example/popup?x=1" }
            }
            val opened = Intents.getIntents().first { it.action == Intent.ACTION_VIEW }
            assertEquals(setOf(Intent.CATEGORY_BROWSABLE), opened.categories)
            assertTrue("a popup to another site showed a dialog", popupView() == null)
        }
    }
}
