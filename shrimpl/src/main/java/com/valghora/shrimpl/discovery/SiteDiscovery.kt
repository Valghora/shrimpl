package com.valghora.shrimpl.discovery

import android.content.Context
import android.webkit.WebSettings
import com.valghora.shrimpl.core.discovery.Discovery
import com.valghora.shrimpl.core.discovery.DiscoveryException
import com.valghora.shrimpl.core.discovery.Draft
import com.valghora.shrimpl.core.discovery.Evidence
import com.valghora.shrimpl.core.discovery.HtmlHead
import com.valghora.shrimpl.core.discovery.IconSources
import com.valghora.shrimpl.core.discovery.PublicSuffixList
import com.valghora.shrimpl.core.discovery.Request
import com.valghora.shrimpl.core.discovery.ScriptSignals
import com.valghora.shrimpl.core.discovery.SimpleIcon
import com.valghora.shrimpl.core.discovery.SimpleIcons
import com.valghora.shrimpl.core.discovery.SlimVisit
import com.valghora.shrimpl.core.discovery.Visit
import java.io.IOException
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
class SiteDiscovery(private val context: Context) {
    /** Stages shown while discovery runs (R3.2). */
    enum class Stage { FETCHING_PAGE, READING_MANIFEST, CHECKING_PAGE, FINDING_ICON }

    private val data by lazy { DiscoveryData.get(context) }

    suspend fun discover(input: String, onStage: (Stage) -> Unit = {}): Draft {
        val url = normalizeUrl(input) ?: throw DiscoveryException("Enter a web address, like example.com")
        val desktopAgent = RenderedPass.desktopUserAgent(WebSettings.getDefaultUserAgent(context))
        val network = Network(desktopAgent)

        // ----- Static pass -----
        onStage(Stage.FETCHING_PAGE)
        val document = withContext(Dispatchers.IO) {
            runInterruptible {
                try {
                    network.get(url)
                } catch (e: IOException) {
                    throw DiscoveryException("Could not load $url: ${e.message}")
                }
            }
        }
        if (document.status >= 400) throw DiscoveryException("$url answered with HTTP ${document.status}")
        val staticPage = HtmlHead.parse(document.body.decodeToString(), document.finalUrl)

        onStage(Stage.READING_MANIFEST)
        var manifestUrl = HtmlHead.manifestUrl(staticPage)
        var manifest = manifestUrl?.let { fetchManifest(network, it) }

        // ----- Rendered pass (R9.3): at most 20 seconds over three visits; the static result stands otherwise -----
        onStage(Stage.CHECKING_PAGE)
        val rendered = RenderedPass(context)
        val desktop = rendered.visit(url, RenderedPass.Mode.DESKTOP_LIGHT, DESKTOP_TIMEOUT_MS)
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
            runInterruptible { Discovery.draft(evidence, url, data.psl, sources) }
        }
    }

    /** The simple-icons brand named [name] ("Discord" -> `discord`), or null (R10.1). */
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
