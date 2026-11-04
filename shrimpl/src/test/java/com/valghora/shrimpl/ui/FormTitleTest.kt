package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
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
import com.valghora.shrimpl.ui.home.FULL_TILE_WIDTH
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
 * R1.1 and R3.5: beside the list, an app's settings page puts its
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

    /**
     * The back arrow's glyph (24 dp, centred in its button) starts where the section labels [sections]
     * do, and the large title follows the arrow on the same line.
     */
    private fun assertArrowAndTitleOverColumn(sections: Float, title: String) {
        val back = compose.onNode(hasContentDescription(string(R.string.action_back))).fetchSemanticsNode()
        assertEquals("the arrow's glyph starts where the sections do", sections, back.boundsInRoot.center.x / density - 12f, 1f)
        val heading = compose.onAllNodes(hasText(title)).fetchSemanticsNodes()
            .filter { it.left() > back.left() }
            .minBy { it.boundsInRoot.top }
        assertTrue("the title follows the arrow", heading.left() >= back.boundsInRoot.right / density - 0.5f)
        val arrowMiddle = back.boundsInRoot.center.y
        assertEquals("on the arrow's line", arrowMiddle / density, heading.boundsInRoot.center.y / density, 4f)
    }

    @Test
    fun besideTheListTheTitleLinesUpWithTheColumn() {
        open(1280, 800)
        // The list pane is wide enough for four full-width tiles beside its 16 dp margin. Its ⋮ buttons end
        // 8 dp inside the card (the rows' inset), at most 2 dp of rounding slack past four tiles.
        val listEnd = compose.onNode(hasContentDescription(string(R.string.action_app_options, "Example"))).fetchSemanticsNode()
            .boundsInRoot.right / density
        val fourTiles = 16f + tilesWidth(4, FULL_TILE_WIDTH).value
        assertTrue("the list ends where four tiles fit ($listEnd)", listEnd in fourTiles - 8f..fourTiles - 6f + 0.5f)
        val sections = compose.onNodeWithText(string(R.string.section_identity)).fetchSemanticsNode().left()
        assertTrue("the column is beside the list", sections > listEnd)
        assertArrowAndTitleOverColumn(sections, "Squoosh")

        // The ⋮ icon sits over the items' trailing chevrons (32 dp in from the column's end), not at the
        // window's edge.
        val columnEnd = compose.onAllNodes(hasText(string(R.string.field_name))).fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.width }.boundsInRoot.right / density
        val more = compose.onNode(hasContentDescription(string(R.string.action_more))).fetchSemanticsNode().boundsInRoot.center.x / density
        assertEquals("⋮ over the chevrons", columnEnd - 32f, more, 1f)
    }

    /**
     * R3.5: Shrimpl's Settings has the same bar as an app's settings page, and having no floating
     * button, its column is centred in the detail pane.
     */
    @Test
    fun settingsHasTheSameBarWithItsColumnCentred() {
        open(1280, 800)
        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(string(R.string.settings_appearance))).fetchSemanticsNodes().isNotEmpty() }
        val sections = compose.onNodeWithText(string(R.string.settings_appearance)).fetchSemanticsNode().left()
        assertArrowAndTitleOverColumn(sections, string(R.string.settings_title))

        // The detail pane runs from the list pane's end plus the 24 dp gap to 16 dp from the window's edge.
        val paneStart = 16f + tilesWidth(4, FULL_TILE_WIDTH).value + 2f + 24f
        val paneEnd = 1280f - 16f
        val item = compose.onAllNodes(hasText(string(R.string.theme_mode))).fetchSemanticsNodes().maxBy { it.boundsInRoot.width }
        val itemCenter = item.boundsInRoot.center.x / density
        assertEquals("the column is centred in the pane", (paneStart + paneEnd) / 2, itemCenter, 3f)
    }

    /** R3.5: with one pane the large title shrinks to the usual bar size as the page scrolls; the arrow stays put. */
    @Test
    fun onAPhoneTheTitleShrinksAsThePageScrolls() {
        open(420, 900)
        fun titleHeight() = compose.onAllNodes(hasText("Squoosh")).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.top }.boundsInRoot.height / density
        fun arrow() = compose.onNode(hasContentDescription(string(R.string.action_back))).fetchSemanticsNode().boundsInRoot
        val expanded = titleHeight()
        val arrowBefore = arrow()
        compose.onNodeWithText(string(R.string.field_location)).performScrollTo()
        compose.waitForIdle()
        assertTrue("the title is smaller once scrolled (${titleHeight()} < $expanded)", titleHeight() < expanded - 4f)
        // Found on a device: the bar shrank as it collapsed and moved the arrow up with it.
        assertEquals("the back arrow stays where it was", arrowBefore, arrow())
    }
}
