package com.valghora.shrimpl.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The network filter against rules as EasyList and EasyPrivacy write them (docs/TEMPLATE_SPEC.md T6.19). */
class FilterEngineTest {
    private val page = "news.example"

    private fun engine(vararg rules: String) = FilterEngine.parse(rules.joinToString("\n"))

    private fun FilterEngine.blocks(url: String, thirdParty: Boolean = true, pageHost: String = page) =
        shouldBlock(url, pageHost, thirdParty)

    @Test
    fun hostRulesBlockTheHostAndItsSubdomainsOnly() {
        val e = engine("||ads.tracker.test^")
        assertTrue(e.blocks("https://ads.tracker.test/pixel.gif"))
        assertTrue(e.blocks("https://cdn.ads.tracker.test/x.js"))
        assertTrue(e.blocks("http://ads.tracker.test:8080/"))
        assertFalse(e.blocks("https://tracker.test/x.js"))
        assertFalse(e.blocks("https://badads.tracker.test/x.js"))
        assertFalse(e.blocks("https://example.com/?u=ads.tracker.test"))
    }

    @Test
    fun patternsFollowAdblockPlusSyntax() {
        val e = engine("/banner/*/img^", "|https://start.test/", "end.js|", "-ad-300x250.", "||cdn.test/ads/")
        assertTrue(e.blocks("https://x.test/banner/big/img?x=1"))
        assertTrue(e.blocks("https://x.test/banner/big/img"))
        assertFalse(e.blocks("https://x.test/banner/big/imgs"))
        assertTrue(e.blocks("https://start.test/anything"))
        assertFalse(e.blocks("https://other.test/?https://start.test/"))
        assertTrue(e.blocks("https://x.test/end.js"))
        assertFalse(e.blocks("https://x.test/end.js?v=2"))
        assertTrue(e.blocks("https://x.test/img/top-ad-300x250.png"))
        assertTrue(e.blocks("https://img.cdn.test/ads/1.png"))
        assertFalse(e.blocks("https://cdn.test/adsense/1.png"))
    }

    @Test
    fun exceptionsLiftBlocksUnlessImportant() {
        val e = engine("||ads.test^", "@@||ads.test/allowed/", "/promo/*\$important", "@@/promo/")
        assertTrue(e.blocks("https://ads.test/x.js"))
        assertFalse(e.blocks("https://ads.test/allowed/x.js"))
        assertTrue(e.blocks("https://site.test/promo/a.js"))
        val hostException = engine("/track.js", "@@||good.test^")
        assertTrue(hostException.blocks("https://bad.test/track.js"))
        assertFalse(hostException.blocks("https://good.test/track.js"))
    }

    @Test
    fun thirdPartyAndDomainOptionsUseTheAppsSite() {
        val e = engine("||widgets.test^\$third-party", "/sponsor/\$domain=news.example|~sports.news.example", "/local/\$~third-party")
        assertTrue(e.blocks("https://widgets.test/w.js", thirdParty = true))
        assertFalse(e.blocks("https://widgets.test/w.js", thirdParty = false))
        assertTrue(e.blocks("https://cdn.test/sponsor/a.png"))
        assertTrue(e.blocks("https://cdn.test/sponsor/a.png", pageHost = "www.news.example"))
        assertFalse(e.blocks("https://cdn.test/sponsor/a.png", pageHost = "sports.news.example"))
        assertFalse(e.blocks("https://cdn.test/sponsor/a.png", pageHost = "other.example"))
        assertTrue(e.blocks("https://news.example/local/a.png", thirdParty = false))
        assertFalse(e.blocks("https://cdn.test/local/a.png", thirdParty = true))
        val wildcard = engine("/promo.js\$domain=filemoon.*")
        assertTrue(wildcard.blocks("https://x.test/promo.js", pageHost = "filemoon.sx"))
        assertTrue(wildcard.blocks("https://x.test/promo.js", pageHost = "www.filemoon.to"))
        assertFalse(wildcard.blocks("https://x.test/promo.js", pageHost = "notfilemoon.to"))
    }

