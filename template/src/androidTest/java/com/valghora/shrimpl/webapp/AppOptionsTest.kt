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
 * The App options panel (docs/TEMPLATE_SPEC.md T5.8): Ctrl+, opens it, its Desktop site switch (T6.11)
 * keeps its state while the panel is open, and Reset to defaults puts the profile's choice back. With
 * the toggle off in the profile, the panel says the app has no options yet.
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

    private fun openPanel() {
        pressCtrlComma()
        assertTrue("Ctrl+, didn't open the panel", awaitText(context.getString(R.string.app_options)))
    }

    @Test
    fun ctrlCommaOpensTheDesktopSiteSwitchAndResetPutsTheDefaultBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
            PageDriver(scenario).loadTestPage()
            openPanel()
            assertTrue("the panel doesn't name the app", awaitText(WebApp.profile.name))
            // Reset first, so a choice saved by an earlier run doesn't decide the starting state.
            tapText(context.getString(R.string.action_reset_defaults))
            device.wait(Until.gone(By.text(context.getString(R.string.app_options))), 10_000)

            openPanel()
            val startedOn = desktopSiteRow().isChecked
            desktopSiteRow().click()
            awaitUntil("the switch to change", 10_000) { desktopSiteRow().isChecked != startedOn }

            tapText(context.getString(R.string.action_reset_defaults))
            device.wait(Until.gone(By.text(context.getString(R.string.app_options))), 10_000)
            openPanel()
            assertEquals("Reset to defaults didn't put the default back", startedOn, desktopSiteRow().isChecked)
            tapText(context.getString(R.string.action_done))
        }
    }

    @Test
    fun withoutTheToggleThePanelHasNoOptions() {
        withProfile(WebApp.profile.copy(uaUserToggle = false)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                PageDriver(scenario).loadTestPage()
                openPanel()
                assertTrue("the panel didn't say there are no options", awaitText(context.getString(R.string.app_options_none)))
                assertTrue("Reset to defaults shows with nothing to reset", !device.hasObject(By.text(context.getString(R.string.action_reset_defaults))))
                tapText(context.getString(R.string.action_done))
            }
        }
    }
}
