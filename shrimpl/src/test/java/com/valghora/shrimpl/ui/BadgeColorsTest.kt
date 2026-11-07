package com.valghora.shrimpl.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.android.material.color.MaterialColors
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.BadgeTheme
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.BadgeColors
import com.valghora.shrimpl.ui.common.LocalBadgeTheme
import com.valghora.shrimpl.ui.common.Tone
import com.valghora.shrimpl.ui.common.badgeColors
import com.valghora.shrimpl.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Badge theme setting (docs/SHRIMPL_SPEC.md R2.5): home badge and row button colors. */
@RunWith(RobolectricTestRunner::class)
class BadgeColorsTest {
    @get:Rule
    val compose = createComposeRule()

    private val tones = listOf(Tone.ERROR, Tone.CAUTION, Tone.SUCCESS)

    /** Wallpaper-like primaries (blue, terracotta, purple) with their container colors, light and dark. */
    private val schemes = listOf(
        lightColorScheme(primary = Color(0xFF3F5F90), primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF254777)),
        darkColorScheme(primary = Color(0xFFA9C7FF), primaryContainer = Color(0xFF254777), onPrimaryContainer = Color(0xFFD6E3FF)),
        lightColorScheme(primary = Color(0xFF8F4C38), primaryContainer = Color(0xFFFFDBD1), onPrimaryContainer = Color(0xFF723523)),
        darkColorScheme(primary = Color(0xFF6E5091), primaryContainer = Color(0xFF553878), onPrimaryContainer = Color(0xFFF0DBFF)),
    )

    /** Each tone's badge colors under [theme], read under every scheme in turn. */
    private fun colorsUnder(theme: BadgeTheme): List<Triple<ColorScheme, Tone, BadgeColors>> {
        val seen = mutableListOf<Triple<ColorScheme, Tone, BadgeColors>>()
        compose.setContent {
            CompositionLocalProvider(LocalBadgeTheme provides theme) {
                schemes.forEach { scheme ->
                    MaterialTheme(colorScheme = scheme) { tones.forEach { seen += Triple(scheme, it, badgeColors(it)) } }
                }
            }
        }
        compose.waitForIdle()
        return seen
    }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private val fixed = mapOf(
        Tone.ERROR to BadgeColors(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
        Tone.CAUTION to BadgeColors(Color(0xFFFFF1BF), Color(0xFF664A00)),
        Tone.SUCCESS to BadgeColors(Color(0xFFD4F5DC), Color(0xFF0F5223)),
    )

    @Test
    fun shrimplKeepsTheFixedPastels() {
        colorsUnder(BadgeTheme.SHRIMPL).forEach { (_, tone, colors) -> assertEquals(tone.name, fixed.getValue(tone), colors) }
    }

    /** Harmony is Material's harmonize of both shades with the theme's primary color, and keeps 7:1. */
    @Test
    fun harmonyHarmonizesWithThePrimaryAndKeepsItsContrast() {
        val harmony = colorsUnder(BadgeTheme.HARMONY)
        harmony.forEach { (scheme, tone, colors) ->
            val base = fixed.getValue(tone)
            val expected = BadgeColors(
                Color(MaterialColors.harmonize(base.container.toArgb(), scheme.primary.toArgb())),
                Color(MaterialColors.harmonize(base.content.toArgb(), scheme.primary.toArgb())),
            )
            assertEquals("$tone under ${scheme.primary}", expected, colors)
            val ratio = contrast(colors.container, colors.content)
            assertTrue("$tone under ${scheme.primary} is $ratio:1", ratio >= 6.9f)
        }
        assertTrue("Harmony changes some colors", harmony.any { (_, tone, colors) -> colors != fixed.getValue(tone) })
    }

    @Test
    fun monoUsesThePrimaryContainerColorsForEveryStatus() {
        colorsUnder(BadgeTheme.MONO).forEach { (scheme, tone, colors) ->
            assertEquals(tone.name, BadgeColors(scheme.primaryContainer, scheme.onPrimaryContainer), colors)
        }
    }

    @Test
    fun theSettingIsShrimplByDefaultAndSaysWhatItDoes() {
        compose.setContent { MaterialTheme { SettingsScreen(inPane = false, onBack = {}) } }
        compose.onNodeWithText(string(R.string.badge_theme)).performScrollTo()
        compose.onNodeWithText(string(R.string.badge_theme_shrimpl_detail)).assertExists()
        assertEquals(BadgeTheme.SHRIMPL, TestApps.app.container.settings.badgeTheme.value)
        assertNotEquals(0, compose.onAllNodes(hasText(string(R.string.badge_theme_shrimpl))).fetchSemanticsNodes().size)
    }
}
