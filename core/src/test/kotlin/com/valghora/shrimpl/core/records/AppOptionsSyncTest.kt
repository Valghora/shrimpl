package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.AdBlocking
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

    @Test
    fun onlyChangesOutsideTheLiveSettingsNeedARebuild() {
        val live = profile.copy(userAgentMode = UaMode.DESKTOP, nativeContextMenu = true, keepAliveWhenHidden = false, pauseOnPowerSave = true)
        assertEquals(false, LiveSettings.needsRebuild(profile, live))
        assertEquals(false, LiveSettings.needsRebuild(profile, profile))
        assertEquals(true, LiveSettings.needsRebuild(profile, profile.copy(name = "Example 2")))
        assertEquals(true, LiveSettings.needsRebuild(profile, live.copy(headerColor = "#000000")))
    }

    @Test
    fun liveSettingsRoundTripThroughAProfile() {
        val settings = LiveSettings(UaMode.MOBILE, nativeContextMenu = true, keepAliveWhenHidden = false, pauseOnPowerSave = true)
        assertEquals(settings, LiveSettings.of(settings.applyTo(profile)))
    }

    @Test
    fun theBlockAdsSwitchKeepsAChosenLevel() {
        assertEquals(AdBlocking.OFF, AppOptionsSync.adBlockingFor(false, AdBlocking.ADS))
        assertEquals(AdBlocking.ADS_AND_TRACKERS, AppOptionsSync.adBlockingFor(true, AdBlocking.OFF))
        assertEquals(AdBlocking.ADS, AppOptionsSync.adBlockingFor(true, AdBlocking.ADS))
        assertEquals(AdBlocking.ADS, AppOptionsSync.apply(profile, null, null, AdBlocking.ADS).adBlocking)
        assertEquals(false, LiveSettings.needsRebuild(profile, profile.copy(adBlocking = AdBlocking.ADS_AND_TRACKERS)))
    }

    @Test
    fun theSiteLayoutReplacesAutoWithThisDevicesLayout() {
        val auto = profile.copy(userAgentMode = UaMode.AUTO)
        assertEquals(UaMode.DESKTOP, com.valghora.shrimpl.core.profile.ProfileForm.from(auto, largeOrPc = true).userAgentMode)
        assertEquals(UaMode.MOBILE, com.valghora.shrimpl.core.profile.ProfileForm.from(auto, largeOrPc = false).userAgentMode)
        val chosen = profile.copy(userAgentMode = UaMode.MOBILE)
        assertEquals(UaMode.MOBILE, com.valghora.shrimpl.core.profile.ProfileForm.from(chosen, largeOrPc = true).userAgentMode)
    }
}
