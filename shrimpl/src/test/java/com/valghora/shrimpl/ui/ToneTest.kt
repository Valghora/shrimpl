package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.valghora.shrimpl.ui.common.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The status tones (notices, home badges and row buttons) keep one fixed pastel and deep pair each. */
@RunWith(RobolectricTestRunner::class)
class ToneTest {
    @get:Rule
    val compose = createComposeRule()

    private val pairs = mapOf(
        Tone.CAUTION to (Color(0xFF664A00) to Color(0xFFFFF1BF)),
        Tone.ERROR to (Color(0xFF8C1D18) to Color(0xFFFFDAD6)),
        Tone.SUCCESS to (Color(0xFF0F5223) to Color(0xFFD4F5DC)),
    )

    /** Every tone's colors, read under each theme color in turn. */
    private fun seenUnderThemes(): Map<Pair<Color, Tone>, Pair<Color, Color>> {
        val seen = mutableMapOf<Pair<Color, Tone>, Pair<Color, Color>>()
        var primary by mutableStateOf(Color(0xFF6750A4))
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = primary)) {
                pairs.keys.forEach { seen[primary to it] = it.content to it.container }
            }
        }
        for (theme in listOf(Color(0xFF1B6EF3), Color(0xFF386A20), Color(0xFFB3261E))) {
            compose.runOnIdle { primary = theme }
        }
        compose.waitForIdle()
        return seen
    }

    @Test
    fun everyToneKeepsItsColorsOnEveryTheme() {
        val seen = seenUnderThemes()
        assertEquals("four themes, three tones", 12, seen.size)
        seen.forEach { (key, colors) -> assertEquals("${key.second} under ${key.first}", pairs.getValue(key.second), colors) }
    }

    @Test
    fun everyStatusShadeMeetsTheHighContrastTarget() {
        fun contrast(a: Color, b: Color): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
        pairs.forEach { (tone, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue("$tone text on its container is $ratio:1", ratio >= 7f)
        }
    }
}
