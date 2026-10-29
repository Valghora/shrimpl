package com.valghora.shrimple.webapp

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.Log
import android.webkit.WebView
import androidx.core.graphics.toColorInt
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.shrimple.core.DesktopSessionController
import com.valghora.shrimple.core.UrlClass
import com.valghora.shrimple.core.profile.allowedOriginRules

/**
 * The page's colors along its top and bottom edges, for the Dynamic header (docs/TEMPLATE_SPEC.md T3.3):
 * a document-start script samples them after loading, scrolling, resizing and theme changes, and
 * posts them here; [onColors] gets them on the main thread whenever they change.
 */
class PageColors(private val controller: DesktopSessionController, private val onColors: (top: Int, bottom: Int) -> Unit) {
    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || controller.classify(sourceOrigin.toString()) != UrlClass.INTERNAL) return@WebMessageListener
        val match = MESSAGE.matchEntire(message.data.orEmpty()) ?: return@WebMessageListener
        onColors(match.groupValues[1].toColorInt(), match.groupValues[2].toColorInt())
    }

    /** False when the WebView can't run the script; the header then keeps the system color. */
    fun install(webView: WebView): Boolean {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addWebMessageListener(webView, "AndroidPageColors", WebApp.profile.allowedOriginRules, listener)
                WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, WebApp.profile.allowedOriginRules)
                return true
            }
        }
        Log.w(WebApp.profile.appId, "Dynamic header disabled: WebView lacks WEB_MESSAGE_LISTENER or DOCUMENT_START_SCRIPT")
        return false
    }

    private companion object {
        val MESSAGE = Regex("(#[0-9A-F]{6}),(#[0-9A-F]{6})")

        // Samples three points along each edge and keeps the most common color: the topmost opaque
        // background at each point that isn't part of an overlay (a dialog, or a fixed panel narrower
        // than the window such as a drawer), so opening a drawer or its see-through scrim doesn't change
        // the bars. Computed colors may be oklab() and the like; painting one pixel yields plain sRGB.
        // Re-samples only on page loads (and twice after, for apps that render late), in-app
        // navigation, resizing, and theme changes on <html> or <body>; reports only changes.
        val SCRIPT = """
            (() => {
              if (window.top !== window || window.__shrimplePageColors || !window.AndroidPageColors) return;
              window.__shrimplePageColors = true;
              const canvas = document.createElement('canvas');
              canvas.width = canvas.height = 1;
              const ctx = canvas.getContext('2d', { willReadFrequently: true });
              const opaque = (color) => {
                ctx.clearRect(0, 0, 1, 1);
                ctx.fillStyle = '#000';
                ctx.fillStyle = color;
                ctx.fillRect(0, 0, 1, 1);
                const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data;
                return a < 240 ? null : '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase();
              };
              const overlay = (el) => {
                for (; el && el !== document.body; el = el.parentElement) {
                  if (el.matches('dialog[open], [role="dialog"], [role="alertdialog"], [aria-modal="true"]')) return true;
                  if (getComputedStyle(el).position === 'fixed' && el.getBoundingClientRect().width < innerWidth * 0.9) return true;
                }
                return false;
              };
              const at = (x, y) => {
                for (const el of document.elementsFromPoint(x, y)) {
                  if (overlay(el)) continue;
                  const color = opaque(getComputedStyle(el).backgroundColor);
                  if (color) return color;
                }
                return opaque(getComputedStyle(document.documentElement).backgroundColor) || '#FFFFFF';
              };
              const sample = (y) => {
                const counts = {};
                for (const fx of [0.1, 0.5, 0.9]) {
                  const color = at(Math.floor(innerWidth * fx), y);
                  counts[color] = (counts[color] || 0) + 1;
                }
                return Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
              };
              let last = '';
              const report = () => {
                if (!document.body || innerHeight < 4) return;
                const colors = sample(1) + ',' + sample(innerHeight - 2);
                if (colors !== last) {
                  last = colors;
                  AndroidPageColors.postMessage(colors);
                }
              };
              let timer = 0;
              const schedule = (delay = 300) => {
                clearTimeout(timer);
                timer = setTimeout(() => requestAnimationFrame(report), delay);
              };
              const settle = () => { schedule(); setTimeout(() => schedule(), 1000); setTimeout(() => schedule(), 3000); };
              addEventListener('DOMContentLoaded', () => schedule());
              addEventListener('load', settle);
              addEventListener('pageshow', () => schedule());
              addEventListener('resize', () => schedule());
              addEventListener('popstate', settle);
              addEventListener('hashchange', settle);
              for (const name of ['pushState', 'replaceState']) {
                const original = history[name];
                history[name] = function (...args) {
                  const result = original.apply(this, args);
                  settle();
                  return result;
                };
              }
              matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => schedule());
              const themeObserver = new MutationObserver(() => schedule());
              const watch = () => {
                const options = { attributes: true, attributeFilter: ['class', 'style', 'data-theme', 'data-color-mode'] };
                themeObserver.observe(document.documentElement, options);
                if (document.body) themeObserver.observe(document.body, options);
              };
              if (document.body) watch(); else addEventListener('DOMContentLoaded', watch);
            })();
        """.trimIndent()
    }
}

/** The window background behind the system bars: [top] under the status bar, [bottom] everywhere else. */
class BarsBackground : Drawable() {
    var top = 0
    var bottom = 0
    var topHeight = 0
    private val paint = Paint()

    override fun draw(canvas: Canvas) {
        paint.color = bottom
        canvas.drawRect(bounds, paint)
        paint.color = top
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), (bounds.top + topHeight).toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
