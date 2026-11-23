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

    // ----- Records -----

    @Test
    fun recordRoundTripAndBuildNumbers() {
        val record = WebAppRecord.create(profile, IconOptions.of(null, IconRecord.letter('e')), templateVersion = 1, signerFingerprint = "AA", now = 100)
        assertEquals(1, record.buildNumber)
        assertEquals(2, record.nextBuildNumber)
        assertEquals("com.valghora.shrimpl.app.example", record.packageName)
        assertEquals(record, WebAppRecord.decode(WebAppRecord.encode(record)))
        val updated = record.installed(profile.copy(name = "Ex"), IconOptions.of(null, IconRecord.letter('x')), built = 2, templateVersion = 2, signerFingerprint = "AA", now = 200)
        assertEquals(IconRecord.letter('x'), updated.icon)
        assertEquals(2, updated.buildNumber)
        assertEquals(100, updated.createdAt)
        assertEquals(200, updated.updatedAt)
        assertTrue(record.updateAvailable(bundledTemplateVersion = 2))
        assertFalse(updated.updateAvailable(bundledTemplateVersion = 2))
        assertThrows(IllegalArgumentException::class.java) { WebAppRecord.decode("""{"profile":1}""") }
        assertThrows(IllegalArgumentException::class.java) { WebAppRecord.decode("not json") }
        // A record saved before header modes keeps the system color.
        val systemHeader = record.copy(profile = profile.copy(headerMode = HeaderMode.SYSTEM, headerColor = Profile.DEFAULT_HEADER_COLOR))
        val legacy = WebAppRecord.encode(systemHeader).replace("\"headerMode\": \"system\",", "").replace(Profile.DEFAULT_HEADER_COLOR, Profile.SYSTEM)
        assertTrue("headerMode" !in legacy)
        assertEquals(systemHeader, WebAppRecord.decode(legacy))
    }

    @Test
    fun iconSetOptionsAndSelection() {
        val brand = IconRecord.glyph(svg, "simple-icons 'example'")
        val website = IconRecord.image(maskable = true, origin = "web manifest icon", file = IconOptions.WEBSITE_IMAGE)
        val custom = IconRecord.image(maskable = false, origin = "Photo", file = IconOptions.CUSTOM_IMAGE)

        val withBrand = IconOptions.of(brand, website)
        assertEquals(IconOptions.Option.BRAND, withBrand.selected)
        assertEquals(brand, withBrand.icon)
        assertEquals(listOf(IconOptions.Option.BRAND, IconOptions.Option.WEBSITE, IconOptions.Option.CUSTOM), withBrand.available)

        val noBrand = IconOptions.of(null, website)
        assertEquals(IconOptions.Option.WEBSITE, noBrand.selected)
        assertEquals(listOf(IconOptions.Option.WEBSITE, IconOptions.Option.CUSTOM), noBrand.available)
        // Options without an icon fall back to the website's.
        assertEquals(website, noBrand.copy(selected = IconOptions.Option.BRAND).icon)
        assertEquals(website, noBrand.copy(selected = IconOptions.Option.CUSTOM).icon)
        assertEquals(custom, noBrand.copy(selected = IconOptions.Option.CUSTOM, custom = custom).icon)

        assertEquals('E', IconOptions.letterFor("example"))
        assertEquals('A', IconOptions.letterFor("ñ!"))
    }

    @Test
    fun recordsWithoutIconSetsAreUpgraded() {
        val glyph = IconRecord.glyph(svg, "simple-icons 'example'")
        val image = IconRecord.image(maskable = false, origin = "Photo", file = "icon-image")
        val siteImage = IconRecord.image(maskable = true, origin = "web manifest icon (maskable 512px png)", file = "icon-image")
        val letter = IconRecord.letter('x')
        val letterE = IconRecord.letter('E')
        assertEquals(IconOptions(IconOptions.Option.BRAND, brand = glyph, website = letterE), IconOptions.legacy(glyph, "Example"))
        assertEquals(IconOptions(IconOptions.Option.CUSTOM, website = letterE, custom = image), IconOptions.legacy(image, "Example"))
        // An image that came from the site is its Website icon.
        assertEquals(IconOptions(IconOptions.Option.WEBSITE, website = siteImage), IconOptions.legacy(siteImage, "Example"))
        assertEquals(setOf("icon-image"), IconOptions.legacy(siteImage, "Example").imageFiles)
        assertEquals(IconOptions(IconOptions.Option.WEBSITE, website = letter), IconOptions.legacy(letter, "Example"))

        val record = WebAppRecord.create(profile, IconOptions.of(glyph, letterE), templateVersion = 1, signerFingerprint = "AA", now = 100)
        assertEquals(record.icons, record.iconOptions)
        // Saved before icon sets: no "icons" key, just the one icon.
        val old = WebAppRecord.decode(WebAppRecord.encode(record.copy(icons = null)))
        assertEquals(null, old.icons)
        assertEquals(IconOptions.legacy(glyph, profile.name), old.iconOptions)
    }

    @Test
    fun iconRecordsToSources() {
        val glyph = SvgGlyph.parse(svg)
        assertEquals(IconSource.Glyph(glyph), IconRecord.glyph(svg, "simple-icons 'x'").toSource { error("no image") })

        val bytes = byteArrayOf(1, 2, 3)
        val image = IconRecord.image(maskable = true, origin = "web manifest icon", file = IconOptions.WEBSITE_IMAGE)
        val source = image.toSource { name -> assertEquals(IconOptions.WEBSITE_IMAGE, name); bytes } as IconSource.Raster
        assertTrue(source.maskable)
        assertArrayEquals(bytes, source.bytes)

        val letter = IconRecord.letter('q')
        assertEquals("Q", letter.letter)
        assertEquals(IconSource.Letter('Q'), letter.toSource { error("no image") })
        assertThrows(IllegalArgumentException::class.java) { IconRecord(IconRecord.Kind.LETTER).toSource { ByteArray(0) } }
    }

    // ----- Drafts -----

    private val raster = IconChoice.Raster(byteArrayOf(9, 9), "png", 512, true, "web manifest icon (maskable 512px png)", Confidence.MEDIUM)

    @Test
    fun draftsFromDiscovery() {
        val form = ProfileForm.from(profile)
        val brand = WebAppDraft.brandOf(IconChoice.Glyph(svg, SvgGlyph.parse(svg), "#000000", "", "simple-icons 'x'", Confidence.HIGH))
        assertEquals(IconRecord.Kind.GLYPH, brand?.kind)
        assertNull(WebAppDraft.brandOf(raster))

        val withBrand = WebAppDraft.create(form, brand, raster)
        assertEquals(IconOptions.Option.BRAND, withBrand.icons.selected)
        assertEquals(IconRecord.image(true, raster.source, IconOptions.WEBSITE_IMAGE), withBrand.icons.website)
        assertArrayEquals(raster.bytes, withBrand.newImages.getValue(IconOptions.WEBSITE_IMAGE))
        assertEquals(setOf(IconOptions.WEBSITE_IMAGE), withBrand.icons.imageFiles)

        // No brand glyph and no image: a letter of the name, selected.
        val bare = WebAppDraft.create(form, null, null)
        assertEquals(IconOptions.Option.WEBSITE, bare.icons.selected)
        assertEquals(IconRecord.letter('E'), bare.icons.website)
        assertEquals(emptyMap<String, ByteArray>(), bare.newImages)
        assertEquals(emptySet<String>(), bare.icons.imageFiles)
    }

    @Test
    fun draftEdits() {
        val form = ProfileForm.from(profile)
        val draft = WebAppDraft.create(form, null, null)

        // The letter follows a new name, and only then.
        assertEquals(IconRecord.letter('Z'), draft.withForm(form.copy(name = "Zed")).icons.website)
        assertEquals(draft.icons, draft.withForm(form.copy(startUrl = "https://example.com/x")).icons)
        val picked = draft.copy(icons = draft.icons.copy(website = IconRecord.letter('Q')))
        assertEquals(IconRecord.letter('Q'), picked.withForm(form.copy(startUrl = "https://example.com/x")).icons.website)
        // An image website icon doesn't follow the name.
        val withImage = WebAppDraft.create(form, null, raster)
        assertEquals(withImage.icons, withImage.withForm(form.copy(name = "Zed")).icons)

        val filled = withImage.withWebsiteFill(false)
        assertFalse(filled.icons.website.maskable)

        val custom = draft.withCustomIcon(byteArrayOf(1), maskable = false, origin = "Photos")
        assertEquals(IconOptions.Option.CUSTOM, custom.icons.selected)
        assertEquals(IconRecord.image(false, "Photos", IconOptions.CUSTOM_IMAGE), custom.icons.icon)
        assertEquals(IconOptions.Option.WEBSITE, custom.select(IconOptions.Option.WEBSITE).icons.selected)

        // Equality looks at image contents, so an identical pick is no change but a new image is.
        assertEquals(custom, draft.withCustomIcon(byteArrayOf(1), maskable = false, origin = "Photos"))
        assertEquals(custom.hashCode(), draft.withCustomIcon(byteArrayOf(1), maskable = false, origin = "Photos").hashCode())
        assertFalse(custom == draft.withCustomIcon(byteArrayOf(2), maskable = false, origin = "Photos"))
        assertFalse(custom.equals(form))
    }

    // ----- Form -----

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
