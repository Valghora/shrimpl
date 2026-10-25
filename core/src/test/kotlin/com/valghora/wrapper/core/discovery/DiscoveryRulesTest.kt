package com.valghora.wrapper.core.discovery

import com.valghora.wrapper.core.icons.IconException
import com.valghora.wrapper.core.profile.Profile
import com.valghora.wrapper.core.profile.ProfileValidator
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pieces behind [Discovery]; the parity test covers the rules end to end. */
class DiscoveryRulesTest {
    private val psl = PublicSuffixList(javaClass.getResource("/discovery/public_suffix_list.dat")!!.readText())

    private class Map(private val responses: kotlin.collections.Map<String, ByteArray>) : IconSources {
        val fetched = mutableListOf<String>()
        override fun fetch(url: String): ByteArray? {
            fetched += url
            return responses[url]
        }
    }

    private val glyphSvg = """<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M0 0h24v24H0z"/></svg>""".toByteArray()

    // ---------- URLs and the Public Suffix List ----------

    @Test
    fun urlParts() {
        val p = Urls.split("https://User@Sub.Example.COM:8443/a/b?q=1#f")
        assertEquals("https", p.scheme)
        assertEquals("sub.example.com", p.hostname)
        assertEquals("/a/b", p.path)
        assertEquals("q=1", p.query)
        assertNull(Urls.hostOf("data:,"))
        assertEquals("::1", Urls.hostOf("http://[::1]:80/"))
    }

    @Test
    fun urlJoin() {
        assertEquals("https://a.com/x", Urls.join("https://a.com", "x"))
        assertEquals("https://a.com/m/icon.png", Urls.join("https://a.com/m/manifest.json", "icon.png"))
        assertEquals("https://b.com/", Urls.join("https://a.com/", "https://b.com/"))
        assertEquals("/rel", Urls.join("", "/rel"))
        assertEquals("not a url", Urls.join("https://a.com/", "not a url"))
    }

    @Test
    fun registrableDomains() {
        assertEquals("discordapp.com", psl.registrable("cdn.discordapp.com"))
        assertEquals("example.co.uk", psl.registrable("www.example.co.uk"))
        assertNull(psl.registrable("co.uk"))
        assertEquals("example.com", psl.registrable("Example.COM."))
        val tiny = PublicSuffixList("// comment\ncom\n*.ck\n!www.ck\n")
        assertEquals("a.b.ck", tiny.registrable("x.a.b.ck"))
        assertEquals("www.ck", tiny.registrable("www.ck"))
        assertEquals("b.example", tiny.registrable("a.b.example"))
    }

    // ---------- Colors, slugs and titles ----------

    @Test
    fun colors() {
        assertEquals("#FFFFFF", Colors.parse(" White "))
        assertEquals("#AABBCC", Colors.parse("#abc"))
        assertEquals("#AABBCC", Colors.parse("#abcd"))
        assertEquals("#112233", Colors.parse("#112233ff"))
        assertNull(Colors.parse("#11223300"))
        assertEquals("#0A141E", Colors.parse("rgb(10, 20, 30)"))
        assertEquals("#FF0000", Colors.parse("rgba(300 0 0 / 80%)"))
        assertNull(Colors.parse("rgba(0, 0, 0, 0)"))
        assertNull(Colors.parse("oklab(0.5 0 0)"))
        assertNull(Colors.parse(""))
        assertNull(Colors.parse("#12345"))
    }

    @Test
    fun titleToSlug() {
        assertEquals("discord", SimpleIcons.titleToSlug("Discord"))
        assertEquals("dotnet", SimpleIcons.titleToSlug(".NET"))
        assertEquals("cplusplus", SimpleIcons.titleToSlug("C++"))
        assertEquals("pokemon", SimpleIcons.titleToSlug("Pokémon"))
        assertEquals("larss", SimpleIcons.titleToSlug("Larß!"))
    }

    @Test
    fun cleanTitle() {
        assertEquals("GitHub", Discovery.cleanTitle("Pull requests · GitHub", "github"))
        assertEquals("Last", Discovery.cleanTitle("First - Last", "none"))
        assertEquals("", Discovery.cleanTitle("", "x"))
    }

    private val acmeSvg = """<svg fill="#12ab9F" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Acme &amp; Co</title><path d="M0 0h24v24H0z"/></svg>""".toByteArray()

