package com.valghora.shrimpl.core.build

import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.EmbeddedRecord
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** docs/SHRIMPL_SPEC.md R11.6: a built web app carries enough to list it again after a reinstall. */
class EmbeddedRecordTest {
    private val tmp: File = Files.createTempDirectory("embedded").toFile()

    private val profile = Profile(
        name = "Example",
        appId = "example",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#3367D6",
        headerColor = "#FFFFFF",
    )
    private val installed = EmbeddedRecord.Installed(
        packageName = "com.valghora.shrimpl.app.example",
        versionCode = 3,
        versionName = "7.3",
        signerFingerprint = "AA:BB",
        firstInstalled = 100,
        lastUpdated = 200,
    )
    private val website = byteArrayOf(9, 8, 7)
    private val custom = byteArrayOf(1, 2)
    private val options = IconOptions(
        IconOptions.Option.CUSTOM,
        brand = IconRecord.glyph("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M0 0h24v24H0z\"/></svg>".toByteArray(), "simple-icons 'x'"),
        website = IconRecord.image(maskable = false, origin = "web manifest icon", file = IconOptions.WEBSITE_IMAGE),
        custom = IconRecord.image(maskable = true, origin = "Photos", file = IconOptions.CUSTOM_IMAGE),
    )

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun build(extras: Map<String, ByteArray>): File {
        val out = File(tmp, "app.apk")
        WebAppBuilder.build(TemplateFixture.apk, out, WebAppSpec(profile, buildNumber = 3, extras = extras), TemplateFixture.key)
        return out
    }

    private val images = mapOf(IconOptions.WEBSITE_IMAGE to website, IconOptions.CUSTOM_IMAGE to custom)

    @Test
    fun recoversTheTemplateVersionFromANewVersionName() {
        val apk = build(EmbeddedRecord.entries(options, images::getValue))
        val recovered = ZipFile(apk).use { EmbeddedRecord.recover(it, installed.copy(versionName = "1.4.3")) { _, _ -> error("not needed") } }
        assertEquals("R11.8: 1.4 is code 104", 104, recovered.record.templateVersion)
    }

    @Test
    fun recoversProfileIconsAndImages() {
        val apk = build(EmbeddedRecord.entries(options, images::getValue))
        val recovered = ZipFile(apk).use { EmbeddedRecord.recover(it, installed) { _, _ -> error("not needed") } }
        val record = recovered.record
        assertEquals(profile, record.profile)
        assertEquals(options, record.icons)
        assertEquals(options.icon, record.icon)
        assertEquals(3, record.buildNumber)
        assertEquals("a name from before R11.8 (template 7, build 3)", 7, record.templateVersion)
        assertEquals("AA:BB", record.signerFingerprint)
        assertEquals(100L, record.createdAt)
        assertEquals(200L, record.updatedAt)
        assertArrayEquals(website, recovered.images.getValue(IconOptions.WEBSITE_IMAGE))
        assertArrayEquals(custom, recovered.images.getValue(IconOptions.CUSTOM_IMAGE))
    }

    @Test
    fun olderAppsComeBackWithTheirDrawnIcon() {
        val apk = build(emptyMap())
        val drawn = byteArrayOf(5, 5)
        var layers: Pair<Int, Int>? = null
        val recovered = ZipFile(apk).use { zip ->
            EmbeddedRecord.recover(zip, installed) { background, foreground ->
                layers = background.size to foreground.size
                drawn
            }
        }
        assertEquals(true, layers!!.first > 0 && layers!!.second > 0)
        val icons = recovered.record.iconOptions
        assertEquals(IconOptions.Option.CUSTOM, icons.selected)
        assertEquals(EmbeddedRecord.DRAWN_ORIGIN, icons.custom?.origin)
        assertEquals(IconRecord.Kind.LETTER, icons.website.kind)
        assertArrayEquals(drawn, recovered.images.getValue(IconOptions.CUSTOM_IMAGE))

        val undrawable = ZipFile(apk).use { zip -> EmbeddedRecord.recover(zip, installed) { _, _ -> null } }
        assertEquals(IconOptions.Option.WEBSITE, undrawable.record.iconOptions.selected)
        assertNull(undrawable.record.iconOptions.custom)
        assertEquals(emptyMap<String, ByteArray>(), undrawable.images)
    }

    @Test
    fun rejectsMismatchesAndBadVersions() {
        val apk = build(emptyMap())
        ZipFile(apk).use { zip ->
            assertThrows(EmbeddedRecord.RecoveryException::class.java) {
                EmbeddedRecord.recover(zip, installed.copy(packageName = "com.valghora.shrimpl.app.other")) { _, _ -> null }
            }
            assertThrows(EmbeddedRecord.RecoveryException::class.java) {
                EmbeddedRecord.recover(zip, installed.copy(versionName = "x")) { _, _ -> null }
            }
            assertThrows(EmbeddedRecord.RecoveryException::class.java) {
                EmbeddedRecord.recover(zip, installed.copy(versionCode = 0)) { _, _ -> null }
            }
        }
        // The template itself has no record for this package, and a corrupt icons.json is reported.
        val corrupt = build(mapOf(EmbeddedRecord.ICONS to "{".toByteArray()))
        ZipFile(corrupt).use { zip ->
            assertThrows(EmbeddedRecord.RecoveryException::class.java) { EmbeddedRecord.recover(zip, installed) { _, _ -> null } }
        }
        val missingImage = build(EmbeddedRecord.entries(options, images::getValue).filterKeys { !it.endsWith(IconOptions.CUSTOM_IMAGE) })
        ZipFile(missingImage).use { zip ->
            assertThrows(EmbeddedRecord.RecoveryException::class.java) { EmbeddedRecord.recover(zip, installed) { _, _ -> null } }
        }
    }

    @Test
    fun extrasStayInsideTheShrimplAssets() {
        listOf("assets/other.json", WebAppBuilder.Paths.PROFILE, "assets/shrimpl/../x").forEach { path ->
            assertThrows(BuildException::class.java) { build(mapOf(path to byteArrayOf(1))) }
        }
    }
}
