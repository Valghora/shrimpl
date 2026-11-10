package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.discovery.Note
import com.valghora.shrimpl.ui.theme.AppShapes

/**
 * Status colors. Info follows the Material theme (the secondary container, like tonal buttons), since
 * it carries no urgency. The others are fixed pairs of a pastel and a deep shade, so a warning reads
 * as one whatever the wallpaper: pastel notifications and buttons with deep text in both themes.
 * Status text on ordinary surfaces (a failure description) is pastel in dark theme and deep in light
 * theme, so it stays readable.
 *
 * Invariant: each deep shade has at least 7:1 contrast on its pastel, Material's high-contrast
 * target, since these shades don't follow the system's contrast setting. They don't follow the
 * theme either: every status keeps one fixed pair on every wallpaper.
 */
enum class Tone(private val pastel: Color?, private val deep: Color?) {
    INFO(null, null),
    CAUTION(Color(0xFFFFF1BF), Color(0xFF664A00)),
    ERROR(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
    SUCCESS(Color(0xFFD4F5DC), Color(0xFF0F5223)),
    ;

    /** The background of a notification, home status badge or row button in this tone. */
    val container: Color @Composable get() = pastel ?: MaterialTheme.colorScheme.secondaryContainer

    /** Text and icons on [container]. */
    val content: Color @Composable get() = deep ?: MaterialTheme.colorScheme.onSecondaryContainer

    /** Status text on ordinary surfaces, such as a failure description. */
    val text: Color @Composable get() = (if (isDarkTheme()) pastel else deep) ?: MaterialTheme.colorScheme.secondary
}

/** Whether the app is showing its dark theme (set in Settings or by the system). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * An in-app notification: what kind it is and what it says. [openInBrowser] is an address its Open in
 * Chrome button opens (R3.3), for a site that needs a Google sign-in.
 */
data class Notice(val kind: Kind, val text: String, val openInBrowser: String? = null) {
    /** Each kind has its own icon and tone. */
    enum class Kind(val tone: Tone) {
        INFO(Tone.INFO),
        CAUTION(Tone.CAUTION),
        ERROR(Tone.ERROR),
    }

