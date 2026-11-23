package com.valghora.shrimpl.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors
import com.valghora.shrimpl.data.BadgeTheme

/** A home status badge's or row button's colors (R2.2): [container] behind its symbol, drawn in [content]. */
data class BadgeColors(val container: Color, val content: Color)

/** The Badge theme setting (R2.5) for the home screen's badges and row buttons. */
val LocalBadgeTheme = staticCompositionLocalOf { BadgeTheme.SHRIMPL }

/**
 * The colors [tone] gives a home status badge or row button under the Badge theme (R2.5):
 * - Shrimpl: the tone's fixed pastel and deep shade, on every theme.
 * - Harmony: both run through Material's harmonize, which turns a hue up to 15 degrees toward the
 *   theme's primary color and keeps its tone, so the pair keeps its 7:1 contrast and still reads as
 *   red, yellow or green.
 * - Mono: the theme's primary container colors, the same for every status; the symbols tell them apart.
 *
 * Notices keep their fixed tones whatever the setting (AGENTS.md, palette rules).
 */
@Composable
fun badgeColors(tone: Tone): BadgeColors {
    val scheme = MaterialTheme.colorScheme
    return when (LocalBadgeTheme.current) {
        BadgeTheme.SHRIMPL -> BadgeColors(tone.container, tone.content)
        BadgeTheme.HARMONY -> {
            val container = tone.container
            val content = tone.content
            val primary = scheme.primary
            remember(container, content, primary) { BadgeColors(harmonize(container, primary), harmonize(content, primary)) }
        }
        BadgeTheme.MONO -> BadgeColors(scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}

private fun harmonize(color: Color, with: Color): Color = Color(MaterialColors.harmonize(color.toArgb(), with.toArgb()))
