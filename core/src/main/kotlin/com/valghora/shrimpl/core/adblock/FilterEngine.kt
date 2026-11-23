package com.valghora.shrimpl.core.adblock

/**
 * Ad blocking's network filter (docs/TEMPLATE_SPEC.md T6.19): Adblock Plus network rules, as EasyList
 * and EasyPrivacy write them, matched against each request a page makes.
 *
 * WebView reports a request's URL but not its type, so rules that name a type (`$script`, `$image`, …)
 * are left out; rules that only exclude types (`$~xmlhttprequest`) apply to every request. A web app is
 * one site, so `$third-party` and `$domain=` are exact: the caller says whether a request leaves the
 * app's site and which page asked. `$popup` and `$document` rules, element hiding (`##`) and other
 * options this filter doesn't know are left out too, so a list can only ever block less, never more.
 *
 * Most of EasyList is `||host^`; those rules are a sorted array searched by each of the request host's
 * parent domains. The rest are filed under one word of their pattern, so a request only tries the rules
 * filed under the words in its URL, as Adblock Plus and uBlock Origin do. Thread-safe once built.
 */
class FilterEngine private constructor(
    private val blockedHosts: Array<String>,
    private val allowedHosts: Array<String>,
    private val blocking: RuleIndex,
    private val exceptions: RuleIndex,
    /** The rules this filter applies, for a download's sanity check (docs/SHRIMPL_SPEC.md R15.2). */
    val ruleCount: Int,
) {
    /**
     * Whether a request for [url] from a page on [pageHost] is blocked. [thirdParty] says whether the
     * request leaves the app's site. An exception rule (`@@`) wins over a block, unless the block is
     * `$important`.
     */
    fun shouldBlock(url: String, pageHost: String, thirdParty: Boolean): Boolean {
        val lower = url.lowercase()
        val host = hostOf(lower) ?: return false
        val request = Request(url, lower, host, pageHost.lowercase(), thirdParty, tokensOf(lower))
        val blockedByHost = hostListed(blockedHosts, host)
        val important = blocking.important(request)
        if (important) return true
        if (!blockedByHost && !blocking.matches(request)) return false
        return !(hostListed(allowedHosts, host) || exceptions.matches(request))
    }

    /** One request, prepared once for every rule it meets. */
    internal class Request(
        val url: String,
        val lower: String,
        val host: String,
        val pageHost: String,
        val thirdParty: Boolean,
        val tokens: Set<String>,
    )

    companion object {
        /** An engine for the rules in [lists], the text of each list as it was downloaded. */
        fun parse(vararg lists: String): FilterEngine {
            val blockedHosts = mutableListOf<String>()
            val allowedHosts = mutableListOf<String>()
            val blocking = RuleIndex.Builder()
            val exceptions = RuleIndex.Builder()
            var count = 0
            lists.forEach { list ->
                list.lineSequence().forEach { line ->
                    when (val parsed = FilterRule.parse(line)) {
                        null -> Unit
                        is FilterRule.Host -> {
                            (if (parsed.exception) allowedHosts else blockedHosts) += parsed.host
                            count++
                        }
                        is FilterRule.Pattern -> {
                            (if (parsed.exception) exceptions else blocking).add(parsed)
                            count++
                        }
                    }
                }
            }
            return FilterEngine(
                blockedHosts.distinct().sorted().toTypedArray(),
                allowedHosts.distinct().sorted().toTypedArray(),
                blocking.build(),
                exceptions.build(),
                count,
            )
        }

        /** The rules [list] holds that this filter applies; Shrimpl checks a download with it. */
        fun countRules(list: String): Int = list.lineSequence().count { FilterRule.parse(it) != null }

        /** The lowercase host of [url], or null when it has none (`data:`, `blob:`). */
        internal fun hostOf(url: String): String? {
            val scheme = url.indexOf("://")
            if (scheme <= 0) return null
            val start = scheme + 3
            var end = start
            while (end < url.length && url[end] !in "/?#") end++
            val authority = url.substring(start, end).substringAfterLast('@')
            val host = if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
            return host.ifEmpty { null }
        }

        /** The words of a URL: runs of letters, digits and `%`, the units rules are filed under. */
        internal fun tokensOf(lower: String): Set<String> {
            val tokens = HashSet<String>()
            var start = -1
            for (i in 0..lower.length) {
                val word = i < lower.length && isTokenChar(lower[i])
                if (word && start < 0) start = i
                if (!word && start >= 0) {
                    if (i - start >= RuleIndex.MIN_TOKEN) tokens += lower.substring(start, i)
                    start = -1
                }
            }
            return tokens
        }

        internal fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%'

        /** Whether [host] or one of its parent domains is in [sorted]. */
        private fun hostListed(sorted: Array<String>, host: String): Boolean {
            if (sorted.isEmpty()) return false
            var candidate = host
            while (true) {
                if (sorted.binarySearch(candidate) >= 0) return true
                val dot = candidate.indexOf('.')
                if (dot < 0) return false
                candidate = candidate.substring(dot + 1)
            }
        }
    }
}
