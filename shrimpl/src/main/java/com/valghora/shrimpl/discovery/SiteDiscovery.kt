package com.valghora.shrimpl.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebSettings
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.discovery.Discovery
import com.valghora.shrimpl.core.discovery.DiscoveryException
import com.valghora.shrimpl.core.discovery.Draft
import com.valghora.shrimpl.core.discovery.Evidence
import com.valghora.shrimpl.core.discovery.HtmlHead
import com.valghora.shrimpl.core.discovery.IconChoice
import com.valghora.shrimpl.core.discovery.IconSources
import com.valghora.shrimpl.core.discovery.PublicSuffixList
import com.valghora.shrimpl.core.discovery.Request
import com.valghora.shrimpl.core.discovery.ScriptSignals
import com.valghora.shrimpl.core.discovery.SimpleIcon
import com.valghora.shrimpl.core.discovery.SimpleIcons
import com.valghora.shrimpl.core.discovery.SlimVisit
import com.valghora.shrimpl.core.discovery.Visit
import com.valghora.shrimpl.core.testing.TestSearch
import com.valghora.shrimpl.icons.IconImages
import com.valghora.shrimpl.testing.TestMode
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Turns a URL into a [Draft] on the device (docs/SHRIMPL_SPEC.md R9): a static fetch of the page and
 * its web manifest, a hidden-WebView pass for what only exists once rendered, then the core discovery
 * rules. Cancel the calling coroutine to stop it.
 */
/** Search stopped at [stage], with [message] to show under it (R3.2). */
class SearchFailure(val stage: SiteDiscovery.Stage, message: String) : Exception(message)

/** Discovery's draft, and the site's own icon for the Website option (a PNG of at most 512 px), if it has one. */
class Discovered(val draft: Draft, val websiteIcon: IconChoice.Raster?)

class SiteDiscovery(private val context: Context) {
    /** Stages shown while discovery runs (R3.2). */
    enum class Stage { FETCHING_PAGE, READING_MANIFEST, CHECKING_PAGE, FINDING_ICON }

    private val data by lazy { DiscoveryData.get(context) }

    suspend fun discover(input: String, onStage: suspend (Stage) -> Unit = {}): Discovered {
        val url = normalizeUrl(input) ?: throw DiscoveryException("Enter a web address, like example.com")
        TestMode.search(url)?.let { playBack(it, onStage) }
        val desktopAgent = RenderedPass.desktopUserAgent(WebSettings.getDefaultUserAgent(context))
        val network = Network(desktopAgent)

        // ----- Static pass -----
        onStage(Stage.FETCHING_PAGE)
        val document = withContext(Dispatchers.IO) {
            runInterruptible {
                try {
                    network.get(url)
                } catch (e: IOException) {
                    throw SearchFailure(Stage.FETCHING_PAGE, context.getString(loadFailure(e)))
                }
            }
        }
        // R3.2: sites behind bot protection often answer 403 to a plain request but let a browser in,
        // so a 403 only fails the search if the hidden browser is refused too (below).
        val refused = document.status == HTTP_FORBIDDEN
        httpFailure(document.status)?.let { throw SearchFailure(Stage.FETCHING_PAGE, context.getString(it)) }
        val staticPage = HtmlHead.parse(document.body.decodeToString(), document.finalUrl)

        onStage(Stage.READING_MANIFEST)
        var manifestUrl = HtmlHead.manifestUrl(staticPage)
        var manifest = manifestUrl?.let { fetchManifest(network, it) }

        // ----- Rendered pass (R9.3): at most 20 seconds over three visits; the static result stands otherwise -----
        onStage(Stage.CHECKING_PAGE)
        val rendered = RenderedPass(context)
        val desktop = rendered.visit(url, RenderedPass.Mode.DESKTOP_LIGHT, DESKTOP_TIMEOUT_MS)
        if (refused && (desktop == null || (desktop.errorStatus ?: 0) >= HTTP_ERROR)) {
            throw SearchFailure(Stage.CHECKING_PAGE, context.getString(R.string.search_denied))
        }
        val dark = desktop?.let { rendered.visit(it.finalUrl ?: url, RenderedPass.Mode.DESKTOP_DARK, SECONDARY_TIMEOUT_MS) }
        val webview = rendered.visit(url, RenderedPass.Mode.WEBVIEW, SECONDARY_TIMEOUT_MS)
        val page = desktop?.page ?: staticPage
        if (manifestUrl == null) {
            // Some single-page apps add the manifest link from script.
            manifestUrl = HtmlHead.manifestUrl(page)
            manifest = manifestUrl?.let { fetchManifest(network, it) }
        }
        val signals = withContext(Dispatchers.IO) { runInterruptible { scanScripts(network, desktop) } }

        val evidence = Evidence(
            desktop = Visit(
                requestedUrl = url,
                finalUrl = desktop?.finalUrl ?: document.finalUrl,
                status = document.status,
                headers = document.headers,
                redirectChain = document.redirectChain,
                requests = listOf(Request(document.finalUrl, "document")) + desktop?.requests.orEmpty(),
                signals = signals,
                page = page,
                manifestUrl = manifestUrl,
                manifest = manifest,
            ),
            dark = SlimVisit(topColor = dark?.page?.topColor),
            webview = SlimVisit(
                status = webview?.errorStatus,
                finalUrl = webview?.finalUrl,
                title = webview?.page?.title,
                text = webview?.text,
                navigations = webview?.navigations.orEmpty(),
            ),
        )

        onStage(Stage.FINDING_ICON)
        val sources = object : IconSources {
            override fun fetch(url: String) = network.bodyOrNull(url)
        }
        return withContext(Dispatchers.IO) {
            runInterruptible {
                val draft = Discovery.draft(evidence, url, data.psl, sources)
                // R10.1: the Website option, also when the best icon was a brand glyph. Stored small.
                val website = Discovery.websiteIcon(draft, sources)?.let { icon ->
                    runCatching { IconImages.squarePng(icon.bytes) }.getOrNull()
                        ?.let { png -> icon.copy(bytes = png, format = "png", size = minOf(icon.size, IconImages.MAX_PX)) }
                }
                Discovered(draft, website)
            }
        }
    }

