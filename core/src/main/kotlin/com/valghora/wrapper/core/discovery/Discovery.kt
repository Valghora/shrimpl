package com.valghora.wrapper.core.discovery

import com.valghora.wrapper.core.profile.AppIds
import com.valghora.wrapper.core.profile.Profile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class Confidence { HIGH, MEDIUM, LOW }

/** A discovered value, where it came from, and how sure discovery is (shown next to the form field). */
data class Field<T>(val value: T, val source: String, val confidence: Confidence)

/** Discovery's suggestion for a new web app (docs/UNIWRAP_SPEC.md R9.1). */
data class Draft(
    val appId: String,
    val name: Field<String>,
    val startUrl: Field<String>,
    val internalDomains: Field<List<String>>,
    val headerColor: Field<String>,
    /** Empty when there is no distinct dark-theme color. */
    val headerColorNight: Field<String>,
    val iconBackground: Field<String>,
    val allowMicrophone: Field<Boolean>,
    val allowCamera: Field<Boolean>,
    val allowNotifications: Field<Boolean>,
    val icon: IconChoice,
    val notes: List<String>,
) {
    /** The draft as a profile; every other setting keeps its default. */
    fun toProfile(): Profile = Profile(
        name = name.value,
        appId = appId,
        startUrl = startUrl.value,
        internalDomains = internalDomains.value,
        iconBackground = iconBackground.value,
        headerColor = headerColor.value,
        headerColorNight = headerColorNight.value.ifEmpty { null },
        allowMicrophone = allowMicrophone.value,
        allowCamera = allowCamera.value,
        allowNotifications = allowNotifications.value,
    )
}

class DiscoveryException(message: String) : Exception(message)

/** Color parsing as in discover_site.parse_color: hex, rgb()/rgba() and a few names; transparent is null. */
object Colors {
    private val NAMED = mapOf(
        "white" to "#FFFFFF", "black" to "#000000", "red" to "#FF0000", "blue" to "#0000FF",
        "green" to "#008000", "gray" to "#808080", "grey" to "#808080", "orange" to "#FFA500",
    )
    private val HEX = Regex("#([0-9a-f]{3,8})")
    private val RGB = Regex("rgba?\\(\\s*(\\d+)[,\\s]+(\\d+)[,\\s]+(\\d+)(?:[,\\s/]+([\\d.]+%?))?\\s*\\)")

    fun parse(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val v = value.trim().lowercase()
        NAMED[v]?.let { return it }
        HEX.matchEntire(v)?.let { m ->
            var h = m.groupValues[1]
            if (h.length == 3 || h.length == 4) {
                h = h.take(3).map { "$it$it" }.joinToString("")
            } else if (h.length == 8 && h.substring(6).toInt(16) < 128) {
                return null
            }
            return if (h.length >= 6) "#" + h.take(6).uppercase() else null
        }
        RGB.matchEntire(v)?.let { m ->
            val alpha = m.groupValues[4]
            if (alpha.isNotEmpty()) {
                val a = alpha.trimEnd('%').toDoubleOrNull() ?: return null
                if (a / (if (alpha.endsWith("%")) 100.0 else 1.0) < 0.5) return null
            }
            return "#" + (1..3).joinToString("") { "%02X".format(minOf(m.groupValues[it].toInt(), 255)) }
        }
        return null
    }
}

/** The discovery rules, ported from the retired scripts/discover_site.py. */
object Discovery {
    private val WEBVIEW_BLOCK_PATTERNS = listOf(
        "disallowed_useragent",
        "browser (or app )?(may not be|is not) (secure|supported)",
        "(this|your) browser is not supported",
        "unsupported browser",
        "please (use|open this in) (a|another|your) (supported )?browser",
        "embedded browsers? (are|is) not supported",
    )
    private val API_TYPES = setOf("xhr", "fetch", "websocket", "eventsource", "document")
    private val TITLE_SEPARATOR = Regex("\\s+[|\\-–—:·•]\\s+")

