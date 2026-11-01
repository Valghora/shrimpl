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
 * (rotation, folding, resizing) and are discarded on leaving the page.
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

    /** The Camera permission switch item. */
    private fun camera() = compose.onNode(hasText(string(R.string.field_camera)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
