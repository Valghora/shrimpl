package com.valghora.shrimpl.core.adblock

/**
 * One network rule of an Adblock Plus list, as [FilterEngine] applies it (docs/TEMPLATE_SPEC.md T6.19).
 * [parse] returns null for everything the filter leaves out: comments, element hiding, and rules with
 * an option it can't honor.
 */
internal sealed class FilterRule {
    abstract val exception: Boolean

    /** `||host^` with no options: the request's host is [host] or one of its subdomains. */
    data class Host(val host: String, override val exception: Boolean) : FilterRule()

    /** Any other rule: a URL pattern and the options that limit it. */
    class Pattern(
        /** The pattern as written, without `@@` and options. */
        val text: String,
        override val exception: Boolean,
        /** True or false limits the rule to third-party or first-party requests; null applies to both. */
        val thirdParty: Boolean?,
        /** Pages the rule applies on (`$domain=a.com`); empty means every page. */
        val includeDomains: List<String>,
        /** Pages the rule doesn't apply on (`$domain=~a.com`). */
        val excludeDomains: List<String>,
        val matchCase: Boolean,
        /** `$important`: a block no exception lifts. */
        val important: Boolean,
    ) : FilterRule() {
        /** The pattern as a regular expression, built the first time the rule meets a request. */
        @Volatile
        private var compiled: Regex? = null

        fun matches(request: FilterEngine.Request): Boolean {
            if (thirdParty != null && thirdParty != request.thirdParty) return false
            if (includeDomains.isNotEmpty() && includeDomains.none { onDomain(request.pageHost, it) }) return false
            if (excludeDomains.any { onDomain(request.pageHost, it) }) return false
            val regex = compiled ?: toRegex(text, matchCase).also { compiled = it }
            return regex.containsMatchIn(if (matchCase) request.url else request.lower)
        }
    }

    companion object {
        /** Options that type a request; this filter can't see the type, so a rule needing one is left out. */
        private val TYPES = setOf(
            "script", "image", "stylesheet", "object", "xmlhttprequest", "xhr", "subdocument", "frame",
            "ping", "websocket", "webrtc", "font", "media", "other", "object-subrequest", "css",
        )

        /** Options that don't apply to a page's requests at all: whole pages, popups, element hiding. */
        private val NOT_REQUESTS = setOf(
            "document", "doc", "popup", "elemhide", "ehide", "generichide", "ghide", "genericblock",
            "csp", "rewrite", "redirect", "redirect-rule", "removeparam", "header", "permissions",
        )

        fun parse(raw: String): FilterRule? {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) return null
            // Element hiding and scriptlets: ##, #@#, #?#, #$#, #%#.
            if (ELEMENT_HIDING.containsMatchIn(line)) return null
            val exception = line.startsWith("@@")
            val body = if (exception) line.substring(2) else line
            val (pattern, options) = splitOptions(body)
            if (pattern.isEmpty() || pattern == "*" || pattern == "|" || pattern == "||") return null

            var thirdParty: Boolean? = null
            val include = mutableListOf<String>()
            val exclude = mutableListOf<String>()
            var matchCase = false
            var important = false
            for (option in options) {
                val negated = option.startsWith('~')
                val name = option.removePrefix("~").substringBefore('=').lowercase()
                when {
                    name == "third-party" || name == "3p" -> thirdParty = !negated
                    name == "first-party" || name == "1p" -> thirdParty = negated
                    name == "domain" || name == "from" -> option.substringAfter('=').split('|').forEach { d ->
                        val domain = d.trim().lowercase()
                        if (domain.startsWith('~')) exclude += domain.substring(1) else if (domain.isNotEmpty()) include += domain
                    }
                    name == "match-case" -> matchCase = true
                    name == "important" -> important = true
                    // `~script`: every request but scripts. The type is unknown, so the rule applies to all.
                    name in TYPES && negated -> Unit
                    else -> return null
                }
            }
            if (!exception && include.isEmpty() && exclude.isEmpty() && thirdParty == null && !important && !matchCase) {
                HOST_RULE.matchEntire(pattern)?.let { return Host(it.groupValues[1].lowercase(), exception) }
            }
            if (exception && options.isEmpty()) {
                HOST_RULE.matchEntire(pattern)?.let { return Host(it.groupValues[1].lowercase(), exception) }
            }
            if (!isRegexRule(pattern) && runCatching { toRegex(pattern, matchCase) }.isFailure) return null
            if (isRegexRule(pattern) && runCatching { Regex(pattern.substring(1, pattern.length - 1)) }.isFailure) return null
            return Pattern(pattern, exception, thirdParty, include, exclude, matchCase, important)
        }

