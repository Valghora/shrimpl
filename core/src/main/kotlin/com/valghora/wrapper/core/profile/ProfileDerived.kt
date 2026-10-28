package com.valghora.wrapper.core.profile

import com.valghora.wrapper.core.DesktopSessionController
import com.valghora.wrapper.core.SessionConfig
import com.valghora.wrapper.core.ShortcutSpec

// Values computed from a profile (docs/UNIWRAP_SPEC.md R4.2).
// They assume a valid profile (ProfileValidator).


/** The icon foreground, with [Profile.AUTO] resolved by contrast against the background. */
val Profile.resolvedIconForeground: String
    get() = when {
        iconForeground != Profile.AUTO -> iconForeground
        DesktopSessionController.isDarkColor(iconBackground) -> "#FFFFFF"
        else -> "#000000"
    }

/** Origin rules for the notification bridge: each internal domain, and any subdomain of it, over https. */
val Profile.allowedOriginRules: Set<String>
    get() = internalDomains.flatMap { listOf("https://$it", "https://*.$it") }.toSet()

/** The store-redirect fallback and the UA toggle only work without a user agent override. */
val Profile.uaFeaturesActive: Boolean get() = userAgentOverride.isEmpty()

val Profile.notificationChannelId: String get() = "${appId}_notifications"

val Profile.newWindowShortcutSpec: ShortcutSpec?
    get() = newWindowShortcut?.let { ShortcutSpec(NEW_WINDOW_LABEL, it.key, it.modifiers) }

fun Profile.sessionConfig() = SessionConfig(
    internalDomains = internalDomains,
    oauthDomains = oauthDomains,
    pauseOnPowerSave = pauseOnPowerSave,
    enableMultiWindow = enableMultiWindow,
    appShortcuts = appShortcuts,
    newWindowShortcut = newWindowShortcutSpec,
)

const val NEW_WINDOW_LABEL = "New Window"

object AppIds {
    private val NON_ALNUM = Regex("[^a-z0-9]")

    /** Lowercase letters and digits of [name], prefixed "app" if empty or starting with a digit. */
    fun fromName(name: String): String {
        val id = NON_ALNUM.replace(name.lowercase(), "")
        return if (id.isEmpty() || id[0].isDigit()) "app$id" else id
    }

    /** [fromName], with "2", "3", ... appended until it is not in [taken]. */
    fun unique(name: String, taken: Set<String>): String {
        val base = fromName(name)
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base$it" }.first { it !in taken }
    }
}
