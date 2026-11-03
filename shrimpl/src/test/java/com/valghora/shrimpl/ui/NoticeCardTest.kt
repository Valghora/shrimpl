package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.common.NoticeCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A dismissable notice offers Dismiss, and also goes when swiped either way. */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class NoticeCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val text = "The site may refuse WebViews"

    private fun show(dismissable: Boolean = true) {
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            MaterialTheme {
                if (shown) NoticeCard(Notice(Notice.Kind.CAUTION, text), onDismiss = if (dismissable) ({ shown = false }) else null)
            }
        }
    }

    @Test
    fun dismissButtonDismisses() {
        show()
        compose.onNodeWithText(string(R.string.action_dismiss)).performClick()
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun swipingLeftDismisses() {
        show()
        compose.onNodeWithText(text).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun swipingRightDismisses() {
        show()
        compose.onNodeWithText(text).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun aNoticeThatCannotBeDismissedOffersNoDismiss() {
        show(dismissable = false)
        compose.onNodeWithText(string(R.string.action_dismiss)).assertDoesNotExist()
    }
}
