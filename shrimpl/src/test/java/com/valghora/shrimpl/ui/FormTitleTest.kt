package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsNode
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
import com.valghora.shrimpl.ui.home.tilesWidth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * R1.1 and R3.5: beside the list, which starts wide enough for four tiles, an app's settings page puts its
 * name over the form's column, starting where the section labels start, and its ⋮ button over the
 * column's end. With one pane (below 840 dp)
 * the column sits 24 dp from the edge to clear the floating button, so the title keeps its place after
 * the back arrow.
 */
@RunWith(RobolectricTestRunner::class)
class FormTitleTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun open(width: Int, height: Int) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp")
        TestApps.save()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.awaitHome()
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Squoosh")).performClick()
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(string(R.string.section_identity))).fetchSemanticsNodes().isNotEmpty() }
    }

    private val density get() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().layoutInfo.density.density

    private fun SemanticsNode.left() = boundsInRoot.left / density

    /** The settings page's title: the topmost "Squoosh" right of [after] dp. */
    private fun title(after: Float) = compose.onAllNodes(hasText("Squoosh"))
        .fetchSemanticsNodes()
        .filter { it.left() > after }
        .minBy { it.boundsInRoot.top }

    private fun assertTitleOverColumn(after: Float) {
        val sections = compose.onNodeWithText(string(R.string.section_identity)).fetchSemanticsNode().left()
        assertEquals("the title starts where the sections do", sections, title(after).left(), 1f)
    }

    @Test
    fun besideTheListTheTitleLinesUpWithTheColumn() {
        open(1280, 800)
        // The list pane starts wide enough for four tiles beside its 16 dp margin. Its ⋮ buttons end
        // 8 dp inside the card (the rows' inset), at most 2 dp of rounding slack past four tiles.
        val listEnd = compose.onNode(hasContentDescription(string(R.string.action_app_options, "Example"))).fetchSemanticsNode()
            .boundsInRoot.right / density
        val fourTiles = 16f + tilesWidth(4).value
        assertTrue("the list ends where four tiles fit ($listEnd)", listEnd in fourTiles - 8f..fourTiles - 6f + 0.5f)
        assertTitleOverColumn(after = listEnd)

        // The ⋮ icon sits over the items' trailing chevrons (32 dp in from the column's end), not at the
        // window's edge.
        val columnEnd = compose.onAllNodes(hasText(string(R.string.field_name))).fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.width }.boundsInRoot.right / density
        val more = compose.onNode(hasContentDescription(string(R.string.action_more))).fetchSemanticsNode().boundsInRoot.center.x / density
        assertEquals("⋮ over the chevrons", columnEnd - 32f, more, 1f)
    }

    /** R3.5: Shrimpl's Settings uses the same column, title and button placement as an app's settings page. */
    @Test
    fun settingsMatchesTheAppSettingsPage() {
        open(1280, 800)
        val appColumn = compose.onNodeWithText(string(R.string.section_identity)).fetchSemanticsNode().left()
        val appMore = compose.onNode(hasContentDescription(string(R.string.action_more))).fetchSemanticsNode().boundsInRoot.center.x / density

        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(string(R.string.settings_appearance))).fetchSemanticsNodes().isNotEmpty() }
        val sections = compose.onNodeWithText(string(R.string.settings_appearance)).fetchSemanticsNode().left()
        assertEquals("the same column as an app's settings", appColumn, sections, 1f)
        val title = compose.onAllNodes(hasText(string(R.string.settings_title))).fetchSemanticsNodes()
            .filter { it.left() > sections - 8f }
            .minBy { it.boundsInRoot.top }
        assertEquals("the title starts where the sections do", sections, title.left(), 1f)
        val close = compose.onNode(hasContentDescription(string(R.string.action_close))).fetchSemanticsNode().boundsInRoot.center.x / density
        assertEquals("Close where the app page's ⋮ is", appMore, close, 1f)
    }
}
