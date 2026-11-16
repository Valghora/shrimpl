package com.valghora.shrimpl.core.discovery

import com.valghora.shrimpl.core.profile.AppIds
import com.valghora.shrimpl.core.profile.Profile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** How sure discovery is of a value; the form shows it next to the field. */
enum class Confidence { HIGH, MEDIUM, LOW }

/** A discovered value, where it came from, and how sure discovery is (shown next to the form field). */
data class Field<T>(val value: T, val source: String, val confidence: Confidence)

/** Discovery's suggestion for a new web app (docs/SHRIMPL_SPEC.md R9.1). */
data class Draft(
    val appId: String,
    val name: Field<String>,
    val startUrl: Field<String>,
    val internalDomains: Field<List<String>>,
    /** The site's brand color, for the icon background. */
    val iconBackground: Field<String>,
    val allowMicrophone: Field<Boolean>,
    val allowCamera: Field<Boolean>,
    val allowNotifications: Field<Boolean>,
    val allowLocation: Field<Boolean>,
    /** The best icon: the brand glyph when there is one, else the site's own icon, else a letter. */
    val icon: IconChoice,
    /** The site's own icons, best first, for the Website icon when [icon] is a brand glyph (R10.1). */
    val websiteIcons: List<WebsiteIcon>,
    val notes: List<Note>,
) {
    /** The draft as a profile; every other setting (header color, glyph color, ...) keeps its default. */
    fun toProfile(): Profile = Profile(
        name = name.value,
        appId = appId,
        startUrl = startUrl.value,
        internalDomains = internalDomains.value,
        iconBackground = iconBackground.value,
        allowMicrophone = allowMicrophone.value,
        allowCamera = allowCamera.value,
        allowNotifications = allowNotifications.value,
        allowLocation = allowLocation.value,
    )

    /**
     * Where each guessed value came from, by form field (R3.3): every value found with low or medium
     * confidence, except a permission discovery left off, since an off switch has nothing it suggests.
     */
    fun hints(): Map<String, String> = buildMap {
        fun hint(key: String, field: Field<*>) {
            if (field.confidence != Confidence.HIGH) put(key, field.source)
        }
        fun permission(key: String, field: Field<Boolean>) {
            if (field.value) hint(key, field)
        }
        hint("name", name)
        hint("startUrl", startUrl)
        hint("internalDomains", internalDomains)
        hint("iconBackground", iconBackground)
        permission("allowMicrophone", allowMicrophone)
        permission("allowCamera", allowCamera)
        permission("allowNotifications", allowNotifications)
        permission("allowLocation", allowLocation)
    }
}

/**
 * Something the user should know about a draft, shown as an in-app notification. [openInBrowser] is
 * an address the notification offers to open in Chrome; Shrimpl words such a note itself (R9.2), so
 * [text] is the English fallback.
 */
data class Note(val level: Level, val text: String, val openInBrowser: String? = null) {
    enum class Level { INFO, CAUTION }
}

/** The site couldn't be read at all, so there is no draft. */
class DiscoveryException(message: String) : Exception(message)

/** Color parsing: hex, rgb()/rgba() and a few names; transparent is null. */
object Colors {
    private val NAMED = mapOf(
        "white" to "#FFFFFF", "black" to "#000000", "red" to "#FF0000", "blue" to "#0000FF",
        "green" to "#008000", "gray" to "#808080", "grey" to "#808080", "orange" to "#FFA500",
    )
    private val HEX = Regex("#([0-9a-f]{3,8})")
    private val RGB = Regex("rgba?\\(\\s*(\\d+)[,\\s]+(\\d+)[,\\s]+(\\d+)(?:[,\\s/]+([\\d.]+%?))?\\s*\\)")

    /**
     * [value] as `#RRGGBB`, or null when it is missing, unparsable or
     * mostly transparent; short hex is expanded and alpha dropped.
     */
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

    /** Not white, black or gray: HSL saturation at least 0.25 and lightness between 0.12 and 0.88. */
    fun isColorful(hex: String): Boolean {
        val rgb = channels(hex)
        val max = rgb.max()
        val min = rgb.min()
        val lightness = (max + min) / 2
        if (max == min || lightness < 0.12 || lightness > 0.88) return false
        val saturation = (max - min) / (1 - kotlin.math.abs(2 * lightness - 1))
        return saturation >= 0.25
    }

    /** How far from gray a `#RRGGBB` color is, 0 to 1. */
    fun chroma(hex: String): Double = channels(hex).let { it.max() - it.min() }

    private fun channels(hex: String) = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
}

