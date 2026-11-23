package com.valghora.shrimpl.webapp

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.shrimpl.core.DarkMode
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.profile.allowedOriginRules
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONException
import org.json.JSONObject

/**
 * Dark mode (docs/TEMPLATE_SPEC.md T6.21). The app's own theme turns dark or light with it, which is
 * also what the page's `prefers-color-scheme` reports, so a site with a dark design of its own shows it.
 * A site that stays light is darkened by Dark Reader (MIT, `assets/darkreader/`), which rewrites the
 * page's colors rather than inverting it, so images and video keep theirs.
 *
 * Dark Reader runs in the page and reads the site's style sheets to rewrite them; those on other
 * domains it can't read there, so it asks the app ([listener]), which fetches them without cookies.
 */
class DarkTheme(private val context: Context, private val controller: DesktopSessionController) {
    private var webView: WebView? = null
    private var script: ScriptHandler? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val library: String by lazy {
        context.assets.open(LIBRARY_ASSET).bufferedReader().use { it.readText() }
    }

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, reply ->
        if (controller.classify(sourceOrigin.toString()) != UrlClass.INTERNAL) return@WebMessageListener
        val request = try {
            JSONObject(message.data ?: return@WebMessageListener)
        } catch (_: JSONException) {
            return@WebMessageListener
        }
        val id = request.optInt("id")
        val url = request.optString("url")
        io.execute {
            val answer = fetch(url)?.let { (type, body) ->
                JSONObject().put("id", id).put("ok", true).put("type", type).put("body", Base64.encodeToString(body, Base64.NO_WRAP))
            } ?: JSONObject().put("id", id).put("ok", false)
            main.post { post(reply, answer.toString()) }
        }
    }

    /** Adds the fetch bridge and the script to [webView] before its first load, if WebView supports both. */
    fun install(webView: WebView) {
        this.webView = webView
        // Invariant: each androidx.webkit call sits directly inside its own isFeatureSupported check,
        // nested, never `a && b`: lint's RequiresFeature check can't follow anything else (T1.3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, WebApp.profile.allowedOriginRules, listener)
        }
        apply(reload = false)
    }

    /**
     * Applies the profile's dark mode: the script for the pages to come and, on the page showing now,
     * at once. [reload] loads the page again, for a change the user made, so the page also hears the new
     * `prefers-color-scheme`.
     */
    fun apply(reload: Boolean) {
        val view = webView ?: return
        val on = WebApp.profile.darkMode.isOn(deviceDark())
        script?.remove()
        script = null
        // Invariant: each androidx.webkit call sits directly inside its own isFeatureSupported check (T1.3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val source = "(function () {\nvar module = { exports: {} }, exports = module.exports;\n$library\n" +
                BOOTSTRAP.replace("\$ON", on.toString()) + "\n})();"
            script = WebViewCompat.addDocumentStartJavaScript(view, source, WebApp.profile.allowedOriginRules)
        }
        if (reload) view.reload() else view.evaluateJavascript("window.__shrimplDark && window.__shrimplDark.set($on);", null)
    }

    /** Stops the fetch thread once the activity is gone. */
    fun release() {
        io.shutdownNow()
        webView = null
    }

    private fun post(reply: JavaScriptReplyProxy, message: String) {
        if (webView == null) return
        // Invariant: each androidx.webkit call sits directly inside its own isFeatureSupported check (T1.3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            reply.postMessage(message)
        }
    }

    /**
     * A style sheet or image Dark Reader asked for: its type and bytes, or null. This reads past CORS, so
     * it is kept to what Dark Reader needs: only the app's own pages ask, without cookies, only style
     * sheets and images come back, and at most [MAX_BYTES].
     */
    private fun fetch(url: String): Pair<String, ByteArray>? {
        val scheme = url.toUri().scheme
        if (scheme != "https" && scheme != "http") return null
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            Log.w(WebApp.profile.appId, "Dark mode couldn't open $url: ${e.message}")
            return null
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val type = connection.contentType.orEmpty()
            if (!type.startsWith("text/css") && !type.startsWith("image/")) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val body = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    body.write(buffer, 0, read)
                    if (body.size() > MAX_BYTES) return null
                }
            }
            type to body.toByteArray()
        } catch (e: IOException) {
            Log.w(WebApp.profile.appId, "Dark mode couldn't fetch $url: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val LIBRARY_ASSET = "darkreader/darkreader.js"
        private const val BRIDGE = "AndroidDarkFetch"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_BYTES = 4L * 1024 * 1024
        private const val BUFFER = 16 * 1024

        /** Whether the device's own theme is dark now, whatever this app's theme is. */
        fun deviceDark(): Boolean =
            Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        /** The app theme's night mode for [mode]: the device's for SYSTEM. */
        fun nightModeFor(mode: DarkMode): Int = when (mode) {
            DarkMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            DarkMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            DarkMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        }

        /**
         * Runs after the library, in the same function, so `DarkReader` stays out of the page's globals.
         * Top frame only. While the page loads, a dark background stands in for it so it doesn't flash
         * white; once the document is parsed the cover is set aside to read the site's own background.
         * A dark one (or a transparent one on a dark color scheme) is the site's dark design, left as it
         * is; anything else gets Dark Reader.
         */
        private const val BOOTSTRAP = """
var DR = module.exports;
if (window === window.top && !window.__shrimplDark) {
  var on = ${'$'}ON, cover = null, waiting = false, pending = {}, next = 0;
  DR.setFetchMethod(function (url) {
    return new Promise(function (resolve, reject) {
      if (!window.AndroidDarkFetch) { reject(new Error('No fetch bridge')); return; }
      var id = ++next;
      pending[id] = { resolve: resolve, reject: reject };
      AndroidDarkFetch.postMessage(JSON.stringify({ id: id, url: url }));
    });
  });
  if (window.AndroidDarkFetch) {
    AndroidDarkFetch.onmessage = function (event) {
      var m = JSON.parse(event.data), p = pending[m.id];
      if (!p) return;
      delete pending[m.id];
      if (!m.ok) { p.reject(new Error('Not fetched')); return; }
      var raw = atob(m.body), bytes = new Uint8Array(raw.length);
      for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
      p.resolve(new Response(bytes, { headers: { 'Content-Type': m.type } }));
    };
  }
  var background = function (el) {
    var c = getComputedStyle(el).backgroundColor.match(/[\d.]+/g);
    if (!c || (c.length > 3 && +c[3] === 0)) return null;
    return c.slice(0, 3).map(Number);
  };
  var siteIsDark = function () {
    var root = document.documentElement;
    var bg = background(root) || (document.body && background(document.body));
    if (!bg) return /dark/.test(getComputedStyle(root).colorScheme);
    var max = Math.max(bg[0], bg[1], bg[2]), min = Math.min(bg[0], bg[1], bg[2]);
    return (max + min) / 2 / 255 < 0.5;
  };
  var removeCover = function () { if (cover) { cover.remove(); cover = null; } };
  var decide = function () {
    waiting = false;
    if (!on) { removeCover(); return; }
    if (cover) cover.disabled = true;
    var dark = siteIsDark();
    removeCover();
    if (!dark) DR.enable({ mode: 1, brightness: 100, contrast: 100, sepia: 0 });
  };
  var set = function (value) {
    on = value;
    if (!on) { removeCover(); if (DR.isEnabled()) DR.disable(); return; }
    if (DR.isEnabled() || waiting) return;
    if (document.readyState !== 'loading') { decide(); return; }
    waiting = true;
    cover = document.createElement('style');
    cover.textContent = 'html, body { background-color: #181a1b !important; }';
    (document.head || document.documentElement).appendChild(cover);
    document.addEventListener('DOMContentLoaded', decide, { once: true });
  };
  window.__shrimplDark = { set: set };
  set(on);
}
"""
    }
}
