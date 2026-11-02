package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.valghora.shrimpl.R
import com.valghora.shrimpl.build.WebAppFactory
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.AppTextField
import com.valghora.shrimpl.ui.common.BuildKind
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.BuildState
import com.valghora.shrimpl.ui.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What accessibility services are told (.claude/skills/accessibility): headings, the state of build
 * steps, rows that only open a menu, the selected row, and a field's error.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private fun home(selected: String? = null) {
        TestApps.save()
        compose.setContent {
            MaterialTheme { HomeScreen(selected = selected, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        compose.awaitHome()
    }

    /** The list row of [name] (its merged node, with the long-press action). */
    private fun row(name: String): SemanticsNode = compose.onAllNodes(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        .fetchSemanticsNodes().single()

    @Test
    fun theListHasHeadings() {
        home()
        compose.onNode(hasText(string(R.string.home_title)) and isHeading()).assertExists()
        compose.onNode(hasText(string(R.string.home_not_installed)) and isHeading()).assertExists()
    }

    @Test
    fun rowsThatOnlyOpenAMenuOfferNoTap() {
        home()
        val showOptions = string(R.string.action_show_options)
        // Squoosh isn't installed; Example is signed with another key (tests have no signing key).
        for (name in listOf("Squoosh", "Example")) {
            val node = row(name)
            assertFalse("$name offers no tap", node.config.contains(SemanticsActions.OnClick))
            assertEquals("$name's long press is named", showOptions, node.config[SemanticsActions.OnLongClick].label)
        }
    }

    @Test
    fun theSelectedRowIsAnnouncedAsSelected() {
        home(selected = TestApps.squoosh.appId)
        assertEquals(true, row("Squoosh").config.getOrNull(SemanticsProperties.Selected))
        assertEquals(null, row("Example").config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun buildStepsSayTheirState() {
        compose.setContent {
            MaterialTheme {
                BuildSheet(
                    state = BuildState.Running(WebAppFactory.Stage.SIGNING, "Example", BuildKind.CREATE),
                    onCancel = {},
                    onShowInstaller = {},
                    onRetry = {},
                    onOpen = {},
                    onDone = {},
                )
            }
        }
        fun state(label: Int) = compose.onNode(hasText(string(label))).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(string(R.string.step_done), state(R.string.stage_building))
        assertEquals(string(R.string.step_in_progress), state(R.string.stage_signing))
        assertEquals(string(R.string.step_not_started), state(R.string.stage_installing))
        val signing = compose.onNode(hasText(string(R.string.stage_signing))).fetchSemanticsNode()
        assertTrue("a step change is announced", signing.config.contains(SemanticsProperties.LiveRegion))
    }

    @Test
    fun aFieldsErrorIsPartOfTheField() {
        compose.setContent {
            MaterialTheme { AppTextField(value = "x", onValueChange = {}, label = "Website", isError = true, supportingText = "Not a web address") }
        }
        assertEquals("Not a web address", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.Error])
    }
}
