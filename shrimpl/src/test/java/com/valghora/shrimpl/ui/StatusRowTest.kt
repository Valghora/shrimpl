package com.valghora.shrimpl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.ui.common.SettingsItem
import com.valghora.shrimpl.ui.common.StatusButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * docs/SHRIMPL_SPEC.md R14.3, R6.2: a status row (the version row, Request Root, Request Shizuku) keeps its
 * height and its button's size in every state, with any label or the spinner, so nothing moves.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class StatusRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val labels = listOf("Check", "Update", "Retry")

    /** Each state: the description (none, one line) and the button's label, or null for the spinner. */
    private val states = listOf(
        "Not checked yet" to "Check",
        "Checking…" to null,
        "Up to date" to "Check",
        "Update available" to "Update",
        "Downloading…" to null,
        "Update check failed" to "Retry",
        null to "Retry",
    )

    @Test
    fun theRowAndItsButtonKeepTheirSizeInEveryState() {
        var index by mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme {
                val (summary, label) = states[index]
                Box(Modifier.width(420.dp)) {
                    SettingsItem(
                        "Shrimpl 1.0.3",
                        Modifier.testTag("row"),
                        summary = summary,
                        summaryLines = 2,
                        trailing = {
                            Box(Modifier.testTag("button")) {
                                StatusButton(label ?: "", labels, busy = label == null, busyDescription = summary.orEmpty(), container = Color.LightGray, content = Color.Black, onClick = {})
                            }
                        },
                    )
                }
            }
        }
        val sizes = states.indices.map { state ->
            compose.runOnIdle { index = state }
            compose.waitForIdle()
            compose.onNodeWithTag("row").fetchSemanticsNode().size to compose.onNodeWithTag("button").fetchSemanticsNode().size
        }
        val first: Pair<IntSize, IntSize> = sizes.first()
        sizes.forEachIndexed { state, size -> assertEquals("state ${states[state]}", first, size) }
    }
}
