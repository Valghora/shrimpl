package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Two-pane layouts show each pane as a rounded card on the window's surfaceContainer background,
// separated by a 24 dp gap holding a drag handle (or the hinge of a dual-screen or book-posture
// device), as Material 3's list-detail layouts do. With one pane, screens stay edge to edge. docs/ADAPTIVE.md.

/** True inside a [PaneCard]: screens use the pane's color for their top bars. */
val LocalInPane = staticCompositionLocalOf { false }

/** The background behind pane cards: the window and caption bar color. */
@Composable
fun paneBackground(): Color = MaterialTheme.colorScheme.surfaceContainer

/** Which edge of the window a pane is on; it takes that side's window insets and margin. */
enum class PaneSide { START, END }

/**
 * Shows [content] as a rounded pane card when [twoPane], else unchanged. The card takes the window
 * insets on its own side, so the screens inside don't pad for them again.
 */
@Composable
fun PaneCard(twoPane: Boolean, side: PaneSide, content: @Composable () -> Unit) {
    if (!twoPane) {
        content()
        return
    }
    val insets = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Vertical + if (side == PaneSide.START) WindowInsetsSides.Start else WindowInsetsSides.End,
    )
    val margins = when (side) {
        PaneSide.START -> PaddingValues(start = PANE_MARGIN, top = PANE_TOP_MARGIN, bottom = PANE_MARGIN)
        PaneSide.END -> PaddingValues(end = PANE_MARGIN, top = PANE_TOP_MARGIN, bottom = PANE_MARGIN)
    }
    Surface(color = paneBackground(), modifier = Modifier.fillMaxSize()) {
        // Pads for this pane's own edges; the inner edge's insets are consumed, not padded twice.
        Box(Modifier.windowInsetsPadding(insets).consumeWindowInsets(WindowInsets.safeDrawing).padding(margins)) {
            Surface(
                shape = RoundedCornerShape(PANE_CORNER),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                CompositionLocalProvider(LocalInPane provides true, content = content)
            }
        }
    }
}

/** The space between a pane card and the window's edges. */
internal val PANE_MARGIN = 16.dp
private val PANE_TOP_MARGIN = 8.dp
private val PANE_CORNER = 24.dp
