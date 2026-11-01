package com.valghora.shrimpl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.WebAppDraft
import com.valghora.shrimpl.ui.form.ProfileFormFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * docs/SHRIMPL_SPEC.md R3.5: the app settings form is one column of at most 600 dp, centred or moved
 * left so the floating button (192 dp at the right) never covers it, and full width on narrow panes.
 */
// A screen wider than every pane tried, so each pane gets its full width (Robolectric's default is 320 dp).
@Config(qualifiers = "w1600dp-h1200dp")
@RunWith(RobolectricTestRunner::class)
class ProfileFormLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val draft = WebAppDraft.create(
        ProfileForm.from(
            Profile(
                name = "Example",
                appId = "example",
                startUrl = "https://example.com/",
                internalDomains = listOf("example.com"),
                iconBackground = "#3367D6",
            ),
        ),
        brand = null,
        website = null,
    )

    /** The Name item's left and right edges in a pane [pane] wide, in dp. */
    private fun nameItemEdges(pane: Dp): Pair<Float, Float> {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(pane).height(1600.dp)) {
                    ProfileFormFields(draft, onChange = {}, hints = emptyMap(), notes = emptyList(), storedImage = null)
                }
            }
        }
        val bounds = compose.onNodeWithText("Name").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        return bounds.left / density to bounds.right / density
    }

    private fun assertBeside(pane: Dp, expectedWidth: Float) {
        val (left, right) = nameItemEdges(pane)
        assertEquals("column width in a $pane pane", expectedWidth, right - left, 1f)
        assertTrue("column clears the floating button in a $pane pane ($right)", right <= pane.value - FAB_CLEARANCE + 1f)
        assertTrue("column keeps its left margin in a $pane pane ($left)", left >= SIDE_MARGIN - 1f)
    }

    @Test
    fun wideDesktopPaneCentresA600DpColumn() {
        val pane = 1400.dp
        val (left, right) = nameItemEdges(pane)
        assertEquals(600f, right - left, 1f)
        // Room on both sides for the button: exactly centred.
        assertEquals((pane.value - 600f) / 2, left, 1f)
    }

    @Test
    fun mediumPaneShiftsTheColumnLeftToClearTheButton() = assertBeside(900.dp, 600f)

    @Test
    fun narrowerPaneShrinksTheColumnBesideTheButton() = assertBeside(700.dp, 700f - FAB_CLEARANCE - SIDE_MARGIN)

    @Test
    fun phoneWidthUsesTheFullWidth() {
        val (left, right) = nameItemEdges(400.dp)
        assertEquals(16f, left, 1f)
        assertEquals(400f - 32f, right - left, 1f)
    }

    private companion object {
        const val FAB_CLEARANCE = 192f
        const val SIDE_MARGIN = 24f
    }
}
