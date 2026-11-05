package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2.3, R11.7: an installed app signed with another key can be edited. Its page warns that saving
 * uninstalls it, and its ⋮ menu offers Fix, then Remove. With [TestApps], that is Example.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class EditOtherKeyTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        TestApps.save()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.awaitHome()
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Example")).performClick()
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { compose.onAllNodes(hasText(string(R.string.edit_other_key))).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() = scenario.close()

    @Test
    fun itsMenuOffersFixThenRemove() {
        compose.onNodeWithContentDescription(string(R.string.action_more)).performClick()
        val items = compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
        assertEquals(listOf(R.string.action_fix, R.string.action_remove).map { string(it) }, items)
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
