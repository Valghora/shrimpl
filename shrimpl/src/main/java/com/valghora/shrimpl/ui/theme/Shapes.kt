package com.valghora.shrimpl.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shrimpl's corners, one size per role (docs/SHRIMPL_SPEC.md R1.5): rounded, from Material's shape
 * scale, with actions and short status fully round and the two-pane cards tighter so the desktop
 * layout stays crisp. Screens pick a role here, never a number.
 *
 * Invariant: every corner in Shrimpl's UI that isn't fully round comes from this file, directly or
 * through [MaterialShapes] for the Material components that read the theme (dialogs, sheets, chips,
 * tooltips, snackbars).
 */
object AppShapes {
    /** Text fields. */
    val Input: Shape = RoundedCornerShape(12.dp)

    /** The website search field on Create, a pill like Material's search bars. */
    val Search: Shape = CircleShape

    /** Buttons with a container (main actions, row buttons, the search button): fully round. */
    val Control: Shape = CircleShape

    /** The outside corners of list content: home tiles, settings groups and lone items. */
    val ItemRadius = 20.dp

    /** Where items in a settings group meet. */
    val ItemInnerRadius = 4.dp

    /** A home tile, or a settings item on its own. */
    val Item: Shape = RoundedCornerShape(ItemRadius)

    /** A home row's press, hover and selection highlight: a pill, like Material's navigation items. */
    val Row: Shape = CircleShape

    /** Notices of more than one line (R3.3): half a one-line notice's usual 56 dp height. */
    val Notice: Shape = RoundedCornerShape(28.dp)

    /** A one-line notice: a pill at any text size. */
    val NoticeOneLine: Shape = CircleShape

    /** Menus; each item's highlight inside one uses [MenuItem]. */
    val Menu: Shape = RoundedCornerShape(16.dp)

    /** A menu item's press, hover and focus highlight, and other highlights inside a container. */
    val MenuItem: Shape = RoundedCornerShape(12.dp)

    /** The two-pane cards: tighter than sheets and dialogs, so the desktop layout stays crisp. */
    val Pane: Shape = RoundedCornerShape(16.dp)

    /** Bottom sheets and dialogs, set through [MaterialShapes]. */
    val Surface = RoundedCornerShape(28.dp)

    /** App icons, badges, color swatches and icon-only buttons without a container. */
    val Round: Shape = CircleShape
}

/** Material's shape scale, for the components that take their shape from the theme. */
val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = AppShapes.Surface,
)
