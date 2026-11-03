package com.valghora.shrimpl.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.ui.common.DESCRIPTION_MAX_WIDTH
import com.valghora.shrimpl.ui.common.SettingsItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R3.3: a settings item's description wraps at a fixed width, clear of the value or switch at its end. */
@Config(qualifiers = "w1280dp-h800dp")
@RunWith(RobolectricTestRunner::class)
class SettingsItemTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aLongDescriptionWrapsAtTheFixedWidth() {
        val description = "Pauses the site while battery saver is enabled. Saves battery, but notifications will not arrive until resuming the app."
        compose.setContent {
            MaterialTheme {
                SettingsItem("Pause in battery saver", Modifier.width(680.dp), summary = description, trailing = { Switch(false, {}) })
            }
        }
        val node = compose.onNodeWithText(description).fetchSemanticsNode()
        val width = node.size.width / node.layoutInfo.density.density
        assertTrue("the description is at most ${DESCRIPTION_MAX_WIDTH.value} dp wide ($width)", width <= DESCRIPTION_MAX_WIDTH.value + 0.5f)
        assertTrue("and it wraps", node.size.height / node.layoutInfo.density.density > 30f)
    }
}