/** The discovery rules (docs/SHRIMPL_SPEC.md R9.2): evidence in, a draft profile out. */
object Discovery {
    private val WEBVIEW_BLOCK_PATTERNS = listOf(
        "disallowed_useragent",
        "browser (or app )?(may not be|is not) (secure|supported)",
        "(this|your) browser is not supported",
        "unsupported browser",
        "please (use|open this in) (a|another|your) (supported )?browser",
        "embedded browsers? (are|is) not supported",
    )
    /** Google's own refusal of a sign-in in an embedded WebView. */
    private val GOOGLE_REFUSAL_PATTERNS = listOf(
        "disallowed_useragent",
        "couldn.t sign you in",
        "this browser or app may not be secure",
    )
    private val TITLE_SEPARATOR = Regex("\\s+[|\\-–—:·•]\\s+")

    /**
     * Discovery's draft for [requestedUrl] from the collected [evidence] (R9.2): start URL, internal
     * domains, name, icon, brand color and capabilities, each with its source and confidence.
     * [forcedId] keeps an existing app's ID. Throws [DiscoveryException] when the page never loaded
     * or its address has no host.
     */
    fun draft(
        evidence: Evidence,
        requestedUrl: String,
        psl: PublicSuffixList,
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
        val notes = mutableListOf<Note>()

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
        val internalDomains = Field(internal.toList(), "site domain + observed domains containing '$token'", Confidence.MEDIUM)

        // ----- Name -----
        val shortName = manifest.string("short_name")
        val manifestName = manifest.string("name")
        // simple-icons by name (docs/SHRIMPL_SPEC.md R9.2): a match on a name the site gives itself is
        // trusted; a match on the bare domain word ("example" of example.com) is only likely.
        val siteNames = listOf(shortName, manifestName, meta["application-name"], meta["apple-mobile-web-app-title"], meta["og:site_name"])
        val found = SimpleIcons.find(siteNames + token, sources)
        val simple = found?.first
        val simpleConfidence = if (found != null && found.second in siteNames) Confidence.HIGH else Confidence.MEDIUM
        val nameCandidates = listOf(
            (shortName?.takeIf { it.codePointCount(0, it.length) in 1..15 }) to "web manifest short_name",
            manifestName to "web manifest name",
            simple?.title to "simple-icons brand title",
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

        // ----- Icon -----
        val appId = forcedId ?: AppIds.fromName(name)
        val icon = IconChooser.choose(simple, simpleConfidence, page.links, manifest, manifestUrl, finalUrl, sources)
            ?: IconChoice.Letter(
                letter = name.uppercase().firstOrNull { it.code < 128 && it.isLetterOrDigit() } ?: 'A',
                source = "no usable brand icon found",
                confidence = Confidence.LOW,
            )

        // ----- Brand color, for the icon background -----
        val iconBackground = brandColor(icon, page, manifest)

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
        val location = Field(
            signals["geolocation"] == true || policyAllows("geolocation"),
            "navigator.geolocation in page scripts or Permissions-Policy",
            Confidence.LOW,
        )

        // ----- WebView compatibility -----
        val webview = evidence.webview
        val blob = listOf(webview.title, webview.text, webview.error).joinToString(" ") { it.orEmpty() }
        val blocked = WEBVIEW_BLOCK_PATTERNS.filter { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(blob) }
        val googleSignIn = Urls.isGoogleHost(startHost) ||
            webview.navigations.any { Urls.hostOf(it) == "accounts.google.com" } ||
            GOOGLE_REFUSAL_PATTERNS.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(blob) }
        if (googleSignIn) {
            notes += Note(Note.Level.CAUTION, GOOGLE_SIGN_IN, openInBrowser = start.value)
        } else if ((webview.status ?: 0) >= 400 || blocked.isNotEmpty()) {
            notes += Note(
                Note.Level.CAUTION,
                "The site may refuse Android WebViews (status ${webview.status ?: "unknown"}, " +
                    "matched: ${blocked.joinToString(", ").ifEmpty { "HTTP error" }}). The app may not work.",
            )
        }
        if (webview.navigations.any { it.startsWith("market:") || it.startsWith("intent:") || "play.google.com/store/apps" in it }) {
            notes += Note(
                Note.Level.INFO,
                "With a mobile user agent the site redirects to the Play Store; the app switches to the desktop site when that happens.",
            )
        }
        if (simple == null) notes += Note(Note.Level.CAUTION, NO_GLYPH)
        if (d.manifest == null || d.manifest.isEmpty()) notes += Note(Note.Level.INFO, "No web manifest found; name and colors come from page metadata.")

        return Draft(
            appId = appId,
            name = nameField,
            startUrl = start,
            internalDomains = internalDomains,
            iconBackground = iconBackground,
            allowMicrophone = microphone,
            allowCamera = camera,
            allowNotifications = notifications,
            allowLocation = location,
            icon = icon,
            websiteIcons = IconChooser.websiteIcons(page.links, manifest, manifestUrl, finalUrl),
            notes = notes,
        )
    }

