package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.discovery.Note

/**
 * Status colors. Info follows the Material theme (the secondary container, like tonal buttons), since
 * it carries no urgency. The others are fixed pairs of a pastel and a deep shade, so a warning reads
 * as one whatever the wallpaper: pastel notifications and buttons with deep text in both themes.
 * Status text on ordinary surfaces (a failure description) is pastel in dark theme and deep in light
 * theme, so it stays readable.
 *
 * Invariant: each deep shade has at least 7:1 contrast on its pastel, Material's high-contrast
 * target, since these shades don't follow the system's contrast setting.
 *
 * Success is harmonized with the theme: its hue turns up to 15 degrees toward the theme's primary
 * color, keeping tone and chroma, so it still reads as green and keeps its contrast. Caution and error
 * stay fixed, since turned toward a purple, blue or red theme caution becomes peach, too close to
 * error's pink to tell apart.
 */
enum class Tone(private val pastel: Color?, private val deep: Color?, private val harmonized: Boolean = false) {
    INFO(null, null),
    CAUTION(Color(0xFFFFF1BF), Color(0xFF664A00)),
    ERROR(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
    SUCCESS(Color(0xFFD4F5DC), Color(0xFF0F5223), harmonized = true),
    ;

    /** The background of a notification or button in this tone. */
    val container: Color @Composable get() = pastel?.let { themed(it) } ?: MaterialTheme.colorScheme.secondaryContainer

    /** Text and icons on [container]. */
    val content: Color @Composable get() = deep?.let { themed(it) } ?: MaterialTheme.colorScheme.onSecondaryContainer

    /** Status text on ordinary surfaces, such as a failure description. */
    val text: Color @Composable get() = (if (isDarkTheme()) pastel else deep)?.let { themed(it) } ?: MaterialTheme.colorScheme.secondary

    @Composable
    private fun themed(color: Color): Color {
        if (!harmonized) return color
        val primary = MaterialTheme.colorScheme.primary
        return remember(color, primary) { Color(MaterialColors.harmonize(color.toArgb(), primary.toArgb())) }
    }
}

/** Whether the app is showing its dark theme (set in Settings or by the system). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** An in-app notification: what kind it is and what it says. */
data class Notice(val kind: Kind, val text: String) {
    /** Each kind has its own icon and tone. */
    enum class Kind(val tone: Tone) {
        INFO(Tone.INFO),
        CAUTION(Tone.CAUTION),
        ERROR(Tone.ERROR),
    }

    companion object {
        fun of(note: Note) = Notice(if (note.level == Note.Level.CAUTION) Kind.CAUTION else Kind.INFO, note.text)
    }
}

/**
 * A notification card: the kind's icon and text, and under the text an optional [action] button
 * (such as Open settings) and, when [onDismiss] is set, Dismiss at the other end of that row, inset
 * as far from the card's end as [action] is from its start. A dismissable card also goes when
 * swiped left or right; the Dismiss button is the way for keyboards, mice and accessibility services.
 */
@Composable
fun NoticeCard(
    notice: Notice,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    if (onDismiss == null) {
        NoticeContent(notice, modifier, null, action)
        return
    }
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {},
        modifier = modifier,
        onDismiss = { onDismiss() },
    ) { NoticeContent(notice, Modifier, onDismiss, action) }
}

@Composable
private fun NoticeContent(notice: Notice, modifier: Modifier, onDismiss: (() -> Unit)?, action: Pair<String, () -> Unit>?) {
    val color = notice.kind.tone.content
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(notice.kind.tone.container)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(notice.kind.icon(), contentDescription = stringResource(notice.kind.label()), tint = color, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column(Modifier.weight(1f).padding(top = 3.dp)) {
            // Paragraphs (split at blank lines) sit closer together than a whole blank line would.
            Column(verticalArrangement = Arrangement.spacedBy(PARAGRAPH_GAP)) {
                notice.text.split("\n\n").forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
            }
            if (action != null || onDismiss != null) {
                // The icon column's width (24 dp icon and 12 dp gap) mirrored at the end.
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, end = if (onDismiss != null) 36.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label, color = color, style = MaterialTheme.typography.labelLarge) } }
                    Spacer(Modifier.weight(1f))
                    onDismiss?.let {
                        TextButton(onClick = it) { Text(stringResource(R.string.action_dismiss), color = color, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

private fun Notice.Kind.icon(): ImageVector = when (this) {
    Notice.Kind.INFO -> Icons.Filled.Info
    Notice.Kind.CAUTION -> Icons.Filled.Warning
    Notice.Kind.ERROR -> ErrorIcon
}

private fun Notice.Kind.label() = when (this) {
    Notice.Kind.INFO -> R.string.notice_info
    Notice.Kind.CAUTION -> R.string.notice_caution
    Notice.Kind.ERROR -> R.string.notice_error
}

/** Material's "error" symbol (an exclamation mark in a circle); material-icons-core doesn't include it. */
private val ErrorIcon: ImageVector = ImageVector.Builder("Error", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        PathParser().parsePathString("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2L11,7h2v6z").toNodes(),
        fill = SolidColor(Color.Black),
    )
    .build()

private val PARAGRAPH_GAP = 8.dp
