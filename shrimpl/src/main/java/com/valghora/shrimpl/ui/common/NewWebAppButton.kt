package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.R

/**
 * The one "New app" button, wherever it appears: a fully rounded filled button in the theme's
 * primary color, with its keyboard shortcut in the tooltip.
 */
@Composable
fun NewWebAppButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    WithTooltip(stringResource(R.string.tooltip_new_web_app), modifier) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 56.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_new_web_app))
        }
    }
}

/**
 * An empty state whose [center] content is exactly centered in the space it fills, with [below]
 * hanging under it without moving it, so empty states in side-by-side panes line up.
 */
@Composable
fun CenteredEmptyState(modifier: Modifier = Modifier, below: @Composable () -> Unit = {}, center: @Composable () -> Unit) {
    val gap = 24.dp
    Layout(
        contents = listOf(center, below),
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
    ) { (centerMeasurables, belowMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val centerPlaceables = centerMeasurables.map { it.measure(loose) }
        val belowPlaceables = belowMeasurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val centerHeight = centerPlaceables.maxOfOrNull { it.height } ?: 0
            val top = (constraints.maxHeight - centerHeight) / 2
            centerPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, top) }
            belowPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, top + centerHeight + gap.roundToPx()) }
        }
    }
}

/** The text of an empty state, centered. */
@Composable
fun EmptyStateText(text: String, title: Boolean = false) {
    StyledEmptyStateText(AnnotatedString(text), title)
}

/** The getting-started line: the pitch, then the tagline in italics. */
@Composable
fun PitchText() {
    val pitch = stringResource(R.string.home_start_text)
    val tagline = stringResource(R.string.home_start_tagline)
    StyledEmptyStateText(
        buildAnnotatedString {
            append(pitch)
            append(' ')
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(tagline) }
        },
        title = false,
    )
}

@Composable
private fun StyledEmptyStateText(text: AnnotatedString, title: Boolean) {
    Text(
        text,
        style = if (title) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
        color = if (title) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
