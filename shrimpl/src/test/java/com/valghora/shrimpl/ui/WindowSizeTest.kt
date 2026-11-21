package com.valghora.shrimpl.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.valghora.shrimpl.MainActivity
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The whole app (MainActivity) in the testing-setup skill's nine window sizes: widths 400, 610 and
 * 900 dp (compact, medium, expanded) by heights 400, 500 and 1000 dp, each filling the screen.
 * Expanded widths show the list and the detail side by side (R1.1); the others show
 * one pane at a time with a back arrow. The window size comes from Robolectric's screen qualifiers, not
 * DeviceConfigurationOverride.ForcedSize, which doesn't change the window size class the panes
 * follow (docs/SKILLS.md).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WindowSizeTest(private val width: Int, private val height: Int) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val twoPanes get() = width >= EXPANDED_WIDTH

    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp")
        TestApps.save()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.awaitHome()
    }

    @After
    fun tearDown() = scenario.close()

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun existsDescribed(description: String) =
        compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()

    private val listShown get() = existsDescribed(string(R.string.action_app_options, "Example"))
    private val placeholderShown get() = exists(string(R.string.home_pick_app))
    private val editShown get() = exists(string(R.string.field_name))
    private val backArrowShown get() = existsDescribed(string(R.string.action_back))

    @Test
    fun panesFollowTheWidthAndNewAppStaysInTheWindow() {
        assertTrue("the app list shows", listShown)
        assertEquals("the empty detail pane shows beside the list", twoPanes, placeholderShown)

        // One New app button: the list's, or the empty detail pane's while the list's is hidden.
        val newApp = compose.onNode(hasClickAction() and hasText(string(R.string.action_new_web_app))).fetchSemanticsNode()
        // The window's root; a focused button's tooltip can be a second, smaller one.
        val root = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.size.width }
        val window = root.boundsInRoot
        val button = newApp.boundsInRoot
        assertTrue("New app lies inside the ${width}x$height window ($button in $window)", window.contains(button.topLeft) && window.contains(button.bottomRight - SLACK))
        assertTrue("New app is at least 48 dp tall", button.height / root.layoutInfo.density.density >= 48f - 0.5f)
    }

    @Test
    fun editOpensBesideOrInsteadOfTheListAndBackCloses() {
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Squoosh")).performClick()
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { editShown }

        assertEquals("the list stays beside the app's settings", twoPanes, listShown)
        assertTrue("a back arrow in every layout (beside the list it closes the pane)", backArrowShown)

        // Back closes the app's settings, in two panes too (Shrimpl stays open: R1.1).
        scenario.onActivity { activity ->
            assertTrue("Back is handled in the app", activity.onBackPressedDispatcher.hasEnabledCallbacks())
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertTrue("Back closes the app's settings", !editShown)
        assertTrue("Back returns to the list", listShown)
        assertEquals("the empty detail pane returns beside the list", twoPanes, placeholderShown)
    }

    companion object {
        /** Filling the screen, Shrimpl splits at Material's 840 dp (a window sharing it waits for 1000, PaneWidthTest). */
        private const val EXPANDED_WIDTH = 840
        private const val LOAD_TIMEOUT_MS = 5_000L
        private val SLACK = Offset(0.5f, 0.5f)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1}dp")
        fun sizes(): List<Array<Any>> = listOf(400, 610, 900).flatMap { w -> listOf(400, 500, 1000).map { h -> arrayOf(w, h) } }
    }
}
