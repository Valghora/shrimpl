package com.valghora.shrimpl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.ui.common.LocalInPane
import com.valghora.shrimpl.ui.common.PageTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R3.5: only a page with one pane collapses its bar as it scrolls. A pane too narrow for the settings
 * column (beside the list across a hinge) has no column either, but its bar stays put in the pane's
 * color (found on a device, 2026-11-04).
 */
@Config(qualifiers = "w420dp-h800dp")
@RunWith(RobolectricTestRunner::class)
class PageTopBarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun page(inPane: Boolean) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalInPane provides inPane) {
                    val scroll = rememberScrollState()
                    Column {
                        PageTopBar(title = "Settings", paneWidth = 380.dp, navigation = {}, scroll = scroll, floatingButton = false)
                        Column(Modifier.verticalScroll(scroll)) {
                            Spacer(Modifier.height(2000.dp))
                            Text("Bottom")
                        }
                    }
                }
            }
        }
    }

    private fun titleSize(): Float {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText("Settings") and isHeading()).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontSize.value
    }

    @Test
    fun inANarrowPaneTheBarStaysPut() {
        page(inPane = true)
        val expanded = titleSize()
        compose.onNodeWithText("Bottom").performScrollTo()
        compose.waitForIdle()
        assertEquals("the title keeps its size", expanded, titleSize())
    }

    @Test
    fun withOnePaneTheBarCollapses() {
        page(inPane = false)
        val expanded = titleSize()
        compose.onNodeWithText("Bottom").performScrollTo()
        compose.waitForIdle()
        assertTrue("the title shrinks", titleSize() < expanded)
    }
}
