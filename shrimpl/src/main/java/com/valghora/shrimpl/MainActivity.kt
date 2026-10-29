package com.valghora.shrimpl

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valghora.shrimpl.ui.AppShortcut
import com.valghora.shrimpl.ui.ShrimplNavigation
import com.valghora.shrimpl.ui.theme.ShrimplTheme
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Shrimpl's single activity. It never opts out of recreation (no android:configChanges): state is
 * kept in saved state and ViewModels instead (docs/ADAPTIVE.md).
 */
class MainActivity : ComponentActivity() {
    private val shortcuts = MutableSharedFlow<AppShortcut>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle()
            ShrimplTheme(theme) {
                ShrimplNavigation(shortcuts)
            }
        }
    }

    /** Ctrl+N: new web app. Ctrl+,: settings. Works wherever focus is. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.repeatCount == 0) {
            val shortcut = when (keyCode) {
                KeyEvent.KEYCODE_N -> AppShortcut.NEW_WEB_APP
                KeyEvent.KEYCODE_COMMA -> AppShortcut.SETTINGS
                else -> null
            }
            if (shortcut != null) return shortcuts.tryEmit(shortcut)
        }
        return super.onKeyShortcut(keyCode, event)
    }

    /** Lists the shortcuts in the system's keyboard shortcuts helper (Meta+/). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += KeyboardShortcutGroup(
            getString(R.string.app_name),
            listOf(
                KeyboardShortcutInfo(getString(R.string.action_new_web_app), KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.settings_title), KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON),
            ),
        )
    }
}
