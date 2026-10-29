package com.valghora.shrimpl.core.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * What discovery observed about a site (docs/SHRIMPL_SPEC.md R9), the input to the [Discovery] rules.
 * Shrimpl fills it from a plain page fetch (static pass) and a hidden WebView (rendered pass).
 */
@Serializable
data class Evidence(
    /** The site in a light-theme desktop browser. */
    val desktop: Visit,
    /** The same page in dark theme (only its top color is used). */
    val dark: SlimVisit = SlimVisit(),
    /** The requested URL with an Android WebView user agent (for blocking and store-redirect checks). */
    val webview: SlimVisit = SlimVisit(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun decode(text: String): Evidence = json.decodeFromString(serializer(), text)

        fun encode(evidence: Evidence): String = json.encodeToString(serializer(), evidence)
    }
}

@Serializable
data class Visit(
    val requestedUrl: String? = null,
    /** Where the page ended up after redirects. */
    val finalUrl: String? = null,
    val status: Int? = null,
    /** Set when the page could not be loaded at all. */
    val error: String? = null,
    /** Response headers of the main document, with lowercase names. */
    val headers: Map<String, String> = emptyMap(),
    /** URLs of the main document, first request first. */
    val redirectChain: List<String> = emptyList(),
    /** Everything the page requested, with a resource type ("document", "script", "xhr", "fetch", ...). */
    val requests: List<Request> = emptyList(),
    /** API use found in the page's scripts, by [ScriptSignals] name. */
    val signals: Map<String, Boolean> = emptyMap(),
    val page: PageInfo? = null,
    val manifestUrl: String? = null,
    /** The web manifest as parsed JSON, when it was found and valid. */
    val manifest: JsonObject? = null,
)

@Serializable
data class Request(val url: String, val type: String)

@Serializable
data class PageInfo(
    val title: String = "",
    val metas: List<Meta> = emptyList(),
    /** `<link rel>` elements with absolute hrefs. */
    val links: List<Link> = emptyList(),
    /** `<meta name="theme-color">` values with their media queries. */
    val themeColors: List<ThemeColor> = emptyList(),
    /** The most common background color along the top edge of the viewport, as #RRGGBB. */
    val topColor: String? = null,
    /** Colors the rendered page uses for its brand: CSS variables named brand/primary/accent, then buttons. */
    val brandColors: List<BrandColor> = emptyList(),
)

/** A brand color seen on the page and where it was seen ("CSS variable --brand", "button background"). */
@Serializable
data class BrandColor(val color: String, val source: String)

/** A `<meta>` with its name (or property) in lowercase. */
@Serializable
data class Meta(val name: String, val content: String, val media: String = "")

@Serializable
data class Link(val rel: String, val href: String, val sizes: String = "", val color: String = "")

@Serializable
data class ThemeColor(val color: String, val media: String = "")

@Serializable
data class SlimVisit(
    val status: Int? = null,
    val error: String? = null,
    val finalUrl: String? = null,
    val title: String? = null,
    val topColor: String? = null,
    /** The page's visible text (truncated). */
    val text: String? = null,
    /** Main-frame navigations, including ones to market: or intent: URLs. */
    val navigations: List<String> = emptyList(),
)
