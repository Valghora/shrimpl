package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/** The app list (docs/SHRIMPL_SPEC.md R2.2, R11.7), with [TestApps]: Discord installed, Squoosh not. */
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
        val label = string(R.string.home_not_installed)
        compose.onNodeWithText(label).assertExists()
        assertTrue("Discord is above the label", top("Discord") < top(label))
        assertTrue("Squoosh is below the label", top("Squoosh") > top(label))
    }

    @Test
    fun installedAppWithAnotherKeyOffersFixAndOnlyRemove() {
        compose.onNodeWithContentDescription(string(R.string.action_fix_app, "Discord")).assertExists()
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Discord")).performClick()
        assertEquals(listOf(string(R.string.action_remove)), menuItems())
    }

    @Test
    fun uninstalledAppOffersRebuild() {
        compose.onNodeWithContentDescription(string(R.string.action_rebuild_app, "Squoosh")).assertExists()
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
}
