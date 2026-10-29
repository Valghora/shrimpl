package com.valghora.shrimple.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UaMode {
    @SerialName("auto") AUTO,
    @SerialName("desktop") DESKTOP,
    @SerialName("mobile") MOBILE,
}

@Serializable
enum class Modifier { CTRL, SHIFT, ALT, META }

@Serializable
data class ShortcutSpec(
    val label: String,
    @SerialName("key") val keyName: String,
    val modifiers: Set<Modifier> = emptySet(),
)

enum class UrlClass { INTERNAL, OAUTH, EXTERNAL, PASSTHROUGH, SPECIAL_SCHEME, BLOCKED }

enum class KeyAction { RELOAD, ZOOM_IN, ZOOM_OUT, ZOOM_RESET, NEW_WINDOW, PRINT, NONE }

enum class HitType { ANCHOR, IMAGE_ANCHOR, IMAGE, OTHER }

enum class MenuAction { COPY_LINK, OPEN_LINK, SAVE_IMAGE }

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
    val internalDomains: List<String>,
    val oauthDomains: List<String>,
    val pauseOnPowerSave: Boolean,
    val enableMultiWindow: Boolean,
    val appShortcuts: List<ShortcutSpec>,
    val newWindowShortcut: ShortcutSpec?,
)
