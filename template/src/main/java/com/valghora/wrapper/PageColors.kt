package com.valghora.wrapper

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
import com.valghora.wrapper.core.DesktopSessionController
import com.valghora.wrapper.core.UrlClass

/**
 * The page's colors along its top and bottom edges, for the Dynamic header (docs/UNIWRAP_SPEC.md R7):
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
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            Log.w(WebApp.profile.appId, "Dynamic header disabled: WebView lacks WEB_MESSAGE_LISTENER or DOCUMENT_START_SCRIPT")
            return false
        }
        WebViewCompat.addWebMessageListener(webView, "AndroidPageColors", WebApp.profile.allowedOriginRules, listener)
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, WebApp.profile.allowedOriginRules)
        return true
    }

    private companion object {
        val MESSAGE = Regex("(#[0-9A-F]{6}),(#[0-9A-F]{6})")

        // Samples three points along each edge and keeps the most common background, walking up to the
        // first ancestor with an opaque one. Computed colors may be oklab() and the like; painting one
        // pixel yields plain sRGB. Reports at most every 200 ms, and only changes.
        val SCRIPT = """
            (() => {
              if (window.top !== window || window.__uniwrapPageColors || !window.AndroidPageColors) return;
              window.__uniwrapPageColors = true;
              const canvas = document.createElement('canvas');
              canvas.width = canvas.height = 1;
              const ctx = canvas.getContext('2d', { willReadFrequently: true });
              const hex = (color) => {
                ctx.clearRect(0, 0, 1, 1);
                ctx.fillStyle = '#000';
                ctx.fillStyle = color;
                ctx.fillRect(0, 0, 1, 1);
                const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data;
                return a < 128 ? null : '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase();
              };
              const background = (el) => {
                for (; el; el = el.parentElement) {
                  const color = hex(getComputedStyle(el).backgroundColor);
                  if (color) return color;
                }
                return hex(getComputedStyle(document.body || document.documentElement).backgroundColor) ||
                  hex(getComputedStyle(document.documentElement).backgroundColor) || '#FFFFFF';
              };
              const sample = (y) => {
                const counts = {};
                for (const fx of [0.1, 0.5, 0.9]) {
                  const color = background(document.elementFromPoint(Math.floor(innerWidth * fx), y));
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
              let pending = false;
              const schedule = () => {
                if (pending) return;
                pending = true;
                setTimeout(() => { pending = false; requestAnimationFrame(report); }, 200);
              };
              addEventListener('DOMContentLoaded', schedule);
              addEventListener('load', schedule);
              addEventListener('resize', schedule);
              addEventListener('scroll', schedule, { passive: true, capture: true });
              matchMedia('(prefers-color-scheme: dark)').addEventListener('change', schedule);
              new MutationObserver(schedule).observe(document, {
                subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'style'],
              });
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
