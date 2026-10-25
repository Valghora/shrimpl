package com.valghora.wrapper.core.profile

import com.valghora.wrapper.core.DesktopSessionController
import com.valghora.wrapper.core.UaMode
import com.valghora.wrapper.core.UaStore
import com.valghora.wrapper.core.ZoomStore
import java.net.URI
import java.net.URISyntaxException

/** A problem with one profile field; [field] is the JSON key, so a form can show it next to the input. */
data class FieldError(val field: String, val message: String)

/** Checks a profile against the rules of the retired site_config.py (docs/UNIWRAP_SPEC.md R4.2). */
object ProfileValidator {
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
    private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private val KEYCODE = Regex("^KEYCODE_[A-Z0-9_]+$")
    private val APP_ID = Regex("^[a-z][a-z0-9]*$")
    private const val MAX_NAME_LENGTH = 50
    private const val MIN_WINDOW_DP = 200
    private const val MAX_WINDOW_DP = 10000

    fun validate(profile: Profile): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        fun error(field: String, message: String) {
            errors += FieldError(field, message)
        }

        with(profile) {
            if (schemaVersion != Profile.SCHEMA_VERSION) {
                error("schemaVersion", "unsupported schema version $schemaVersion (expected ${Profile.SCHEMA_VERSION})")
            }
            if (name.isBlank()) error("name", "must not be empty")
            if (name.length > MAX_NAME_LENGTH) error("name", "must be at most $MAX_NAME_LENGTH characters")
            if (!APP_ID.matches(appId)) error("appId", "must be lowercase letters and digits, starting with a letter")

            if (internalDomains.isEmpty()) error("internalDomains", "must list at least one domain")
            internalDomains.filterNot(HOST::matches).forEach {
                error("internalDomains", "'$it' must be a bare lowercase hostname (no scheme, port or path)")
            }
            oauthDomains.filterNot(HOST::matches).forEach {
                error("oauthDomains", "'$it' must be a bare lowercase hostname (no scheme, port or path)")
            }
            val host = httpsHost(startUrl)
            when {
                host == null -> error("startUrl", "must be an https:// URL")
                internalDomains.none { host == it || host.endsWith(".$it") } ->
                    error("startUrl", "host '$host' is not covered by the internal domains")
            }

            if (userAgentOverride.any { it == '\n' || it == '\r' }) {
                error("userAgentOverride", "must be a single line")
            }

            if (!HEX.matches(iconBackground)) error("iconBackground", "must be #RRGGBB")
            if (iconForeground != Profile.AUTO && !HEX.matches(iconForeground)) {
                error("iconForeground", "must be #RRGGBB or \"auto\"")
            }
            if (headerColor != Profile.SYSTEM && !HEX.matches(headerColor)) error("headerColor", "must be #RRGGBB or \"system\"")

            listOf("defaultWindowDp" to defaultWindowDp, "minWindowDp" to minWindowDp).forEach { (field, size) ->
                if (size.width !in MIN_WINDOW_DP..MAX_WINDOW_DP || size.height !in MIN_WINDOW_DP..MAX_WINDOW_DP) {
                    error(field, "width and height must be between $MIN_WINDOW_DP and $MAX_WINDOW_DP dp")
                }
            }
            if (minWindowDp.width > defaultWindowDp.width || minWindowDp.height > defaultWindowDp.height) {
                error("minWindowDp", "must not exceed the default window size")
            }

            appShortcuts.forEachIndexed { i, shortcut ->
                if (shortcut.label.isBlank()) error("appShortcuts", "shortcut ${i + 1}: label must not be empty")
                if (!KEYCODE.matches(shortcut.keyName)) {
                    error("appShortcuts", "shortcut ${i + 1}: key must look like KEYCODE_K")
                }
            }
            newWindowShortcut?.let {
                if (!KEYCODE.matches(it.key)) error("newWindowShortcut", "key must look like KEYCODE_N")
            }
        }
        if (errors.none { it.field == "appShortcuts" || it.field == "newWindowShortcut" }) {
            shortcutClashes(profile).forEach { errors += it }
        }
        return errors
    }

    /** Shortcuts that collide with the built-in reload, zoom and new-window keys, checked by the session controller. */
    private fun shortcutClashes(profile: Profile): List<FieldError> {
        val controller = DesktopSessionController(NoZoomStore, NoUaStore(), profile.sessionConfig())
        return controller.validateShortcuts().map { message ->
            val field = if (message.startsWith("NEW_WINDOW_SHORTCUT")) "newWindowShortcut" else "appShortcuts"
            FieldError(field, message.substringAfter(' '))
        }
    }

    private fun httpsHost(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.scheme?.lowercase() != "https" || uri.rawUserInfo != null) return null
        return uri.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
    }

    private object NoZoomStore : ZoomStore {
        override fun load(): Int? = null
        override fun save(value: Int) = Unit
    }

    private class NoUaStore : UaStore {
        override var userChoice: UaMode? = null
        override var learnedDesktop: Boolean = false
    }
}
