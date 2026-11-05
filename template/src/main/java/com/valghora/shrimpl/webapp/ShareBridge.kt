package com.valghora.shrimpl.webapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.profile.allowedOriginRules
import com.valghora.shrimpl.core.share.PageShare
import com.valghora.shrimpl.core.share.ShareContent
import org.json.JSONObject

/**
 * The Web Share API, which WebView lacks (docs/TEMPLATE_SPEC.md T6.16): a document-start script on the
 * internal origins adds `navigator.share` and `navigator.canShare` for titles, text and links, and
 * posts each share to a `WebMessageListener`, which opens Android's share sheet and answers the page's
 * promise. Files aren't shared: `canShare` says so, and sites then offer a download instead (T6.15).
 */
class ShareBridge(private val activity: Activity, private val controller: DesktopSessionController) {

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, reply ->
        if (controller.classify(sourceOrigin.toString()) == UrlClass.INTERNAL) {
            message.data?.let { data -> PageShare.parse(data)?.let { share(it, reply) } }
        }
    }

    /** Adds the listener and script to [webView] before its first load, if WebView supports both (T6.16). */
    fun install(webView: WebView) {
        // Invariant: each androidx.webkit call sits directly inside its own isFeatureSupported check,
        // nested, never `a && b`: lint's RequiresFeature check can't follow anything else (T1.3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addWebMessageListener(webView, BRIDGE, WebApp.profile.allowedOriginRules, listener)
                WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, WebApp.profile.allowedOriginRules)
            }
        }
    }

    /**
     * Opens the share sheet; the page's promise resolves once it is open, since Android doesn't say
     * whether the user went on to share.
     */
    private fun share(content: ShareContent, reply: JavaScriptReplyProxy) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, content.text)
        if (content.subject.isNotEmpty()) {
            send.putExtra(Intent.EXTRA_SUBJECT, content.subject).putExtra(Intent.EXTRA_TITLE, content.subject)
        }
        val opened = try {
            activity.startActivity(Intent.createChooser(send, null))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
        reply.postMessage(JSONObject().put("id", content.id).put("ok", opened).toString())
    }

    private companion object {
        const val BRIDGE = "AndroidShare"

        const val SCRIPT = """
(function () {
  if (navigator.share || !window.AndroidShare) return;
  var next = 0;
  var waiting = new Map();
  AndroidShare.onmessage = function (event) {
    var answer = JSON.parse(event.data);
    var promise = waiting.get(answer.id);
    if (!promise) return;
    waiting.delete(answer.id);
    if (answer.ok) promise.resolve(); else promise.reject(new DOMException('Share canceled', 'AbortError'));
  };
  function shareable(data) {
    return !!data && !(data.files && data.files.length) && !!(data.title || data.text || data.url);
  }
  navigator.canShare = function (data) { return shareable(data); };
  navigator.share = function (data) {
    if (data && data.files && data.files.length) {
      return Promise.reject(new DOMException('Sharing files is not supported', 'NotAllowedError'));
    }
    if (!shareable(data)) return Promise.reject(new TypeError('Nothing to share'));
    var url = '';
    try { if (data.url) url = new URL(String(data.url), location.href).href; } catch (e) {
      return Promise.reject(new TypeError('Invalid URL'));
    }
    var id = ++next;
    return new Promise(function (resolve, reject) {
      waiting.set(id, { resolve: resolve, reject: reject });
      AndroidShare.postMessage(JSON.stringify({ id: id, title: String(data.title || ''), text: String(data.text || ''), url: url }));
    });
  };
})();
"""
    }
}