    fun draft(
        evidence: Evidence,
        requestedUrl: String,
        psl: PublicSuffixList,
        simpleIcons: List<SimpleIcon>,
        sources: IconSources,
        forcedId: String? = null,
    ): Draft {
        val d = evidence.desktop
        val page = d.page ?: PageInfo()
        val manifest = d.manifest ?: JsonObject(emptyMap())
        val manifestUrl = d.manifestUrl.orEmpty()
        val finalUrl = d.finalUrl ?: requestedUrl
        if (d.error != null && d.page == null) throw DiscoveryException("Could not load $requestedUrl: ${d.error}")

        val startHost = Urls.hostOf(finalUrl) ?: Urls.hostOf(requestedUrl)
            ?: throw DiscoveryException("$requestedUrl has no host")
        val site = psl.registrable(startHost) ?: startHost
        val token = site.substringBefore('.')
        val meta = page.metas.associate { it.name to it.content }
        val notes = mutableListOf<String>()

        // ----- Start URL -----
        // Keep the URL that was asked for: redirects on the same host are usually logged-out bounces
        // (/app -> /login). A redirect to another host of the same site (example.com -> www.example.com)
        // keeps the requested path on the new host.
        val requested = Urls.split(requestedUrl)
        val final = Urls.split(finalUrl)
        var start = when {
            final.hostname == requested.hostname || final.hostname == null ->
                Field(requestedUrl, "requested URL", Confidence.HIGH)
            psl.registrable(final.hostname!!) == psl.registrable(requested.hostname.orEmpty()) -> Field(
                "https://${final.netloc}${requested.path.ifEmpty { "/" }}" + (if (requested.query.isNotEmpty()) "?${requested.query}" else ""),
                "requested path on the redirected host",
                Confidence.HIGH,
            )
            else -> Field(finalUrl, "final URL after redirects (different site)", Confidence.MEDIUM)
        }
        manifest.string("start_url")?.takeIf(String::isNotEmpty)?.let { startUrl ->
            val candidate = Urls.join(manifestUrl, startUrl)
            val host = Urls.hostOf(candidate)
            if (host != null && psl.registrable(host) == site && candidate.startsWith("https://")) {
                start = Field(candidate, "web manifest start_url", Confidence.HIGH)
            }
        }
        if (!start.value.startsWith("https://")) start = Field("https://$startHost/", "https origin of the site", Confidence.MEDIUM)

        // ----- Domains -----
        val observed = sortedMapOf<String, MutableSet<String>>()
        for (request in d.requests) {
            val host = Urls.hostOf(request.url) ?: continue
            if (request.url.startsWith("data:") || request.url.startsWith("blob:")) continue
            val reg = psl.registrable(host) ?: continue
            observed.getOrPut(reg) { mutableSetOf() } += request.type
        }
        val internal = mutableListOf(site)
        for (url in d.redirectChain + manifestUrl) {
            val reg = if (url.isNotEmpty()) psl.registrable(Urls.hostOf(url).orEmpty()) else null
            if (reg != null && reg !in internal && token in reg) internal += reg
        }
        for (reg in observed.keys) {
            if (reg !in internal && token.length >= 3 && token in reg.substringBefore('.')) internal += reg
        }
        val apiHosts = observed.filter { (reg, types) -> reg !in internal && types.any { it in API_TYPES } }.keys.toList()
        val internalDomains = Field(internal.toList(), "site domain + observed domains containing '$token'", Confidence.MEDIUM)
        if (apiHosts.isNotEmpty()) {
            notes += "Other domains the page called (not internal; add any that belong to the site): " +
                apiHosts.take(15).joinToString(", ")
        }

        // ----- Name -----
        val shortName = manifest.string("short_name")
        val manifestName = manifest.string("name")
        val simple = SimpleIconMatcher.match(
            simpleIcons,
            listOf(shortName, manifestName, meta["application-name"], meta["apple-mobile-web-app-title"], meta["og:site_name"], token),
            site,
            psl,
        )
        val nameCandidates = listOf(
            (shortName?.takeIf { it.codePointCount(0, it.length) in 1..15 }) to "web manifest short_name",
            manifestName to "web manifest name",
            (if (simple != null && simple.domainMatch) simple.icon.title else null) to "simple-icons brand title",
            meta["application-name"] to "application-name meta",
            meta["apple-mobile-web-app-title"] to "apple-mobile-web-app-title meta",
            meta["og:site_name"] to "og:site_name meta",
            cleanTitle(page.title, token) to "page title",
            token.lowercase().replaceFirstChar { it.uppercaseChar() } to "domain name",
        )
        val (name, nameSource) = nameCandidates.first { (n, _) -> !n.isNullOrBlank() }.let { (n, s) -> n!!.trim() to s }
        val nameField = Field(
            name,
            nameSource,
            if ("manifest" in nameSource || "simple-icons" in nameSource) Confidence.HIGH else Confidence.MEDIUM,
        )

        // ----- Colors -----
        var themeLight: String? = null
        var themeDark: String? = null
        for (tc in page.themeColors) {
            if ("dark" in tc.media) themeDark = themeDark ?: Colors.parse(tc.color) else themeLight = themeLight ?: Colors.parse(tc.color)
        }
        val topLight = Colors.parse(page.topColor)
        val topDark = Colors.parse(evidence.dark.topColor)
        val manifestTheme = Colors.parse(manifest.string("theme_color"))
        val manifestBackground = Colors.parse(manifest.string("background_color"))
        val header = listOf(
            Field(themeLight, "theme-color meta", Confidence.HIGH),
            Field(topLight, "page background at the top edge (light mode)", Confidence.MEDIUM),
            Field(manifestTheme, "web manifest theme_color", Confidence.MEDIUM),
            Field("#FFFFFF", "default", Confidence.LOW),
        ).first { it.value != null }.let { Field(it.value!!, it.source, it.confidence) }
        val night = when {
            themeDark != null && themeDark != header.value ->
                Field(themeDark, "theme-color meta for prefers-color-scheme: dark", Confidence.HIGH)
            topDark != null && topDark != header.value && topDark != topLight ->
                Field(topDark, "page background at the top edge (dark mode)", Confidence.MEDIUM)
            else -> Field("", "same as light (no dark variant detected)", Confidence.MEDIUM)
        }

        // ----- Icon -----
        val appId = forcedId ?: AppIds.fromName(name)
        val icon = IconChooser.choose(simple, page.links, manifest, manifestUrl, finalUrl, sources)
            ?: IconChoice.Letter(
                letter = name.uppercase().firstOrNull { it.code < 128 && it.isLetterOrDigit() } ?: 'A',
                source = "no usable brand icon found",
                confidence = Confidence.LOW,
            )
        val iconBackground = when {
            icon is IconChoice.Glyph && icon.color != null -> Field(icon.color, icon.colorSource, Confidence.HIGH)
            icon is IconChoice.Raster && !icon.maskable && manifestBackground != null ->
                Field(manifestBackground, "web manifest background_color", Confidence.MEDIUM)
            else -> listOf(
                Field(manifestTheme, "web manifest theme_color", Confidence.MEDIUM),
                Field(themeLight, "theme-color meta", Confidence.MEDIUM),
                Field(header.value, "header color", Confidence.LOW),
            ).first { it.value != null }.let { Field(it.value!!, it.source, it.confidence) }
        }

        // ----- Capabilities -----
        val signals = d.signals
        val policy = d.headers["permissions-policy"].orEmpty()
        fun policyAllows(feature: String) = Regex("$feature=\\(\\s*\\)").find(policy) == null && feature in policy
        val usesMedia = signals["getUserMedia"] == true || signals["webRtc"] == true
        val microphone = Field(
            usesMedia || policyAllows("microphone"),
            "getUserMedia/WebRTC in page scripts or Permissions-Policy",
            Confidence.LOW,
        )
        val camera = Field(
            signals["getUserMedia"] == true || policyAllows("camera"),
            "getUserMedia in page scripts or Permissions-Policy",
            Confidence.LOW,
        )
        val notifies = signals["notificationPermission"] == true || signals["showNotification"] == true || signals["pushManager"] == true
        val notifications = Field(notifies, "Notification/showNotification/Push in page scripts", Confidence.MEDIUM)

        // ----- WebView compatibility -----
        val webview = evidence.webview
        val blob = listOf(webview.title, webview.text, webview.error).joinToString(" ") { it.orEmpty() }
        val blocked = WEBVIEW_BLOCK_PATTERNS.filter { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(blob) }
        if ((webview.status ?: 0) >= 400 || blocked.isNotEmpty()) {
            notes += "WARNING: the site may refuse Android WebViews (status ${webview.status ?: "unknown"}, " +
                "matched: ${blocked.joinToString(", ").ifEmpty { "HTTP error" }}). The app may not work."
        }
        if (webview.navigations.any { it.startsWith("market:") || it.startsWith("intent:") || "play.google.com/store/apps" in it }) {
            notes += "With a mobile user agent the site redirects to the Play Store; the store-redirect " +
                "fallback (UA_STORE_REDIRECT_FALLBACK) will switch to the desktop site."
        }
        if (d.manifest == null || d.manifest.isEmpty()) notes += "No web manifest found; name and colors come from page metadata."

        return Draft(
            appId = appId,
            name = nameField,
            startUrl = start,
            internalDomains = internalDomains,
            headerColor = header,
            headerColorNight = night,
            iconBackground = iconBackground,
            allowMicrophone = microphone,
            allowCamera = camera,
            allowNotifications = notifications,
            icon = icon,
            notes = notes,
        )
    }

    /** The part of a page title that names the site ("Pull requests · GitHub" -> "GitHub"). */
    internal fun cleanTitle(title: String, token: String): String {
        val parts = title.split(TITLE_SEPARATOR).map(String::trim).filter(String::isNotEmpty)
        return parts.firstOrNull { token.isNotEmpty() && token in SimpleIcon.titleToSlug(it) } ?: parts.lastOrNull().orEmpty()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
