package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import com.valghora.shrimpl.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Resolve on click (docs/SHRIMPL_SPEC.md R2.5): on, tapping an app with a pending action does it.
 * With [TestApps], Discord needs a fix and Squoosh a rebuild.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class ResolveOnClickTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings get() = TestApps.app.container.settings

    private fun home(layout: HomeLayout) {
        TestApps.save()
        settings.setResolveOnClick(true)
        settings.setHomeLayout(layout)
        compose.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
    }

    /** The tap label of [name]'s row or tile. */
    private fun tapLabel(name: String): String? = compose.onAllNodes(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        .fetchSemanticsNodes().single().config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label

    @Test
    fun rowsOfferTheirPendingActionOnATap() {
        home(HomeLayout.LIST)
        compose.awaitHome()
        assertEquals(string(R.string.action_fix_app, "Discord"), tapLabel("Discord"))
        assertEquals(string(R.string.action_rebuild_app, "Squoosh"), tapLabel("Squoosh"))
    }

    @Test
    fun tilesOfferTheirPendingActionOnATap() {
        home(HomeLayout.TILES)
        compose.waitUntil(LOAD_TIMEOUT_MS) { compose.onAllNodes(hasText(string(R.string.status_needs_fix))).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(string(R.string.action_fix_app, "Discord"), tapLabel("Discord"))
    }

    @Test
    fun theSwitchIsOffByDefaultAndSaysWhatItDoes() {
        compose.setContent { MaterialTheme { SettingsScreen(inPane = false, onBack = {}) } }
        val switch = compose.onNode(hasText(string(R.string.resolve_on_click)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        switch.performScrollTo().assertIsOff()
        compose.onNodeWithText(string(R.string.resolve_on_click_off_detail)).assertExists()
        switch.performClick()
        switch.assertIsOn()
        compose.onNodeWithText(string(R.string.resolve_on_click_on_detail)).assertExists()
        assertTrue("the choice is saved", settings.resolveOnClick.value)
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
