package com.valghora.shrimpl.core.discovery

import java.text.Normalizer

/** A simple-icons.org brand found by name: its glyph and brand color (CC0; brands are trademarks). */
class SimpleIcon(val slug: String, val title: String, val hex: String, val svg: ByteArray) {
    /** The brand color as `#RRGGBB`. */
    val color: String get() = "#" + hex.uppercase()
}

/**
 * Finds simple-icons brands by name, without a bundled copy: `https://cdn.simpleicons.org/<slug>`
 * serves the glyph filled with the brand color, and answers 404 for unknown slugs.
 */
object SimpleIcons {
    /** The simple-icons CDN address of the glyph for [slug]. */
    fun url(slug: String) = "https://cdn.simpleicons.org/$slug"

    /** The brand for [slug] from a CDN response, or null if it isn't a simple-icons glyph. */
    fun parse(slug: String, svg: ByteArray): SimpleIcon? {
        val text = svg.decodeToString()
        val hex = FILL.find(text)?.groupValues?.get(1) ?: return null
        val title = TITLE.find(text)?.groupValues?.get(1)?.let(::unescape) ?: slug
        return SimpleIcon(slug, title, hex, svg)
    }

    /**
     * The first brand whose slug matches one of [names] (in order, at most [MAX_LOOKUPS] distinct
     * slugs, so a site costs a few small requests), or null.
     */
    fun find(names: List<String?>, sources: IconSources): Pair<SimpleIcon, String>? {
        val tried = mutableSetOf<String>()
        for (name in names) {
            val slug = name?.let(::titleToSlug)?.takeIf(String::isNotEmpty) ?: continue
            if (!tried.add(slug)) continue
            sources.simpleIcon(slug)?.let { return it to name }
            if (tried.size == MAX_LOOKUPS) break
        }
        return null
    }

    const val MAX_LOOKUPS = 4

    private val FILL = Regex("""<svg[^>]*\sfill="#([0-9A-Fa-f]{6})"""")
    private val TITLE = Regex("<title>([^<]*)</title>")

    private fun unescape(text: String) =
        text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    private val REPLACEMENTS = mapOf(
        '+' to "plus", '.' to "dot", '&' to "and", 'đ' to "d", 'ħ' to "h", 'ı' to "i", 'ĸ' to "k",
        'ŀ' to "l", 'ł' to "l", 'ß' to "ss", 'ŧ' to "t", 'ø' to "o",
    )
    private val NOT_SLUG = Regex("[^a-z0-9]")

    /** simple-icons' titleToSlug. */
    fun titleToSlug(title: String): String {
        val replaced = title.lowercase().map { REPLACEMENTS[it] ?: it.toString() }.joinToString("")
        return NOT_SLUG.replace(Normalizer.normalize(replaced, Normalizer.Form.NFD), "")
    }
}
