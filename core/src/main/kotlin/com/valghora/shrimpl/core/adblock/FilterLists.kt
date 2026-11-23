package com.valghora.shrimpl.core.adblock

import com.valghora.shrimpl.core.AdBlocking

/**
 * The lists ad blocking uses (docs/SHRIMPL_SPEC.md R15.1): Shrimpl downloads them and web apps read them
 * from Shrimpl. Both are GPL-3.0 or CC BY-SA 3.0, downloaded on the device and never shipped in an APK.
 */
enum class FilterList(val id: String, val url: String) {
    EASYLIST("easylist", "https://easylist.to/easylist/easylist.txt"),
    EASYPRIVACY("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
    ;

    companion object {
        /** The lists [level] blocks with: EasyList for ads, and EasyPrivacy too for trackers. */
        fun forLevel(level: AdBlocking): List<FilterList> = when (level) {
            AdBlocking.OFF -> emptyList()
            AdBlocking.ADS -> listOf(EASYLIST)
            AdBlocking.ADS_AND_TRACKERS -> listOf(EASYLIST, EASYPRIVACY)
        }

        /** The list with [id], or null. */
        fun byId(id: String): FilterList? = entries.firstOrNull { it.id == id }

        /** A list with fewer rules than this is broken, whatever came before it. */
        const val MIN_RULES = 1_000

        /**
         * Whether a download with [rules] rules replaces the copy with [previousRules] (R15.2): it must hold
         * at least [MIN_RULES], and at least half the previous count, since a list never shrinks by half
         * in a day; a truncated or wrong file would.
         */
        fun accept(rules: Int, previousRules: Int?): Boolean =
            rules >= MIN_RULES && (previousRules == null || rules * 2 >= previousRules)
    }
}
