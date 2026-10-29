package com.valghora.shrimple.webapp

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * WebView whose visibility follows the workspace wake state instead of the window (docs/TEMPLATE_SPEC.md T8.2):
 * asleep reports GONE so rendering stops; awake with KEEP_ALIVE_WHEN_HIDDEN reports VISIBLE
 * so the page keeps running while minimized or covered.
 */
class DesktopWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    private var awake = true

    fun setWorkspaceAwake(awake: Boolean) {
        if (this.awake == awake) return
        this.awake = awake
        onWindowVisibilityChanged(windowVisibility)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        when {
            !awake -> super.onWindowVisibilityChanged(GONE)
            WebApp.profile.keepAliveWhenHidden -> super.onWindowVisibilityChanged(VISIBLE)
            else -> super.onWindowVisibilityChanged(visibility)
        }
    }
}
