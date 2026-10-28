package com.valghora.wrapper.core

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Platform-independent session logic: URL routing, text zoom, key bindings, context menus,
 * the wake state machine, user-agent selection, shortcut validation and color rules (docs/TEMPLATE_SPEC.md T2).
 */
class DesktopSessionController(
    private val zoomStore: ZoomStore,
    val uaStore: UaStore,
    private val config: SessionConfig,
) {

    // ---------- a. URL classification ----------

    fun classify(url: String): UrlClass {
        val trimmed = url.trim()
        val scheme = schemeOf(trimmed) ?: return UrlClass.BLOCKED
        if (scheme in PASSTHROUGH_SCHEMES) return UrlClass.PASSTHROUGH
        if (scheme != "http" && scheme != "https") return UrlClass.SPECIAL_SCHEME
        val host = hostOf(trimmed) ?: return UrlClass.BLOCKED
        return when {
            matchesAny(host, config.internalDomains) -> UrlClass.INTERNAL
            matchesAny(host, config.oauthDomains) -> UrlClass.OAUTH
            else -> UrlClass.EXTERNAL
        }
    }

    // ---------- b. Text zoom ----------

    var textZoom: Int = snapZoom(zoomStore.load())
        private set

    fun zoomIn(): Int = setZoom(textZoom + ZOOM_STEP)

    fun zoomOut(): Int = setZoom(textZoom - ZOOM_STEP)

    fun resetZoom(): Int = setZoom(ZOOM_DEFAULT)

    private fun setZoom(value: Int): Int {
        textZoom = snapZoom(value)
        zoomStore.save(textZoom)
        return textZoom
    }

    // ---------- c. Key resolution ----------

    fun resolveKey(keyName: String, modifiers: Set<Modifier>, repeatCount: Int): KeyAction {
        val action = builtInAction(keyName, modifiers, includeNewWindow = true)
        val oncePerPress = action == KeyAction.RELOAD || action == KeyAction.NEW_WINDOW || action == KeyAction.PRINT
        return if (oncePerPress && repeatCount != 0) KeyAction.NONE else action
    }

    private fun builtInAction(keyName: String, modifiers: Set<Modifier>, includeNewWindow: Boolean): KeyAction {
        val ctrl = modifiers == CTRL_ONLY
        return when {
            keyName == "KEYCODE_F5" && modifiers.isEmpty() -> KeyAction.RELOAD
            ctrl && keyName == "KEYCODE_R" -> KeyAction.RELOAD
            ctrl && keyName in ZOOM_IN_KEYS -> KeyAction.ZOOM_IN
            ctrl && keyName in ZOOM_OUT_KEYS -> KeyAction.ZOOM_OUT
            ctrl && keyName in ZOOM_RESET_KEYS -> KeyAction.ZOOM_RESET
            ctrl && keyName == "KEYCODE_P" -> KeyAction.PRINT
            includeNewWindow && isNewWindowShortcut(keyName, modifiers) -> KeyAction.NEW_WINDOW
            else -> KeyAction.NONE
        }
    }

    private fun isNewWindowShortcut(keyName: String, modifiers: Set<Modifier>): Boolean {
        val shortcut = config.newWindowShortcut ?: return false
        return config.enableMultiWindow && shortcut.keyName == keyName && shortcut.modifiers == modifiers
    }

    // ---------- d. Context menu ----------

    fun contextMenuActions(hit: HitType): List<MenuAction> = when (hit) {
        HitType.ANCHOR -> listOf(MenuAction.COPY_LINK, MenuAction.OPEN_LINK)
        HitType.IMAGE_ANCHOR -> listOf(MenuAction.COPY_LINK, MenuAction.OPEN_LINK, MenuAction.SAVE_IMAGE)
        HitType.IMAGE -> listOf(MenuAction.SAVE_IMAGE)
        HitType.OTHER -> emptyList()
    }

    // ---------- e. Wake state machine ----------

    var isAwake: Boolean = true
        private set

    fun computeAwake(interactive: Boolean, anyDisplayOn: Boolean, powerSaveMode: Boolean): Boolean =
        interactive && anyDisplayOn && !(config.pauseOnPowerSave && powerSaveMode)

    fun onWakeInput(interactive: Boolean, anyDisplayOn: Boolean, powerSaveMode: Boolean): WakeEvent {
        val awake = computeAwake(interactive, anyDisplayOn, powerSaveMode)
        if (awake == isAwake) return WakeEvent.NONE
        isAwake = awake
        return if (awake) WakeEvent.WAKE else WakeEvent.SLEEP
    }

    // ---------- f. User agent ----------

    fun resolveUa(
        configMode: UaMode,
        isLargeOrPc: Boolean,
        userChoice: UaMode?,
        learnedDesktop: Boolean,
        override: String,
    ): UaResolution {
        val choice = userChoice?.takeIf { it != UaMode.AUTO }
        val resolution = when {
            choice != null -> UaResolution(choice, UaReason.USER)
            learnedDesktop -> UaResolution(UaMode.DESKTOP, UaReason.LEARNED)
            configMode != UaMode.AUTO -> UaResolution(configMode, UaReason.CONFIG)
            isLargeOrPc -> UaResolution(UaMode.DESKTOP, UaReason.AUTO_LARGE)
            else -> UaResolution(UaMode.MOBILE, UaReason.AUTO_COMPACT)
        }
        return if (override.isNotEmpty()) resolution.copy(reason = UaReason.OVERRIDE) else resolution
    }

    fun resolveUaMode(configMode: UaMode, isLargeOrPc: Boolean, userChoice: UaMode?, learnedDesktop: Boolean): UaMode =
        resolveUa(configMode, isLargeOrPc, userChoice, learnedDesktop, override = "").mode

    fun buildUserAgent(defaultUa: String, mode: UaMode, override: String): String {
        if (override.isNotEmpty()) return override
        val mobile = defaultUa.replace("; wv", "").replace(VERSION_TOKEN, "")
        if (mode != UaMode.DESKTOP) return mobile
        val chrome = CHROME_VERSION.find(defaultUa)?.groupValues?.get(1) ?: return mobile
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }

    fun isStoreRedirect(url: String): Boolean {
        val trimmed = url.trim()
        return when (schemeOf(trimmed)) {
            "market" -> true
            "intent" -> trimmed.contains("package=") || intentFallbackUrl(trimmed)?.let(::isPlayStoreAppsUrl) == true
            "http", "https" -> isPlayStoreAppsUrl(trimmed)
            else -> false
        }
    }

    fun toggleTarget(current: UaMode): UaMode =
        if (current == UaMode.DESKTOP) UaMode.MOBILE else UaMode.DESKTOP

    // ---------- g. Shortcut validation ----------

    /** Errors for invalid key names, empty labels, and collisions with built-in bindings. */
    fun validateShortcuts(): List<String> {
        val errors = mutableListOf<String>()
        fun check(source: String, spec: ShortcutSpec, includeNewWindow: Boolean) {
            if (!KEY_NAME.matches(spec.keyName)) errors += "$source '${spec.label}': invalid key name '${spec.keyName}'"
            if (spec.label.isBlank()) errors += "$source ${spec.keyName}: empty label"
            val clash = builtInAction(spec.keyName, spec.modifiers, includeNewWindow)
            if (clash != KeyAction.NONE) errors += "$source '${spec.label}' collides with built-in $clash"
        }
        config.appShortcuts.forEach { check("APP_SHORTCUTS", it, includeNewWindow = true) }
        config.newWindowShortcut?.let { check("NEW_WINDOW_SHORTCUT", it, includeNewWindow = false) }
        return errors
    }

    companion object {
        const val ZOOM_MIN = 50
        const val ZOOM_MAX = 300
        const val ZOOM_STEP = 10
        const val ZOOM_DEFAULT = 100

        private val PASSTHROUGH_SCHEMES = setOf("about", "blob", "data", "javascript")
        private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
        private val CTRL_ONLY = setOf(Modifier.CTRL)
        private val ZOOM_IN_KEYS = setOf("KEYCODE_EQUALS", "KEYCODE_PLUS", "KEYCODE_NUMPAD_ADD")
        private val ZOOM_OUT_KEYS = setOf("KEYCODE_MINUS", "KEYCODE_NUMPAD_SUBTRACT")
        private val ZOOM_RESET_KEYS = setOf("KEYCODE_0", "KEYCODE_NUMPAD_0")
        private val KEY_NAME = Regex("^KEYCODE_[A-Z0-9_]+$")
        private val VERSION_TOKEN = Regex(" Version/\\d+(\\.\\d+)*")
        private val CHROME_VERSION = Regex("Chrome/(\\d[\\d.]*)")
        private val FALLBACK_URL = Regex("S\\.browser_fallback_url=([^;]*)")
        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        /** Clamps to 50–300 and snaps to the nearest 10; null means "nothing stored". */
        fun snapZoom(value: Int?): Int {
            if (value == null) return ZOOM_DEFAULT
            val clamped = value.coerceIn(ZOOM_MIN, ZOOM_MAX)
            return (clamped - ZOOM_MIN + ZOOM_STEP / 2) / ZOOM_STEP * ZOOM_STEP + ZOOM_MIN
        }

        // ---------- h. Colors ----------

        /** Returns 0xRRGGBB for a valid "#RRGGBB" string, otherwise null. */
        fun parseHex(hex: String): Int? = if (HEX_COLOR.matches(hex)) hex.substring(1).toInt(16) else null

        /** True when the WCAG contrast ratio against white is at least the ratio against black. */
        fun isDarkColor(hex: String): Boolean {
            val rgb = requireNotNull(parseHex(hex)) { "Invalid color: $hex" }
            val luminance = relativeLuminance(rgb)
            val vsWhite = 1.05 / (luminance + 0.05)
            val vsBlack = (luminance + 0.05) / 0.05
            return vsWhite >= vsBlack
        }

        private fun relativeLuminance(rgb: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((rgb shr shift) and 0xFF) / 255.0
                return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        private fun schemeOf(url: String): String? = SCHEME.find(url)?.groupValues?.get(1)?.lowercase()

        private fun hostOf(url: String): String? {
            val host = try {
                URI(url).host
            } catch (_: URISyntaxException) {
                null
            }
            return host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
        }

        private fun matchesAny(host: String, domains: List<String>): Boolean =
            domains.any { host == it || host.endsWith(".$it") }

        private fun intentFallbackUrl(intentUri: String): String? {
            val encoded = FALLBACK_URL.find(intentUri)?.groupValues?.get(1) ?: return null
            return try {
                URLDecoder.decode(encoded, "UTF-8")
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun isPlayStoreAppsUrl(url: String): Boolean {
            val uri = try {
                URI(url.trim())
            } catch (_: URISyntaxException) {
                return false
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            if (uri.host?.lowercase() != "play.google.com") return false
            val path = uri.path ?: return false
            return path == "/store/apps" || path.startsWith("/store/apps/")
        }
    }
}
