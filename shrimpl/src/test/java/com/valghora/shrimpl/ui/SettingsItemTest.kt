package com.valghora.shrimpl.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.ui.common.SettingsItem
import com.valghora.shrimpl.ui.common.SettingsSection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R3.3: every description in a section wraps at one line, 24 dp before the section's widest trailing
 * element, so none runs up against a value or switch.
 */
@Config(qualifiers = "w1280dp-h800dp")
@RunWith(RobolectricTestRunner::class)
class SettingsItemTest {
    @get:Rule
    val compose = createComposeRule()

    // Robolectric's text is narrow (about 1 dp a character), so these are long enough to wrap there.
    private val agent = "Site will request the desktop layout, which some sites only offer to computers. ".repeat(12)
    private val battery = "Pauses the site while battery saver is enabled; notifications wait until the app resumes. ".repeat(12)

    @Test
    fun descriptionsWrapBeforeTheSectionsWidestTrailingElement() {
        compose.setContent {
            MaterialTheme {
                SettingsSection("Behavior", Modifier.width(680.dp)) {
                    // A wide value, as a dropdown shows, and a switch narrower than it.
                    SettingsItem("User agent", summary = agent.trim(), trailing = { Spacer(Modifier.size(160.dp, 24.dp).testTag("value")) })
                    SettingsItem("Pause in battery saver", summary = battery.trim(), trailing = { Switch(false, {}) })
                }
            }
        }
        compose.waitForIdle()
        fun dp(px: Float) = px / compose.onNodeWithTag("value").fetchSemanticsNode().layoutInfo.density.density
        fun right(text: String) = dp(compose.onNodeWithText(text.trim()).fetchSemanticsNode().boundsInRoot.right)
        val line = dp(compose.onNodeWithTag("value").fetchSemanticsNode().boundsInRoot.left) - 24f
        assertTrue("the user agent description ends at the line (${right(agent)}, line $line)", right(agent) <= line + 0.5f)
        assertTrue("so does the one beside the narrower switch (${right(battery)}, line $line)", right(battery) <= line + 0.5f)
        // Lines end on whole words, so each ends within a word of the line, not short of it.
        assertTrue("the user agent description uses the room up to the line", right(agent) > line - 40f)
        assertTrue("so does the battery saver one, which has more room beside its own switch", right(battery) > line - 40f)
    }
}
