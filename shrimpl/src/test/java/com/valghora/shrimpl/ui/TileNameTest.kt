package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2.6: with a font size above the default every tile keeps two lines for its name, and a one-line
 * name sits centred in them (found on a device: it sat on top of a blank line). With [TestApps],
 * Example and Squoosh both fit on one line.
 */
@Config(qualifiers = "w400dp-h900dp", fontScale = 1.3f)
@RunWith(RobolectricTestRunner::class)
class TileNameTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aOneLineNameIsCentredInItsTwoLines() {
        TestApps.save()
        TestApps.app.container.settings.setHomeLayout(HomeLayout.TILES)
        compose.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        compose.waitUntil(LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasStateDescription(string(R.string.status_fix_required))).fetchSemanticsNodes().isNotEmpty()
        }
        val density = Density(TestApps.app)
        val tile = compose.onNode(hasText("Example") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).fetchSemanticsNode().boundsInRoot
        val name = compose.onNode(hasText("Example"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // The name's space: below the icon (16 dp padding, 56 dp icon, 8 dp gap), above the 16 dp padding.
        val slotTop = tile.top + with(density) { (16 + 56 + 8) * this.density }
        val slotBottom = tile.bottom - with(density) { 16 * this.density }
        val above = name.top - slotTop
        val below = slotBottom - name.bottom
        assertTrue("there is space above the name ($above)", above > 1f)
        assertTrue("the space above ($above) matches the space below ($below)", abs(above - below) <= 1f)
        val squoosh = compose.onNode(hasText("Squoosh") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).fetchSemanticsNode().boundsInRoot
        assertEquals("tiles side by side match", tile.height, squoosh.height, 0.5f)
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
