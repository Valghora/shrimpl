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
import org.junit.Assert.assertTrue
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

    private fun show(dismissable: Boolean = true, note: String = text, action: Pair<String, () -> Unit>? = null) {
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            MaterialTheme {
                if (shown) NoticeCard(Notice(Notice.Kind.CAUTION, note), onDismiss = if (dismissable) ({ shown = false }) else null, action = action)
            }
        }
    }

    private fun bounds(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

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

    /** R3.3: a short note keeps Dismiss on its line, so the notice stays one line tall. */
    @Test
    fun aShortNoteKeepsDismissOnItsLine() {
        show()
        val note = bounds(text)
        val dismiss = bounds(string(R.string.action_dismiss))
        assertTrue("Dismiss is beside the text", dismiss.left > note.right)
        assertTrue("on the same line", dismiss.top < note.bottom && dismiss.bottom > note.top)
    }

    /** R3.3: text too long for one line beside Dismiss keeps the width; Dismiss goes under it. */
    @Test
    fun aLongNotePutsDismissUnderTheText() {
        // Robolectric's text is narrow (about 1 dp a character), so this is long enough to wrap there.
        val long = "The site answered with an error, so its name and icon couldn't be read. ".repeat(8).trim()
        show(note = long)
        assertTrue("Dismiss is under the text", bounds(string(R.string.action_dismiss)).top >= bounds(long).bottom)
    }

    /** R3.3: with another button, both sit in the row under the text, Dismiss at its end. */
    @Test
    fun aNoteWithAnotherButtonPutsBothUnderTheText() {
        show(action = "Open settings" to {})
        val note = bounds(text)
        val dismiss = bounds(string(R.string.action_dismiss))
        val action = bounds("Open settings")
        assertTrue("the action is under the text", action.top >= note.bottom)
        assertTrue("Dismiss is on the action's row, after it", dismiss.top == action.top && dismiss.left > action.right)
    }
}
