package com.valghora.uniwrap.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.valghora.uniwrap.R
import com.valghora.wrapper.core.discovery.Note

/**
 * Status colors as pairs of a pastel and a deep shade. Dark theme: pastel notifications and buttons
 * with deep text, and pastel status text on ordinary surfaces (a failure description in light red).
 * Light theme: the opposite, deep notifications and buttons with pastel text, and deep status text.
 */
enum class Tone(private val pastel: Color, private val deep: Color) {
    INFO(Color(0xFFD6E6FF), Color(0xFF0B3A7A)),
    CAUTION(Color(0xFFFFF1BF), Color(0xFF6B4E00)),
    ERROR(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
    SUCCESS(Color(0xFFD4F5DC), Color(0xFF0F5223)),
    ;

    /** The background of a notification or button in this tone. */
    val container: Color @Composable get() = if (isDarkTheme()) pastel else deep

    /** Text and icons on [container]. */
    val content: Color @Composable get() = if (isDarkTheme()) deep else pastel

    /** Status text on ordinary surfaces, such as a failure description. */
    val text: Color @Composable get() = if (isDarkTheme()) pastel else deep
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
 * A notification card: the kind's icon and text, an optional [action] button (such as Retry), and a
 * dismiss button when [onDismiss] is set.
 */
@Composable
fun NoticeCard(
    notice: Notice,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    val color = notice.kind.tone.content
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(notice.kind.tone.container)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss != null) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(notice.kind.icon(), contentDescription = stringResource(notice.kind.label()), tint = color, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column(Modifier.weight(1f).padding(top = 3.dp)) {
            Text(notice.text, style = MaterialTheme.typography.bodyMedium, color = color)
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
                    Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss), tint = color, modifier = Modifier.size(20.dp))
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
