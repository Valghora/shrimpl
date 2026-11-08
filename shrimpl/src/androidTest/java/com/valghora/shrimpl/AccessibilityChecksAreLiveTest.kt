package com.valghora.shrimpl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A control for [AccessibilityChecksTest]: a clickable 10 dp box with no label must fail the checks. Under
 * Robolectric the same checks passed it silently (docs/TESTING.md, "Robolectric"), so without this a green run
 * could mean "the checks didn't run".
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksAreLiveTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anUnlabelledTinyClickableFailsTheChecks() {
        compose.enableAccessibilityChecks()
        compose.setContent { Box(Modifier.size(10.dp).background(Color.Gray).clickable { }) }
        val failure = try {
            compose.onRoot().tryPerformAccessibilityChecks()
            null
        } catch (e: Exception) {
            // The framework reports results as an AccessibilityViewCheckException.
            e
        }
        if (failure == null) fail("an unlabelled 10 dp clickable passed the accessibility checks: they aren't running")
        assertTrue("the failure isn't an accessibility result: $failure", failure!!.message.orEmpty().contains("accessibility results"))
    }
}
