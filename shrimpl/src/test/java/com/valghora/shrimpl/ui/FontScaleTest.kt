package com.valghora.shrimpl.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import com.valghora.shrimpl.MainActivity
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.ui.TestApps.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * R2.1 and R2.6 at larger font sizes: tiles keep their count across, a name may take a second line
 * instead of being cut short, and the home bar's title never runs under its buttons.
 */
@RunWith(RobolectricTestRunner::class)
class FontScaleTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** A snapped window (849 dp, one pane) with eight apps as tiles. */
    private fun launch() {
        RuntimeEnvironment.setQualifiers("w849dp-h900dp")
        TestApps.save()
        TestApps.saveMany(6)
        TestApps.app.container.settings.setHomeLayout(HomeLayout.TILES)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Snapped, so sharing the screen: one pane.
        scenario!!.onActivity { it.onMultiWindowModeChanged(true, it.resources.configuration) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Fillerf")).fetchSemanticsNodes().isNotEmpty() }
    }

    private val tile = { name: String -> hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) }

    private fun tilesAcross(): Int {
        val names = listOf("Example", "Squoosh") + ('a'..'f').map { "Filler$it" }
        val tops = names.mapNotNull { compose.onAllNodes(tile(it)).fetchSemanticsNodes().map { n -> n.boundsInRoot }.firstOrNull { b -> b.height > 0f }?.top }
        return tops.count { kotlin.math.abs(it - tops.min()) < 1f }
    }

    /** The most lines the name text of [name]'s tile may take. */
    private fun nameLines(name: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodes(hasText(name), useUnmergedTree = true).fetchSemanticsNodes()
            .first { SemanticsActions.GetTextLayoutResult in it.config }
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.maxLines
    }

    private fun checks(lines: Int) {
        launch()
        assertEquals("tiles keep their count across", 6, tilesAcross())
        assertEquals("names may take $lines line(s)", lines, nameLines("Example"))
        val title = compose.onNode(hasText(string(R.string.home_title)) and isHeading()).fetchSemanticsNode().boundsInRoot
        val switch = compose.onNode(hasContentDescription(string(R.string.action_show_list))).fetchSemanticsNode().boundsInRoot
        assertTrue("the title ends before the bar's buttons ($title, $switch)", title.right <= switch.left + 0.5f)
    }

    @Test
    fun atTheUsualFontSizeNamesTakeOneLine() = checks(lines = 1)

    @Config(fontScale = 1.3f)
    @Test
    fun atALargerFontSizeNamesMayWrap() = checks(lines = 2)

    @Config(fontScale = 2.0f)
    @Test
    fun atTheLargestFontSizeNamesMayWrapAndTheBarHolds() = checks(lines = 2)
}
