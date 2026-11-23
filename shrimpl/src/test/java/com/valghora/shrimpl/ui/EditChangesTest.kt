package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.valghora.shrimpl.MainActivity
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2.3: changes on an app's settings page wait for Save. They survive the window being recreated
 * (rotation, folding, resizing) and are discarded on leaving the page. Save asks before a rebuild.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class EditChangesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        TestApps.save()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.awaitHome()
    }

    @After
    fun tearDown() = scenario.close()

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private val saveShown get() = compose.onAllNodes(hasClickAction() and hasText(string(R.string.action_save))).fetchSemanticsNodes().isNotEmpty()

    private fun openSquoosh() {
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Squoosh")).performClick()
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.field_name)) }
    }

    @Test
    fun changesSurviveRecreationAndAreDiscardedOnLeaving() {
        openSquoosh()
        camera().assertIsOff()
        camera().performScrollTo().performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { saveShown }

        scenario.recreate()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.field_name)) }
        camera().assertIsOn()
        assertTrue("Save still offered after recreation", saveShown)

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.awaitHome()
        openSquoosh()
        camera().assertIsOff()
        assertFalse("nothing left to save", saveShown)
    }

    /**
     * R3.3: Site layout offers Desktop and Mobile only. Squoosh is stored with Auto; on this phone-sized
     * window (420 dp) it shows Mobile, the layout Auto picks here, with nothing to save.
     */
    @Test
    fun siteLayoutShowsThisDevicesLayoutInPlaceOfAuto() {
        openSquoosh()
        compose.onNode(hasText(string(R.string.field_user_agent))).performScrollTo()
        assertTrue(exists(string(R.string.ua_mobile_detail)))
        compose.onNode(hasClickAction() and hasText(string(R.string.ua_mobile))).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.ua_desktop)) }
        assertFalse("Auto is offered", exists("Auto"))
        assertFalse("showing the layout made a change", saveShown)
    }

    /** R3.3, T6.19: Ad blocking is a choice in Behavior, Off by default, its summary naming the level. */
    @Test
    fun adBlockingIsAChoiceThatStartsOff() {
        openSquoosh()
        compose.onNode(hasText(string(R.string.field_ad_blocking))).performScrollTo()
        assertTrue(exists(string(R.string.ad_blocking_off_detail)))
        compose.onNode(hasClickAction() and hasText(string(R.string.ad_blocking_off))).performClick()
        compose.onNode(hasText(string(R.string.ad_blocking_strict))).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.ad_blocking_strict_detail)) }
        assertTrue("the change waits for Save", saveShown)
    }

    /** R3.3, T6.21: Dark mode follows the device at first (light under Robolectric), and turning it on waits for Save. */
    @Test
    fun darkModeStartsAsTheDeviceIs() {
        openSquoosh()
        darkMode().performScrollTo().assertIsOff()
        assertTrue(exists(string(R.string.field_dark_mode_detail)))
        darkMode().performClick()
        darkMode().assertIsOn()
        assertTrue("the change waits for Save", saveShown)
    }

    /** R2.3, R11.10: a change that needs a rebuild asks first; Cancel keeps the change unsaved. */
    @Test
    fun saveAsksBeforeARebuild() {
        openSquoosh()
        camera().performScrollTo().performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { saveShown }
        compose.onNode(hasClickAction() and hasText(string(R.string.action_save))).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.rebuild_title, "Squoosh")) }
        assertTrue(exists(string(R.string.rebuild_text, "Squoosh")))

        compose.onNode(hasClickAction() and hasText(string(R.string.action_cancel))).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { !exists(string(R.string.rebuild_title, "Squoosh")) }
        camera().assertIsOn()
        assertTrue("the change is still unsaved", saveShown)
    }

    /** R2.3: Save appears in the top bar, above the form, once something is changed. */
    @Test
    fun saveAppearsInTheTopBar() {
        openSquoosh()
        assertFalse("no Save before a change", saveShown)
        camera().performScrollTo().performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { saveShown }
        val save = compose.onNode(hasClickAction() and hasText(string(R.string.action_save))).fetchSemanticsNode().boundsInRoot
        // The page title: the topmost visible "Squoosh" (the list behind the page lays out at zero size).
        val title = compose.onAllNodes(hasText("Squoosh")).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0f }.minBy { it.top }
        assertTrue("on the title's line", save.top < title.bottom && save.bottom > title.top)
    }

    /** The Camera permission switch item. */
    private fun darkMode() = compose.onNode(hasText(string(R.string.field_dark_mode)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    private fun camera() = compose.onNode(hasText(string(R.string.field_camera)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
