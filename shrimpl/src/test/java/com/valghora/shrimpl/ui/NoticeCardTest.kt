package com.valghora.shrimpl.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.common.NoticeCard
import org.junit.Assert.assertEquals
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

    private fun show(dismissable: Boolean = true, note: String = text, action: Pair<String, () -> Unit>? = null, width: Dp = 360.dp) {
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            MaterialTheme {
                if (shown) NoticeCard(Notice(Notice.Kind.CAUTION, note), Modifier.width(width), onDismiss = if (dismissable) ({ shown = false }) else null, action = action)
            }
        }
    }

    private fun dp(px: Float) = px / compose.onNodeWithText(string(R.string.action_dismiss)).fetchSemanticsNode().layoutInfo.density.density

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

    /** A button's touch area, which the layout places; Robolectric's text heights don't follow the font. */
    private fun button(label: String) = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot

    /**
     * Where a button row starts below the text for its 20 dp label to sit 12 dp below it: the label is
     * centered in the button (48 dp on a device; Robolectric's taller text makes it a little taller).
     */
    private fun labelSpace(button: Rect) = 12f - (dp(button.height) - 20f) / 2

    /** R3.3: a short note keeps Dismiss on its line, so the notice stays one line tall. */
    @Test
    fun aShortNoteKeepsDismissOnItsLine() {
        show()
        val note = bounds(text)
        val dismiss = button(string(R.string.action_dismiss))
        assertTrue("Dismiss is beside the text", dismiss.left > note.right)
        assertTrue("on the same line", dismiss.top < note.bottom && dismiss.bottom > note.top)
    }

    /** R3.3: text too long for one line beside Dismiss keeps the width; Dismiss goes under it. */
    @Test
    fun aLongNotePutsDismissUnderTheText() {
        // Robolectric's text is narrow (about 1 dp a character), so this is long enough to wrap there.
        val long = "The site answered with an error, so its name and icon couldn't be read. ".repeat(8).trim()
        show(note = long)
        val note = bounds(long)
        val dismiss = button(string(R.string.action_dismiss))
        assertEquals(labelSpace(dismiss), dp(dismiss.top - note.bottom), 1f)
        assertTrue("Dismiss ends inside the text's end", dismiss.right <= note.right)
    }

    /** R3.3: on a narrow card the action sits under the start of the text, Dismiss at the end of the row. */
    @Test
    fun onANarrowCardTheActionSitsUnderTheStartOfTheText() {
        show(action = "Open settings" to {})
        val note = bounds(text)
        val action = button("Open settings")
        val dismiss = button(string(R.string.action_dismiss))
        assertEquals("under the start of the text", note.left, action.left, 1f)
        assertEquals(labelSpace(action), dp(action.top - note.bottom), 1f)
        assertTrue("Dismiss on the same row, at its end", dismiss.top == action.top && dismiss.left > action.right)
    }

    /** R3.3: from 480 dp the action sits beside Dismiss at the end, 16 dp between their buttons. */
    @Config(qualifiers = "w900dp-h900dp")
    @Test
    fun onAWideCardBothButtonsSitTogetherAtTheEnd() {
        show(action = "Open settings" to {}, width = 600.dp)
        val note = bounds(text)
        val action = button("Open settings")
        val dismiss = button(string(R.string.action_dismiss))
        assertEquals(16f, dp(dismiss.left - action.right), 1f)
        assertTrue("away from the text's start", dp(action.left - note.left) > 200f)
        assertTrue("on Dismiss's row", action.top == dismiss.top)
    }
}
