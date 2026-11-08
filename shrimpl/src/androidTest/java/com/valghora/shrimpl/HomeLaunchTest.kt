package com.valghora.shrimpl

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shrimpl starts on a real Android system (docs/SHRIMPL_SPEC.md R2.1): the real activity, the real
 * Material You colors, the real package manager. Robolectric covers the layout; this is the smoke test
 * that nothing about the app itself, its manifest or its dependencies, breaks at launch.
 */
@RunWith(AndroidJUnit4::class)
class HomeLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsItsTitle() {
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.home_title)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }
}
