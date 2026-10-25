package com.valghora.uniwrap.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteDiscoveryTest {
    @Test
    fun normalizesWhatPeopleType() {
        assertEquals("https://example.com", SiteDiscovery.normalizeUrl("example.com"))
        assertEquals("https://example.com/app", SiteDiscovery.normalizeUrl("  HTTPS://example.com/app "))
        assertEquals("https://example.com/", SiteDiscovery.normalizeUrl("http://example.com/"))
        assertEquals("https://sub.example.co.uk/a?b=1", SiteDiscovery.normalizeUrl("sub.example.co.uk/a?b=1"))
    }

    @Test
    fun rejectsWhatIsNotAWebAddress() {
        listOf("", "   ", "not a url", "localhost", "ftp://example.com", "javascript:alert(1)", "https://").forEach {
            assertNull(it, SiteDiscovery.normalizeUrl(it))
        }
    }

    @Test
    fun desktopUserAgentKeepsTheChromeVersion() {
        val webView = "Mozilla/5.0 (Linux; Android 17; Pixel 9; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/145.0.7632.218 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.7632.218 Safari/537.36",
            RenderedPass.desktopUserAgent(webView),
        )
        assertEquals(true, RenderedPass.desktopUserAgent("Unknown/1.0").contains("Chrome/140.0.0.0"))
    }
}
