package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.AdBlocking
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

    /** The Context menu switch (T5.5). */
    const val KEY_LINK_MENU = "link_menu"

    /** The Block ads switch (T6.19), and [LiveSettings.adBlocking], as the [AdBlocking] name. */
    const val KEY_AD_BLOCKING = "ad_blocking"

    /** The live-settings answer's key for a list's version (R15.3), followed by the list's id. */
    const val KEY_LIST_VERSION = "list_version_"

    /** The path, under the provider's authority, of a list's file (R15.3), followed by the list's id. */
    const val LIST_PATH = "adblock"

    /** The answer's key: whether this Shrimpl built the caller and saved the change. */
    const val KEY_SAVED = "saved"

    /** The provider call a web app makes for its live settings ([LiveSettings]); the answer holds them. */
    const val METHOD_SETTINGS = "live_settings"

    /** [LiveSettings.userAgentMode], as the [UaMode] name. */
    const val KEY_USER_AGENT_MODE = "user_agent_mode"

    /** [LiveSettings.keepAliveWhenHidden]. */
    const val KEY_KEEP_ALIVE = "keep_alive"

    /** [LiveSettings.pauseOnPowerSave]. */
    const val KEY_PAUSE_POWER_SAVE = "pause_power_save"

    /**
     * The level the Block ads switch sets: off is Off; on keeps a level already chosen, and is Strict
     * (ADS_AND_TRACKERS) when ad blocking was off (T5.8).
     */
    fun adBlockingFor(blockAds: Boolean, current: AdBlocking): AdBlocking = when {
        !blockAds -> AdBlocking.OFF
        current == AdBlocking.OFF -> AdBlocking.ADS_AND_TRACKERS
        else -> current
    }

    /** [profile] with the panel's switches applied; a null switch leaves its setting as it is. */
    fun apply(profile: Profile, desktopSite: Boolean?, linkMenu: Boolean?, adBlocking: AdBlocking? = null): Profile = profile.copy(
        userAgentMode = when (desktopSite) {
            true -> UaMode.DESKTOP
            false -> UaMode.MOBILE
            null -> profile.userAgentMode
        },
        nativeContextMenu = linkMenu ?: profile.nativeContextMenu,
        adBlocking = adBlocking ?: profile.adBlocking,
    )
}

/**
 * The settings Shrimpl can change in an installed web app without a rebuild (docs/SHRIMPL_SPEC.md
 * R11.10): the edit page's Behavior section. The app reads them from the profile while it runs, so it
 * takes new ones when it comes back to the front (docs/TEMPLATE_SPEC.md T5.9). Everything else lives in
 * the APK (its manifest, resources or the profile read once at start) and needs a rebuild.
 */
data class LiveSettings(
    val userAgentMode: UaMode,
    val nativeContextMenu: Boolean,
    val keepAliveWhenHidden: Boolean,
    val pauseOnPowerSave: Boolean,
    val adBlocking: AdBlocking = AdBlocking.OFF,
) {
    /** [profile] with these settings. */
    fun applyTo(profile: Profile): Profile = profile.copy(
        userAgentMode = userAgentMode,
        nativeContextMenu = nativeContextMenu,
        keepAliveWhenHidden = keepAliveWhenHidden,
        pauseOnPowerSave = pauseOnPowerSave,
        adBlocking = adBlocking,
    )

    companion object {
        /** [profile]'s live settings. */
        fun of(profile: Profile) = LiveSettings(
            profile.userAgentMode,
            profile.nativeContextMenu,
            profile.keepAliveWhenHidden,
            profile.pauseOnPowerSave,
            profile.adBlocking,
        )

        /**
         * Whether going from the [built] profile to the [edited] one needs a rebuild: true when anything
         * but the live settings changed (R11.10).
         */
        fun needsRebuild(built: Profile, edited: Profile): Boolean = of(edited).applyTo(built) != edited
    }
}
