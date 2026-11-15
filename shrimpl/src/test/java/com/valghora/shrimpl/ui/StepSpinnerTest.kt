package com.valghora.shrimpl.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.valghora.shrimpl.ui.common.StepSpinner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * docs/SHRIMPL_SPEC.md R3.2: a checklist's current step spins with the platform's indeterminate
 * ProgressBar, which animates on the render thread while the search's hidden WebView keeps the main
 * thread busy; the step row, not the view, tells accessibility services its state.
 */
@RunWith(RobolectricTestRunner::class)
class StepSpinnerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun View.progressBars(): List<ProgressBar> = when (this) {
        is ProgressBar -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).progressBars() }
        else -> emptyList()
    }

    @Test
    fun theCurrentStepSpinsOnThePlatformProgressBar() {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            MaterialTheme { StepSpinner() }
        }
        compose.waitForIdle()
        val spinner = root.progressBars().single()
        assertTrue("indeterminate", spinner.isIndeterminate)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, spinner.importantForAccessibility)
    }
}
