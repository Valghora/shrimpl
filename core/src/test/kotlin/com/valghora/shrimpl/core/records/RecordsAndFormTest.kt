package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.Modifier
import com.valghora.shrimpl.core.ShortcutSpec
import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.discovery.Confidence
import com.valghora.shrimpl.core.discovery.IconChoice
import com.valghora.shrimpl.core.icons.IconSource
import com.valghora.shrimpl.core.icons.SvgGlyph
import com.valghora.shrimpl.core.profile.HeaderMode
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.profile.ShortcutRow
import com.valghora.shrimpl.core.profile.WindowSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsAndFormTest {
    private val profile = Profile(
        name = "Example",
        appId = "example",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#3367D6",
        headerColor = "#FFFFFF",
        appShortcuts = listOf(ShortcutSpec("Search", "KEYCODE_K", setOf(Modifier.CTRL))),
    )
    private val svg = """<svg viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>""".toByteArray()

    // ---------- Records ----------

    @Test
    fun recordRoundTripAndBuildNumbers() {
        val record = WebAppRecord.create(profile, IconRecord.letter('e'), templateVersion = 1, signerFingerprint = "AA", now = 100)
        assertEquals(1, record.buildNumber)
        assertEquals(2, record.nextBuildNumber)
        assertEquals("com.valghora.shrimpl.app.example", record.packageName)
        assertEquals(record, WebAppRecord.decode(WebAppRecord.encode(record)))
        val updated = record.installed(profile.copy(name = "Ex"), IconRecord.letter('x'), built = 2, templateVersion = 2, signerFingerprint = "AA", now = 200)
        assertEquals(2, updated.buildNumber)
        assertEquals(100, updated.createdAt)
        assertEquals(200, updated.updatedAt)
        assertTrue(record.updateAvailable(bundledTemplateVersion = 2))
        assertFalse(updated.updateAvailable(bundledTemplateVersion = 2))
        assertThrows(IllegalArgumentException::class.java) { WebAppRecord.decode("""{"profile":1}""") }
        assertThrows(IllegalArgumentException::class.java) { WebAppRecord.decode("not json") }
        // A record saved before header modes keeps the system color.
        val systemHeader = record.copy(profile = profile.copy(headerColor = Profile.DEFAULT_HEADER_COLOR))
        val legacy = WebAppRecord.encode(systemHeader).replace("\"headerMode\": \"system\",", "").replace(Profile.DEFAULT_HEADER_COLOR, Profile.SYSTEM)
        assertTrue("headerMode" !in legacy)
        assertEquals(systemHeader, WebAppRecord.decode(legacy))
    }

    @Test
    fun iconRecordsFromDiscoveryAndToSources() {
        val glyph = SvgGlyph.parse(svg)
        val (glyphRecord, noBytes) = IconRecord.from(IconChoice.Glyph(svg, glyph, "#000000", "", "simple-icons 'x'", Confidence.HIGH))
        assertNull(noBytes)
        assertEquals(IconSource.Glyph(glyph), glyphRecord.toSource { error("no image") })

        val bytes = byteArrayOf(1, 2, 3)
        val (imageRecord, imageBytes) = IconRecord.from(IconChoice.Raster(bytes, "png", 512, true, "web manifest icon", Confidence.MEDIUM))
        assertArrayEquals(bytes, imageBytes)
        val source = imageRecord.toSource { name -> assertEquals(IconRecord.IMAGE_FILE, name); bytes } as IconSource.Raster
        assertTrue(source.maskable)

        val (letterRecord, _) = IconRecord.from(IconChoice.Letter('q', "no usable brand icon found", Confidence.LOW))
        assertEquals("Q", letterRecord.letter)
        assertEquals(IconSource.Letter('Q'), letterRecord.toSource { error("no image") })
        assertThrows(IllegalArgumentException::class.java) { IconRecord(IconRecord.Kind.LETTER).toSource { ByteArray(0) } }
    }

    // ---------- Form ----------

    @Test
    fun formRoundTripsAProfile() {
        val form = ProfileForm.from(profile)
        assertEquals(listOf(ShortcutRow("Search", "K", setOf(Modifier.CTRL))), form.shortcuts)
        assertEquals("", ProfileForm.from(profile.copy(iconForeground = Profile.AUTO)).iconForeground) // "auto" shows as empty
        val result = form.result()
        assertEquals(emptyList<Any>(), result.errors)
        assertEquals(profile, result.profile)
    }

    @Test
    fun formNormalizesInput() {
        val form = ProfileForm.from(profile).copy(
            name = "  Example  ",
            startUrl = " https://app.example.com/ ",
            internalDomains = listOf("Example.com ", "", "example.com", "cdn.example.net"),
            headerColor = "fff",
            iconForeground = "000000",
            userAgentMode = UaMode.DESKTOP,
            defaultWidth = " 1000", defaultHeight = "700",
            shortcuts = listOf(ShortcutRow("Help", "f1", emptySet()), ShortcutRow("Find", "keycode_slash", setOf(Modifier.CTRL, Modifier.SHIFT))),
        )
        val p = form.result().profile!!
        assertEquals("Example", p.name)
        assertEquals(listOf("example.com", "cdn.example.net"), p.internalDomains)
        assertEquals("#FFFFFF", p.headerColor)
        assertEquals(Profile.DEFAULT_HEADER_COLOR, form.copy(headerColor = " ").result().profile!!.headerColor)
        assertEquals(HeaderMode.DYNAMIC, form.copy(headerMode = HeaderMode.DYNAMIC).result().profile!!.headerMode)
        assertEquals("#000000", p.iconForeground)
        assertEquals(UaMode.DESKTOP, p.userAgentMode)
        assertEquals(WindowSize(1000, 700), p.defaultWindowDp)
        assertEquals(listOf("KEYCODE_F1", "KEYCODE_SLASH"), p.appShortcuts.map { it.keyName })
    }

    @Test
    fun formReportsFieldErrors() {
        val bad = ProfileForm.from(profile).copy(name = "", headerColor = "blue", startUrl = "http://example.com")
        val result = bad.result()
        assertNull(result.profile)
        assertEquals("must not be empty", result.errorFor("name"))
        assertEquals("must be #RRGGBB", result.errorFor("headerColor"))
        assertEquals("must be an https:// URL", result.errorFor("startUrl"))
        assertNull(result.errorFor("iconBackground"))

        // Unparseable values don't hide other errors.
        val dims = ProfileForm.from(profile).copy(defaultWidth = "wide", minHeight = "", name = " ").result()
        assertNull(dims.profile)
        assertEquals("must be a whole number", dims.errorFor("defaultWindowDp"))
        assertEquals("must be a whole number", dims.errorFor("minWindowDp"))
        assertEquals("must not be empty", dims.errorFor("name"))
        assertEquals(1, dims.errors.count { it.field == "defaultWindowDp" })

        val keys = ProfileForm.from(profile).copy(shortcuts = listOf(ShortcutRow("X", "!"))).result()
        assertTrue(keys.errorFor("appShortcuts")!!.contains("enter a key"))
        val clash = ProfileForm.from(profile).copy(shortcuts = listOf(ShortcutRow("Reload", "R"))).result()
        assertTrue(clash.errorFor("appShortcuts")!!.contains("collides with built-in RELOAD"))
    }

    @Test
    fun colorAndKeyHelpers() {
        assertEquals("#AABBCC", ProfileForm.color("abc"))
        assertEquals("#A1B2C3", ProfileForm.color(" #a1b2c3 "))
        assertEquals("", ProfileForm.color("  "))
        assertEquals("red", ProfileForm.color("red"))
        assertEquals("KEYCODE_K", ProfileForm.keyName("k"))
        assertEquals("KEYCODE_F12", ProfileForm.keyName("F12"))
        assertEquals("KEYCODE_MOVE_HOME", ProfileForm.keyName("move_home"))
        assertNull(ProfileForm.keyName("?"))
        assertNull(ProfileForm.keyName(""))
    }
}
