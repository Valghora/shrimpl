package com.valghora.shrimpl.data

import android.content.Context
import androidx.core.content.edit
import com.valghora.shrimpl.core.update.UpdateFrequency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How Shrimpl installs and uninstalls web apps (docs/SHRIMPL_SPEC.md R6). */
enum class InstallMethod { SESSION, SHIZUKU, ROOT }

/** Shrimpl's theme (R2.5): the system's light or dark setting, or always light or dark. */
enum class ThemeSetting { SYSTEM, LIGHT, DARK }

/**
 * The colors of home status badges and row buttons (R2.5): Shrimpl's fixed pastels, those pastels
 * harmonized with the wallpaper, or the wallpaper's primary container colors.
 */
enum class BadgeTheme { SHRIMPL, HARMONY, MONO }

/** What tapping an app in the home list does (R2.5). */
enum class ClickAction { OPEN, EDIT }

/** How the home screen shows the apps (R2.2): rows, or tiles. */
enum class HomeLayout { LIST, TILES }

/** Shrimpl's own settings (R2.5), in private SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("shrimpl_settings", Context.MODE_PRIVATE)

    private val _installMethod = MutableStateFlow(
        prefs.getString(KEY_INSTALL_METHOD, null)?.let { runCatching { InstallMethod.valueOf(it) }.getOrNull() } ?: InstallMethod.SESSION,
    )

    /** How web apps are installed (R6.2); the system installer unless the user picked Shizuku or root. */
    val installMethod: StateFlow<InstallMethod> = _installMethod.asStateFlow()

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM)
    val theme: StateFlow<ThemeSetting> = _theme.asStateFlow()

    private val _clickAction = MutableStateFlow(
        prefs.getString(KEY_CLICK_ACTION, null)?.let { runCatching { ClickAction.valueOf(it) }.getOrNull() } ?: ClickAction.EDIT,
    )

    /** What tapping an app in the home list does; Edit unless the user picked Open. */
    val clickAction: StateFlow<ClickAction> = _clickAction.asStateFlow()

    private val _resolveOnTap = MutableStateFlow(prefs.getBoolean(KEY_RESOLVE_ON_CLICK, false))

    /** Tapping an installed app with a pending action (Update or Fix) does that action instead (R2.5); off by default. */
    val resolveOnTap: StateFlow<Boolean> = _resolveOnTap.asStateFlow()

    /** Saves the Resolve on tap switch (R2.5). */
    fun setResolveOnTap(resolve: Boolean) {
        prefs.edit { putBoolean(KEY_RESOLVE_ON_CLICK, resolve) }
        _resolveOnTap.value = resolve
    }

    private val _badgeTheme = MutableStateFlow(
        prefs.getString(KEY_BADGE_THEME, null)?.let { runCatching { BadgeTheme.valueOf(it) }.getOrNull() } ?: BadgeTheme.SHRIMPL,
    )

    /** How home status badges and row buttons are colored (R2.5); Shrimpl's pastels unless the user picked another. */
    val badgeTheme: StateFlow<BadgeTheme> = _badgeTheme.asStateFlow()

    /** Saves the Badge theme setting; the home screen follows at once (R2.5). */
    fun setBadgeTheme(theme: BadgeTheme) {
        prefs.edit { putString(KEY_BADGE_THEME, theme.name) }
        _badgeTheme.value = theme
    }

    private val _hapticFeedback = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))

    /** A long press that opens an app's menu vibrates (R2.5); on by default, like the system's touch feedback. */
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    /** Saves the Haptic feedback switch (R2.5). */
    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HAPTIC_FEEDBACK, enabled) }
        _hapticFeedback.value = enabled
    }

    private val _homeLayout = MutableStateFlow(
        prefs.getString(KEY_HOME_LAYOUT, null)?.let { runCatching { HomeLayout.valueOf(it) }.getOrNull() } ?: HomeLayout.LIST,
    )
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()

    /** Saves the home layout; the home screen switches at once (R2.6). */
    fun setHomeLayout(layout: HomeLayout) {
        prefs.edit { putString(KEY_HOME_LAYOUT, layout.name) }
        _homeLayout.value = layout
    }

    private val _keyBackedUp = MutableStateFlow(prefs.getBoolean(KEY_BACKED_UP, false))
    val keyBackedUp: StateFlow<Boolean> = _keyBackedUp.asStateFlow()

    /** Whether the one-time search has run for web apps that an earlier install of Shrimpl built (R11.6). */
    var recoveryDone: Boolean
        get() = prefs.getBoolean(KEY_RECOVERY_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_RECOVERY_DONE, value) }

    private val _keyNoticeDismissed = MutableStateFlow(prefs.getStringSet(KEY_KEY_NOTICE_DISMISSED, null).orEmpty().toSet())

    /** The apps the different-key notice (R11.7) was dismissed for; another such app shows it again. */
    val keyNoticeDismissed: StateFlow<Set<String>> = _keyNoticeDismissed.asStateFlow()

    /**
     * Hides the different-key notice for [appIds]; another app signed
     * with another key brings it back (R11.7).
     */
    fun dismissKeyNotice(appIds: Set<String>) {
        prefs.edit { putStringSet(KEY_KEY_NOTICE_DISMISSED, appIds) }
        _keyNoticeDismissed.value = appIds
    }

    /** The one-time "back up your key" prompt (R5.3) has been shown. */
    var backupPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_PROMPT_SHOWN, value) }

    /** Saves the install method (R6.2). */
    fun setInstallMethod(method: InstallMethod) {
        prefs.edit { putString(KEY_INSTALL_METHOD, method.name) }
        _installMethod.value = method
    }

    /** Saves the theme; Shrimpl's screens follow at once. */
    fun setTheme(theme: ThemeSetting) {
        prefs.edit { putString(KEY_THEME, theme.name) }
        _theme.value = theme
    }

    /** Saves the default click action (R2.5). */
    fun setClickAction(action: ClickAction) {
        prefs.edit { putString(KEY_CLICK_ACTION, action.name) }
        _clickAction.value = action
    }

    /**
     * Records whether the signing key has been exported or imported;
     * once it has, the backup prompt isn't shown (R5.3).
     */
    fun setKeyBackedUp(backedUp: Boolean) {
        prefs.edit { putBoolean(KEY_BACKED_UP, backedUp) }
        _keyBackedUp.value = backedUp
    }

    private val _updateFrequency = MutableStateFlow(
        prefs.getString(KEY_UPDATE_FREQUENCY, null)?.let { runCatching { UpdateFrequency.valueOf(it) }.getOrNull() } ?: UpdateFrequency.DEFAULT,
    )

    /** How often the background check runs, or Disabled (R14.2); once a day unless the user picked another. */
    val updateFrequency: StateFlow<UpdateFrequency> = _updateFrequency.asStateFlow()

    /** Saves Check for updates (R14.2); the caller reschedules the background check. */
    fun setUpdateFrequency(frequency: UpdateFrequency) {
        prefs.edit { putString(KEY_UPDATE_FREQUENCY, frequency.name) }
        _updateFrequency.value = frequency
    }

    private companion object {
        const val KEY_UPDATE_FREQUENCY = "update_frequency"
        const val KEY_INSTALL_METHOD = "install_method"
        const val KEY_THEME = "theme"
        const val KEY_CLICK_ACTION = "click_action"
        const val KEY_HOME_LAYOUT = "home_layout"
        // Invariant: the key keeps the switch's old name, so a saved choice survives the rename.
        const val KEY_RESOLVE_ON_CLICK = "resolve_on_click"
        const val KEY_BADGE_THEME = "badge_theme"
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        const val KEY_BACKED_UP = "key_backed_up"
        const val KEY_PROMPT_SHOWN = "backup_prompt_shown"
        const val KEY_RECOVERY_DONE = "recovery_done"
        const val KEY_KEY_NOTICE_DISMISSED = "key_notice_dismissed"
    }
}
