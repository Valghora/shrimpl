package com.valghora.pwa.discord.core

/** Every <config> value and derived value of the Universal APK Wrapper spec (R2.2). */
object AppConfig {
    // ---------- Identity ----------
    const val APP_NAME = "Discord"
    const val PACKAGE_PREFIX = "com.valghora.pwa"
    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.1.0"

    // ---------- SDK ----------
    const val MIN_SDK = 37
    const val TARGET_SDK = 37
    const val COMPILE_SDK = 37

    // ---------- Domains ----------
    const val START_URL = "https://discord.com/app"
    val INTERNAL_DOMAINS: List<String> =
        listOf("discord.com", "discordapp.com", "discord.gg", "discord.net", "discordapp.net")
    val OAUTH_DOMAINS: List<String> = listOf(
        "accounts.google.com",
        "appleid.apple.com",
        "github.com",
        "login.microsoftonline.com",
        "login.live.com",
    )

    // ---------- User agent ----------
    val USER_AGENT_MODE: UaMode = UaMode.AUTO
    const val USER_AGENT_OVERRIDE = ""
    const val UA_STORE_REDIRECT_FALLBACK = true
    const val UA_USER_TOGGLE = true

    // ---------- Branding ----------
    const val ICON_SVG = "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/discord.svg"
    const val ICON_BACKGROUND_HEX = "#5865F2"
    const val ICON_FOREGROUND_HEX = "#FFFFFF"
    const val HEADER_SURFACE_HEX = "#1E1F22"
    const val HEADER_SURFACE_HEX_NIGHT = ""

    // ---------- Capabilities ----------
    const val ALLOW_MICROPHONE = true
    const val ALLOW_CAMERA = true
    const val ALLOW_NOTIFICATIONS = true
    const val ALLOW_PROTECTED_MEDIA = false
    const val ENABLE_MULTI_WINDOW = true
    const val KEEP_ALIVE_WHEN_HIDDEN = true
    const val PAUSE_ON_POWER_SAVE = false
    const val NATIVE_CONTEXT_MENU = true

    // ---------- Build output ----------
    const val SIGN_RELEASE_WITH_DEBUG_KEY = true

    // ---------- Freeform window defaults (dp) ----------
    val DEFAULT_WINDOW_DP: List<Int> = listOf(1280, 800)
    val MIN_WINDOW_DP: List<Int> = listOf(400, 500)

    // ---------- Shortcuts ----------
    val APP_SHORTCUTS: List<ShortcutSpec> = listOf(
        ShortcutSpec("Quick Switcher", "KEYCODE_K", setOf(Modifier.CTRL)),
        ShortcutSpec("Toggle Mute", "KEYCODE_M", setOf(Modifier.CTRL, Modifier.SHIFT)),
        ShortcutSpec("Toggle Deafen", "KEYCODE_D", setOf(Modifier.CTRL, Modifier.SHIFT)),
    )
    val NEW_WINDOW_SHORTCUT: ShortcutSpec? = null

    // ---------- Derived values ----------
    const val APP_ID = "discord"
    const val PACKAGE_NAME = "com.valghora.pwa.discord"
    const val CORE_PACKAGE = "com.valghora.pwa.discord.core"
    const val NOTIFICATION_CHANNEL_ID = "discord_notifications"
    const val HEADER_NIGHT = "#1E1F22"
    const val USES_NIGHT_RESOURCES = false
    const val HEADER_IS_DARK = true
    const val HEADER_NIGHT_IS_DARK = true
    val ALLOWED_ORIGIN_RULES: Set<String> =
        INTERNAL_DOMAINS.flatMap { listOf("https://$it", "https://*.$it") }.toSet()
    const val UA_FEATURES_ACTIVE = true
    const val JDK_TOOLCHAIN = 21
    const val DEBUG_APK_NAME = "discord-debug.apk"
    const val RELEASE_APK_NAME = "discord-release.apk"
    const val RELEASE_APK_PATH = "app/build/outputs/apk/release/app-release.apk"
}
