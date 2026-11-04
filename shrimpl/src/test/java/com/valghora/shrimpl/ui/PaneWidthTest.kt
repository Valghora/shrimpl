package com.valghora.shrimpl.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.valghora.shrimpl.MainActivity
import com.valghora.shrimpl.data.HomeLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** R1.1: with two panes the list pane fits exactly four tiles across, whatever the window's width. */
@RunWith(RobolectricTestRunner::class)
class PaneWidthTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** The top of [name]'s tile, in px. */
    private fun top(name: String) = compose.onAllNodes(hasText(name)).fetchSemanticsNodes().first().boundsInRoot.top

    private fun fourTilesAcross(width: Int) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h900dp")
        // Five apps: Example (installed) first, then the four not installed in name order.
        TestApps.save()
        TestApps.saveMany(3)
        TestApps.app.container.settings.setHomeLayout(HomeLayout.TILES)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Fillerc")).fetchSemanticsNodes().isNotEmpty() }
        val first = top("Example")
        listOf("Fillera", "Fillerb", "Fillerc").forEach { assertEquals("$it is on the first line at $width dp", first, top(it), 1f) }
        assertTrue("the fifth tile wraps at $width dp", top("Squoosh") > first)
    }

    @Test
    fun fourTilesFitOnALaptopWindow() = fourTilesAcross(1280)

    @Test
    fun theListStaysFourTilesWideOnAWideWindow() = fourTilesAcross(1920)
}
