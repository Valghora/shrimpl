package com.valghora.shrimpl.webapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.core.content.ContextCompat
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.WakeEvent

/** Watches screen, display and power-save state and reports SLEEP/WAKE transitions (docs/TEMPLATE_SPEC.md T8.1). */
class WorkspaceMonitor(
    private val context: Context,
    private val controller: DesktopSessionController,
    private val onEvent: (WakeEvent) -> Unit,
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var registered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            isWorkspaceAwake(context)
        }

        override fun onDisplayRemoved(displayId: Int) {
            isWorkspaceAwake(context)
        }

        override fun onDisplayChanged(displayId: Int) {
            isWorkspaceAwake(context)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isWorkspaceAwake(context)
        }
    }

    fun register() {
        if (registered) return
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        isWorkspaceAwake(context)
    }

    fun unregister() {
        if (!registered) return
        displayManager.unregisterDisplayListener(displayListener)
        context.unregisterReceiver(receiver)
        registered = false
    }

    /** Feeds the current power/display state to the controller's wake state machine. */
    fun isWorkspaceAwake(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val anyDisplayOn = context.getSystemService(DisplayManager::class.java).displays.any {
            it.state == Display.STATE_ON
        }
        val event = controller.onWakeInput(powerManager.isInteractive, anyDisplayOn, powerManager.isPowerSaveMode)
        if (event != WakeEvent.NONE) onEvent(event)
        return controller.isAwake
    }
}
