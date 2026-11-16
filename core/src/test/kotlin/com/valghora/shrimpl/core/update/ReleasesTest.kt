package com.valghora.shrimpl.core.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/SHRIMPL_SPEC.md R14.1: GitHub's latest release, read as a Shrimpl release, and its checksum file. */
class ReleasesTest {
    private fun answer(tag: String = "v1.0.3", assets: List<String> = listOf("shrimpl-1.0.3.apk", "shrimpl-1.0.3.apk.sha256")) = """
        {"tag_name": "$tag", "html_url": "https://github.com/Valghora/shrimpl/releases/tag/$tag", "draft": false,
         "assets": [${assets.joinToString(",") { """{"name": "$it", "browser_download_url": "https://example.com/$it", "size": 1}""" }}]}
    """.trimIndent()

    @Test
    fun aReleaseWithItsTwoFilesIsRead() {
        val release = Releases.parse(answer())
        assertEquals(
            Release(
                version = "1.0.3",
                code = 103,
                apkUrl = "https://example.com/shrimpl-1.0.3.apk",
                checksumUrl = "https://example.com/shrimpl-1.0.3.apk.sha256",
                pageUrl = "https://github.com/Valghora/shrimpl/releases/tag/v1.0.3",
            ),
            release,
        )
    }

    @Test
    fun anythingElseIsNoRelease() {
        assertNull("no checksum file", Releases.parse(answer(assets = listOf("shrimpl-1.0.3.apk"))))
        assertNull("no APK", Releases.parse(answer(assets = listOf("shrimpl-1.0.3.apk.sha256"))))
        assertNull("a tag outside the version scheme", Releases.parse(answer(tag = "v1.12")))
        assertNull("not JSON", Releases.parse("<html>"))
        assertNull("no tag", Releases.parse("""{"assets": []}"""))
        assertNull("not an object", Releases.parse("[1, 2]"))
        assertNull("assets of the wrong shape", Releases.parse("""{"tag_name": "v1.0.3", "assets": [1, {"name": 2}]}"""))
        assertEquals("a page link is optional", "", Releases.parse(answer().replace(""""html_url": "https://github.com/Valghora/shrimpl/releases/tag/v1.0.3",""", ""))?.pageUrl)
    }

    @Test
    fun theChecksumIsReadForTheFile() {
        val hash = "AB00ccbddc20f41c1afe4119da5877a5d8735b9e332ddd2c4bee36158ea9a116"
        assertEquals(hash.lowercase(), Releases.checksumFor("$hash  shrimpl-1.0.3.apk\n", "shrimpl-1.0.3.apk"))
        assertEquals("binary-mode marker", hash.lowercase(), Releases.checksumFor("$hash *shrimpl-1.0.3.apk", "shrimpl-1.0.3.apk"))
        assertEquals("the hash alone", hash.lowercase(), Releases.checksumFor(hash, "shrimpl-1.0.3.apk"))
        assertNull("another file's hash", Releases.checksumFor("$hash  other.apk", "shrimpl-1.0.3.apk"))
        assertNull(Releases.checksumFor("not a hash", "shrimpl-1.0.3.apk"))
    }

    @Test
    fun namesAndHashing() {
        assertEquals("shrimpl-1.2.3.apk", Releases.apkName("1.2.3"))
        assertEquals("shrimpl-1.2.3.apk.sha256", Releases.checksumName("1.2.3"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Releases.sha256(ByteArrayInputStream("abc".toByteArray())))
    }

    @Test
    fun frequenciesInHoursWithADailyDefault() {
        assertEquals("Disabled first, then each interval", listOf(0L, 1L, 6L, 12L, 24L, 168L), UpdateFrequency.entries.map { it.hours })
        assertEquals(UpdateFrequency.DAY, UpdateFrequency.DEFAULT)
    }
}
