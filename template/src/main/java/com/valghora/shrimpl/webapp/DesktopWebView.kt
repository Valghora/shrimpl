package com.valghora.shrimpl.webapp

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
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

    /**
     * Shows the app's context menu at a point in this view, or returns false for the page to have the
     * press (T5.5). Null when the profile leaves the menu to the site.
     */
    var contextMenuHandler: ((x: Float, y: Float) -> Boolean)? = null

    /** Where the last touch or click went down, for a long press, which reports no point of its own. */
    private var lastDownX = 0f
    private var lastDownY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastDownX = event.x
            lastDownY = event.y
        }
        return super.dispatchTouchEvent(event)
    }

    // A long press, a right-click and the menu key all come here, as they would to a registered menu.
    override fun showContextMenu(): Boolean =
        contextMenuHandler?.invoke(lastDownX, lastDownY) ?: super.showContextMenu()

    override fun showContextMenu(x: Float, y: Float): Boolean =
        contextMenuHandler?.invoke(x, y) ?: super.showContextMenu(x, y)

    /**
     * Follows the workspace wake state (T8.2): asleep, the page reports itself hidden so it stops rendering.
     */
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
