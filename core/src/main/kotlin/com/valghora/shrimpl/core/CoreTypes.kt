package com.valghora.shrimpl.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which site a web app asks for (T6.12): Auto picks desktop on large screens and PCs, mobile elsewhere. */
@Serializable
enum class UaMode {
    @SerialName("auto") AUTO,
    @SerialName("desktop") DESKTOP,
    @SerialName("mobile") MOBILE,
}

/** A modifier key in a shortcut. */
@Serializable
enum class Modifier { CTRL, SHIFT, ALT, META }

/**
 * A site keyboard shortcut listed in the Keyboard Shortcuts Helper
 * (T5.4); [keyName] is an Android `KEYCODE_` name.
 */
@Serializable
data class ShortcutSpec(
    val label: String,
    @SerialName("key") val keyName: String,
    val modifiers: Set<Modifier> = emptySet(),
)

/**
 * How the web app treats a URL (T2a): load it in place (internal, sign-in,
 * passthrough schemes), open the browser, hand it to another app, or block it.
 */
enum class UrlClass { INTERNAL, OAUTH, EXTERNAL, PASSTHROUGH, SPECIAL_SCHEME, BLOCKED }

/** The web app's own keyboard shortcuts (T5.3); NONE passes the key to the page. */
enum class KeyAction { RELOAD, ZOOM_IN, ZOOM_OUT, ZOOM_RESET, NEW_WINDOW, PRINT, APP_OPTIONS, NONE }

/** What a long press or right-click landed on, for the native context menu (T5.5). */
enum class HitType { ANCHOR, IMAGE_ANCHOR, IMAGE, OTHER }

/** An item of the native context menu (T5.5). */
enum class MenuAction { COPY_LINK, OPEN_LINK, SAVE_IMAGE, APP_OPTIONS }

/** A switch of the App options panel (T5.8), in the panel's order. */
enum class AppOption { DESKTOP_SITE }

/** A change of the workspace's wake state (T8.3); NONE when nothing changed. */
enum class WakeEvent { SLEEP, WAKE, NONE }

/** Why a UA mode was chosen; [label] is the value logged at startup (docs/TEMPLATE_SPEC.md T6.12). */
enum class UaReason(val label: String) {
    OVERRIDE("override"),
    USER("user"),
    LEARNED("learned"),
    CONFIG("config"),
    AUTO_LARGE("auto-large"),
    AUTO_COMPACT("auto-compact"),
}

/** The user agent mode in effect and why it was chosen. */
data class UaResolution(val mode: UaMode, val reason: UaReason)

/** Where the text zoom the user picked is kept between launches; null from [load] means none. */
interface ZoomStore {
    /** The saved zoom in percent, or null if the user never zoomed. */
    fun load(): Int?
    /** Saves the zoom in percent. */
    fun save(value: Int)

    /** Forgets the saved zoom, so text follows the system font size again. */
    fun clear() {}
}

/**
 * The user agent choices kept between launches: the user's toggle, and
 * desktop mode learned from a store redirect (T6.10).
 */
interface UaStore {
    var userChoice: UaMode?
    var learnedDesktop: Boolean
}

/** The config values the controller depends on, injectable so tests can cover every branch. */
data class SessionConfig(
    val internalDomains: List<String>,
    val oauthDomains: List<String>,
    val pauseOnPowerSave: Boolean,
    val enableMultiWindow: Boolean,
    val appShortcuts: List<ShortcutSpec>,
    val newWindowShortcut: ShortcutSpec?,
)
