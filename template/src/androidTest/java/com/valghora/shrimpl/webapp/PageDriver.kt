package com.valghora.shrimpl.webapp

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Drives the template's real WebView in its real [MainActivity] (docs/BUILD_NOTES.md, "Emulator"): it
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
    fun loadTestPage() {
        instrumentation.runOnMainSync {
            webView.stopLoading()
            webView.loadDataWithBaseURL(ORIGIN, "<!doctype html><html><body><p id=\"marker\">test page</p></body></html>", "text/html", "utf-8", null)
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
