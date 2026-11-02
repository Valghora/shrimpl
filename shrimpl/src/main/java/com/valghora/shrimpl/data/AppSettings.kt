package com.valghora.shrimpl.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How Shrimpl installs and uninstalls web apps (docs/SHRIMPL_SPEC.md R6). */
enum class InstallMethod { SESSION, SHIZUKU, ROOT }

enum class ThemeSetting { SYSTEM, LIGHT, DARK }

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

    private val _resolveOnClick = MutableStateFlow(prefs.getBoolean(KEY_RESOLVE_ON_CLICK, false))

    /** Tapping an app with a pending action (Update, Fix, Rebuild) does that action instead (R2.5); off by default. */
    val resolveOnClick: StateFlow<Boolean> = _resolveOnClick.asStateFlow()

    fun setResolveOnClick(resolve: Boolean) {
        prefs.edit { putBoolean(KEY_RESOLVE_ON_CLICK, resolve) }
        _resolveOnClick.value = resolve
    }

    private val _homeLayout = MutableStateFlow(
        prefs.getString(KEY_HOME_LAYOUT, null)?.let { runCatching { HomeLayout.valueOf(it) }.getOrNull() } ?: HomeLayout.LIST,
    )
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()

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

    fun dismissKeyNotice(appIds: Set<String>) {
        prefs.edit { putStringSet(KEY_KEY_NOTICE_DISMISSED, appIds) }
        _keyNoticeDismissed.value = appIds
    }

    /** The one-time "back up your key" prompt (R5.3) has been shown. */
    var backupPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_PROMPT_SHOWN, value) }

    fun setInstallMethod(method: InstallMethod) {
        prefs.edit { putString(KEY_INSTALL_METHOD, method.name) }
        _installMethod.value = method
    }

    fun setTheme(theme: ThemeSetting) {
        prefs.edit { putString(KEY_THEME, theme.name) }
        _theme.value = theme
    }

    fun setClickAction(action: ClickAction) {
        prefs.edit { putString(KEY_CLICK_ACTION, action.name) }
        _clickAction.value = action
    }

    fun setKeyBackedUp(backedUp: Boolean) {
        prefs.edit { putBoolean(KEY_BACKED_UP, backedUp) }
        _keyBackedUp.value = backedUp
    }

    private companion object {
        const val KEY_INSTALL_METHOD = "install_method"
        const val KEY_THEME = "theme"
        const val KEY_CLICK_ACTION = "click_action"
        const val KEY_HOME_LAYOUT = "home_layout"
        const val KEY_RESOLVE_ON_CLICK = "resolve_on_click"
        const val KEY_BACKED_UP = "key_backed_up"
        const val KEY_PROMPT_SHOWN = "backup_prompt_shown"
        const val KEY_RECOVERY_DONE = "recovery_done"
        const val KEY_KEY_NOTICE_DISMISSED = "key_notice_dismissed"
    }
}
