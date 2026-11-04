package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.valghora.shrimpl.MainActivity
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.ui.TestApps.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * R1.1: below Material's expanded width (840 dp) one pane; from it, Material's 412 dp list pane at
 * every width, three tiles across (about 119 dp each, near a phone's 124), and an app's settings at
 * least a phone's width. Resizing the window keeps what's open.
 */
@RunWith(RobolectricTestRunner::class)
class PaneWidthTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private val density get() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().layoutInfo.density.density

    /** Five apps as tiles: Example (installed) first, then the four not installed in name order. */
    private fun launch(width: Int) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h900dp")
        TestApps.save()
        TestApps.saveMany(3)
        TestApps.app.container.settings.setHomeLayout(HomeLayout.TILES)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists("Fillerc") }
    }

    /** How many tiles share the first line. */
    private fun tilesAcross(): Int {
        val top = { name: String -> compose.onAllNodes(hasText(name)).fetchSemanticsNodes().first().boundsInRoot.top }
        val first = top("Example")
        return listOf("Example", "Fillera", "Fillerb", "Fillerc", "Squoosh").count { kotlin.math.abs(top(it) - first) < 1f }
    }

    /** Opens Squoosh's settings and returns the width of its Name item, the settings column's width, in dp. */
    private fun settingsColumn(): Float {
        // Tiles have no ⋮ button; a long press opens the same menu.
        compose.onNodeWithText("Squoosh").performTouchInput { longClick() }
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.field_name)) }
        return compose.onNode(hasText(string(R.string.field_name)) and hasClickAction()).fetchSemanticsNode().boundsInRoot.width / density
    }

    private val twoPanes get() = exists(string(R.string.home_pick_app))

    @Test
    fun belowExpandedThereIsOnePane() {
        launch(839)
        assertFalse("no detail pane beside the list", twoPanes)
    }

    /** The width of [name]'s tile: the node offering its long-press menu. */
    private fun tileWidth(name: String) = compose.onAllNodes(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        .fetchSemanticsNodes().first().boundsInRoot.width / density

    /** Three tiles at about their phone width at [width] dp, and room for an app's settings. */
    private fun threeTilesAndRoomForSettings(width: Int) {
        launch(width)
        assertTrue("two panes", twoPanes)
        assertEquals(3, tilesAcross())
        assertTrue("tiles near their phone width (${tileWidth("Example")} dp)", tileWidth("Example") >= 118f)
        assertTrue("the settings column is at least a phone's width", settingsColumn() >= 360f)
    }

    @Test
    fun anExpandedWindow() = threeTilesAndRoomForSettings(840)

    @Test
    fun theLargestExpandedWindow() = threeTilesAndRoomForSettings(1199)

    @Test
    fun aLargeWindow() = threeTilesAndRoomForSettings(1200)

    @Test
    fun anExtraLargeWindowWithTheSettingsAtTheirMaximum() {
        threeTilesAndRoomForSettings(1920)
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.field_name)) }
        assertEquals(680f, compose.onNode(hasText(string(R.string.field_name)) and hasClickAction()).fetchSemanticsNode().boundsInRoot.width / density, 2f)
    }

    /** Narrowing the window to one pane and widening it again keeps the open settings and their edits. */
    @Test
    fun resizingKeepsTheOpenSettingsAndTheirChanges() {
        launch(1280)
        settingsColumn()
        camera().performScrollTo().performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { saveShown }

        // Narrow: the settings fill the window, with the change kept.
        RuntimeEnvironment.setQualifiers("w600dp-h900dp")
        scenario!!.recreate()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists(string(R.string.field_name)) }
        assertFalse("one pane", twoPanes)
        camera().assertIsOn()

        // Wide again: the list returns beside the same settings, still changed.
        RuntimeEnvironment.setQualifiers("w1280dp-h900dp")
        scenario!!.recreate()
        compose.waitUntil(LOAD_TIMEOUT_MS) { exists("Fillerc") && exists(string(R.string.field_name)) }
        camera().assertIsOn()
        assertTrue("Save still offered", saveShown)
    }

    private fun camera() = compose.onNode(hasText(string(R.string.field_camera)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    private val saveShown get() = compose.onAllNodes(hasClickAction() and hasText(string(R.string.action_save))).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
