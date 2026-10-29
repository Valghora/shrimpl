package com.valghora.shrimple.ui.common

import androidx.compose.runtime.saveable.Saver
import com.valghora.shrimple.core.Modifier
import com.valghora.shrimple.core.profile.ShortcutRow

// Savers for UI state that Bundles can't hold directly, so dialogs and drafts survive rotation,
// folding and window resizing (Tier 3 Config_Changes; adaptive skill step 6.1).

val StringListSaver: Saver<List<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toList() },
)

val ShortcutRowSaver: Saver<ShortcutRow, ArrayList<String>> = Saver(
    save = { row -> ArrayList<String>().apply { add(row.label); add(row.key); row.modifiers.mapTo(this, Modifier::name) } },
    restore = { saved -> ShortcutRow(saved[0], saved[1], saved.drop(2).mapTo(mutableSetOf(), Modifier::valueOf)) },
)

val NoticeSetSaver: Saver<Set<Notice>, ArrayList<String>> = Saver(
    save = { notices -> notices.mapTo(ArrayList()) { "${it.kind.name}\n${it.text}" } },
    restore = { saved -> saved.mapTo(mutableSetOf()) { Notice(Notice.Kind.valueOf(it.substringBefore('\n')), it.substringAfter('\n')) } },
)
