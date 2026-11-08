package com.valghora.shrimpl.webapp

import androidx.test.core.app.ActivityScenario
import org.junit.rules.ExternalResource

/**
 * One [MainActivity] and one test page for a whole test class (`@ClassRule`): launching the app and waiting
 * for its start page to load took 11 to 20 s of every test, which was most of the template's test time
 * (docs/BUILD_NOTES.md, "Emulator"). Each test calls [page], which reloads the test page only if an earlier
 * test navigated away from it. Tests keep their own state in the page's `window.__r` and the handles they
 * create; they must not depend on another test having run.
 */
class TemplatePageRule : ExternalResource() {
    private var scenario: ActivityScenario<MainActivity>? = null
    private var driver: PageDriver? = null

    override fun before() {
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        driver = PageDriver(launched).also { it.loadTestPage() }
    }

    override fun after() {
        scenario?.close()
    }

    /** The shared page, with the test page showing. */
    fun page(): PageDriver = requireNotNull(driver).also { it.ensureTestPage() }
}
