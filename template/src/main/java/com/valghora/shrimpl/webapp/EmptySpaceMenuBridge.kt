package com.valghora.shrimpl.webapp

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.profile.allowedOriginRules
import org.json.JSONException
import org.json.JSONObject

/**
 * The context menu on empty space (docs/TEMPLATE_SPEC.md T5.5): a right-click or long press on nothing
 * the page uses shows the menu with App options alone.
 *
 * Android reports a press on text and one on empty space alike, so the page decides: a document-start
 * script on the internal origins hears the `contextmenu` event once the site's own handlers have run,
 * and asks for the menu only when the site didn't show a menu of its own (`preventDefault`) and the
 * press isn't on text, a selection, a field, media, a link or an image. Text selection and the site's
 * own menus are untouched.
 */
class EmptySpaceMenuBridge(
    private val controller: DesktopSessionController,
    /** Shows the menu at a point in the WebView, in its own pixels. */
    private val onEmptySpace: (x: Float, y: Float) -> Unit,
) {
    private var webView: WebView? = null

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
        val view = webView ?: return@WebMessageListener
        if (!isMainFrame || controller.classify(sourceOrigin.toString()) != UrlClass.INTERNAL) return@WebMessageListener
        try {
            val point = JSONObject(message.data ?: return@WebMessageListener)
            // CSS pixels to the WebView's: the page is as wide as the WebView (width=device-width).
            val scale = view.width / point.getDouble("w")
            onEmptySpace((point.getDouble("x") * scale).toFloat(), (point.getDouble("y") * scale).toFloat())
        } catch (_: JSONException) {
            // Not this script's message: nothing to show.
        }
    }

    /** Adds the listener and script to [webView] before its first load, if WebView supports both (T5.5). */
    fun install(webView: WebView) {
        this.webView = webView
        // Invariant: each androidx.webkit call sits directly inside its own isFeatureSupported check,
        // nested, never `a && b`: lint's RequiresFeature check can't follow anything else (T1.3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addWebMessageListener(webView, BRIDGE, WebApp.profile.allowedOriginRules, listener)
                WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, WebApp.profile.allowedOriginRules)
            }
        }
    }

    private companion object {
        const val BRIDGE = "AndroidEmptyMenu"

        const val SCRIPT = """
(function () {
  if (window !== window.top || window.__shrimplEmptyMenu || !window.AndroidEmptyMenu) return;
  window.__shrimplEmptyMenu = true;
  var USED = 'a[href], img, picture, svg, video, audio, canvas, iframe, input, textarea, select, button, label, ' +
    '[contenteditable], [role="button"], [role="link"], [role="textbox"], [role="menuitem"]';
  // Whether the point is on text: the caret's text node has a box under the point.
  function onText(x, y) {
    var range = document.caretRangeFromPoint && document.caretRangeFromPoint(x, y);
    if (!range || range.startContainer.nodeType !== Node.TEXT_NODE) return false;
    var text = document.createRange();
    text.selectNodeContents(range.startContainer);
    var boxes = text.getClientRects();
    for (var i = 0; i < boxes.length; i++) {
      var b = boxes[i];
      if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) return true;
    }
    return false;
  }
  window.addEventListener('contextmenu', function (event) {
    var target = event.target;
    if (target && target.closest && target.closest(USED)) return;
    if (target && target.isContentEditable) return;
    var selection = window.getSelection();
    if (selection && !selection.isCollapsed) return;
    if (onText(event.clientX, event.clientY)) return;
    var point = JSON.stringify({ x: event.clientX, y: event.clientY, w: window.innerWidth });
    // This listener runs before the site's; by the next task every handler has, so a menu of the
    // site's own (preventDefault) is known.
    setTimeout(function () {
      if (!event.defaultPrevented) AndroidEmptyMenu.postMessage(point);
    }, 0);
  });
})();
"""
    }
}
