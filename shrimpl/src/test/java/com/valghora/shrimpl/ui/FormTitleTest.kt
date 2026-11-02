package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * R1.1 and R3.5: beside the list, which takes a third of the window, an app's settings page puts its
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

    private val density get() = compose.onNodeWithText(string(R.string.section_identity)).fetchSemanticsNode().layoutInfo.density.density

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
        // The list pane ends about a third of the way across (its ⋮ buttons sit at its end edge).
        val listEnd = compose.onNode(hasContentDescription(string(R.string.action_app_options, "Example"))).fetchSemanticsNode()
            .boundsInRoot.right / density
        assertTrue("the list ends near a third of the window ($listEnd)", listEnd in 1280f / 3 - 48f..1280f / 3)
        assertTitleOverColumn(after = listEnd)

        // The ⋮ icon sits over the items' trailing chevrons (32 dp in from the column's end), not at the
        // window's edge.
        val columnEnd = compose.onAllNodes(hasText(string(R.string.field_name))).fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.width }.boundsInRoot.right / density
        val more = compose.onNode(hasContentDescription(string(R.string.action_more))).fetchSemanticsNode().boundsInRoot.center.x / density
        assertEquals("⋮ over the chevrons", columnEnd - 32f, more, 1f)
    }
}
