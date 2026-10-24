package com.valghora.wrapper

import android.content.Context
import androidx.core.content.edit
import com.valghora.wrapper.core.AppConfig
import com.valghora.wrapper.core.UaMode
import com.valghora.wrapper.core.UaStore
import com.valghora.wrapper.core.ZoomStore

/** ZoomStore and UaStore backed by one private SharedPreferences file (R6.1). */
class SessionPrefs(context: Context) : ZoomStore, UaStore {

    private val prefs = context.getSharedPreferences("${AppConfig.APP_ID}_session", Context.MODE_PRIVATE)

    override fun load(): Int? = if (prefs.contains(KEY_TEXT_ZOOM)) prefs.getInt(KEY_TEXT_ZOOM, 0) else null

    override fun save(value: Int) = prefs.edit { putInt(KEY_TEXT_ZOOM, value) }

    override var userChoice: UaMode?
        get() = prefs.getString(KEY_UA_CHOICE, null)?.let { name -> UaMode.entries.firstOrNull { it.name == name } }
        set(value) = prefs.edit { if (value == null) remove(KEY_UA_CHOICE) else putString(KEY_UA_CHOICE, value.name) }

    override var learnedDesktop: Boolean
        get() = prefs.getBoolean(KEY_UA_LEARNED_DESKTOP, false)
        set(value) = prefs.edit { putBoolean(KEY_UA_LEARNED_DESKTOP, value) }

    private companion object {
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_UA_CHOICE = "ua_user_choice"
        const val KEY_UA_LEARNED_DESKTOP = "ua_learned_desktop"
    }
}
