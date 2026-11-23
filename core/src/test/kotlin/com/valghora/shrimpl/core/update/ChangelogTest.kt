package com.valghora.shrimpl.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogTest {
    private val text = """
        # What's new in Shrimpl

        ## Unreleased

        ### New

        - **App options.** Open it with Ctrl+, to switch
          settings without leaving the app.
        - Ad blocking.

        ### Fixed

        - A fix.

        ## 1.1.2

        ### New

        - Live settings.

        ## 1.0.9

        - Initial release
    """.trimIndent()

    private val releases = Changelog.parse(text)

    @Test
    fun releasesSectionsAndWrappedItemsAreRead() {
        assertEquals(listOf(null, "1.1.2", "1.0.9"), releases.map { it.version })
        val unreleased = releases[0]
        assertEquals(listOf("New", "Fixed"), unreleased.sections.map { it.heading })
        assertEquals("**App options.** Open it with Ctrl+, to switch settings without leaving the app.", unreleased.sections[0].items[0])
        assertEquals(listOf(Changelog.Section("", listOf("Initial release"))), releases[2].sections)
    }

    @Test
    fun anUpdateShowsEveryReleaseSinceTheLastSeenOne() {
        assertEquals(listOf(null, "1.1.2"), Changelog.since(releases, lastSeenCode = 109, currentCode = 116).map { it.version })
        assertEquals(listOf<String?>(null), Changelog.since(releases, lastSeenCode = 112, currentCode = 116).map { it.version })
        assertEquals("nothing new once seen", emptyList<Changelog.Release>(), Changelog.since(releases, lastSeenCode = 116, currentCode = 116))
        assertEquals("first time with this feature: the newest only", listOf<String?>(null), Changelog.since(releases, null, 116).map { it.version })
        assertEquals("a release above the build isn't shown", listOf("1.1.2"), Changelog.since(releases.drop(1), 109, 112).map { it.version })
    }

    @Test
    fun boldLeadsBecomeRuns() {
        assertEquals(
            listOf(Changelog.Run("App options.", true), Changelog.Run(" Open it.", false)),
            Changelog.runs("**App options.** Open it."),
        )
        assertEquals(listOf(Changelog.Run("Plain", false)), Changelog.runs("Plain"))
    }

    @Test
    fun theRepositorysChangelogParses() {
        val file = java.io.File("../CHANGELOG.md")
        val parsed = Changelog.parse(file.readText())
        assertEquals("the newest release has items", true, parsed.first().sections.any { it.items.isNotEmpty() })
        assertEquals("1.0.9", parsed.last().version)
    }
}
