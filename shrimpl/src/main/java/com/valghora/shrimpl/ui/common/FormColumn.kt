package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.lerp
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
 * beside the list they form the column, with [columnBottom] below it. [scrollState] is shared with the
 * page's [PageTopBar], which collapses as the page scrolls.
 */
@Composable
fun FormColumnLayout(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    floatingButton: Boolean = true,
    fullWidthBottom: Dp = 32.dp,
    columnBottom: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inPane = LocalInPane.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val column = formColumn(maxWidth, inPane, floatingButton)
        val scroll = Modifier.fillMaxSize().verticalScroll(scrollState)
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
 * The form's column in a pane [paneWidth] wide beside the list ([inPane]): at most 680 dp, centred, or,
 * on a page with a [floatingButton] (Save, Create), moved left so the button never covers it. Null with
 * one pane, or a pane too narrow: the form then takes the full width and the button floats over it.
 */
fun formColumn(paneWidth: Dp, inPane: Boolean, floatingButton: Boolean = true): FormColumn? {
    if (!inPane) return null
    val clearance = if (floatingButton) FAB_CLEARANCE else SIDE_MARGIN
    val width = minOf(MAX_COLUMN, paneWidth - clearance - SIDE_MARGIN)
    if (width < MIN_COLUMN) return null
    return FormColumn(minOf((paneWidth - width) / 2, paneWidth - clearance - width), width)
}

/**
 * The top bar of every settings page (R3.5): the back arrow and the page's large [title] on one line,
 * with [actions] at its end.
 *
 * With one pane the arrow sits at the bar's start and the title follows; as [scroll] moves the page,
 * the title shrinks to the usual top bar size and the bar takes the container color, as Material's
 * large top app bar does. Beside the list the bar stays as it is while the page scrolls under it: the
 * arrow's glyph starts where the section labels do, the title follows it, and the last action sits
 * over the items' trailing chevrons ([floatingButton] as for [formColumn]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(
    title: String,
    paneWidth: Dp,
    navigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState? = null,
    floatingButton: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val column = formColumn(paneWidth, LocalInPane.current, floatingButton)
    val density = LocalDensity.current
    // 0 at the top of the page, 1 once it has scrolled by COLLAPSE_DISTANCE (one pane only). Derived, so
    // the bar recomposes only while the title is between sizes, not on every scrolled pixel.
    val distance = with(density) { COLLAPSE_DISTANCE.toPx() }
    val collapsing = column == null && scroll != null
    val collapsed by remember(scroll, distance, collapsing) {
        derivedStateOf { if (collapsing && scroll != null) (scroll.value / distance).coerceIn(0f, 1f) else 0f }
    }
    LargeTitleBar(
        title = title,
        collapsed = collapsed,
        navigation = navigation,
        modifier = modifier,
        start = column?.let { (it.start + SECTION_LABEL_INSET - GLYPH_INSET).coerceAtLeast(0.dp) } ?: BAR_EDGE,
        // A 48 dp button centred over the chevrons, 32 dp in from the column's end, ends 8 dp before it.
        end = column?.let { (paneWidth - (it.start + it.width) + CHEVRON_INSET - BUTTON_HALF).coerceAtLeast(0.dp) } ?: BAR_EDGE,
        actions = actions,
    )
}

/**
 * The large-title bar every Shrimpl page uses (Home and the settings pages, R2.1, R3.5): [navigation]
 * (a back arrow, or Home's glyph), the [title] in large type, and [actions] at the end. [collapsed] runs
 * from 0 (large title on the page's background) to 1 (the usual title size on the container color),
 * as the page scrolls; a bar that stays put passes 0. The bar keeps one height, so only the title
 * moves.
 *
 * Invariant: [navigation] and [actions] don't move as the bar collapses (found on a device, 2026-11-03:
 * a bar that shrank from 72 to 64 dp moved the back arrow up 4 dp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTitleBar(
    title: String,
    collapsed: Float,
    navigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    start: Dp = BAR_EDGE,
    end: Dp = BAR_EDGE,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val typography = MaterialTheme.typography
    Row(
        modifier
            .fillMaxWidth()
            .background(lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainer, collapsed))
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            // One height whether expanded or collapsed: only the title changes size, so the arrow and
            // the actions centred beside it stay where they are as the page scrolls.
            .heightIn(min = BAR_HEIGHT)
            .padding(start = start, end = end),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation()
        Text(
            title,
            style = lerp(typography.headlineLarge, typography.titleLarge, collapsed),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = TITLE_GAP).asHeading(),
        )
        actions()
    }
}

/** How far a list must scroll before a collapsing bar is fully collapsed. */
val COLLAPSE_DISTANCE = 56.dp

/** The bar's height; the large title's 40 dp line fits in it. */
private val BAR_HEIGHT = 64.dp

/** The bar's start and end padding with one pane, as Material's top app bar has. */
private val BAR_EDGE = 4.dp

/** Between the back arrow's button and the title. */
private val TITLE_GAP = 8.dp

/** The section labels sit 4 dp in from the column's edge (SettingsSection). */
private val SECTION_LABEL_INSET = 4.dp

/** An icon button's glyph (24 dp) sits 12 dp inside its 48 dp touch target. */
private val GLYPH_INSET = 12.dp
private val BUTTON_HALF = 24.dp

/** The items' trailing chevrons are centred this far in from the column's end. */
private val CHEVRON_INSET = 32.dp