        /** The pattern and its options; a `$` inside a regular expression rule isn't an options mark. */
        private fun splitOptions(body: String): Pair<String, List<String>> {
            val dollar = body.lastIndexOf('$')
            if (dollar < 0) return body to emptyList()
            val head = body.substring(0, dollar)
            if (isRegexRule(body) && !isRegexRule(head)) return body to emptyList()
            val options = body.substring(dollar + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (options.isEmpty()) return body to emptyList()
            return head to options
        }

        private fun isRegexRule(pattern: String) = pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')

        /** Whether [pageHost] is [domain] or one of its subdomains; `example.*` matches any ending. */
        fun onDomain(pageHost: String, domain: String): Boolean {
            if (domain.endsWith(".*")) {
                val stem = domain.dropLast(2)
                return pageHost == stem || pageHost.startsWith("$stem.") || pageHost.contains(".$stem.")
            }
            return pageHost == domain || pageHost.endsWith(".$domain")
        }

        /**
         * The Adblock Plus pattern [pattern] as a regular expression: `||` is the start of the host or a
         * label of it, `|` the start or end of the URL, `*` anything, `^` a separator (anything but a
         * letter, digit, `_`, `-`, `.` or `%`) or the end. A `/…/` rule is a regular expression already.
         */
        fun toRegex(pattern: String, matchCase: Boolean): Regex {
            if (isRegexRule(pattern)) {
                val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                return Regex(pattern.substring(1, pattern.length - 1), options)
            }
            var text = if (matchCase) pattern else pattern.lowercase()
            val out = StringBuilder()
            when {
                text.startsWith("||") -> {
                    out.append("^[a-z][a-z0-9+.-]*://([^/?#]*\\.)?")
                    text = text.substring(2)
                }
                text.startsWith("|") -> {
                    out.append('^')
                    text = text.substring(1)
                }
            }
            val anchoredEnd = text.endsWith("|")
            if (anchoredEnd) text = text.dropLast(1)
            for (c in text) {
                when (c) {
                    '*' -> out.append(".*")
                    '^' -> out.append("(?:[^a-zA-Z0-9_.%-]|$)")
                    else -> if (c.isLetterOrDigit()) out.append(c) else out.append('\\').append(c)
                }
            }
            if (anchoredEnd) out.append('$')
            return Regex(out.toString())
        }

        private val ELEMENT_HIDING = Regex("#[@?$%]?#")
        private val HOST_RULE = Regex("""\|\|([a-z0-9][a-z0-9.-]*[a-z0-9])\^""", RegexOption.IGNORE_CASE)
    }
}

/**
 * Pattern rules filed by one word of their pattern, so a request tries only the rules filed under the
 * words of its URL (and the few with no usable word).
 */
internal class RuleIndex private constructor(
    private val byToken: Map<String, List<FilterRule.Pattern>>,
    private val untokened: List<FilterRule.Pattern>,
) {
    fun matches(request: FilterEngine.Request): Boolean = candidates(request).any { it.matches(request) }

    /** Whether an `$important` rule blocks the request. */
    fun important(request: FilterEngine.Request): Boolean =
        candidates(request).any { it.important && it.matches(request) }

    private fun candidates(request: FilterEngine.Request): Sequence<FilterRule.Pattern> =
        request.tokens.asSequence().flatMap { byToken[it].orEmpty().asSequence() } + untokened.asSequence()

    class Builder {
        private val byToken = HashMap<String, MutableList<FilterRule.Pattern>>()
        private val untokened = mutableListOf<FilterRule.Pattern>()

        fun add(rule: FilterRule.Pattern) {
            val token = tokenOf(rule.text)
            if (token == null) untokened += rule else byToken.getOrPut(token) { mutableListOf() } += rule
        }

        fun build() = RuleIndex(byToken, untokened)
    }

    companion object {
        /** Words shorter than this are too common to narrow anything down. */
        const val MIN_TOKEN = 3

        /**
         * The longest word of [pattern] that is a whole word in every URL the pattern matches: bounded on
         * both sides by a separator or an anchor, never by `*` or the pattern's open start or end (which
         * could continue the word in the URL). Null for regular expression rules and patterns with none.
         */
        fun tokenOf(pattern: String): String? {
            if (pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')) return null
            var text = pattern.lowercase()
            var openStart = true
            if (text.startsWith("||")) {
                text = text.substring(2)
                openStart = false
            } else if (text.startsWith("|")) {
                text = text.substring(1)
                openStart = false
            }
            var openEnd = true
            if (text.endsWith("|")) {
                text = text.dropLast(1)
                openEnd = false
            }
            var best: String? = null
            var start = -1
            for (i in 0..text.length) {
                val word = i < text.length && FilterEngine.isTokenChar(text[i])
                if (word && start < 0) start = i
                if (!word && start >= 0) {
                    val boundedBefore = if (start == 0) !openStart else text[start - 1] != '*'
                    val boundedAfter = if (i == text.length) !openEnd else text[i] != '*'
                    val candidate = text.substring(start, i)
                    if (boundedBefore && boundedAfter && candidate.length >= MIN_TOKEN && candidate.length > (best?.length ?: 0)) {
                        best = candidate
                    }
                    start = -1
                }
            }
            return best
        }
    }
}
