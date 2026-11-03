package com.valghora.shrimpl.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shrimpl's corners, one size per role (docs/SHRIMPL_SPEC.md R1.5): tight corners from Material's
 * shape scale, so the app reads as a precise tool. Screens pick a role here, never a number.
 *
 * Invariant: every corner in Shrimpl's UI that isn't fully round comes from this file, directly or
 * through [MaterialShapes] for the Material components that read the theme (dialogs, sheets, chips,
 * tooltips, snackbars).
 */
object AppShapes {
    /** Text fields. */
    val Input: Shape = RoundedCornerShape(4.dp)

    /** Buttons with a container (main actions, row buttons, the search button) and chips. */
    val Control: Shape = RoundedCornerShape(8.dp)

    /** The outside corners of list content: home rows and tiles, settings groups and lone items. */
    val ItemRadius = 8.dp

    /** Where items in a settings group meet. */
    val ItemInnerRadius = 2.dp

    /** A home row or tile, or a settings item on its own. */
    val Item: Shape = RoundedCornerShape(ItemRadius)

    /** Notices (R3.3). */
    val Notice: Shape = RoundedCornerShape(8.dp)

    /** Menus; each item's highlight inside one uses [MenuItem]. */
    val Menu: Shape = RoundedCornerShape(8.dp)

    /** A menu item's press, hover and focus highlight, and other highlights inside a container. */
    val MenuItem: Shape = RoundedCornerShape(4.dp)

    /** Large surfaces: the two-pane cards, bottom sheets and dialogs. */
    val Surface: Shape = RoundedCornerShape(12.dp)

    /** App icons, badges, color swatches and icon-only buttons without a container. */
    val Round: Shape = CircleShape
}

/** Material's shape scale set to [AppShapes], for the components that take their shape from the theme. */
val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
