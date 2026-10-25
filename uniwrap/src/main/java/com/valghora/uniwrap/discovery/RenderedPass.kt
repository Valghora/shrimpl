package com.valghora.uniwrap.discovery

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.valghora.wrapper.core.discovery.PageInfo
import com.valghora.wrapper.core.discovery.Request
import java.util.Collections
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What one hidden-WebView visit saw (docs/UNIWRAP_SPEC.md R9.3). */
class RenderedVisit(
    val finalUrl: String?,
    val page: PageInfo?,
    val text: String?,
    /** Requests seen by the WebView client plus the page's resource timing entries. */
    val requests: List<Request>,
    /** Script URLs to scan for API use, and the page's inline script text. */
    val scriptUrls: List<String>,
    val inlineScripts: String,
    /** Main-frame navigations, including blocked market: and intent: ones. */
    val navigations: List<String>,
    /** HTTP status of the main document when it failed (>= 400). */
    val errorStatus: Int?,
)

/**
 * Loads a page in an offscreen WebView, the closest thing on the device to the browser the retired
 * collect.js drove: a desktop-size viewport in light or dark theme, or a phone viewport with the plain
 * WebView user agent. Must be used from the main thread's coroutine context (WebView's rule).
 */
class RenderedPass(private val context: Context) {
    enum class Mode(val widthDp: Int, val heightDp: Int, val night: Boolean, val desktopAgent: Boolean) {
        DESKTOP_LIGHT(1280, 800, night = false, desktopAgent = true),
        DESKTOP_DARK(1280, 800, night = true, desktopAgent = true),
        WEBVIEW(412, 915, night = false, desktopAgent = false),
    }

    @Serializable
    private class Collected(
        val url: String? = null,
        val page: PageInfo? = null,
        val text: String? = null,
        val resources: List<Request> = emptyList(),
        val inlineScripts: String = "",
    )

    private val collector: String by lazy {
        context.assets.open("discovery/collect_page.js").bufferedReader().use { it.readText() }
    }

    /** Visits [url]; null if the page did not finish loading within [timeoutMs]. */
    suspend fun visit(url: String, mode: Mode, timeoutMs: Long): RenderedVisit? = withContext(Dispatchers.Main) {
        val webView = WebView(themedContext(mode.night))
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val navigations = mutableListOf<String>()
        var errorStatus: Int? = null
        var rendererGone = false
        try {
            configure(webView, mode)
            val finished = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val type = if (request.isForMainFrame) "document" else "other"
                            requests += Request(request.url.toString(), type)
                            return null
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            val target = request.url.toString()
                            navigations += target
                            // Store redirects are recorded, not followed; nothing else leaves the hidden page.
                            return !(target.startsWith("https://") || target.startsWith("http://"))
                        }

                        override fun onPageStarted(view: WebView, startedUrl: String, favicon: Bitmap?) {
                            if (navigations.lastOrNull() != startedUrl) navigations += startedUrl
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) errorStatus = response.statusCode
                        }

                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        /** A crashed or killed renderer ends this visit; it must not take UniWrap down with it. */
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            rendererGone = true
                            if (continuation.isActive) continuation.resume(Unit)
                            return true
                        }
                    }
                    webView.loadUrl(url)
                }
                // Let client-side apps render and fetch for a moment before reading the page.
                delay(SETTLE_MS)
                true
            }
            if (finished != true || rendererGone) return@withContext null
            val raw = evaluate(webView, collector)
            val collected = runCatching { json.decodeFromString(Collected.serializer(), raw) }.getOrNull()
            RenderedVisit(
                finalUrl = collected?.url ?: webView.url,
                page = collected?.page,
                text = collected?.text,
                requests = requests.toList() + collected?.resources.orEmpty(),
                scriptUrls = collected?.resources.orEmpty().filter { it.type == "script" }.map { it.url }.distinct(),
                inlineScripts = collected?.inlineScripts.orEmpty(),
                navigations = navigations.toList(),
                errorStatus = errorStatus,
            )
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun configure(webView: WebView, mode: Mode) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkImage = true // discovery reads metadata, not pixels of images
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        if (mode.desktopAgent) settings.userAgentString = desktopUserAgent(WebSettings.getDefaultUserAgent(context))
        // Lay the view out offscreen at the viewport size the page should see.
        val density = context.resources.displayMetrics.density
        val width = (mode.widthDp * density).toInt()
        val height = (mode.heightDp * density).toInt()
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, width, height)
    }

    /** A context whose theme makes the page's prefers-color-scheme light or dark. */
    private fun themedContext(night: Boolean): Context {
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return ContextThemeWrapper(context.createConfigurationContext(config), android.R.style.Theme_DeviceDefault_DayNight)
    }

    private suspend fun evaluate(webView: WebView, script: String): String = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            // The result is the returned JSON string, itself encoded as a JSON string literal.
            val decoded = runCatching { json.decodeFromString<String>(result) }.getOrDefault("")
            if (continuation.isActive) continuation.resume(decoded)
        }
    }

    companion object {
        private const val SETTLE_MS = 2_500L
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val CHROME_VERSION = Regex("Chrome/(\\d[\\d.]*)")

        /** A desktop Linux Chrome user agent with the WebView's own Chrome version (as the template uses). */
        fun desktopUserAgent(defaultUserAgent: String): String {
            val version = CHROME_VERSION.find(defaultUserAgent)?.groupValues?.get(1) ?: "140.0.0.0"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
        }
    }
}
