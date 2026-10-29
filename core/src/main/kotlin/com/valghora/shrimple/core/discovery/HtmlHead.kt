package com.valghora.shrimple.core.discovery

/**
 * Reads a page's metadata from its HTML without a browser (the static pass, docs/SHRIMPLE_SPEC.md R9.2):
 * title, metas, `<link rel>` elements with absolute hrefs, and theme colors. Values that need
 * rendering (the top-edge color) are left for the WebView pass.
 */
object HtmlHead {
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val RAW_TEXT = Regex("<(script|style|template|noscript)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TAG = Regex("<(meta|link|base|title)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("<title\\b[^>]*>(.*?)</title\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ATTRIBUTE = Regex("([^\\s=/>\"']+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+))?")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);")
    private val WHITESPACE = Regex("\\s+")
    private const val MAX_CHARS = 1_000_000

    fun parse(html: String, pageUrl: String): PageInfo {
        val text = RAW_TEXT.replace(COMMENT.replace(html.take(MAX_CHARS), ""), "")
        val head = text.substringBefore("</head", text)
        var base = pageUrl
        val metas = mutableListOf<Meta>()
        val links = mutableListOf<Link>()
        val themeColors = mutableListOf<ThemeColor>()
        for (match in TAG.findAll(head)) {
            val attrs = attributes(match.groupValues[2])
            when (match.groupValues[1].lowercase()) {
                "base" -> attrs["href"]?.let { base = Urls.join(pageUrl, it) }
                "meta" -> {
                    val name = (attrs["name"] ?: attrs["property"]).orEmpty().lowercase()
                    if (name.isEmpty()) continue
                    val content = attrs["content"].orEmpty()
                    val media = attrs["media"].orEmpty()
                    metas += Meta(name, content, media)
                    if (name == "theme-color") themeColors += ThemeColor(content, media)
                }
                "link" -> {
                    val rel = attrs["rel"]?.lowercase()?.trim() ?: continue
                    links += Link(
                        rel = rel,
                        href = Urls.join(base, attrs["href"].orEmpty().trim()),
                        sizes = attrs["sizes"].orEmpty(),
                        color = attrs["color"].orEmpty(),
                    )
                }
            }
        }
        val title = TITLE.find(head)?.groupValues?.get(1)?.let { decode(it).replace(WHITESPACE, " ").trim() }.orEmpty()
        return PageInfo(title = title, metas = metas, links = links, themeColors = themeColors)
    }

    /** The web manifest URL, from the first `<link rel="manifest">`. */
    fun manifestUrl(page: PageInfo): String? = page.links.firstOrNull { "manifest" in it.rel.split(WHITESPACE) }?.href

    private fun attributes(source: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (m in ATTRIBUTE.findAll(source)) {
            val name = m.groupValues[1].lowercase()
            val raw = m.groupValues[2]
            val value = if (raw.length >= 2 && (raw[0] == '"' || raw[0] == '\'')) raw.substring(1, raw.length - 1) else raw
            out.putIfAbsent(name, decode(value))
        }
        return out
    }

    internal fun decode(text: String): String = ENTITY.replace(text) { m ->
        when (val entity = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> {
                val code = if (entity.startsWith("#x")) entity.substring(2).toIntOrNull(16) else entity.substring(1).toIntOrNull()
                code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
            }
        }
    }
}

/** API use detected in a page's scripts (R9.3). */
object ScriptSignals {
    val PATTERNS: Map<String, Regex> = linkedMapOf(
        "getUserMedia" to Regex("getUserMedia\\s*\\("),
        "notificationPermission" to Regex("Notification\\.requestPermission|Notification\\.permission"),
        "showNotification" to Regex("showNotification\\s*\\("),
        "pushManager" to Regex("pushManager\\.subscribe"),
        "serviceWorker" to Regex("serviceWorker\\.register\\s*\\("),
        "webRtc" to Regex("RTCPeerConnection"),
        "geolocation" to Regex("geolocation\\.(getCurrentPosition|watchPosition)"),
    )

    /** Names of the signals [script] contains. */
    fun scan(script: String): Set<String> = PATTERNS.filterValues { it.containsMatchIn(script) }.keys
}
