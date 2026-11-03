package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The settings column (docs/SHRIMPL_SPEC.md R3.5), shared by every page of settings items: an app's
// settings, the configuration page and Shrimpl's own settings. Beside the list the page is one column,
// with the page title over its section labels and the top bar's last button over its chevrons. One
// definition keeps the pages in step.

/**
 * A scrolling page of settings sections in the settings column. With one pane, or in a pane too narrow
 * for the column, the sections take the full width with a 16 dp margin and [fullWidthBottom] below them;
 * beside the list they form the column, with [columnBottom] below it.
 */
@Composable
fun FormColumnLayout(
    modifier: Modifier = Modifier,
    fullWidthBottom: Dp = 32.dp,
    columnBottom: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inPane = LocalInPane.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val column = formColumn(maxWidth, inPane)
        val scroll = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        if (column == null) {
            Column(scroll.padding(horizontal = 16.dp).padding(bottom = fullWidthBottom), content = content)
        } else {
            // The scroll area fills the pane; the column inside it gets its own width (a width set after
            // fillMaxSize on the same modifier can't shrink it).
            Column(scroll) {
                Column(Modifier.padding(start = column.start, bottom = columnBottom).width(column.width), content = content)
            }
        }
    }
}

/** The widest the column grows (R3.5). */
private val MAX_COLUMN = 680.dp

/** Narrower than this, the page takes the full width instead. */
private val MIN_COLUMN = 360.dp

/** Kept clear at the right for the floating button: its width, its 16 dp margin and a gap. */
private val FAB_CLEARANCE = 192.dp

/** The least space at the left of the column. */
private val SIDE_MARGIN = 24.dp

/** Where the form's column sits in a pane: [start] from the pane's start edge, [width] wide (R3.5). */
data class FormColumn(val start: Dp, val width: Dp)

/**
 * The form's column in a pane [paneWidth] wide beside the list ([inPane]): at most 680 dp, centred, or
 * moved left so the floating button never covers it. Null with one pane, or a pane too narrow: the
 * form then takes the full width and the button floats over it.
 */
fun formColumn(paneWidth: Dp, inPane: Boolean): FormColumn? {
    if (!inPane) return null
    val width = minOf(MAX_COLUMN, paneWidth - FAB_CLEARANCE - SIDE_MARGIN)
    if (width < MIN_COLUMN) return null
    return FormColumn(minOf((paneWidth - width) / 2, paneWidth - FAB_CLEARANCE - width), width)
}

/**
 * A top bar title that starts where the form's section labels do in a pane [paneWidth] wide (R3.5),
 * so the page name lines up with the sections below it; [afterIcon] when a navigation icon comes before it.
 */
@Composable
fun FormTitle(text: String, paneWidth: Dp, afterIcon: Boolean) {
    val titleStart = if (afterIcon) TITLE_AFTER_ICON else TITLE_START
    // The section labels sit 4 dp in from the column's edge (SettingsSection); the title lines up with them.
    val shift = formColumn(paneWidth, LocalInPane.current)?.let { (it.start + SECTION_LABEL_INSET - titleStart).coerceAtLeast(0.dp) } ?: 0.dp
    Text(text, Modifier.padding(start = shift).asHeading(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The top bar's action buttons, moved in from the pane's end so the last icon sits over the items'
 * trailing chevrons, 32 dp in from the form column's end (two panes only).
 */
@Composable
fun FormActions(paneWidth: Dp, content: @Composable RowScope.() -> Unit) {
    val column = formColumn(paneWidth, LocalInPane.current)
    val end = column?.let { (paneWidth - (it.start + it.width) + ACTIONS_INSET).coerceAtLeast(0.dp) } ?: 0.dp
    Row(Modifier.padding(end = end), verticalAlignment = Alignment.CenterVertically, content = content)
}

/**
 * Material's top bar ends its actions 4 dp from the pane's edge and the last button (40 dp) is centred
 * 24 dp in from there; padding by the column's end distance plus 4 dp puts it 32 dp in from the column's end.
 */
private val ACTIONS_INSET = 4.dp

/** Where Material's top bar puts its title in the pane, without and with a navigation icon. */
private val TITLE_START = 16.dp
private val TITLE_AFTER_ICON = 56.dp
private val SECTION_LABEL_INSET = 4.dp