    /** R13: a test search address goes through the steps up to its failing one, offline, and fails there. */
    private suspend fun playBack(test: TestSearch, onStage: suspend (Stage) -> Unit): Nothing {
        Stage.entries.take(test.stage + 1).forEach { onStage(it) }
        val message = when (test) {
            TestSearch.NOT_FOUND, TestSearch.NO_SUCH_HOST -> R.string.search_not_found
            TestSearch.DENIED -> R.string.search_denied
            TestSearch.UNAVAILABLE -> R.string.search_unavailable
            TestSearch.ERROR -> R.string.search_error
            TestSearch.OFFLINE -> R.string.search_offline
            TestSearch.TIMEOUT -> R.string.search_timeout
            TestSearch.CERTIFICATE -> R.string.search_certificate
            TestSearch.NOT_LOADED -> R.string.search_not_loaded
        }
        throw SearchFailure(Stage.entries[test.stage], context.getString(message))
    }

    /** R3.2: what a failed page load says, by what went wrong. */
    private fun loadFailure(e: IOException): Int = when {
        offline() -> R.string.search_offline
        e is UnknownHostException -> R.string.search_not_found
        e is SocketTimeoutException -> R.string.search_timeout
        e is SSLException -> R.string.search_certificate
        else -> R.string.search_not_loaded
    }

    /** R3.2: what an error status says; null when the page loaded (or for 403, decided later). */
    private fun httpFailure(status: Int): Int? = when {
        status < HTTP_ERROR || status == HTTP_FORBIDDEN -> null
        status == HTTP_NOT_FOUND -> R.string.search_not_found
        status >= HTTP_SERVER_ERROR -> R.string.search_unavailable
        else -> R.string.search_error
    }

    private fun offline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    }

    /** The simple-icons brand named [name] ("Discord" -> `discord`), or null; for "Continue without discovery" (R3.2). */
    suspend fun simpleIcon(name: String): SimpleIcon? = withContext(Dispatchers.IO) {
        runInterruptible {
            val network = Network(WebSettings.getDefaultUserAgent(context))
            val sources = object : IconSources {
                override fun fetch(url: String) = network.bodyOrNull(url)
            }
            SimpleIcons.find(listOf(name), sources)?.first
        }
    }

    /** Signals from inline scripts and up to [SCRIPT_BUDGET_BYTES] of the page's script files. */
    private fun scanScripts(network: Network, visit: RenderedVisit?): Map<String, Boolean> {
        if (visit == null) return emptyMap()
        val found = ScriptSignals.scan(visit.inlineScripts).toMutableSet()
        var budget = SCRIPT_BUDGET_BYTES
        for (scriptUrl in visit.scriptUrls) {
            if (budget <= 0 || found.size == ScriptSignals.PATTERNS.size) break
            val body = network.bodyOrNull(scriptUrl, maxBytes = minOf(budget, Network.MAX_BYTES)) ?: continue
            budget -= body.size
            found += ScriptSignals.scan(body.decodeToString())
        }
        return ScriptSignals.PATTERNS.keys.associateWith { it in found }
    }

    private suspend fun fetchManifest(network: Network, manifestUrl: String): JsonObject? = withContext(Dispatchers.IO) {
        runInterruptible {
            network.bodyOrNull(manifestUrl, MANIFEST_MAX_BYTES)?.let { bytes ->
                runCatching { Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject }.getOrNull()
            }
        }
    }

    companion object {
        private const val DESKTOP_TIMEOUT_MS = 12_000L
        private const val SECONDARY_TIMEOUT_MS = 4_000L
        private const val HTTP_ERROR = 400
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SERVER_ERROR = 500
        private const val MANIFEST_MAX_BYTES = 512 * 1024
        /** Scripts get their own 20 MB budget: large web apps keep their API calls in big bundles. */
        private const val SCRIPT_BUDGET_BYTES = 20 * 1024 * 1024

        /** "example.com" -> "https://example.com"; http is upgraded; null if it isn't a web address. */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val withScheme = when {
                trimmed.startsWith("https://", ignoreCase = true) -> "https://" + trimmed.substring(8)
                trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
                "://" in trimmed -> return null
                else -> "https://$trimmed"
            }
            val host = runCatching { java.net.URI(withScheme).host }.getOrNull() ?: return null
            return withScheme.takeIf { '.' in host }
        }
    }
}

/** Bundled discovery data, loaded once: the Public Suffix List. */
class DiscoveryData private constructor(val psl: PublicSuffixList) {
    companion object {
        @Volatile
        private var instance: DiscoveryData? = null

        fun get(context: Context): DiscoveryData = instance ?: synchronized(this) {
            instance ?: run {
                val assets = context.applicationContext.assets
                val psl = assets.open("discovery/public_suffix_list.dat").bufferedReader().use { PublicSuffixList(it.readText()) }
                DiscoveryData(psl).also { instance = it }
            }
        }
    }
}
