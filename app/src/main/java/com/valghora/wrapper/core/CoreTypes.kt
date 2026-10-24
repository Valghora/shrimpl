package com.valghora.wrapper.core

enum class UaMode { AUTO, DESKTOP, MOBILE }

enum class Modifier { CTRL, SHIFT, ALT, META }

data class ShortcutSpec(val label: String, val keyName: String, val modifiers: Set<Modifier>)

enum class UrlClass { INTERNAL, OAUTH, EXTERNAL, PASSTHROUGH, SPECIAL_SCHEME, BLOCKED }

enum class KeyAction { RELOAD, ZOOM_IN, ZOOM_OUT, ZOOM_RESET, NEW_WINDOW, NONE }

enum class HitType { ANCHOR, IMAGE_ANCHOR, IMAGE, OTHER }

enum class MenuAction { COPY_LINK, OPEN_LINK, SAVE_IMAGE }

enum class WakeEvent { SLEEP, WAKE, NONE }

/** Why a UA mode was chosen; [label] is the value logged at startup (R6.12). */
enum class UaReason(val label: String) {
    OVERRIDE("override"),
    USER("user"),
    LEARNED("learned"),
    CONFIG("config"),
    AUTO_LARGE("auto-large"),
    AUTO_COMPACT("auto-compact"),
}

data class UaResolution(val mode: UaMode, val reason: UaReason)

interface ZoomStore {
    fun load(): Int?
    fun save(value: Int)
}

interface UaStore {
    var userChoice: UaMode?
    var learnedDesktop: Boolean
}

/** The config values the controller depends on, injectable so tests can cover every branch. */
data class SessionConfig(
    val internalDomains: List<String> = AppConfig.INTERNAL_DOMAINS,
    val oauthDomains: List<String> = AppConfig.OAUTH_DOMAINS,
    val pauseOnPowerSave: Boolean = AppConfig.PAUSE_ON_POWER_SAVE,
    val enableMultiWindow: Boolean = AppConfig.ENABLE_MULTI_WINDOW,
    val appShortcuts: List<ShortcutSpec> = AppConfig.APP_SHORTCUTS,
    val newWindowShortcut: ShortcutSpec? = AppConfig.NEW_WINDOW_SHORTCUT,
)
