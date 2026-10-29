package com.valghora.shrimple.core.profile

import com.valghora.shrimple.core.Modifier
import com.valghora.shrimple.core.ShortcutSpec
import com.valghora.shrimple.core.UaMode

/** One keyboard shortcut as typed in the form: a label, a key ("K", "F2", "KEYCODE_SLASH") and modifiers. */
data class ShortcutRow(val label: String = "", val key: String = "", val modifiers: Set<Modifier> = setOf(Modifier.CTRL))

/**
 * The configuration form's state (docs/SHRIMPLE_SPEC.md R3.3): text fields stay text while the user
 * types, so half-typed values are allowed; [result] turns them into a profile plus field errors, using
 * the same keys as [FieldError.field] so each error shows next to its input.
 */
data class ProfileForm(
    val name: String,
    val appId: String,
    val startUrl: String,
    val internalDomains: List<String>,
    val headerMode: HeaderMode,
    /** The custom header color; empty means the default. */
    val headerColor: String,
    val iconBackground: String,
    val iconForeground: String,
    val allowMicrophone: Boolean,
    val allowCamera: Boolean,
    val allowNotifications: Boolean,
    val allowLocation: Boolean,
    val userAgentMode: UaMode,
    val keepAliveWhenHidden: Boolean,
    val pauseOnPowerSave: Boolean,
    val enableMultiWindow: Boolean,
    val nativeContextMenu: Boolean,
    val defaultWidth: String,
    val defaultHeight: String,
    val minWidth: String,
    val minHeight: String,
    val shortcuts: List<ShortcutRow>,
    /** Settings the form doesn't show, carried over from the profile it was made from. */
    val base: Profile,
) {
    data class Result(val profile: Profile?, val errors: List<FieldError>) {
        fun errorFor(field: String): String? = errors.firstOrNull { it.field == field }?.message
    }

    fun result(): Result {
        // Values that don't parse get a placeholder so every other field is still validated; the
        // parse errors are reported instead of the placeholder's.
        val parseErrors = mutableListOf<FieldError>()
        fun dimension(field: String, value: String, fallback: Int): Int = value.trim().toIntOrNull() ?: fallback.also {
            if (parseErrors.none { e -> e.field == field }) parseErrors += FieldError(field, "must be a whole number")
        }
        val shortcutSpecs = shortcuts.mapIndexed { i, row ->
            val key = keyName(row.key)
            if (key == null) parseErrors += FieldError("appShortcuts", "shortcut ${i + 1}: enter a key like K, F2 or SLASH")
            ShortcutSpec(row.label.trim(), key ?: UNPARSED_KEY, row.modifiers)
        }
        val profile = base.copy(
            name = name.trim(),
            appId = appId,
            startUrl = startUrl.trim(),
            internalDomains = internalDomains.map { it.trim().lowercase() }.filter(String::isNotEmpty).distinct(),
            headerMode = headerMode,
            headerColor = color(headerColor).ifEmpty { Profile.DEFAULT_HEADER_COLOR },
            iconBackground = color(iconBackground),
            iconForeground = color(iconForeground).ifEmpty { Profile.AUTO },
            allowMicrophone = allowMicrophone,
            allowCamera = allowCamera,
            allowNotifications = allowNotifications,
            allowLocation = allowLocation,
            userAgentMode = userAgentMode,
            keepAliveWhenHidden = keepAliveWhenHidden,
            pauseOnPowerSave = pauseOnPowerSave,
            enableMultiWindow = enableMultiWindow,
            nativeContextMenu = nativeContextMenu,
            defaultWindowDp = WindowSize(
                dimension("defaultWindowDp", defaultWidth, base.defaultWindowDp.width),
                dimension("defaultWindowDp", defaultHeight, base.defaultWindowDp.height),
            ),
            minWindowDp = WindowSize(
                dimension("minWindowDp", minWidth, base.minWindowDp.width),
                dimension("minWindowDp", minHeight, base.minWindowDp.height),
            ),
            appShortcuts = shortcutSpecs,
        )
        val parsedFields = parseErrors.map { it.field }.toSet()
        val errors = parseErrors + ProfileValidator.validate(profile).filter { it.field !in parsedFields }
        return Result(profile.takeIf { errors.isEmpty() }, errors)
    }

    companion object {
        private val SIMPLE_KEY = Regex("[A-Z0-9]|F([1-9]|1[0-2])|[A-Z_]{2,}")
        private const val UNPARSED_KEY = "KEYCODE_UNKNOWN"

        fun from(profile: Profile) = ProfileForm(
            name = profile.name,
            appId = profile.appId,
            startUrl = profile.startUrl,
            internalDomains = profile.internalDomains,
            headerMode = profile.headerMode,
            headerColor = profile.headerColor,
            iconBackground = profile.iconBackground,
            iconForeground = if (profile.iconForeground == Profile.AUTO) "" else profile.iconForeground,
            allowMicrophone = profile.allowMicrophone,
            allowCamera = profile.allowCamera,
            allowNotifications = profile.allowNotifications,
            allowLocation = profile.allowLocation,
            userAgentMode = profile.userAgentMode,
            keepAliveWhenHidden = profile.keepAliveWhenHidden,
            pauseOnPowerSave = profile.pauseOnPowerSave,
            enableMultiWindow = profile.enableMultiWindow,
            nativeContextMenu = profile.nativeContextMenu,
            defaultWidth = profile.defaultWindowDp.width.toString(),
            defaultHeight = profile.defaultWindowDp.height.toString(),
            minWidth = profile.minWindowDp.width.toString(),
            minHeight = profile.minWindowDp.height.toString(),
            shortcuts = profile.appShortcuts.map { ShortcutRow(it.label, it.keyName.removePrefix("KEYCODE_"), it.modifiers) },
            base = profile,
        )

        /** "#abc", "abc123" or "#ABC123" -> "#ABC123"; anything else is kept for the validator to reject. */
        fun color(text: String): String {
            val t = text.trim().removePrefix("#")
            return when {
                t.isEmpty() -> ""
                Regex("[0-9a-fA-F]{3}").matches(t) -> "#" + t.map { "$it$it" }.joinToString("").uppercase()
                Regex("[0-9a-fA-F]{6}").matches(t) -> "#" + t.uppercase()
                else -> text.trim()
            }
        }

        /** "k" -> "KEYCODE_K", "f2" -> "KEYCODE_F2", "keycode_slash" -> "KEYCODE_SLASH"; null if it can't be a key. */
        fun keyName(text: String): String? {
            val t = text.trim().uppercase().removePrefix("KEYCODE_")
            return if (SIMPLE_KEY.matches(t)) "KEYCODE_$t" else null
        }
    }
}
