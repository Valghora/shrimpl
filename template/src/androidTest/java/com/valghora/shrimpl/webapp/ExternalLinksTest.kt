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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Link routing in the real WebView (docs/TEMPLATE_SPEC.md T2a, T6.6): a page's own domain stays in the
 * app, and any other domain opens in the user's browser instead of inside the web app.
 */
@RunWith(AndroidJUnit4::class)
class ExternalLinksTest {
    @get:Rule
    val intents = IntentsRule()

    @Test
    fun aLinkToAnotherSiteOpensInTheBrowser() {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val page = PageDriver(scenario).also { it.loadTestPage() }
            page.js("window.location.href = 'https://elsewhere.example/page?x=1'; 0")
            awaitUntil("the browser to be asked to open the link", 15_000) {
                Intents.getIntents().any { it.action == Intent.ACTION_VIEW && it.dataString == "https://elsewhere.example/page?x=1" }
            }
            val opened = Intents.getIntents().first { it.action == Intent.ACTION_VIEW }
            assertEquals("https://elsewhere.example/page?x=1", opened.dataString)
            assertEquals(setOf(Intent.CATEGORY_BROWSABLE), opened.categories)
        }
    }
}
