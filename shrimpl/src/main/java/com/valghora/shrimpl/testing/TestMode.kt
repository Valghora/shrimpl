package com.valghora.shrimpl.testing

import com.valghora.shrimpl.BuildConfig
import com.valghora.shrimpl.core.testing.TestSearch
import com.valghora.shrimpl.core.testing.TestTag
import com.valghora.shrimpl.core.testing.TestTags

/**
 * Test tags and test search addresses (docs/SHRIMPL_SPEC.md R13), in debug builds only: release
 * builds never read them, so a real app named "update" is just an app.
 */
object TestMode {
    private val enabled = BuildConfig.DEBUG

    /** The test tags in an app's [name]. */
    fun tags(name: String): Set<TestTag> = if (enabled) TestTags.parse(name) else emptySet()

    /** The test search a normalized https [url] names, or null. */
    fun search(url: String): TestSearch? {
        if (!enabled) return null
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        return TestTags.search(host)
    }
}
