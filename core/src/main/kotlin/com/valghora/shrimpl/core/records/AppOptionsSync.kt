package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.profile.Profile

/**
 * What a web app's App options panel (docs/TEMPLATE_SPEC.md T5.8) tells the Shrimpl that built it, so
 * the app's edit page shows the same settings (docs/SHRIMPL_SPEC.md R11.9). Both sides use these names.
 */
object AppOptionsSync {
    /** The intent action Shrimpl's App options provider answers to, so a web app can find it. */
    const val ACTION = "com.valghora.shrimpl.action.APP_OPTIONS"

    /** The provider call that carries a change. */
    const val METHOD = "app_options"

    /** The Desktop site switch (T6.11): on is the Desktop user agent, off is Mobile. */
    const val KEY_DESKTOP_SITE = "desktop_site"

    /** The Link and image menu switch (T5.5). */
    const val KEY_LINK_MENU = "link_menu"

    /** The answer's key: whether this Shrimpl built the caller and saved the change. */
    const val KEY_SAVED = "saved"

    /** [profile] with the panel's switches applied; a null switch leaves its setting as it is. */
    fun apply(profile: Profile, desktopSite: Boolean?, linkMenu: Boolean?): Profile = profile.copy(
        userAgentMode = when (desktopSite) {
            true -> UaMode.DESKTOP
            false -> UaMode.MOBILE
            null -> profile.userAgentMode
        },
        nativeContextMenu = linkMenu ?: profile.nativeContextMenu,
    )
}
