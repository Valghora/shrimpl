package com.valghora.shrimpl.core.update

import com.valghora.shrimpl.core.Versions

/**
 * Shrimpl's change log (docs/SHRIMPL_SPEC.md R14.7): `CHANGELOG.md`, bundled into the APK, read into
 * releases so Shrimpl can show what is new after an update.
 *
 * The file's form: `## <version>` or `## Unreleased` per release, newest first; `### <heading>` groups
 * within one (New, Changed, Fixed); `- ` items, whose wrapped lines are indented. An item may start with
 * a `**bold**` lead.
 */
object Changelog {
    /** One release: [version] is null for Unreleased, the changes not yet released, which ship as the build's own. */
    data class Release(val version: String?, val sections: List<Section>)

    /** A heading's items; the heading is empty for items directly under a release. */
    data class Section(val heading: String, val items: List<String>)

    /** A run of an item's text, bold or not. */
    data class Run(val text: String, val bold: Boolean)

    /** The releases in [text], newest first, as the file lists them. */
    fun parse(text: String): List<Release> {
        val releases = mutableListOf<Release>()
        var version: String? = null
        var inRelease = false
        var sections = mutableListOf<Section>()
        var heading = ""
        var items = mutableListOf<String>()

        fun closeSection() {
            if (items.isNotEmpty()) sections += Section(heading, items)
            items = mutableListOf()
        }

        fun closeRelease() {
            closeSection()
            if (inRelease && sections.isNotEmpty()) releases += Release(version, sections)
            sections = mutableListOf()
            heading = ""
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> {
                    closeRelease()
                    val name = line.removePrefix("## ").trim()
                    version = name.takeUnless { it.equals("Unreleased", ignoreCase = true) }
                    inRelease = true
                }
                line.startsWith("### ") -> {
                    closeSection()
                    heading = line.removePrefix("### ").trim()
                }
                line.startsWith("- ") -> items += line.removePrefix("- ").trim()
                line.startsWith("  ") && line.isNotBlank() && items.isNotEmpty() ->
                    items[items.lastIndex] = items.last() + " " + line.trim()
            }
        }
        closeRelease()
        return releases
    }

    /**
     * The releases to show after an update to [currentCode] from [lastSeenCode] (R14.7): every release
     * newer than the last one seen, Unreleased counting as the current one. With nothing seen yet (an
     * update from a version before this one), only the newest.
     */
    fun since(releases: List<Release>, lastSeenCode: Int?, currentCode: Int): List<Release> {
        if (lastSeenCode != null && lastSeenCode >= currentCode) return emptyList()
        val newer = releases.filter { release ->
            val code = release.version?.let(Versions::code) ?: currentCode
            code <= currentCode && (lastSeenCode == null || code > lastSeenCode)
        }
        return if (lastSeenCode == null) newer.take(1) else newer
    }

    /** [item]'s text in runs: `**bold**` spans bold, the rest plain, the marks removed. */
    fun runs(item: String): List<Run> {
        val runs = mutableListOf<Run>()
        var rest = item
        var bold = false
        while (rest.isNotEmpty()) {
            val mark = rest.indexOf("**")
            if (mark < 0) {
                runs += Run(rest, bold)
                break
            }
            if (mark > 0) runs += Run(rest.substring(0, mark), bold)
            rest = rest.substring(mark + 2)
            bold = !bold
        }
        return runs
    }
}
