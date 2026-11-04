package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.HomeLayout
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

/** The app tiles (docs/SHRIMPL_SPEC.md R2.6), with [TestApps]: Example installed but signed with another key, Squoosh not installed. */
@Config(qualifiers = "w400dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class HomeTilesTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TestApps.save()
        TestApps.app.container.settings.setHomeLayout(HomeLayout.TILES)
        compose.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        // Example shows as installed: its tile says it needs a fix.
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.status_fix_required)) }
    }

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(name: String) = compose.onAllNodes(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        .fetchSemanticsNodes().single().boundsInRoot

    private fun menuItems(): List<String> = compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }

    @Test
    fun tilesSayTheirStatusWithoutRowButtons() {
        compose.onNodeWithText(string(R.string.home_not_installed)).assertExists()
        assertTrue(
            "no ⋮ buttons",
            compose.onAllNodes(hasContentDescription(string(R.string.action_app_options, "Example"))).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "no row buttons",
            compose.onAllNodes(hasContentDescription(string(R.string.action_fix_app, "Example"))).fetchSemanticsNodes().isEmpty(),
        )
        // The uninstalled app follows in the same grid, beside the installed one on a 400 dp phone.
        assertEquals("one line of tiles", bounds("Example").top, bounds("Squoosh").top)
        assertTrue("Squoosh comes after Example", bounds("Squoosh").left > bounds("Example").left)
    }

    @Test
    fun aTileSignedWithAnotherKeyOffersFixAndRemove() {
        compose.onNodeWithText("Example").performTouchInput { longClick() }
        assertEquals(listOf(string(R.string.action_fix), string(R.string.action_remove)), menuItems())
    }

    @Test
    fun anUninstalledTileOffersTheListsActions() {
        compose.onNodeWithText("Squoosh").performTouchInput { longClick() }
        assertEquals(listOf(R.string.action_edit, R.string.action_reinstall, R.string.action_remove).map { string(it) }, menuItems())
    }

    @Test
    fun theSwitchGoesBackToTheListAndIsKept() {
        compose.onNodeWithContentDescription(string(R.string.action_show_list)).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasContentDescription(string(R.string.action_app_options, "Example"))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(string(R.string.action_show_tiles)).assertExists()
        assertEquals(HomeLayout.LIST, TestApps.app.container.settings.homeLayout.value)
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