    @Test
    fun rulesThatNeedARequestTypeOrArentAboutRequestsAreLeftOut() {
        val e = engine(
            "/adasync.min.js\$script",
            "||popads.test^\$popup",
            "||site.test^\$document",
            "example.com##.ad-banner",
            "example.com#@#.ad",
            "example.com#\$#abort-on-property-read x",
            "/cgi-bin/ad/*\$~xmlhttprequest",
            "/weird\$unknown-option",
            "! a comment",
            "[Adblock Plus 2.0]",
            "",
        )
        assertFalse(e.blocks("https://x.test/adasync.min.js"))
        assertFalse(e.blocks("https://popads.test/"))
        assertFalse(e.blocks("https://site.test/"))
        assertFalse(e.blocks("https://x.test/weird"))
        assertTrue("a negated type applies to every request", e.blocks("https://x.test/cgi-bin/ad/1"))
        assertEquals("the popup rule is kept, for popups", 2, e.ruleCount)
        assertEquals(1, FilterEngine.countRules("||a.test^\n/b.js\$script\n! c"))
    }

    @Test
    fun popupRulesDecideOnlyAboutPopups() {
        val e = engine("||popads.test^\$popup", "||ads.test^", "/go/offer\$popup,third-party", "@@||popads.test/ok\$popup")
        assertTrue(e.shouldBlockPopup("https://popads.test/x", page, thirdParty = true))
        assertFalse("an exception lets the window open", e.shouldBlockPopup("https://popads.test/ok", page, thirdParty = true))
        assertFalse("a popup isn't a request", e.blocks("https://popads.test/x"))
        assertFalse("a request rule doesn't close windows", e.shouldBlockPopup("https://ads.test/", page, thirdParty = true))
        assertTrue(e.shouldBlockPopup("https://partner.test/go/offer?id=1", page, thirdParty = true))
        assertFalse(e.shouldBlockPopup("https://news.example/go/offer", page, thirdParty = false))
        assertFalse(e.shouldBlockPopup("about:blank", page, thirdParty = false))
    }

    @Test
    fun matchCaseAndRegularExpressions() {
        val e = engine("/AdBanner/\$match-case", "/\\/ads?\\/[0-9]+\\//", "/bad[/")
        assertTrue(e.blocks("https://x.test/AdBanner/1.png"))
        assertFalse(e.blocks("https://x.test/adbanner/1.png"))
        assertTrue(e.blocks("https://x.test/ad/42/a.png"))
        assertTrue(e.blocks("https://x.test/ADS/7/a.png"))
        assertFalse(e.blocks("https://x.test/ads/x/a.png"))
        assertEquals("an invalid regular expression is left out", 2, e.ruleCount)
    }

    @Test
    fun urlsWithoutAHostAreNeverBlocked() {
        val e = engine("*", "|data:", "/x/")
        assertFalse(e.blocks("data:text/plain,x/x/"))
        assertNull(FilterEngine.hostOf("blob:abc"))
        assertEquals("ads.test", FilterEngine.hostOf("https://user@ads.test:443/a"))
        assertEquals("[::1]", FilterEngine.hostOf("http://[::1]:80/"))
    }

    @Test
    fun rulesAreFiledUnderAWholeWord() {
        assertEquals("banner", RuleIndex.tokenOf("/banner/*"))
        assertNull("/…/ is a regular expression", RuleIndex.tokenOf("/banner/"))
        assertEquals("tracker", RuleIndex.tokenOf("||tracker.test/x"))
        assertNull("an open start can continue the word", RuleIndex.tokenOf("adserver"))
        assertNull("a wildcard can continue the word", RuleIndex.tokenOf("/ab*cdef*/"))
        assertEquals("pixel", RuleIndex.tokenOf("|pixel|"))
        assertNull(RuleIndex.tokenOf("/\\/ads\\//"))
        assertEquals(setOf("https", "ads", "test", "img", "png"), FilterEngine.tokensOf("https://ads.test/img/1.png"))
    }

