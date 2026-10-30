package com.valghora.shrimpl.ui.common

import androidx.compose.runtime.saveable.Saver

// Savers for UI state that Bundles can't hold directly, so dialogs and drafts survive rotation,
// folding and window resizing (Tier 3 Config_Changes; adaptive skill step 6.1).

val StringListSaver: Saver<List<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toList() },
)

val NoticeSetSaver: Saver<Set<Notice>, ArrayList<String>> = Saver(
    save = { notices -> notices.mapTo(ArrayList()) { "${it.kind.name}\n${it.text}" } },
    restore = { saved -> saved.mapTo(mutableSetOf()) { Notice(Notice.Kind.valueOf(it.substringBefore('\n')), it.substringAfter('\n')) } },
)