    companion object {
        fun of(note: Note) = Notice(if (note.level == Note.Level.CAUTION) Kind.CAUTION else Kind.INFO, note.text, note.openInBrowser)
    }
}

/**
 * A notification card (docs/SHRIMPL_SPEC.md R3.3): the kind's icon and text, an optional [action]
 * button (such as Open settings), and Dismiss when [onDismiss] is set. A one-paragraph note that fits
 * on one line beside Dismiss keeps it on that line, so short notes stay one line tall; otherwise the
 * buttons sit in a row under the text, 12 dp from it and from the card's bottom, Dismiss inset as far
 * from the card's end as the text is from its start. Below 480 dp [action] sits under the start of
 * the text; from 480 dp it sits beside Dismiss, so the buttons stay together on wide cards. A dismissable card also goes when swiped left or right; the Dismiss
 * button is the way for keyboards, mice and accessibility services.
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
    val button: @Composable (String, () -> Unit) -> Unit = { label, onClick ->
        TextButton(onClick = onClick) { Text(label, color = color, style = MaterialTheme.typography.labelLarge) }
    }
    val dismissLabel = stringResource(R.string.action_dismiss)
    val labelLine = MaterialTheme.typography.labelLarge.lineHeight
    // Paragraphs (split at blank lines) sit closer together than a whole blank line would.
    val paragraphs = notice.text.split("\n\n")
    Layout(
        contents = listOf(
            { Icon(notice.kind.icon(), contentDescription = stringResource(notice.kind.label()), tint = color, modifier = Modifier.size(ICON_SIZE)) },
            {
                Column(verticalArrangement = Arrangement.spacedBy(PARAGRAPH_GAP)) {
                    paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
                }
            },
            { action?.let { (label, onClick) -> button(label, onClick) } },
            { onDismiss?.let { button(dismissLabel, it) } },
            // Both backgrounds are composed; layout draws the one for the notice's shape, decided there.
            { Box(Modifier.background(notice.kind.tone.container, AppShapes.NoticeOneLine)) },
            { Box(Modifier.background(notice.kind.tone.container, AppShapes.Notice)) },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { slots, constraints ->
        val (iconSlot, textSlot, actionSlot, dismissSlot) = slots
        val (pillBackground, roundedBackground) = slots[4].single() to slots[5].single()
        val width = constraints.maxWidth
        val icon = iconSlot.single().measure(Constraints())
        val dismiss = dismissSlot.singleOrNull()?.measure(Constraints())
        val actionButton = actionSlot.singleOrNull()?.measure(Constraints())
        val textStart = SIDE.roundToPx() + icon.width + GAP.roundToPx()
        val text = textSlot.single()
        val inlineRoom = width - textStart - INLINE_END.roundToPx() - (dismiss?.width ?: 0) - GAP.roundToPx()
        val inline = dismiss != null && actionButton == null && paragraphs.size == 1 && text.maxIntrinsicWidth(Constraints.Infinity) <= inlineRoom
        if (inline) {
            // One line: icon, text and Dismiss centered on one row, Dismiss's own padding at the end.
            val placedText = text.measure(Constraints(maxWidth = inlineRoom))
            val inner = maxOf(icon.height, placedText.height, dismiss!!.height)
            val height = inner + INLINE_VERTICAL.roundToPx() * 2
            layout(width, height) {
                pillBackground.measure(Constraints.fixed(width, height)).place(0, 0)
                val mid = height / 2
                icon.place(SIDE.roundToPx(), mid - icon.height / 2)
                placedText.place(textStart, mid - placedText.height / 2)
                dismiss.place(width - INLINE_END.roundToPx() - dismiss.width, mid - dismiss.height / 2)
            }
        } else {
            val textWidth = width - textStart - SIDE.roundToPx()
            val placedText = text.measure(Constraints(maxWidth = textWidth.coerceAtLeast(0)))
            val textTop = VERTICAL.roundToPx() + TEXT_TOP.roundToPx()
            val textBottom = textTop + placedText.height
            val buttonsHeight = maxOf(actionButton?.height ?: 0, dismiss?.height ?: 0)
            // A label sits centered in its button's 48 dp touch area; the spacing is measured from
            // the label, so the touch area overlaps the space around it (and can reach the edge).
            val labelInset = (buttonsHeight - labelLine.roundToPx()) / 2
            val buttonsTop = textBottom + LABEL_SPACE.roundToPx() - labelInset
            val contentBottom = if (buttonsHeight > 0) buttonsTop + labelInset + labelLine.roundToPx() + LABEL_SPACE.roundToPx() else textBottom + VERTICAL.roundToPx()
            val height = maxOf(contentBottom, VERTICAL.roundToPx() * 2 + ICON_TOP.roundToPx() + icon.height)
            // The icon column's width (icon and gap) mirrored at the end.
            val dismissX = width - SIDE.roundToPx() - (icon.width + GAP.roundToPx()) - (dismiss?.width ?: 0)
            // Narrow: the action under the start of the text. Wide: beside Dismiss, so the two stay together.
            val actionX = if (width < WIDE_NOTICE.roundToPx() || dismiss == null) textStart else dismissX - BUTTON_GAP.roundToPx() - (actionButton?.width ?: 0)
            layout(width, height) {
                roundedBackground.measure(Constraints.fixed(width, height)).place(0, 0)
                icon.place(SIDE.roundToPx(), VERTICAL.roundToPx() + ICON_TOP.roundToPx())
                placedText.place(textStart, textTop)
                actionButton?.place(actionX, buttonsTop)
                dismiss?.place(dismissX, buttonsTop)
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
private val ICON_SIZE = 24.dp
private val SIDE = 16.dp
private val GAP = 12.dp
private val VERTICAL = 12.dp
private val ICON_TOP = 2.dp
private val TEXT_TOP = 3.dp

/** The space above and below the button labels in the row under the text. */
private val LABEL_SPACE = 12.dp

/** From this width a notice's buttons sit together at the end, [BUTTON_GAP] apart; narrower, they split. */
private val WIDE_NOTICE = 480.dp
private val BUTTON_GAP = 16.dp

/** On one line: Dismiss's own 12 dp padding plus this puts its label 16 dp from the end, like the icon at the start. */
private val INLINE_END = 4.dp
/** With Dismiss's 48 dp target, makes a one-line notice 56 dp tall. */
private val INLINE_VERTICAL = 4.dp
