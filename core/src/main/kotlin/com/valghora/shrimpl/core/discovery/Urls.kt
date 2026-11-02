package com.valghora.shrimpl.core.discovery

import java.net.URI

/** Lenient URL helpers: real pages carry URLs java.net.URI rejects, so these never throw. */
internal object Urls {
    private val SPLIT = Regex(
        "^(?:([A-Za-z][A-Za-z0-9+.-]*):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$",
        RegexOption.DOT_MATCHES_ALL,
    )

    data class Parts(val scheme: String, val netloc: String, val path: String, val query: String) {
        /** Lowercase host without user info or port, or null (urllib's `hostname`). */
        val hostname: String?
            get() {
                val hostPort = netloc.substringAfterLast('@')
                val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']').drop(1) else hostPort.substringBefore(':')
                return host.lowercase().takeIf { it.isNotEmpty() }
            }
    }

    fun split(url: String): Parts {
        val m = SPLIT.matchEntire(url) ?: return Parts("", "", url, "")
        return Parts(
            scheme = m.groupValues[1].lowercase(),
            netloc = m.groupValues[2],
            path = m.groupValues[3],
            query = m.groupValues[4],
        )
    }

    fun hostOf(url: String?): String? = url?.let { split(it).hostname }

    /** RFC 3986 reference resolution (urllib's `urljoin`); falls back to [ref] if either can't be parsed. */
    fun join(base: String, ref: String): String {
        if (base.isEmpty()) return ref
        return try {
            var baseUri = URI(base)
            if (baseUri.rawPath.isNullOrEmpty() && baseUri.rawAuthority != null) {
                baseUri = URI(baseUri.scheme, baseUri.rawAuthority, "/", null, null)
            }
            baseUri.resolve(URI(ref)).toString()
        } catch (_: Exception) {
            ref
        }
    }
}

/** Registrable domains ("eTLD+1") from the Public Suffix List. */
class PublicSuffixList(text: String) {
    private val rules = HashSet<String>()
    private val wildcards = HashSet<String>()
    private val exceptions = HashSet<String>()

    init {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            when {
                line.startsWith("!") -> exceptions += line.substring(1)
                line.startsWith("*.") -> wildcards += line.substring(2)
                else -> rules += line
            }
        }
    }

    /** eTLD+1 of [host], e.g. cdn.example.co.uk -> example.co.uk; null for a public suffix itself. */
    fun registrable(host: String): String? {
        val labels = host.lowercase().trimEnd('.').split('.')
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in exceptions) return candidate
            val parent = labels.subList(minOf(i + 1, labels.size), labels.size).joinToString(".")
            if (candidate in rules || parent in wildcards) {
                return if (i > 0) labels.subList(i - 1, labels.size).joinToString(".") else null
            }
        }
        return if (labels.size >= 2) labels.takeLast(2).joinToString(".") else null
    }
}
