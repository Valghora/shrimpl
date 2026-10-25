package com.valghora.wrapper.core.discovery

import java.text.Normalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One brand from simple-icons.org's data file (CC0); [path] is present in UniWrap's bundled copy. */
@Serializable
data class SimpleIcon(
    val title: String,
    val hex: String,
    val slug: String? = null,
    val source: String = "",
    val guidelines: String? = null,
    val aliases: Aliases? = null,
    /** The glyph's SVG path data in a 24x24 viewBox. */
    val path: String? = null,
) {
    @Serializable
    data class Aliases(val aka: List<String> = emptyList())

    val resolvedSlug: String get() = slug ?: titleToSlug(title)

    /** The glyph as an SVG document, like simple-icons' own files. */
    fun svg(): ByteArray? = path?.let {
        """<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="$it"/></svg>""".toByteArray()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseList(text: String): List<SimpleIcon> = json.decodeFromString(ListSerializer(serializer()), text)

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
}

/** A brand that matched the site, and whether its source/guidelines domain is the site's own. */
data class SimpleIconMatch(val icon: SimpleIcon, val domainMatch: Boolean)

object SimpleIconMatcher {
    /** Finds the simple-icons entry for the brand; null if nothing matches confidently. */
    fun match(icons: List<SimpleIcon>, names: List<String?>, site: String, psl: PublicSuffixList): SimpleIconMatch? {
        val wanted = names.mapNotNull { name -> name?.let(SimpleIcon::titleToSlug)?.takeIf(String::isNotEmpty) }.toSet()
        var best: SimpleIconMatch? = null
        for (entry in icons) {
            val keys = setOf(entry.resolvedSlug, SimpleIcon.titleToSlug(entry.title)) +
                entry.aliases?.aka.orEmpty().map(SimpleIcon::titleToSlug)
            if (keys.none { it in wanted }) continue
            val domains = listOf(entry.source, entry.guidelines.orEmpty()).map { url ->
                Urls.hostOf(url)?.let(psl::registrable) ?: psl.registrable("")
            }
            val candidate = SimpleIconMatch(entry, domainMatch = site in domains)
            if (best == null || (candidate.domainMatch && !best.domainMatch)) best = candidate
        }
        return best
    }
}
