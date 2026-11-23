package com.valghora.shrimpl.webapp

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The App options panel (docs/TEMPLATE_SPEC.md T5.8): Ctrl+, opens it, and its Desktop site (T6.11)
 * and Context menu (T5.5) switches keep what was chosen. Saving the choice to Shrimpl's edit
 * page (docs/SHRIMPL_SPEC.md R11.9) needs Shrimpl's own build of the app, so it isn't tested here.
 */
@RunWith(AndroidJUnit4::class)
class AppOptionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext

    private fun pressCtrlComma() {
        val now = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA, 0, meta))
        instrumentation.sendKeySync(KeyEvent(now, now + 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_COMMA, 0, meta))
    }

    /** The Desktop site row, which accessibility services hear as one switch (T5.8). */
    private fun desktopSiteRow(): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.descStartsWith(context.getString(R.string.option_desktop_site)).checkable(true)), 10_000)) {
            "the Desktop site switch isn't on screen"
        }

    /**
     * Ctrl+, goes to the focused window, so the press waits until the app's window has focus again: just
     * after the panel closes, the closing dialog still has it and the key is lost (found in CI, 2026-11-22).
     */
    private fun openPanel(scenario: ActivityScenario<MainActivity>) {
        awaitUntil("the app's window to have focus", 10_000) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        pressCtrlComma()
        assertTrue("Ctrl+, didn't open the panel", awaitText(context.getString(R.string.app_options)))
    }

    private fun closePanel() {
        tapText(context.getString(R.string.action_done))
        device.wait(Until.gone(By.text(context.getString(R.string.app_options))), 10_000)
    }

    @Test
    fun ctrlCommaOpensTheDesktopSiteSwitchAndItsChoiceIsKept() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
            PageDriver(scenario).loadTestPage()
            openPanel(scenario)
            val startedOn = desktopSiteRow().isChecked
            desktopSiteRow().click()
            awaitUntil("the switch to change", 10_000) { desktopSiteRow().isChecked != startedOn }
            closePanel()

            openPanel(scenario)
            assertEquals("the choice wasn't kept", !startedOn, desktopSiteRow().isChecked)
            desktopSiteRow().click() // Back as it was, for the next test.
            awaitUntil("the switch to change back", 10_000) { desktopSiteRow().isChecked == startedOn }
            closePanel()
        }
    }

    /** The Context menu row (T5.5), which every app's panel has. */
    private fun linkMenuRow(): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.descStartsWith(context.getString(R.string.option_link_menu)).checkable(true)), 10_000)) {
            "the Context menu switch isn't on screen"
        }

    @Test
    fun theLinkMenuSwitchIsInEveryAppAndItsChoiceIsKept() {
        withProfile(WebApp.profile.copy(uaUserToggle = false)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                PageDriver(scenario).loadTestPage()
                openPanel(scenario)
                assertTrue("Desktop site shows without the toggle", !device.hasObject(By.descStartsWith(context.getString(R.string.option_desktop_site))))
                val startedOn = linkMenuRow().isChecked
                linkMenuRow().click()
                awaitUntil("the switch to change", 10_000) { linkMenuRow().isChecked != startedOn }
                closePanel()

                openPanel(scenario)
                assertEquals("the choice wasn't kept", !startedOn, linkMenuRow().isChecked)
                linkMenuRow().click()
                awaitUntil("the switch to change back", 10_000) { linkMenuRow().isChecked == startedOn }
                closePanel()
            }
        }
    }

    /**
     * T8.4, T5.9: with Run in background off, the page pauses while the app is hidden and runs again when
     * it comes back, whatever the live settings say by then.
     */
    @Test
    fun aPageHiddenWithoutRunInBackgroundRunsAgainWhenShown() {
        withProfile(WebApp.profile.copy(keepAliveWhenHidden = false)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                val page = PageDriver(scenario).also { it.loadTestPage() }
                page.js("window.ticks = 0; setInterval(() => window.ticks++, 50); 0")
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                val before = page.js("window.ticks").toDouble()
                page.awaitJs("window.ticks > ${before + 5}", "the page's timers to run again", 10_000)
            }
        }
    }
}
