package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app list (docs/SHRIMPL_SPEC.md R2.2, R11.7), with [TestApps]: Example installed, Squoosh not. */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class HomeListTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TestApps.save()
        compose.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        compose.awaitHome()
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    /** The labels of the open menu's items. */
    private fun menuItems(): List<String> = compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }

    @Test
    fun uninstalledAppsSitBelowANotInstalledLabel() {
        val label = compose.onNode(hasText(string(R.string.home_not_installed)) and isHeading()).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Example is above the label", top("Example") < label)
        assertTrue("Squoosh is below the label", top("Squoosh") > label)
    }

    /** R2.2: each row says its status under the name, as a tile does. */
    @Test
    fun rowsShowTheirStatusUnderTheName() {
        for ((name, status) in listOf("Example" to R.string.status_fix_required, "Squoosh" to R.string.home_not_installed)) {
            val nameNode = compose.onNode(hasText(name), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // The status sharing the name's left edge, just under it; Squoosh's is the row's, not the label above.
            val under = compose.onAllNodes(hasText(string(status)), useUnmergedTree = true).fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .filter { it.left == nameNode.left && it.top >= nameNode.bottom && it.top - nameNode.bottom < nameNode.height }
            assertEquals("$name's status is under its name", 1, under.size)
        }
    }

    @Test
    fun installedAppWithAnotherKeyOffersFixAndOnlyRemove() {
        compose.onNodeWithContentDescription(string(R.string.action_fix_app, "Example")).assertExists()
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Example")).performClick()
        assertEquals(listOf(string(R.string.action_remove)), menuItems())
    }

    /** R2.2, R11.7: signed with another key but not installed, Squoosh is just not installed. */
    @Test
    fun uninstalledAppOffersReinstall() {
        // R2.2: not installed, Squoosh offers Reinstall (replay icon, yellow), whatever key it had.
        compose.onNodeWithContentDescription(string(R.string.action_reinstall_app, "Squoosh")).assertExists()
        compose.onNodeWithContentDescription(string(R.string.action_fix_app, "Squoosh")).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.action_update_app, "Squoosh")).assertDoesNotExist()
    }

    @Test
    fun optionsMenuAndLongPressMenuOfferTheSameActions() {
        val expected = listOf(R.string.action_edit, R.string.action_reinstall, R.string.action_remove).map { string(it) }
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Squoosh")).performClick()
        assertEquals("the ⋮ menu", expected, menuItems())
        // Edit closes the menu (the screen's onEdit does nothing here).
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.onNodeWithText("Squoosh").performTouchInput { longClick() }
        assertEquals("the long-press menu", expected, menuItems())
    }

    /** R2.1: with one pane the large title shrinks to the usual bar size as the list scrolls. */
    @Test
    fun theLargeTitleShrinksAsTheListScrolls() {
        TestApps.saveMany(20)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Fillera")).fetchSemanticsNodes().isNotEmpty() }
        // The title's font size, from its text layout (Robolectric's text heights don't follow it).
        fun titleSize(): Float {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(string(R.string.home_title)) and isHeading()).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            return results.single().layoutInput.style.fontSize.value
        }
        fun settings() = compose.onNode(hasContentDescription(string(R.string.settings_title))).fetchSemanticsNode().boundsInRoot
        val expanded = titleSize()
        val settingsBefore = settings()
        compose.onNode(hasScrollAction()).performScrollToIndex(15)
        compose.waitForIdle()
        assertTrue("the title is smaller once scrolled (${titleSize()} sp < $expanded sp)", titleSize() < expanded - 6f)
        assertEquals("the bar's buttons stay where they were", settingsBefore, settings())
    }
}
