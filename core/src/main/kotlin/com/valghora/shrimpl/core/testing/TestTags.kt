package com.valghora.shrimpl.core.testing

/**
 * Test tags for debug builds (docs/SHRIMPL_SPEC.md R13): an app named "[update,cert] Discord" shows
 * the states and outcomes its tags name, so each case can be tried without setting it up for real.
 */
enum class TestTag(val key: String) {
    /** Shows Update, as if built with an older template. */
    UPDATE("update"),

    /** Signed with another key: Fix, the Remove-only menu and the notice (R11.7). */
    CERT("cert"),

    /** Shows "Not installed". */
    MISSING("missing"),

    /** A simulated build fails at installing. */
    FAIL("fail"),

    /** A simulated install is turned down. */
    CANCEL("cancel"),

    /** A simulated install waits for confirmation (R6.5). */
    WAIT("wait"),

    /** A simulated install needs "Install unknown apps" (R6.3). */
    UNKNOWN_SOURCES("unknown-sources"),
}

/** How a test search address ends (R13); [stage] is the step it fails at, counted from 0. */
enum class TestSearch(val host: String, val stage: Int) {
    NOT_FOUND("404.test", 0),
    DENIED("403.test", 2),
    UNAVAILABLE("500.test", 0),
    ERROR("400.test", 0),
    OFFLINE("offline.test", 0),
    NO_SUCH_HOST("nohost.test", 0),
    TIMEOUT("timeout.test", 0),
    CERTIFICATE("certificate.test", 0),
    NOT_LOADED("unloaded.test", 0),
}

object TestTags {
    private val PREFIX = Regex("""^\s*\[([^\]]*)]""")

    /** The tags in a leading "[a,b]" of [name]; unknown ones are ignored. */
    fun parse(name: String): Set<TestTag> {
        val list = PREFIX.find(name)?.groupValues?.get(1) ?: return emptySet()
        val keys = list.split(',').map { it.trim().lowercase() }.toSet()
        return TestTag.entries.filterTo(mutableSetOf()) { it.key in keys }
    }

    /** The test search for [host] ("404.test", any case, with or without "www."), or null. */
    fun search(host: String): TestSearch? {
        val bare = host.lowercase().removePrefix("www.")
        return TestSearch.entries.firstOrNull { it.host == bare }
    }
}
