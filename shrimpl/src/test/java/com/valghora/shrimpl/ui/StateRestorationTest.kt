package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the user was doing survives the activity being recreated (rotation, folding, resizing) and
 * process death (docs/ADAPTIVE.md, Tier 3 Config_Changes; the testing-setup skill: always verify
 * state restoration). Dialogs with a text field can't be tested here yet (docs/TESTING.md, "Robolectric").
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class StateRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    private val tester = StateRestorationTester(compose)

    @Test
    fun anOpenMenuStaysOpen() {
        TestApps.save()
        tester.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        compose.awaitHome()
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Squoosh")).performClick()

        tester.emulateSavedInstanceStateRestore()

        val items = compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
        assertEquals(listOf(R.string.action_reinstall, R.string.action_edit, R.string.action_remove).map { string(it) }, items)
    }
}
