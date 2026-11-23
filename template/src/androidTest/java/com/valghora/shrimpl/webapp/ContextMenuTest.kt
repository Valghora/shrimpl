package com.valghora.shrimpl.webapp

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The context menu (docs/TEMPLATE_SPEC.md T5.5): a long press on a link opens the app's own menu with
 * App options first and the link's actions after it, App options opens the panel (T5.8), a long press
 * on empty space opens the menu with App options alone, and a long press on plain text opens nothing,
 * so the page keeps it for text selection.
 */
@RunWith(AndroidJUnit4::class)
class ContextMenuTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val page = "<a id=\"link\" href=\"https://example.com/report\" style=\"display:block;padding:24px;font-size:24px\">The report</a>" +
        "<p id=\"text\" style=\"font-size:24px;padding:24px\">Plain words to select</p>" +
        "<div id=\"blank\" style=\"height:240px\"></div>"

    @Test
    fun aLongPressOnALinkOpensTheMenuWithAppOptionsFirst() {
        withProfile(WebApp.profile.copy(nativeContextMenu = true)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                PageDriver(scenario).also { it.loadTestPage(page) }.longPress("#link")
                assertTrue("the menu didn't open", awaitText(context.getString(R.string.menu_copy_link)))
                assertTrue(device.hasObject(By.text(context.getString(R.string.menu_open_link))))
                val options = device.findObject(By.text(context.getString(R.string.app_options)))
                val copy = device.findObject(By.text(context.getString(R.string.menu_copy_link)))
                assertTrue("App options isn't above the link's actions", options.visibleBounds.top < copy.visibleBounds.top)
                // As wide as its longest item, within Material's 280 dp: not stretched to the screen's edge.
                val maxWidth = (280 * context.resources.displayMetrics.density).toInt()
                assertTrue("the menu is ${options.visibleBounds.width()} px wide", options.visibleBounds.width() <= maxWidth)
                options.click()
                assertTrue("App options didn't open the panel", awaitText(context.getString(R.string.action_done)))
                tapText(context.getString(R.string.action_done))
            }
        }
    }

    @Test
    fun aLongPressOnPlainTextOpensNoMenu() {
        withProfile(WebApp.profile.copy(nativeContextMenu = true)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                PageDriver(scenario).also { it.loadTestPage(page) }.longPress("#text")
                assertFalse("a menu opened on plain text", awaitText(context.getString(R.string.app_options), timeoutMs = 3_000))
            }
        }
    }

    @Test
    fun aLongPressOnEmptySpaceOpensTheMenuWithAppOptionsAlone() {
        withProfile(WebApp.profile.copy(nativeContextMenu = true)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                answerPermissionPrompt("^allow$", 5_000)
                PageDriver(scenario).also { it.loadTestPage(page) }.longPress("#blank")
                assertTrue("no menu on empty space", awaitText(context.getString(R.string.app_options)))
                assertFalse("link actions on empty space", device.hasObject(By.text(context.getString(R.string.menu_copy_link))))
                device.pressBack()
            }
        }
    }
}
