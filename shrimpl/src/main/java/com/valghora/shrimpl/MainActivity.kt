package com.valghora.shrimpl

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valghora.shrimpl.ui.AppShortcut
import com.valghora.shrimpl.ui.ShrimplNavigation
import com.valghora.shrimpl.ui.theme.ShrimplTheme
import com.valghora.shrimpl.ui.theme.isDark
import com.valghora.shrimpl.update.UpdateResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Shrimpl's single activity. It never opts out of recreation (no android:configChanges): state is
 * kept in saved state and ViewModels instead (docs/ADAPTIVE.md).
 */
class MainActivity : ComponentActivity() {
    /**
     * Keyboard shortcuts and notification actions for navigation. A channel holds one sent before the
     * screen collects it (a notification opening Shrimpl), and hands each to it once.
     */
    private val shortcuts = Channel<AppShortcut>(capacity = Channel.BUFFERED)

    /** Whether the window shares the screen (snapped, split screen, a desktop window), which sets when two panes show (R1.1). */
    private val sharingScreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sharingScreen.value = isInMultiWindowMode
        if (savedInstanceState == null) handleUpdateIntent(intent)
        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark()
            // Transparent system bars with icons for the app's own theme (which can differ from the
            // device's): the screens' body color shows through, and Android adds no contrast scrim
            // behind the 3-button navigation bar, as it does for the automatic style.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ShrimplTheme(theme) {
                ShrimplNavigation(shortcuts.receiveAsFlow(), sharingScreen = sharingScreen.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    /**
     * The update notification's intents (docs/SHRIMPL_SPEC.md R14.5): selecting it opens Settings, and
     * its Update action opens Settings and starts installing the release it announced.
     */
    private fun handleUpdateIntent(intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_UPDATES -> shortcuts.trySend(AppShortcut.SETTINGS)
            ACTION_INSTALL_UPDATE -> {
                shortcuts.trySend(AppShortcut.SETTINGS)
                (container.updates.status.value.result as? UpdateResult.Available)?.let { container.updates.install(it.release) }
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        sharingScreen.value = isInMultiWindowMode
    }

    /** Ctrl+N: new web app. Ctrl+,: settings. Works wherever focus is. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.repeatCount == 0) {
            val shortcut = when (keyCode) {
                KeyEvent.KEYCODE_N -> AppShortcut.NEW_WEB_APP
                KeyEvent.KEYCODE_COMMA -> AppShortcut.SETTINGS
                else -> null
            }
            if (shortcut != null) return shortcuts.trySend(shortcut).isSuccess
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

    companion object {
        /** The update notification opens Settings (R14.5). */
        const val ACTION_SHOW_UPDATES = "com.valghora.shrimpl.action.SHOW_UPDATES"

        /** The update notification's Update action: Settings, and the update starts (R14.5). */
        const val ACTION_INSTALL_UPDATE = "com.valghora.shrimpl.action.INSTALL_UPDATE"
    }
}
