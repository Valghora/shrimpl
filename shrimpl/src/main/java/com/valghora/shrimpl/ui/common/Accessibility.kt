package com.valghora.shrimpl.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

// Accessibility helpers (.claude/skills/accessibility): what TalkBack and other services need to
// know that the screen only shows.

/** A page or section title: screen reader users can jump from heading to heading. */
fun Modifier.asHeading(): Modifier = semantics { heading() }

/**
 * A step of a progress list (building an app, searching a site), read as one item with its [state]
 * ("Done", "In progress"…). It is a polite live region, so a change of state is announced.
 */
fun Modifier.progressStep(state: String): Modifier = semantics(mergeDescendants = true) {
    stateDescription = state
    liveRegion = LiveRegionMode.Polite
}
