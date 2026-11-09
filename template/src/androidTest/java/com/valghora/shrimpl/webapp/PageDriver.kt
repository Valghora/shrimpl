package com.valghora.shrimpl.webapp

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Drives the template's real WebView in its real [MainActivity] (docs/TESTING.md, "The emulator's environment"): it
 * replaces the start page with a test page whose origin is the web app's own (https://example.com, the
 * neutral template's internal domain), so the document-start scripts and message listeners of the
 * bridges (T6.15 to T6.18) apply exactly as they do for a real site, then runs JavaScript in it.
 */
class PageDriver(private val scenario: ActivityScenario<MainActivity>) {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private val webView: WebView
        get() {
            var found: WebView? = null
            scenario.onActivity { found = findWebView(it.window.decorView) }
            return requireNotNull(found) { "MainActivity has no WebView" }
        }

    /**
     * Cancels the start page's load and loads a page of our own at the same origin. Waiting for the start page
     * to finish first took 10 to 20 s of every test; MainActivity has issued its load by the time the
     * scenario is resumed, so stopping it can't be overtaken by a later one.
     */
    fun loadTestPage(body: String = "") {
        val page = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>" +
            "<body><p id=\"marker\">test page</p>$body</body></html>"
        instrumentation.runOnMainSync {
            webView.stopLoading()
            webView.loadDataWithBaseURL(ORIGIN, page, "text/html", "utf-8", null)
        }
        awaitJs("document.getElementById('marker') !== null && location.origin === '$ORIGIN'", "the test page to load at $ORIGIN")
        // Let the swap of the old page for ours finish, then check ours is still the one showing.
        Thread.sleep(1_000)
        awaitJs("document.getElementById('marker') !== null", "the test page to stay loaded")
        // The bridges inject objects named Android*; a missing one means the origin rules didn't match.
        val bridges = js("Object.keys(window).filter(k => k.startsWith('Android')).sort().join(',')")
        check(bridges.contains("AndroidDownload") && bridges.contains("AndroidShare") && bridges.contains("AndroidFiles")) {
            "the test page is missing a bridge; it has: '$bridges'"
        }
    }

    /**
     * Taps the middle of the element [selector] finds with a real touch, as a user would. Scripts can't
     * stand in for it where the page needs a user gesture, such as opening a file chooser (T6.4).
     */
    fun tap(selector: String) {
        val box = JSONObject(js("JSON.stringify((() => { const r = document.querySelector('$selector').getBoundingClientRect(); " +
            "return { x: r.left + r.width / 2, y: r.top + r.height / 2, width: window.innerWidth }; })())"))
        val origin = IntArray(2)
        var widthPx = 0
        instrumentation.runOnMainSync {
            webView.getLocationOnScreen(origin)
            widthPx = webView.width
        }
        // CSS pixels to screen pixels: the page is as wide as the WebView (width=device-width).
        val scale = widthPx / box.getDouble("width")
        val x = origin[0] + (box.getDouble("x") * scale).toInt()
        val y = origin[1] + (box.getDouble("y") * scale).toInt()
        check(UiDevice.getInstance(instrumentation).click(x, y)) { "couldn't tap $selector at $x, $y" }
    }

    /**
     * Evaluates [script] in the page and returns its JSON-decoded value as text ("null" for no value), or
     * null when WebView never answers: it drops the callback of a script that was running when its page
     * was replaced, so a poll must treat silence as "not yet".
     */
    fun jsOrNull(script: String, timeoutMs: Long = 15_000): String? {
        val latch = CountDownLatch(1)
        var raw: String? = null
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) {
                raw = it
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        val value = JSONTokener(raw ?: "null").nextValue()
        return if (value == JSONObject.NULL) "null" else value.toString()
    }

    /** Like [jsOrNull], but silence is a failure. */
    fun js(script: String): String = checkNotNull(jsOrNull(script)) { "JavaScript didn't answer: $script" }

    fun awaitJs(condition: String, what: String, timeoutMs: Long = 20_000) {
        awaitUntil(what, timeoutMs) { jsOrNull(condition, 3_000) == "true" }
    }

    /**
     * Runs the async [body] in the page and returns the text it put in `window.__r`; an exception
     * becomes "error:Name:message", so a test can assert on the web standard's error types.
     */
    fun runAsync(body: String, timeoutMs: Long = 30_000): String {
        js("window.__r = undefined; (async () => { try { $body } catch (e) { window.__r = 'error:' + e.name + ':' + e.message; } })(); 0")
        awaitJs("window.__r !== undefined", "the page's async script to finish: $body", timeoutMs)
        return js("window.__r")
    }

    private companion object {
        const val ORIGIN = "https://example.com"
    }
}

/** Polls [condition] until it holds, failing with a message naming [what] after [timeoutMs]. */
fun awaitUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(200)
    }
    throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for $what")
}
