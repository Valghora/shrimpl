package com.valghora.shrimpl.core.adblock

/**
 * Element hiding (docs/TEMPLATE_SPEC.md T6.20): the `##` rules of EasyList and EasyPrivacy, which hide
 * the ad slots a page keeps after its ads were blocked, and AdGuard's `#$#` style rules
 * (`selector { declarations }`), with which the app banners list (T6.22) hides an overlay and undoes the
 * scroll lock that came with it. A web app is one site, so the app only needs the
 * generic rules and those for its own domains ([site]); a document-start script picks the ones for the
 * page's host and adds them as a stylesheet.
 *
 * Left out, so a list only ever hides less: procedural rules (`#?#`, `:has-text()` and the like),
 * scriptlets (`#%#`, `+js()`, and Adblock Plus's `#$#` snippets, which have no braces), style rules
 * that could load anything (`url(`, `@`), and site rules that also exclude subdomains, which a host-suffix
 * match can't honor.
 */
class CosmeticFilter private constructor(
    /** Selectors every page hides, unless its site turns generic hiding off. */
    val generic: List<String>,
    /** Style rules (`selector{declarations}`) every page applies, unless its site turns generic hiding off. */
    val genericStyles: List<String>,
    private val bySite: Map<String, SiteBuilder>,
    /** The number of rules this filter applies. */
    val ruleCount: Int,
) {
    /**
     * What one domain changes: selectors it hides, its style rules, generic selectors it shows again, and
     * generic hiding off.
     */
    data class SiteRules(
        val hide: List<String>,
        val styles: List<String>,
        val unhide: List<String>,
        /** `$generichide`: no generic rules on this domain. */
        val genericHide: Boolean,
        /** `$elemhide`: no hiding at all on this domain. */
        val elemHide: Boolean,
    )

    /**
     * The rules of every domain that can apply on the app's [domains]: each of them, their subdomains and
     * their parent domains. The page picks those whose domain is its host or a parent of it.
     */
    fun site(domains: Collection<String>): Map<String, SiteRules> {
        val wanted = domains.map { it.lowercase() }
        return bySite.filterKeys { key -> wanted.any { it == key || it.endsWith(".$key") || key.endsWith(".$it") } }
            .mapValues { (_, b) -> SiteRules(b.hide.distinct(), b.styles.distinct(), b.unhide.distinct(), b.genericHide, b.elemHide) }
    }

    private class SiteBuilder {
        val hide = mutableListOf<String>()
        val styles = mutableListOf<String>()
        val unhide = mutableListOf<String>()
        var genericHide = false
        var elemHide = false
    }

    companion object {
        /** The filter for the `##` and `#$#` rules in [lists], and the `$generichide`/`$elemhide` exceptions. */
        fun parse(vararg lists: String): CosmeticFilter {
            val generic = LinkedHashSet<String>()
            val genericStyles = LinkedHashSet<String>()
            val genericExceptions = HashSet<String>()
            val bySite = HashMap<String, SiteBuilder>()
            var count = 0
            fun site(domain: String) = bySite.getOrPut(domain) { SiteBuilder() }
            lists.forEach { list ->
                list.lineSequence().forEach { raw ->
                    val line = raw.trim()
                    PAGE_EXCEPTION.matchEntire(line)?.let { match ->
                        val options = match.groupValues[2].split(',').map { it.trim().lowercase() }
                        if (options.all { it in PAGE_OPTIONS }) {
                            val target = site(match.groupValues[1].lowercase())
                            if (options.any { it == "elemhide" || it == "ehide" }) target.elemHide = true
                            if (options.any { it == "generichide" || it == "ghide" }) target.genericHide = true
                            count++
                        }
                        return@forEach
                    }
                    val rule = parseRule(line) ?: return@forEach
                    count++
                    if (rule.style) {
                        val includes = rule.domains.filterNot { it.startsWith('~') }
                        when {
                            rule.domains.isEmpty() -> genericStyles += rule.selector
                            includes.size == rule.domains.size -> includes.forEach { site(it).styles += rule.selector }
                            else -> count--
                        }
                        return@forEach
                    }
                    val includes = rule.domains.filterNot { it.startsWith('~') }
                    val excludes = rule.domains.filter { it.startsWith('~') }.map { it.substring(1) }
                    when {
                        rule.exception && rule.domains.isEmpty() -> genericExceptions += rule.selector
                        rule.exception -> includes.forEach { site(it).unhide += rule.selector }
                        includes.isEmpty() -> {
                            generic += rule.selector
                            excludes.forEach { site(it).unhide += rule.selector }
                        }
                        excludes.isEmpty() -> includes.forEach { site(it).hide += rule.selector }
                        else -> count--
                    }
                }
            }
            return CosmeticFilter(generic.filterNot { it in genericExceptions }, genericStyles.toList(), bySite, count)
        }

        /**
         * One `##` or `#@#` rule: the domains before the mark, and the selector after it; for a `#$#`
         * style rule, [style] and the whole CSS rule, `selector{declarations}`, in [selector].
         */
        internal class Rule(val domains: List<String>, val selector: String, val exception: Boolean, val style: Boolean = false)

        internal fun parseRule(line: String): Rule? {
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) return null
            val mark = HIDING.find(line) ?: return null
            if (mark.value == "#$#") return parseStyle(line, mark)
            if (mark.value != "##" && mark.value != "#@#") return null
            val selector = line.substring(mark.range.last + 1).trim()
            if (selector.isEmpty() || PROCEDURAL.any { selector.contains(it) } || !balanced(selector)) return null
            val domains = line.substring(0, mark.range.first).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            // Entity rules (`example.*`) would need the public suffix list; they are few, so left out.
            if (domains.any { it.endsWith(".*") }) return null
            return Rule(domains, selector, exception = mark.value == "#@#")
        }

        /**
         * A `#$#` rule of the form `selector { declarations }`, or null for anything else: Adblock Plus's
         * snippets, which share the mark, and declarations that could load something or break out of
         * their rule.
         */
        private fun parseStyle(line: String, mark: MatchResult): Rule? {
            val body = STYLE.matchEntire(line.substring(mark.range.last + 1).trim()) ?: return null
            val selector = body.groupValues[1].trim()
            val declarations = body.groupValues[2].trim().removeSuffix(";").trim()
            if (selector.isEmpty() || PROCEDURAL.any { selector.contains(it) } || !balanced(selector)) return null
            if (!DECLARATIONS.matches(declarations) || UNSAFE.any { declarations.contains(it, ignoreCase = true) }) return null
            val domains = line.substring(0, mark.range.first).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (domains.any { it.endsWith(".*") }) return null
            return Rule(domains, "$selector{$declarations}", exception = false, style = true)
        }

        /**
         * Whether brackets and quotes pair up, and the selector can't close the rule it goes into: one
         * bad selector must not take the stylesheet's other rules with it.
         */
        private fun balanced(selector: String): Boolean {
            if ('{' in selector || '}' in selector || '<' in selector || "/*" in selector) return false
            var round = 0
            var square = 0
            var quote: Char? = null
            for (c in selector) {
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '"' || c == '\'' -> quote = c
                    c == '(' -> round++
                    c == ')' -> if (--round < 0) return false
                    c == '[' -> square++
                    c == ']' -> if (--square < 0) return false
                }
            }
            return round == 0 && square == 0 && quote == null
        }

        private val HIDING = Regex("#[@?$%]?#")
        private val STYLE = Regex("""(.+?)\s*\{([^{}]*)\}""")
        private val DECLARATIONS = Regex("""[a-zA-Z-]+\s*:\s*[^;:{}<>]+(;\s*[a-zA-Z-]+\s*:\s*[^;:{}<>]+)*""")

        /** What a declaration may never hold: anything that loads, imports or escapes. */
        private val UNSAFE = listOf("url(", "@", "\\", "expression(", "/*", "image-set(", "attr(")
        private val PAGE_EXCEPTION = Regex("""@@\|\|([a-z0-9][a-z0-9.-]*[a-z0-9])\^\$(.+)""", RegexOption.IGNORE_CASE)
        private val PAGE_OPTIONS = setOf("generichide", "ghide", "elemhide", "ehide")

        /** uBlock Origin's and Adblock Plus's procedural and action selectors: not CSS. */
        private val PROCEDURAL = listOf(
            ":-abp-", ":has-text(", ":xpath(", ":upward(", ":matches-css", ":remove(", ":style(",
            ":min-text-length(", ":watch-attr(", ":matches-path(", ":matches-attr(", ":others(", ":if(",
            ":if-not(", ":nth-ancestor(", ":remove-attr(", ":remove-class(", "+js(",
        )
    }
}
