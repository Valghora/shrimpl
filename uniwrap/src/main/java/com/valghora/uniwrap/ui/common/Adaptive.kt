package com.valghora.uniwrap.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

// Adaptive building blocks shared by the screens (docs/ADAPTIVE.md): layout decisions come from the
// window size classes, never from hard-coded widths, and icon-only buttons explain themselves on
// hover and long press (desktop Hover_Parity, Tier 2 Hover_States).

/** The window is at least the Medium width class (600 dp): tablets, unfolded foldables, desktop windows. */
@Composable
fun isMediumWidth(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/**
 * An icon button with a tooltip naming its action, shown on hover, long press and keyboard focus.
 * [tooltip] defaults to [label], the icon's content description; pass it to add a shortcut hint
 * ("Settings (Ctrl+,)"). The icon keeps its own content description.
 */
@Composable
fun TooltipIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltip: String = label,
    icon: @Composable () -> Unit,
) {
    WithTooltip(tooltip, modifier) { IconButton(onClick = onClick, enabled = enabled, content = icon) }
}

/** Wraps exactly one icon-only control (a FAB, say) in a plain tooltip above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(tooltip: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = control,
    )
}
