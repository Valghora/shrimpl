package com.valghora.wrapper.core.profile

import com.valghora.wrapper.core.Modifier
import com.valghora.wrapper.core.ShortcutSpec
import com.valghora.wrapper.core.UaMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One site's settings, stored by UniWrap and read by the web app at launch from
 * `assets/uniwrap/profile.json` (docs/UNIWRAP_SPEC.md R7.1, R11.1). Defaults match the retired
 * `sites/defaults.toml`. Check a profile with [ProfileValidator] before using it.
 */
@Serializable
data class Profile(
    val schemaVersion: Int = SCHEMA_VERSION,
    /** Launcher label and task name. */
    val name: String,
    /** Lowercase letters and digits, starting with a letter; fixed when the web app is created. */
    val appId: String,
    val startUrl: String,
    /** Hosts that load inside the app; each also matches its subdomains. */
    val internalDomains: List<String>,
    /** Sign-in hosts that also load inside the app. */
    val oauthDomains: List<String> = DEFAULT_OAUTH_DOMAINS,
    val userAgentMode: UaMode = UaMode.AUTO,
    /** Used verbatim when non-empty; turns off the store-redirect fallback and the UA toggle. */
    val userAgentOverride: String = "",
    val uaStoreRedirectFallback: Boolean = true,
    val uaUserToggle: Boolean = true,
    /** Adaptive icon background; also the notification accent color. */
    val iconBackground: String,
    /** The glyph color: `#RRGGBB`, or [AUTO] for white or black, whichever contrasts better with [iconBackground]. */
    val iconForeground: String = DEFAULT_ICON_FOREGROUND,
    /** Where the window, caption bar and system bar colors come from. */
    val headerMode: HeaderMode = HeaderMode.SYSTEM,
    /** The header color for [HeaderMode.CUSTOM], `#RRGGBB`; kept when another mode is chosen. */
    val headerColor: String = DEFAULT_HEADER_COLOR,
    val allowMicrophone: Boolean = false,
    val allowCamera: Boolean = false,
    val allowNotifications: Boolean = true,
    val allowProtectedMedia: Boolean = false,
    val enableMultiWindow: Boolean = false,
    val keepAliveWhenHidden: Boolean = true,
    val pauseOnPowerSave: Boolean = false,
    val nativeContextMenu: Boolean = true,
    val defaultWindowDp: WindowSize = WindowSize(1280, 800),
    val minWindowDp: WindowSize = WindowSize(400, 500),
    /** The site's own shortcuts, listed in the Keyboard Shortcuts Helper; the keys still reach the page. */
    val appShortcuts: List<ShortcutSpec> = emptyList(),
    /** Opens a new window (needs [enableMultiWindow]). */
    val newWindowShortcut: KeyCombo? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val AUTO = "auto"
        /** The `headerColor` value that meant [HeaderMode.SYSTEM] before header modes existed. */
        const val SYSTEM = "system"
        const val DEFAULT_HEADER_COLOR = "#202124"
        const val DEFAULT_ICON_FOREGROUND = "#FFFFFF"
        val DEFAULT_OAUTH_DOMAINS = listOf(
            "accounts.google.com",
            "appleid.apple.com",
            "github.com",
            "login.microsoftonline.com",
            "login.live.com",
        )
    }
}

/** How the web app colors its header (window background, caption bar and system bars). */
@Serializable
enum class HeaderMode {
    /** The system's surface color for the current light or dark theme. */
    @SerialName("system") SYSTEM,

    /**
     * The site's own colors: on phones and other windows without a caption bar, the status bar takes
     * the color at the top of the page and the navigation bar the color at the bottom. Desktop
     * windows (with a caption bar) use the system color.
     */
    @SerialName("dynamic") DYNAMIC,

    /** [Profile.headerColor]. */
    @SerialName("custom") CUSTOM,
}

@Serializable
data class WindowSize(val width: Int, val height: Int)

@Serializable
data class KeyCombo(val key: String, val modifiers: Set<Modifier> = emptySet())