    @Test
    fun aRealListExcerptBehaves() {
        val easyList = """
            [Adblock Plus 2.0]
            ! Title: EasyList
            ||doubleclick.net^
            ||googlesyndication.com^
            ||adhunt.net^${'$'}third-party
            /wpadgu-adblock.js${'$'}script
            .cfm?ad=
            _728-90_
            ||api.redgifs.com/v2/gifs/promoted
            @@||ads.roblox.com/v1/sponsored-pages${'$'}xmlhttprequest,domain=roblox.com
            ||daily-sun.com/assets/images/banner/
            news.example###top-ad
        """.trimIndent()
        val e = FilterEngine.parse(easyList)
        assertTrue(e.blocks("https://securepubads.g.doubleclick.net/gampad/ads?x=1"))
        assertTrue(e.blocks("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(e.blocks("https://adhunt.net/a.js"))
        assertTrue(e.blocks("https://x.test/show.cfm?ad=12"))
        assertTrue(e.blocks("https://x.test/img/top_728-90_a.gif"))
        assertTrue(e.blocks("https://api.redgifs.com/v2/gifs/promoted?count=3"))
        assertTrue(e.blocks("https://daily-sun.com/assets/images/banner/b.jpg"))
        assertFalse(e.blocks("https://news.example/article/1"))
        assertFalse(e.blocks("https://cdn.test/app.js"))
        assertEquals(7, e.ruleCount)
    }
}

class FilterListsTest {
    @Test
    fun levelsUseTheirLists() {
        assertEquals(emptyList<FilterList>(), FilterList.forLevel(com.valghora.shrimpl.core.AdBlocking.OFF))
        assertEquals(listOf(FilterList.EASYLIST), FilterList.forLevel(com.valghora.shrimpl.core.AdBlocking.ADS))
        assertEquals(listOf(FilterList.EASYLIST, FilterList.EASYPRIVACY), FilterList.forLevel(com.valghora.shrimpl.core.AdBlocking.ADS_AND_TRACKERS))
        assertEquals(FilterList.EASYPRIVACY, FilterList.byId("easyprivacy"))
        assertNull(FilterList.byId("other"))
    }

    @Test
    fun aDownloadReplacesTheLastGoodCopyOnlyWhenItLooksWhole() {
        assertTrue(FilterList.accept(50_000, null))
        assertTrue(FilterList.accept(26_000, 50_000))
        assertFalse(FilterList.accept(24_000, 50_000))
        assertFalse(FilterList.accept(500, null))
    }
}

/** Element hiding (docs/TEMPLATE_SPEC.md T6.20) against rules as EasyList writes them. */
class CosmeticFilterTest {
    private val list = """
        ! Title: test
        ##.ad-banner
        ###top-ad
        ##div[id^="google_ads"]
        news.example##.sponsored
        m.news.example##.mobile-ad
        other.example##.elsewhere
        news.example#@#.ad-banner
        ~shop.example##.promo-box
        #@#.allowed-everywhere
        ##.allowed-everywhere
        news.example,~sports.news.example##.mixed
        example.*##.entity
        ##.ad:has-text(Sponsored)
        news.example#?#.slot:-abp-has(.ad)
        ##.broken[
        ##.x { color: red }
        @@||quiet.example^${'$'}generichide
        @@||bare.example^${'$'}elemhide
        @@||x.test^${'$'}script
    """.trimIndent()

    private val filter = CosmeticFilter.parse(list)

    @Test
    fun genericRulesAreKeptOnceAndGenericExceptionsRemoveThem() {
        assertEquals(listOf(".ad-banner", "#top-ad", "div[id^=\"google_ads\"]", ".promo-box"), filter.generic)
    }

    @Test
    fun siteRulesCoverTheAppsDomainsTheirSubdomainsAndParents() {
        val site = filter.site(listOf("news.example"))
        assertEquals(setOf("news.example", "m.news.example"), site.keys)
        assertEquals(listOf(".sponsored"), site.getValue("news.example").hide)
        assertEquals(listOf(".ad-banner"), site.getValue("news.example").unhide)
        assertEquals(listOf(".mobile-ad"), site.getValue("m.news.example").hide)
        val sub = filter.site(listOf("m.news.example"))
        assertEquals("a parent domain's rules apply too", setOf("news.example", "m.news.example"), sub.keys)
    }

    @Test
    fun excludedDomainsShowGenericRulesAgain() {
        assertEquals(listOf(".promo-box"), filter.site(listOf("shop.example")).getValue("shop.example").unhide)
    }

    @Test
    fun pageExceptionsTurnHidingOff() {
        val quiet = filter.site(listOf("quiet.example")).getValue("quiet.example")
        assertTrue(quiet.genericHide)
        assertFalse(quiet.elemHide)
        assertTrue(filter.site(listOf("bare.example")).getValue("bare.example").elemHide)
    }

    @Test
    fun whatIsntPlainCssIsLeftOut() {
        val all = filter.generic + filter.site(listOf("news.example", "example.com")).values.flatMap { it.hide }
        assertFalse(all.any { "has-text" in it || "-abp-" in it || "broken" in it || "color" in it || "mixed" in it || "entity" in it })
        // 4 generic + 3 site + 1 site exception + 1 excluded generic + 1 generic exception + 2 page exceptions.
        assertEquals(12, filter.ruleCount)
    }
}
