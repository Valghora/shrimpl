package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.profile.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOptionsSyncTest {
    private val profile = Profile(
        name = "Example",
        appId = "example",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#3367D6",
    )

    @Test
    fun desktopSiteOnIsDesktopAndOffIsMobile() {
        assertEquals(UaMode.DESKTOP, AppOptionsSync.apply(profile, desktopSite = true, linkMenu = null).userAgentMode)
        assertEquals(UaMode.MOBILE, AppOptionsSync.apply(profile, desktopSite = false, linkMenu = null).userAgentMode)
    }

    @Test
    fun theLinkMenuSwitchSetsTheMenu() {
        assertEquals(true, AppOptionsSync.apply(profile, desktopSite = null, linkMenu = true).nativeContextMenu)
        assertEquals(false, AppOptionsSync.apply(profile.copy(nativeContextMenu = true), null, linkMenu = false).nativeContextMenu)
    }

    @Test
    fun aSwitchNotSentLeavesItsSetting() {
        assertEquals(profile, AppOptionsSync.apply(profile, desktopSite = null, linkMenu = null))
    }
}
