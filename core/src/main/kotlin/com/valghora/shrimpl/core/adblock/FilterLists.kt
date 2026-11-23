package com.valghora.shrimpl.core.adblock

import com.valghora.shrimpl.core.AdBlocking

/**
 * The lists ad blocking uses (docs/SHRIMPL_SPEC.md R15.1): Shrimpl downloads them and web apps read them
 * from Shrimpl. All are the EasyList project's, GPL-3.0 or CC BY-SA 3.0, downloaded on the device and
 * never shipped in an APK. [minRules] is the fewest rules a whole download of the list holds (R15.2).
 */
enum class FilterList(val id: String, val url: String, val minRules: Int) {
    EASYLIST("easylist", "https://easylist.to/easylist/easylist.txt", 1_000),
    EASYPRIVACY("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt", 1_000),

    /** The Adblock Warning Removal List: unblocks bait files and hides "turn off your ad blocker" walls. */
    WARNINGS("antiadblock", "https://easylist-downloads.adblockplus.org/antiadblockfilters.txt", 200),
    ;

    companion object {
        /**
         * The lists [level] blocks with: EasyList for ads, EasyPrivacy too for trackers, and the warning
         * list at both levels, since a site can ask to turn off ad blocking whenever ads are blocked.
         */
        fun forLevel(level: AdBlocking): List<FilterList> = when (level) {
            AdBlocking.OFF -> emptyList()
            AdBlocking.ADS -> listOf(EASYLIST, WARNINGS)
            AdBlocking.ADS_AND_TRACKERS -> listOf(EASYLIST, EASYPRIVACY, WARNINGS)
        }

        /** The list with [id], or null. */
        fun byId(id: String): FilterList? = entries.firstOrNull { it.id == id }

        /**
         * Whether a download of [list] with [rules] rules replaces the copy with [previousRules] (R15.2):
         * it must hold at least the list's [minRules], and at least half the previous count, since a list
         * never shrinks by half in a day; a truncated or wrong file would.
         */
        fun accept(list: FilterList, rules: Int, previousRules: Int?): Boolean =
            rules >= list.minRules && (previousRules == null || rules * 2 >= previousRules)
    }
}
