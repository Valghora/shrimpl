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
 * keeps its state while the panel is open, and Reset to defaults puts the profile's choice back. The
 * Link and image menu switch (T5.5) is in every app's panel and is kept until Reset to defaults.
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

    /** The Link and image menu row (T5.5), which every app's panel has. */
    private fun linkMenuRow(): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.descStartsWith(context.getString(R.string.option_link_menu)).checkable(true)), 10_000)) {
            "the Link and image menu switch isn't on screen"
        }

    @Test
    fun theLinkMenuSwitchIsKeptUntilReset() {
        withProfile(WebApp.profile.copy(uaUserToggle = false)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                PageDriver(scenario).loadTestPage()
                openPanel()
                tapText(context.getString(R.string.action_reset_defaults))
                device.wait(Until.gone(By.text(context.getString(R.string.app_options))), 10_000)

                openPanel()
                assertTrue("Desktop site shows without the toggle", !device.hasObject(By.descStartsWith(context.getString(R.string.option_desktop_site))))
                val profileDefault = linkMenuRow().isChecked
                assertEquals("the switch doesn't start at the profile's setting", WebApp.profile.nativeContextMenu, profileDefault)
                linkMenuRow().click()
                awaitUntil("the switch to change", 10_000) { linkMenuRow().isChecked != profileDefault }
                tapText(context.getString(R.string.action_done))
                device.wait(Until.gone(By.text(context.getString(R.string.app_options))), 10_000)

                openPanel()
                assertEquals("the choice wasn't kept", !profileDefault, linkMenuRow().isChecked)
                tapText(context.getString(R.string.action_reset_defaults))
                device.wait(Until.gone(By.text(context.getString(R.string.app_options))), 10_000)
                openPanel()
                assertEquals("Reset to defaults didn't put the profile's setting back", profileDefault, linkMenuRow().isChecked)
                tapText(context.getString(R.string.action_done))
            }
        }
    }
}
