package com.valghora.shrimpl.webapp

import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Printing with Ctrl+P (docs/TEMPLATE_SPEC.md T6.14), and full screen (T6.13): a page's element goes
 * full screen with the system bars hidden, and Esc leaves it (T5.3).
 */
@RunWith(AndroidJUnit4::class)
class PrintAndFullScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun press(keyCode: Int, meta: Int = 0) {
        val now = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        instrumentation.sendKeySync(KeyEvent(now, now + 50, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    @Test
    fun ctrlPOpensAndroidsPrintScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
            PageDriver(scenario).loadTestPage()
            press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            val printScreen = By.pkg("com.android.printspooler")
            assertTrue("Android's print screen didn't open", device.wait(Until.hasObject(printScreen), 20_000) == true)
            device.pressBack()
            device.wait(Until.gone(printScreen), 10_000)
        }
    }

    @Test
    fun aPageGoesFullScreenAndEscLeavesIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000)
            val page = PageDriver(scenario).also {
                it.loadTestPage("<div id=\"stage\" style=\"width:100%;height:240px;background:#336\"></div>")
            }
            // Full screen needs a user gesture, so the page asks for it from a real tap.
            page.js("document.getElementById('stage').addEventListener('click', e => e.target.requestFullscreen()); 0")
            page.tap("#stage")
            page.awaitJs("document.fullscreenElement !== null", "the page to go full screen", 10_000)

            fun statusBarShown(): Boolean {
                var shown = true
                scenario.onActivity { shown = it.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) ?: true }
                return shown
            }
            awaitUntil("the system bars to hide", 10_000) { !statusBarShown() }

            press(KeyEvent.KEYCODE_ESCAPE)
            page.awaitJs("document.fullscreenElement === null", "Esc to leave full screen", 10_000)
            awaitUntil("the system bars to come back", 10_000) { statusBarShown() }
        }
    }
}