    @Test
    fun simpleIconsByName() {
        val icon = SimpleIcons.parse("acmeandco", acmeSvg)!!
        assertEquals("Acme & Co", icon.title)
        assertEquals("#12AB9F", icon.color)
        assertNull(SimpleIcons.parse("x", glyphSvg))
        assertEquals("acme", SimpleIcons.parse("acme", "<svg fill=\"#000000\"><path d=\"M0 0h1z\"/></svg>".toByteArray())!!.title)

        val sources = Map(mapOf(SimpleIcons.url("acmeandco") to acmeSvg))
        val (found, name) = SimpleIcons.find(listOf(null, "", "Nope", "nope", "Acme & Co", "later"), sources)!!
        assertEquals("acmeandco", found.slug)
        assertEquals("Acme & Co", name)
        // "Nope" and "nope" share a slug, so it is asked for once.
        assertEquals(listOf("nope", "acmeandco").map(SimpleIcons::url), sources.fetched)

        val many = Map(emptyMap())
        assertNull(SimpleIcons.find(listOf("a", "b", "c", "d", "e"), many))
        assertEquals(SimpleIcons.MAX_LOOKUPS, many.fetched.size)
    }

    // ---------- Raster headers ----------

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(kind: String, data: ByteArray) {
            val bytes = kind.toByteArray() + data
            out.write(java.nio.ByteBuffer.allocate(4).putInt(data.size).array())
            out.write(bytes)
            out.write(java.nio.ByteBuffer.allocate(4).putInt(CRC32().apply { update(bytes) }.value.toInt()).array())
        }
        chunk("IHDR", java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(6).put(0).put(0).put(0).array())
        chunk("IDAT", ByteArrayOutputStream().also { DeflaterOutputStream(it).use { d -> d.write(ByteArray(height * (1 + 4 * width))) } }.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    @Test
    fun rasterInfo() {
        assertEquals(RasterInfo("png", 256, 128), RasterInfo.of(png(256, 128)))
        val bits = (300 - 1) or ((200 - 1) shl 14)
        val vp8l = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8L".toByteArray() + ByteArray(4) + byteArrayOf(0x2f) +
            byteArrayOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
        assertEquals(RasterInfo("webp", 300, 200), RasterInfo.of(vp8l))
        val vp8x = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8X".toByteArray() + ByteArray(8) + byteArrayOf(0xFF.toByte(), 1, 0, 0x3F, 0, 0)
        assertEquals(RasterInfo("webp", 512, 64), RasterInfo.of(vp8x))
        assertThrows(IconException::class.java) { RasterInfo.of("GIF89a....".toByteArray()) }
        assertThrows(IconException::class.java) { RasterInfo.of("RIFF....WEBPVP8?".toByteArray()) }
        assertThrows(IconException::class.java) { RasterInfo.of("RIFF....WEBPVP8X".toByteArray()) }
    }

    // ---------- Icon choice ----------

    private fun manifest(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun rasterCandidatesRankMaskableThenSize() {
        val sources = Map(mapOf("https://a.com/any512.png" to png(512, 512), "https://a.com/mask.png" to png(256, 256)))
        val choice = IconChooser.choose(
            null,
            Confidence.LOW,
            listOf(Link("apple-touch-icon", "https://a.com/touch.png", sizes = "180x180"), Link("icon", "https://a.com/tiny.png", sizes = "32x32 16x16")),
            manifest("""{"icons":[{"src":"any512.png","sizes":"512x512"},{"src":"mask.png","sizes":"256x256","purpose":"any maskable"},{"src":"odd.png","sizes":"300x200"}]}"""),
            "https://a.com/manifest.json",
            "https://a.com/",
            sources,
        ) as IconChoice.Raster
        assertTrue(choice.maskable)
        assertEquals("web manifest icon (maskable 256px png)", choice.source)
        assertEquals(listOf("https://a.com/mask.png"), sources.fetched)
    }

    @Test
    fun smallOrNonSquareRastersAreSkipped() {
        val sources = Map(mapOf("https://a.com/wide.png" to png(400, 200), "https://a.com/apple-touch-icon.png" to png(128, 128)))
        val choice = IconChooser.choose(
            null, Confidence.LOW, listOf(Link("icon", "https://a.com/wide.png", sizes = "400x400")), manifest("{}"), "", "https://a.com/page", sources,
        )
        assertNull(choice)
        assertEquals(listOf("https://a.com/wide.png", "https://a.com/apple-touch-icon.png"), sources.fetched)
    }

    @Test
    fun maskIconGlyphWithColor() {
        val sources = Map(mapOf("https://a.com/mask.svg" to glyphSvg))
        val choice = IconChooser.choose(
            null, Confidence.LOW, listOf(Link("mask-icon", "https://a.com/mask.svg", color = "#ff0000")), manifest("{}"), "", "https://a.com/", sources,
        ) as IconChoice.Glyph
        assertEquals("#FF0000", choice.color)
        assertEquals("Safari pinned-tab icon (mask-icon)", choice.source)
    }

    @Test
    fun unusableSimpleIconFallsThrough() {
        val broken = SimpleIcon("acme", "Acme", "123456", "<svg/>".toByteArray())
        assertNull(IconChooser.choose(broken, Confidence.HIGH, emptyList(), manifest("{}"), "", "https://a.com/", Map(emptyMap())))
        val good = SimpleIcon("acme", "Acme", "123456", glyphSvg)
        val choice = IconChooser.choose(good, Confidence.MEDIUM, emptyList(), manifest("{}"), "", "https://a.com/", Map(emptyMap())) as IconChoice.Glyph
        assertEquals("simple-icons 'acme'", choice.source)
        assertEquals("#123456", choice.color)
        assertEquals(Confidence.MEDIUM, choice.confidence)
    }

    @Test
    fun dataUrls() {
        val none = Map(emptyMap())
        assertEquals("hi", String(IconChooser.load("data:text/plain;base64,aGk=", none)!!))
        assertEquals("a b+", String(IconChooser.load("data:,a%20b+", none)!!))
        assertEquals(0, IconChooser.load("data:,", none)!!.size)
        assertNull(IconChooser.load("data:;base64,!!!", none)?.takeIf { it.isNotEmpty() })
        assertTrue(none.fetched.isEmpty())
    }

    // ---------- Whole drafts ----------

    private fun visit(page: PageInfo?, manifest: String? = null, extra: Visit.() -> Visit = { this }) = Visit(
        requestedUrl = "https://app.example.com/start",
        finalUrl = "https://app.example.com/login",
        page = page,
        manifestUrl = manifest?.let { "https://app.example.com/manifest.json" },
        manifest = manifest?.let(::manifest),
    ).extra()

    private fun draft(evidence: Evidence, url: String = "https://app.example.com/start") =
        Discovery.draft(evidence, url, psl, Map(emptyMap()))

    @Test
    fun startUrlRules() {
        val page = PageInfo(title = "App")
        assertEquals("https://app.example.com/start", draft(Evidence(visit(page))).startUrl.value)
        val www = Evidence(visit(page) { copy(finalUrl = "https://www.example.com/home") })
        assertEquals(Field("https://www.example.com/start?x=1", "requested path on the redirected host", Confidence.HIGH),
            draft(www, "https://example.com/start?x=1").startUrl)
        val elsewhere = Evidence(visit(page) { copy(finalUrl = "https://login.other.org/") })
        assertEquals(Confidence.MEDIUM, draft(elsewhere).startUrl.confidence)
        val manifestStart = Evidence(visit(page, """{"start_url":"/app?pwa=1","short_name":"Ex"}"""))
        assertEquals(Field("https://app.example.com/app?pwa=1", "web manifest start_url", Confidence.HIGH), draft(manifestStart).startUrl)
        val http = Evidence(visit(page) { copy(finalUrl = "http://app.example.com/") })
        assertEquals("https://app.example.com/", draft(http, "http://app.example.com/").startUrl.value)
    }

    @Test
    fun domainsAndApiNote() {
        val evidence = Evidence(visit(PageInfo(title = "Example")) {
            copy(requests = listOf(
                Request("https://cdn.examplestatic.com/a.js", "script"),
                Request("https://api.analytics.io/collect", "xhr"),
                Request("https://fonts.gstatic.com/f.woff", "font"),
                Request("data:image/png;base64,xx", "image"),
            ))
        })
        val draft = draft(evidence)
        assertEquals(listOf("example.com", "examplestatic.com"), draft.internalDomains.value)
        // Other domains the page called are not reported.
        assertTrue(draft.notes.none { "analytics.io" in it.text })
    }

    @Test
    fun namesColorsAndCapabilities() {
        val page = PageInfo(
            title = "Inbox – Example Mail",
            metas = listOf(Meta("og:site_name", "Example Mail")),
            themeColors = listOf(ThemeColor("#ffffff"), ThemeColor("#000000", "(prefers-color-scheme: dark)")),
        )
        val evidence = Evidence(visit(page) {
            copy(signals = mapOf("webRtc" to true, "pushManager" to true), headers = mapOf("permissions-policy" to "camera=(self), microphone=()"))
        })
        val draft = draft(evidence)
        assertEquals(Field("Example Mail", "og:site_name meta", Confidence.MEDIUM), draft.name)
        // White and black theme colors are no brand color.
        assertEquals(Field(Discovery.DEFAULT_BRAND, "default (no brand color found)", Confidence.LOW), draft.iconBackground)
        assertTrue(draft.allowMicrophone.value)
        assertTrue(draft.allowCamera.value)
        assertTrue(draft.allowNotifications.value)
        assertEquals(IconChoice.Letter('E', "no usable brand icon found", Confidence.LOW), draft.icon)
        assertEquals("examplemail", draft.appId)
        assertEquals(emptyList<Any>(), ProfileValidator.validate(draft.toProfile()))
        assertEquals(Profile.SYSTEM, draft.toProfile().headerColor)
        assertEquals("#FFFFFF", draft.toProfile().iconForeground)
    }

    @Test
    fun simpleIconBrandNamesTheAppAndColorsTheIcon() {
        val evidence = Evidence(visit(PageInfo(title = "Home", metas = listOf(Meta("og:site_name", "Acme & Co")))))
        val sources = Map(mapOf(SimpleIcons.url("acmeandco") to acmeSvg))
        val draft = Discovery.draft(evidence, "https://app.example.com/start", psl, sources)
        assertEquals(Field("Acme & Co", "simple-icons brand title", Confidence.HIGH), draft.name)
        assertEquals(Field("#12AB9F", "simple-icons brand color (Acme & Co)", Confidence.HIGH), draft.iconBackground)
        assertEquals(Confidence.HIGH, draft.icon.confidence)
        assertTrue(draft.notes.none { it.text == Discovery.NO_GLYPH })

        // Matched only through the domain word: likely, not certain.
        val byDomain = Map(mapOf(SimpleIcons.url("example") to acmeSvg))
        assertEquals(Confidence.MEDIUM, Discovery.draft(Evidence(visit(PageInfo(title = "Home"))), "https://app.example.com/start", psl, byDomain).icon.confidence)
    }

    @Test
    fun brandColorPrefersColorfulSiteColors() {
        val letter = IconChoice.Letter('A', "x", Confidence.LOW)
        val empty = manifest("{}")
        assertEquals(
            Field("#FF3385", "theme-color meta", Confidence.MEDIUM),
            Discovery.brandColor(letter, PageInfo(themeColors = listOf(ThemeColor("#ffffff", "(prefers-color-scheme: dark)"), ThemeColor("#ff3385"))), empty),
        )
        assertEquals(
            Field("#336699", "web manifest theme_color", Confidence.MEDIUM),
            Discovery.brandColor(letter, PageInfo(), manifest("""{"theme_color":"#336699","background_color":"#ff0000"}""")),
        )
        assertEquals(
            Field("#00AA55", "mask-icon color", Confidence.MEDIUM),
            Discovery.brandColor(letter, PageInfo(links = listOf(Link("mask-icon", "https://a.com/m.svg", color = "#00aa55"))), empty),
        )
        val buttons = PageInfo(brandColors = listOf(BrandColor("#EEEEEE", "button background"), BrandColor("#E91E63", "button background")))
        assertEquals(Field("#E91E63", "button background", Confidence.LOW), Discovery.brandColor(letter, buttons, empty))
        // Gray site colors are used only when nothing is colorful.
        assertEquals(Field("#222222", "web manifest theme_color", Confidence.LOW), Discovery.brandColor(letter, PageInfo(), manifest("""{"theme_color":"#222222"}""")))
        assertEquals(Field("#AA0000", "web manifest background_color", Confidence.LOW), Discovery.brandColor(letter, PageInfo(), manifest("""{"background_color":"#aa0000"}""")))
        // A mask-icon glyph's color is a candidate like any other, not taken as is.
        val mask = IconChoice.Glyph(glyphSvg, com.valghora.wrapper.core.icons.SvgGlyph.parse(glyphSvg), "#000000", "mask-icon color", "m", Confidence.MEDIUM)
        assertEquals(Discovery.DEFAULT_BRAND, Discovery.brandColor(mask, PageInfo(), empty).value)
    }

    @Test
    fun cssBrandVariablesAreRanked() {
        // A slice of discord.com/app's variables: a shade scale, persona colors and grays.
        val discord = listOf(
            "--brand-760" to "#172354", "--logo-primary" to "#FFFFFF", "--primary-300" to "#C9CACE", "--brand-560" to "#4654C0",
            "--checkpoint-persona-eight-primary" to "#D9B777", "--brand-130" to "#E6EAFF", "--brand-345" to "#94A8FF",
            "--checkpoint-guilds-primary" to "#5EFDE2", "--checkpoint-persona-five-primary" to "#F75990", "--brand-430" to "#6C7FF6",
            "--checkpoint-persona-one-primary" to "#5EFDE2", "--brand-600" to "#3A48A3", "--brand-90a" to "#5865F2",
            "--brand-500" to "#5865F2", "--checkpoint-persona-four-primary" to "#F262D5", "--primary-800" to "#121214",
        ).map { (name, color) -> BrandColor(color, "CSS variable $name") } + BrandColor("#123456", "button background")
        assertEquals("#5865F2", Discovery.cssBrandColor(discord)?.color)
        assertEquals("CSS variable --color-primary", Discovery.cssBrandColor(listOf(
            BrandColor("#E91E63", "CSS variable --brand-accent-900"), BrandColor("#3F51B5", "CSS variable --color-primary"),
        ))?.source)
        assertNull(Discovery.cssBrandColor(listOf(BrandColor("#FFFFFF", "CSS variable --primary"), BrandColor("bogus", "CSS variable --brand"))))
        assertTrue(Colors.isColorful("#5865F2"))
        assertTrue(!Colors.isColorful("#777777") && !Colors.isColorful("#FAFAFF") && !Colors.isColorful("#050510"))
    }

    @Test
    fun webviewWarnings() {
        val evidence = Evidence(
            visit(PageInfo(title = "X")),
            webview = SlimVisit(status = 403, text = "This browser is not supported", navigations = listOf("intent://details?id=x#Intent;end")),
        )
        val notes = draft(evidence).notes
        assertTrue(notes.toString(), notes.any { it.level == Note.Level.CAUTION && it.text.startsWith("The site may refuse Android WebViews (status 403") })
        assertTrue(notes.any { it.level == Note.Level.INFO && it.text.startsWith("With a mobile user agent the site redirects to the Play Store") })
        assertTrue(notes.any { it.level == Note.Level.INFO && it.text.startsWith("No web manifest found") })
        assertTrue(Note(Note.Level.CAUTION, Discovery.NO_GLYPH) in notes)
    }

    @Test
    fun unloadablePageFails() {
        val evidence = Evidence(Visit(requestedUrl = "https://x.com/", error = "net::ERR_NAME_NOT_RESOLVED"))
        assertThrows(DiscoveryException::class.java) { draft(evidence, "https://x.com/") }
    }

    @Test
    fun evidenceRoundTrips() {
        val evidence = Evidence(visit(PageInfo(title = "T"), """{"name":"N"}"""))
        assertEquals(evidence, Evidence.decode(Evidence.encode(evidence)))
    }

    // ---------- Static HTML pass ----------

    @Test
    fun parsesHtmlHead() {
        val html = """
            <!doctype html><html><head>
            <!-- <title>commented out</title> -->
            <title> Pull requests &middot; Example &amp; Co </title>
            <base href="/assets/">
            <meta name="theme-color" content="#123456" media="(prefers-color-scheme: light)">
            <meta property="og:site_name" content='Example'>
            <META NAME="Application-Name" CONTENT="Ex">
            <link rel="Icon" href="favicon.png" sizes="32x32">
            <link rel="manifest" href="https://cdn.example.com/m.json">
            <link rel="mask-icon" href="mask.svg" color="black">
            <script>var t = "<meta name='bad' content='x'>";</script>
            </head><body><meta name="late" content="ignored"></body></html>
        """.trimIndent()
        val page = HtmlHead.parse(html, "https://example.com/pulls")
        assertEquals("Pull requests &middot; Example & Co", page.title)
        assertEquals(listOf("theme-color", "og:site_name", "application-name"), page.metas.map { it.name })
        assertEquals(listOf(ThemeColor("#123456", "(prefers-color-scheme: light)")), page.themeColors)
        assertEquals(Link("icon", "https://example.com/assets/favicon.png", sizes = "32x32"), page.links[0])
        assertEquals("https://cdn.example.com/m.json", HtmlHead.manifestUrl(page))
        assertEquals(Link("mask-icon", "https://example.com/assets/mask.svg", color = "black"), page.links[2])
        assertEquals("é<'", HtmlHead.decode("&#233;&lt;&apos;"))
        assertEquals("&#xZZ;", HtmlHead.decode("&#xZZ;"))
        assertNull(HtmlHead.manifestUrl(PageInfo()))
    }

    @Test
    fun scriptSignals() {
        assertEquals(setOf("getUserMedia", "webRtc"), ScriptSignals.scan("navigator.mediaDevices.getUserMedia ({}); new RTCPeerConnection()"))
        assertEquals(setOf("notificationPermission", "pushManager"), ScriptSignals.scan("Notification.permission; reg.pushManager.subscribe()"))
        assertEquals(emptySet<String>(), ScriptSignals.scan("console.log(1)"))
    }
}