    /**
     * The site's brand color: the simple-icons brand color, else the first colorful value among the
     * mask-icon color, the manifest and meta theme colors, CSS brand variables and buttons, else any
     * theme color, else [DEFAULT_BRAND] (docs/SHRIMPL_SPEC.md R9.2).
     */
    internal fun brandColor(icon: IconChoice, page: PageInfo, manifest: JsonObject): Field<String> {
        if (icon is IconChoice.Glyph && icon.color != null && "simple-icons" in icon.colorSource) {
            return Field(icon.color, icon.colorSource, Confidence.HIGH)
        }
        val maskIcon = page.links.firstOrNull { "mask-icon" in it.rel.split(Regex("\\s+")) }?.color
        val themeMeta = page.themeColors.firstOrNull { "dark" !in it.media }?.color
        val candidates = buildList {
            add(Field(Colors.parse(maskIcon), "mask-icon color", Confidence.MEDIUM))
            add(Field(Colors.parse(manifest.string("theme_color")), "web manifest theme_color", Confidence.MEDIUM))
            add(Field(Colors.parse(themeMeta), "theme-color meta", Confidence.MEDIUM))
            cssBrandColor(page.brandColors)?.let { add(Field(it.color, it.source, Confidence.MEDIUM)) }
            page.brandColors.filter { it.source == BUTTON_SOURCE }.forEach { add(Field(Colors.parse(it.color), it.source, Confidence.LOW)) }
            add(Field(Colors.parse(manifest.string("background_color")), "web manifest background_color", Confidence.LOW))
        }
        candidates.firstOrNull { it.value != null && Colors.isColorful(it.value) }?.let { return Field(it.value!!, it.source, it.confidence) }
        candidates.take(3).firstOrNull { it.value != null && it.value != "#FFFFFF" }?.let {
            return Field(it.value!!, it.source, Confidence.LOW)
        }
        return Field(DEFAULT_BRAND, "default (no brand color found)", Confidence.LOW)
    }

    private const val CSS_VARIABLE = "CSS variable "
    private const val BUTTON_SOURCE = "button background"
    private val SHADE = Regex("-(\\d{2,3})$")
    private val PLAIN_NAME = Regex("^(color-)?(brand|primary|accent)(-color)?(-(base|default|main))?$")

    /**
     * The most likely brand color among CSS variables named brand/primary/accent: colorful, a plain
     * name (`--brand`, `--color-primary`) or a middle shade of a scale (`--brand-500`), and used by
     * several variables. Null if none of them is colorful.
     */
    internal fun cssBrandColor(colors: List<BrandColor>): BrandColor? {
        val variables = colors.filter { it.source.startsWith(CSS_VARIABLE) }
            .mapNotNull { brand -> Colors.parse(brand.color)?.let { BrandColor(it, brand.source) } }
            .filter { Colors.isColorful(it.color) }
        val uses = variables.groupingBy { it.color }.eachCount()
        fun score(brand: BrandColor): Double {
            val name = brand.source.removePrefix(CSS_VARIABLE).removePrefix("--").lowercase()
            var score = 2 * Colors.chroma(brand.color) + 0.3 * (uses.getValue(brand.color) - 1)
            if (PLAIN_NAME.matches(name)) score += 1.0 else if ("brand" in name) score += 0.4
            SHADE.find(name)?.groupValues?.get(1)?.toInt()?.let { shade ->
                score += when (shade) {
                    in 400..600 -> 0.3
                    in 300..700 -> 0.0
                    else -> -0.5
                }
            }
            return score
        }
        return variables.maxByOrNull(::score)
    }

    /** The Google sign-in note's English wording; Shrimpl shows its own string (R9.2). */
    const val GOOGLE_SIGN_IN = "Google doesn't allow signing in from apps like this one. To use your Google account, " +
        "open the site in Chrome and install it from Chrome's menu."
    const val NO_GLYPH = "No brand icon was found for this site, so the app uses the site's own icon. You can choose another one under Custom."

    /**
     * The site's own icon for the Website option (R10.1): [draft]'s best icon when that is already an
     * image, else the first usable one of its [Draft.websiteIcons]; null when the site has none.
     */
    fun websiteIcon(draft: Draft, sources: IconSources): IconChoice.Raster? =
        draft.icon as? IconChoice.Raster ?: IconChooser.firstRaster(draft.websiteIcons, sources)

    /** Blue gray, for sites without a brand color. */
    const val DEFAULT_BRAND = "#607D8B"

    /** The part of a page title that names the site ("Pull requests · GitHub" -> "GitHub"). */
    internal fun cleanTitle(title: String, token: String): String {
        val parts = title.split(TITLE_SEPARATOR).map(String::trim).filter(String::isNotEmpty)
        return parts.firstOrNull { token.isNotEmpty() && token in SimpleIcons.titleToSlug(it) } ?: parts.lastOrNull().orEmpty()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
