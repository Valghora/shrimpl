package com.valghora.uniwrap.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How UniWrap installs and uninstalls web apps (docs/UNIWRAP_SPEC.md R6). */
enum class InstallMethod { SESSION, SHIZUKU, ROOT }

enum class ThemeSetting { SYSTEM, LIGHT, DARK }

/** UniWrap's own settings (R2.5), in private SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("uniwrap_settings", Context.MODE_PRIVATE)

    private val _installMethod = MutableStateFlow(
        prefs.getString(KEY_INSTALL_METHOD, null)?.let { runCatching { InstallMethod.valueOf(it) }.getOrNull() } ?: InstallMethod.SESSION,
    )

    /** How web apps are installed (R6.2); the system installer unless the user picked Shizuku or root. */
    val installMethod: StateFlow<InstallMethod> = _installMethod.asStateFlow()

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM)
    val theme: StateFlow<ThemeSetting> = _theme.asStateFlow()

    private val _keyBackedUp = MutableStateFlow(prefs.getBoolean(KEY_BACKED_UP, false))
    val keyBackedUp: StateFlow<Boolean> = _keyBackedUp.asStateFlow()

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

    fun setKeyBackedUp(backedUp: Boolean) {
        prefs.edit { putBoolean(KEY_BACKED_UP, backedUp) }
        _keyBackedUp.value = backedUp
    }

    private companion object {
        const val KEY_INSTALL_METHOD = "install_method"
        const val KEY_THEME = "theme"
        const val KEY_BACKED_UP = "key_backed_up"
        const val KEY_PROMPT_SHOWN = "backup_prompt_shown"
    }
}
