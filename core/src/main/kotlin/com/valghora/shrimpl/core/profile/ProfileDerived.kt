package com.valghora.shrimpl.core.profile

import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.SessionConfig
import com.valghora.shrimpl.core.ShortcutSpec

// Values computed from a profile (docs/SHRIMPL_SPEC.md R4.2).
// They assume a valid profile (ProfileValidator).


/** The icon foreground, with [Profile.AUTO] resolved by contrast against the background. */
val Profile.resolvedIconForeground: String
    get() = if (iconForeground != Profile.AUTO) iconForeground else autoIconForeground(iconBackground)

/** White or black, whichever contrasts better with [background] (`#RRGGBB`). */
fun autoIconForeground(background: String): String = if (DesktopSessionController.isDarkColor(background)) "#FFFFFF" else "#000000"

/** Origin rules for the notification bridge: each internal domain, and any subdomain of it, over https. */
val Profile.allowedOriginRules: Set<String>
    get() = internalDomains.flatMap { listOf("https://$it", "https://*.$it") }.toSet()

/** The store-redirect fallback and the UA toggle only work without a user agent override. */
val Profile.uaFeaturesActive: Boolean get() = userAgentOverride.isEmpty()

/** The ID of the web app's notification channel (T7.4). */
val Profile.notificationChannelId: String get() = "${appId}_notifications"

/**
 * The New window shortcut as the Keyboard Shortcuts Helper lists it
 * (T5.4), or null when the profile sets none.
 */
val Profile.newWindowShortcutSpec: ShortcutSpec?
    get() = newWindowShortcut?.let { ShortcutSpec(NEW_WINDOW_LABEL, it.key, it.modifiers) }

/** The values [DesktopSessionController] needs from this profile. */
fun Profile.sessionConfig() = SessionConfig(
    internalDomains = internalDomains,
    oauthDomains = oauthDomains,
    pauseOnPowerSave = pauseOnPowerSave,
    enableMultiWindow = enableMultiWindow,
    appShortcuts = appShortcuts,
    newWindowShortcut = newWindowShortcutSpec,
)

/** The New window shortcut's label in the Keyboard Shortcuts Helper. */
const val NEW_WINDOW_LABEL = "New Window"

/** Web app IDs (R11.2): derived from the name, unique among Shrimpl's apps, and fixed once the app exists. */
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
