package com.valghora.shrimpl.webapp

import android.content.Context
import androidx.core.content.edit
import com.valghora.shrimpl.core.AdBlocking
import com.valghora.shrimpl.core.DarkMode
import com.valghora.shrimpl.core.OptionStore
import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.UaStore
import com.valghora.shrimpl.core.ZoomStore
import java.util.UUID

/** ZoomStore, UaStore and OptionStore backed by one private SharedPreferences file (docs/TEMPLATE_SPEC.md T6.1). */
class SessionPrefs(context: Context) : ZoomStore, UaStore, OptionStore {

    private val prefs = context.getSharedPreferences("${WebApp.profile.appId}_session", Context.MODE_PRIVATE)

    override fun load(): Int? = if (prefs.contains(KEY_TEXT_ZOOM)) prefs.getInt(KEY_TEXT_ZOOM, 0) else null

    override fun save(value: Int) = prefs.edit { putInt(KEY_TEXT_ZOOM, value) }

    override fun clear() = prefs.edit { remove(KEY_TEXT_ZOOM) }

    override var userChoice: UaMode?
        get() = prefs.getString(KEY_UA_CHOICE, null)?.let { name -> UaMode.entries.firstOrNull { it.name == name } }
        set(value) = prefs.edit { if (value == null) remove(KEY_UA_CHOICE) else putString(KEY_UA_CHOICE, value.name) }

    override var learnedDesktop: Boolean
        get() = prefs.getBoolean(KEY_UA_LEARNED_DESKTOP, false)
        set(value) = prefs.edit { putBoolean(KEY_UA_LEARNED_DESKTOP, value) }

    override var linkMenuChoice: Boolean?
        get() = if (prefs.contains(KEY_LINK_MENU)) prefs.getBoolean(KEY_LINK_MENU, false) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_LINK_MENU) else putBoolean(KEY_LINK_MENU, value) }

    override var adBlockingChoice: AdBlocking?
        get() = prefs.getString(KEY_AD_BLOCKING, null)?.let { name -> AdBlocking.entries.firstOrNull { it.name == name } }
        set(value) = prefs.edit { if (value == null) remove(KEY_AD_BLOCKING) else putString(KEY_AD_BLOCKING, value.name) }

    override var darkModeChoice: DarkMode?
        get() = prefs.getString(KEY_DARK_MODE, null)?.let { name -> DarkMode.entries.firstOrNull { it.name == name } }
        set(value) = prefs.edit { if (value == null) remove(KEY_DARK_MODE) else putString(KEY_DARK_MODE, value.name) }

    override var appBannersChoice: Boolean?
        get() = if (prefs.contains(KEY_APP_BANNERS)) prefs.getBoolean(KEY_APP_BANNERS, false) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_APP_BANNERS) else putBoolean(KEY_APP_BANNERS, value) }

    override var optionsBuild: Long?
        get() = if (prefs.contains(KEY_OPTIONS_BUILD)) prefs.getLong(KEY_OPTIONS_BUILD, 0) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_OPTIONS_BUILD) else putLong(KEY_OPTIONS_BUILD, value) }

    /** A random token the App options shortcut carries, so only that shortcut opens the panel (T5.8). */
    val shortcutToken: String
        get() = prefs.getString(KEY_TOGGLE_TOKEN, null) ?: UUID.randomUUID().toString().also { token ->
            prefs.edit { putString(KEY_TOGGLE_TOKEN, token) }
        }

    /**
     * Whether the app asked Android for [permission] before (T6.5). Android can't tell "never asked"
     * from "Don't allow" chosen twice; with this it can.
     */
    fun wasAsked(permission: String): Boolean = prefs.getBoolean(KEY_ASKED + permission, false)

    /** Records that Android was asked for [permissions] (T6.5). */
    fun markAsked(permissions: List<String>) = prefs.edit { permissions.forEach { putBoolean(KEY_ASKED + it, true) } }

    /** A granted permission starts over: if it is revoked in Settings, Android asks again. */
    fun clearAsked(permissions: List<String>) = prefs.edit { permissions.forEach { remove(KEY_ASKED + it) } }

    private companion object {
        const val KEY_ASKED = "asked_"
        const val KEY_TOGGLE_TOKEN = "toggle_token"
        const val KEY_LINK_MENU = "link_menu"
        const val KEY_AD_BLOCKING = "ad_blocking"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_APP_BANNERS = "app_banners"
        const val KEY_OPTIONS_BUILD = "options_build"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_UA_CHOICE = "ua_user_choice"
        const val KEY_UA_LEARNED_DESKTOP = "ua_learned_desktop"
    }
}
