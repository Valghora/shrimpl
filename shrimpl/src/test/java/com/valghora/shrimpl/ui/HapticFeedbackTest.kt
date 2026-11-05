package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import com.valghora.shrimpl.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Haptic feedback (docs/SHRIMPL_SPEC.md R2.1, R2.5): a long press that opens an app's menu vibrates,
 * unless the switch is off. With [TestApps], Example is installed and needs a fix.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class HapticFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings get() = TestApps.app.container.settings

    /** The haptic types played, in order. */
    private val played = mutableListOf<HapticFeedbackType>()

    private fun home(haptics: Boolean, resolveOnTap: Boolean = false) {
        TestApps.save()
        settings.setHapticFeedback(haptics)
        settings.setResolveOnTap(resolveOnTap)
        val recorder = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                played += hapticFeedbackType
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
            }
        }
        compose.awaitHome()
    }

    private fun longPressExample() {
        compose.onNode(hasText("Example") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    /** Example, signed with another key, does nothing on a tap: its long press is a plain gesture. */
    @Test
    fun aLongPressVibratesWhenOn() {
        home(haptics = true)
        longPressExample()
        assertEquals(listOf(HapticFeedbackType.LongPress), played)
    }

    /** With Resolve on tap, Example's tap fixes it, so the long press is the clickable's own. */
    @Test
    fun aLongPressOnATappableAppVibratesOnce() {
        home(haptics = true, resolveOnTap = true)
        longPressExample()
        assertEquals(listOf(HapticFeedbackType.LongPress), played)
    }

    @Test
    fun aLongPressIsSilentWhenOff() {
        home(haptics = false)
        longPressExample()
        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun aLongPressOnATappableAppIsSilentWhenOff() {
        home(haptics = false, resolveOnTap = true)
        longPressExample()
        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun theSwitchIsOnByDefaultAndSaved() {
        compose.setContent { MaterialTheme { SettingsScreen(inPane = false, onBack = {}) } }
        val switch = compose.onNode(hasText(string(R.string.haptic_feedback)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        switch.performScrollTo().assertIsOn()
        switch.performClick()
        switch.assertIsOff()
        assertFalse("the choice is saved", settings.hapticFeedback.value)
    }
}
