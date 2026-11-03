package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.valghora.shrimpl.ui.common.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Success turns toward the theme's primary color; caution and error keep their fixed shades. */
@RunWith(RobolectricTestRunner::class)
class ToneTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hue(color: Color) = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }[0]

    private fun tones(primary: Color): Map<String, Color> {
        val seen = mutableMapOf<String, Color>()
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = primary)) {
                seen["success"] = Tone.SUCCESS.content
                seen["successContainer"] = Tone.SUCCESS.container
                seen["caution"] = Tone.CAUTION.content
                seen["error"] = Tone.ERROR.container
            }
        }
        compose.waitForIdle()
        return seen
    }

    @Test
    fun successTurnsTowardABlueThemeButStaysGreen() {
        val fixed = Color(0xFF0F5223)
        val seen = tones(Color(0xFF1B6EF3))
        val turn = hue(seen.getValue("success")) - hue(fixed)
        // Toward blue (a higher hue), by Material's 15 degree limit; HSV hue differs a little from Material's own.
        assertTrue("success turned toward blue ($turn degrees)", turn in 3f..25f)
        assertTrue("its container turned too", hue(seen.getValue("successContainer")) > hue(Color(0xFFD4F5DC)))
        assertTrue("and both still read as green", hue(seen.getValue("success")) in 90f..170f)
    }

    @Test
    fun cautionAndErrorStayFixed() {
        val seen = tones(Color(0xFF1B6EF3))
        assertEquals(Color(0xFF664A00), seen.getValue("caution"))
        assertEquals(Color(0xFFFFDAD6), seen.getValue("error"))
    }

    @Test
    fun everyStatusShadeMeetsTheHighContrastTarget() {
        fun contrast(a: Color, b: Color): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
        val seen = mutableMapOf<String, Pair<Color, Color>>()
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFB3261E))) {
                listOf(Tone.CAUTION, Tone.ERROR, Tone.SUCCESS).forEach { seen[it.name] = it.content to it.container }
            }
        }
        compose.waitForIdle()
        seen.forEach { (tone, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue("$tone text on its container is $ratio:1", ratio >= 7f)
        }
    }
}
