package com.valghora.shrimpl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSessionControllerTest {

    private class FakeZoomStore(var stored: Int? = null) : ZoomStore {
        val saves = mutableListOf<Int>()
        override fun load(): Int? = stored
        override fun save(value: Int) {
            stored = value
            saves += value
        }

        override fun clear() {
            stored = null
        }
    }

    private class FakeUaStore(
        override var userChoice: UaMode? = null,
        override var learnedDesktop: Boolean = false,
    ) : UaStore

    private val ctrl = setOf(Modifier.CTRL)
    private val newWindow = ShortcutSpec("New Window", "KEYCODE_N", ctrl)

    /** Fixed test data so these tests do not depend on which site is being built. */
    private val fixture = SessionConfig(
        internalDomains = listOf("discord.com", "discordapp.com", "discord.gg", "discord.net", "discordapp.net"),
        oauthDomains = listOf("accounts.google.com", "github.com"),
        pauseOnPowerSave = false,
        enableMultiWindow = true,
        appShortcuts = listOf(
            ShortcutSpec("Quick Switcher", "KEYCODE_K", ctrl),
            ShortcutSpec("Toggle Mute", "KEYCODE_M", setOf(Modifier.CTRL, Modifier.SHIFT)),
        ),
        newWindowShortcut = null,
    )

    private fun controller(
        zoom: FakeZoomStore = FakeZoomStore(),
        config: SessionConfig = fixture,
    ) = DesktopSessionController(zoom, FakeUaStore(), config)

    // ---------- a. URL classification ----------

    @Test
    fun classifiesInternalDomainsAndSubdomains() {
        val c = controller()
        assertEquals(UrlClass.INTERNAL, c.classify("https://discord.com/app"))
        assertEquals(UrlClass.INTERNAL, c.classify("https://cdn.discordapp.com/x.png"))
        assertEquals(UrlClass.INTERNAL, c.classify("https://a.b.discord.net/"))
        assertEquals(UrlClass.INTERNAL, c.classify("HTTPS://DISCORD.GG/invite"))
        assertEquals(UrlClass.INTERNAL, c.classify("http://discord.com."))
        assertEquals(UrlClass.INTERNAL, c.classify("  https://discord.com/  "))
    }

    @Test
    fun rejectsLookAlikeHosts() {
        val c = controller()
        assertEquals(UrlClass.EXTERNAL, c.classify("https://notdiscord.com/"))
        assertEquals(UrlClass.EXTERNAL, c.classify("https://discord.com.evil.example/"))
        assertEquals(UrlClass.EXTERNAL, c.classify("https://discordxapp.com/"))
    }

    @Test
    fun ignoresUserInfoWhenMatchingHosts() {
        val c = controller()
        assertEquals(UrlClass.EXTERNAL, c.classify("https://discord.com@evil.example/"))
        assertEquals(UrlClass.INTERNAL, c.classify("https://evil.example@discord.com/"))
    }

    @Test
    fun classifiesOauthExternalAndSchemes() {
        val c = controller()
        assertEquals(UrlClass.OAUTH, c.classify("https://accounts.google.com/o/oauth2"))
        assertEquals(UrlClass.OAUTH, c.classify("https://github.com/login/oauth/authorize"))
        assertEquals(UrlClass.EXTERNAL, c.classify("https://example.com/"))
        assertEquals(UrlClass.PASSTHROUGH, c.classify("about:blank"))
        assertEquals(UrlClass.PASSTHROUGH, c.classify("blob:https://discord.com/1234"))
        assertEquals(UrlClass.PASSTHROUGH, c.classify("data:text/plain,hi"))
        assertEquals(UrlClass.PASSTHROUGH, c.classify("javascript:void(0)"))
        assertEquals(UrlClass.SPECIAL_SCHEME, c.classify("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(UrlClass.SPECIAL_SCHEME, c.classify("mailto:a@b.c"))
        assertEquals(UrlClass.SPECIAL_SCHEME, c.classify("tel:123"))
        assertEquals(UrlClass.SPECIAL_SCHEME, c.classify("sms:123"))
        assertEquals(UrlClass.SPECIAL_SCHEME, c.classify("market://details?id=x"))
        assertEquals(UrlClass.SPECIAL_SCHEME, c.classify("ftp://example.com/file"))
    }

    @Test
    fun blocksMalformedUrls() {
        val c = controller()
        assertEquals(UrlClass.BLOCKED, c.classify(""))
        assertEquals(UrlClass.BLOCKED, c.classify("not a url"))
        assertEquals(UrlClass.BLOCKED, c.classify("://missing-scheme"))
        assertEquals(UrlClass.BLOCKED, c.classify("https://"))
        assertEquals(UrlClass.BLOCKED, c.classify("https://exa mple.com/"))
        assertEquals(UrlClass.BLOCKED, c.classify("https:///path-only"))
    }

    // ---------- b. Text zoom ----------

    @Test
    fun zoomDefaultsAndSteps() {
        val store = FakeZoomStore()
        val c = controller(store)
        assertEquals(100, c.textZoom)
        assertEquals(110, c.zoomIn())
        assertEquals(100, c.zoomOut())
        assertEquals(90, c.zoomOut())
        assertEquals(100, c.resetZoom())
        assertEquals(listOf(110, 100, 90), store.saves)
        assertEquals("reset forgets the zoom", null, store.stored)
    }

    @Test
    fun textFollowsTheSystemFontSizeUntilTheUserZooms() {
        val store = FakeZoomStore()
        val c = DesktopSessionController(store, FakeUaStore(), fixture, fontScalePercent = 130)
        assertEquals("starts at the font size", 130, c.textZoom)
        assertEquals("follows a change", 150, c.fontScaleChanged(150))
        assertEquals("115 % snaps to the nearest step", 120, c.fontScaleChanged(115))
        assertEquals(130, c.zoomIn())
        assertEquals("a zoom the user chose wins", 130, c.fontScaleChanged(200))
        assertEquals("reset goes back to the font size", 200, c.resetZoom())
        assertEquals("and follows it again", 100, c.fontScaleChanged(100))
    }

    @Test
    fun aSavedZoomWinsOverTheFontSize() {
        assertEquals(90, DesktopSessionController(FakeZoomStore(90), FakeUaStore(), fixture, fontScalePercent = 150).textZoom)
    }

    @Test
    fun zoomClampsAtBounds() {
        val high = controller(FakeZoomStore(300))
        assertEquals(300, high.zoomIn())
        val low = controller(FakeZoomStore(50))
        assertEquals(50, low.zoomOut())
    }

    @Test
    fun invalidStoredZoomSnapsToNearestStep() {
        assertEquals(120, controller(FakeZoomStore(123)).textZoom)
        assertEquals(130, controller(FakeZoomStore(125)).textZoom)
        assertEquals(300, controller(FakeZoomStore(1000)).textZoom)
        assertEquals(50, controller(FakeZoomStore(-5)).textZoom)
        assertEquals(100, DesktopSessionController.snapZoom(null))
    }

    // ---------- c. Key resolution ----------

    @Test
    fun resolvesBuiltInKeys() {
        val c = controller()
        assertEquals(KeyAction.RELOAD, c.resolveKey("KEYCODE_R", ctrl, 0))
        assertEquals(KeyAction.RELOAD, c.resolveKey("KEYCODE_F5", emptySet(), 0))
        assertEquals(KeyAction.ZOOM_IN, c.resolveKey("KEYCODE_EQUALS", ctrl, 0))
        assertEquals(KeyAction.ZOOM_IN, c.resolveKey("KEYCODE_PLUS", ctrl, 0))
        assertEquals(KeyAction.ZOOM_IN, c.resolveKey("KEYCODE_NUMPAD_ADD", ctrl, 0))
        assertEquals(KeyAction.ZOOM_OUT, c.resolveKey("KEYCODE_MINUS", ctrl, 0))
        assertEquals(KeyAction.ZOOM_OUT, c.resolveKey("KEYCODE_NUMPAD_SUBTRACT", ctrl, 0))
        assertEquals(KeyAction.ZOOM_RESET, c.resolveKey("KEYCODE_0", ctrl, 0))
        assertEquals(KeyAction.PRINT, c.resolveKey("KEYCODE_P", ctrl, 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_P", ctrl, 1)) // once per press
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_P", emptySet(), 0))
        assertEquals(KeyAction.ZOOM_RESET, c.resolveKey("KEYCODE_NUMPAD_0", ctrl, 0))
    }

    @Test
    fun otherKeysPassThrough() {
        val c = controller()
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_R", emptySet(), 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_R", setOf(Modifier.CTRL, Modifier.SHIFT), 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_F5", ctrl, 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_K", ctrl, 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_M", setOf(Modifier.CTRL, Modifier.SHIFT), 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_EQUALS", setOf(Modifier.ALT), 0))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_N", ctrl, 0))
    }

    @Test
    fun repeatedKeysOnlyRepeatZoom() {
        val c = controller()
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_R", ctrl, 1))
        assertEquals(KeyAction.NONE, c.resolveKey("KEYCODE_F5", emptySet(), 3))
        assertEquals(KeyAction.ZOOM_IN, c.resolveKey("KEYCODE_EQUALS", ctrl, 5))
        assertEquals(KeyAction.ZOOM_OUT, c.resolveKey("KEYCODE_MINUS", ctrl, 2))
    }

    @Test
    fun newWindowRequiresShortcutAndMultiWindow() {
        val enabled = controller(config = fixture.copy(newWindowShortcut = newWindow, enableMultiWindow = true))
        assertEquals(KeyAction.NEW_WINDOW, enabled.resolveKey("KEYCODE_N", ctrl, 0))
        assertEquals(KeyAction.NONE, enabled.resolveKey("KEYCODE_N", ctrl, 1))
        assertEquals(KeyAction.NONE, enabled.resolveKey("KEYCODE_N", setOf(Modifier.ALT), 0))
        assertEquals(KeyAction.NONE, enabled.resolveKey("KEYCODE_M", ctrl, 0))
        val disabled = controller(config = fixture.copy(newWindowShortcut = newWindow, enableMultiWindow = false))
        assertEquals(KeyAction.NONE, disabled.resolveKey("KEYCODE_N", ctrl, 0))
        val unset = controller(config = fixture.copy(newWindowShortcut = null, enableMultiWindow = true))
        assertEquals(KeyAction.NONE, unset.resolveKey("KEYCODE_N", ctrl, 0))
    }

    // ---------- d. Context menu ----------

    @Test
    fun mapsHitTypesToMenuActions() {
        val c = controller()
        assertEquals(listOf(MenuAction.COPY_LINK, MenuAction.OPEN_LINK), c.contextMenuActions(HitType.ANCHOR))
        assertEquals(
            listOf(MenuAction.COPY_LINK, MenuAction.OPEN_LINK, MenuAction.SAVE_IMAGE),
            c.contextMenuActions(HitType.IMAGE_ANCHOR),
        )
        assertEquals(listOf(MenuAction.SAVE_IMAGE), c.contextMenuActions(HitType.IMAGE))
        assertTrue(c.contextMenuActions(HitType.OTHER).isEmpty())
    }

    // ---------- e. Wake state machine ----------

    @Test
    fun wakeEventsOnlyOnStateChange() {
        val c = controller(config = fixture.copy(pauseOnPowerSave = false))
        assertTrue(c.isAwake)
        assertEquals(WakeEvent.NONE, c.onWakeInput(interactive = true, anyDisplayOn = true, powerSaveMode = false))
        assertEquals(WakeEvent.SLEEP, c.onWakeInput(interactive = false, anyDisplayOn = true, powerSaveMode = false))
        assertFalse(c.isAwake)
        assertEquals(WakeEvent.NONE, c.onWakeInput(interactive = true, anyDisplayOn = false, powerSaveMode = false))
        assertEquals(WakeEvent.WAKE, c.onWakeInput(interactive = true, anyDisplayOn = true, powerSaveMode = false))
        assertEquals(WakeEvent.NONE, c.onWakeInput(interactive = true, anyDisplayOn = true, powerSaveMode = true))
    }

    @Test
    fun powerSaveCountsAsSleepOnlyWhenConfigured() {
        val pausing = controller(config = fixture.copy(pauseOnPowerSave = true))
        assertFalse(pausing.computeAwake(interactive = true, anyDisplayOn = true, powerSaveMode = true))
        assertEquals(WakeEvent.SLEEP, pausing.onWakeInput(interactive = true, anyDisplayOn = true, powerSaveMode = true))
        assertEquals(WakeEvent.WAKE, pausing.onWakeInput(interactive = true, anyDisplayOn = true, powerSaveMode = false))
        val ignoring = controller(config = fixture.copy(pauseOnPowerSave = false))
        assertTrue(ignoring.computeAwake(interactive = true, anyDisplayOn = true, powerSaveMode = true))
    }

    // ---------- f. User agent ----------

    @Test
    fun userChoiceHasTopPriority() {
        val c = controller()
        assertEquals(UaMode.DESKTOP, c.resolveUaMode(UaMode.MOBILE, false, UaMode.DESKTOP, false))
        assertEquals(UaMode.MOBILE, c.resolveUaMode(UaMode.DESKTOP, true, UaMode.MOBILE, true))
        assertEquals(UaReason.USER, c.resolveUa(UaMode.AUTO, true, UaMode.MOBILE, true, "").reason)
    }

    @Test
    fun autoUserChoiceIsTreatedAsUnset() {
        val c = controller()
        assertEquals(UaMode.DESKTOP, c.resolveUaMode(UaMode.MOBILE, false, UaMode.AUTO, true))
        assertEquals(UaMode.MOBILE, c.resolveUaMode(UaMode.MOBILE, true, UaMode.AUTO, false))
        assertEquals(UaMode.MOBILE, c.resolveUaMode(UaMode.AUTO, false, null, false))
    }

    @Test
    fun learnedDesktopBeatsConfig() {
        val c = controller()
        assertEquals(UaMode.DESKTOP, c.resolveUaMode(UaMode.MOBILE, false, null, true))
        assertEquals(UaReason.LEARNED, c.resolveUa(UaMode.MOBILE, false, null, true, "").reason)
        assertEquals(UaMode.MOBILE, c.resolveUaMode(UaMode.MOBILE, true, null, false))
    }

    @Test
    fun configModeAppliesWhenNothingStored() {
        val c = controller()
        assertEquals(UaMode.DESKTOP, c.resolveUaMode(UaMode.DESKTOP, false, null, false))
        assertEquals(UaMode.MOBILE, c.resolveUaMode(UaMode.MOBILE, true, null, false))
        assertEquals(UaReason.CONFIG, c.resolveUa(UaMode.DESKTOP, false, null, false, "").reason)
    }

    @Test
    fun autoResolvesByDeviceClass() {
        val c = controller()
        assertEquals(UaResolution(UaMode.DESKTOP, UaReason.AUTO_LARGE), c.resolveUa(UaMode.AUTO, true, null, false, ""))
        assertEquals(UaResolution(UaMode.MOBILE, UaReason.AUTO_COMPACT), c.resolveUa(UaMode.AUTO, false, null, false, ""))
    }

    @Test
    fun overrideIsReportedAsReason() {
        val c = controller()
        val resolution = c.resolveUa(UaMode.AUTO, true, null, false, "Custom/1.0")
        assertEquals(UaReason.OVERRIDE, resolution.reason)
        assertEquals(UaMode.DESKTOP, resolution.mode)
    }

    private val webViewUa = "Mozilla/5.0 (Linux; Android 17; Pixel 10 Build/AP4A; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.54 Mobile Safari/537.36"

    @Test
    fun buildUserAgentOverrideWins() {
        assertEquals("Custom/1.0", controller().buildUserAgent(webViewUa, UaMode.DESKTOP, "Custom/1.0"))
    }

    @Test
    fun buildsDesktopUserAgentFromChromeVersion() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.7390.54 Safari/537.36",
            controller().buildUserAgent(webViewUa, UaMode.DESKTOP, ""),
        )
    }

    @Test
    fun desktopWithoutChromeVersionFallsBackToMobile() {
        val ua = "Mozilla/5.0 (Linux; Android 17; wv) AppleWebKit/537.36 Version/4.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Mobile Safari/537.36",
            controller().buildUserAgent(ua, UaMode.DESKTOP, ""),
        )
    }

    @Test
    fun mobileUserAgentDropsWebViewMarkers() {
        val mobile = controller().buildUserAgent(webViewUa, UaMode.MOBILE, "")
        assertEquals(
            "Mozilla/5.0 (Linux; Android 17; Pixel 10 Build/AP4A) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.7390.54 Mobile Safari/537.36",
            mobile,
        )
        assertFalse(mobile.contains("; wv"))
        assertFalse(mobile.contains("Version/"))
    }

    @Test
    fun storeRedirectPositives() {
        val c = controller()
        assertTrue(c.isStoreRedirect("market://details?id=com.discord"))
        assertTrue(c.isStoreRedirect("intent://open#Intent;scheme=discord;package=com.discord;end"))
        assertTrue(
            c.isStoreRedirect(
                "intent://open#Intent;scheme=discord;" +
                    "S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dcom.discord;end",
            ),
        )
        assertTrue(c.isStoreRedirect("https://play.google.com/store/apps/details?id=com.discord"))
        assertTrue(c.isStoreRedirect("http://PLAY.google.com/store/apps"))
    }

    @Test
    fun storeRedirectNegatives() {
        val c = controller()
        assertFalse(c.isStoreRedirect("intent://open#Intent;scheme=discord;end"))
        assertFalse(
            c.isStoreRedirect("intent://open#Intent;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F;end"),
        )
        assertFalse(c.isStoreRedirect("intent://open#Intent;S.browser_fallback_url=%ZZ;end"))
        assertFalse(c.isStoreRedirect("https://play.google.com/store/books/details?id=1"))
        assertFalse(c.isStoreRedirect("https://play.google.com/store/movies"))
        assertFalse(c.isStoreRedirect("https://play.google.com/store/appsfoo"))
        assertFalse(c.isStoreRedirect("https://discord.com/app"))
        assertFalse(c.isStoreRedirect("https://exa mple.com/"))
        assertFalse(c.isStoreRedirect("mailto:a@b.c"))
        assertFalse(c.isStoreRedirect("not a url"))
    }

    @Test
    fun togglesBetweenDesktopAndMobile() {
        val c = controller()
        assertEquals(UaMode.MOBILE, c.toggleTarget(UaMode.DESKTOP))
        assertEquals(UaMode.DESKTOP, c.toggleTarget(UaMode.MOBILE))
    }

    // ---------- g. Shortcut validation ----------

    @Test
    fun configuredShortcutsAreValid() {
        assertTrue(controller().validateShortcuts().isEmpty())
        val withNewWindow = controller(config = fixture.copy(newWindowShortcut = newWindow))
        assertTrue(withNewWindow.validateShortcuts().isEmpty())
    }

    @Test
    fun reportsInvalidShortcuts() {
        val bad = fixture.copy(
            appShortcuts = listOf(
                ShortcutSpec("Lower", "keycode_k", ctrl),
                ShortcutSpec(" ", "KEYCODE_J", ctrl),
                ShortcutSpec("Reload clash", "KEYCODE_R", ctrl),
            ),
            newWindowShortcut = ShortcutSpec("Zoom clash", "KEYCODE_EQUALS", ctrl),
            enableMultiWindow = true,
        )
        val errors = controller(config = bad).validateShortcuts()
        assertEquals(4, errors.size)
        assertTrue(errors.any { it.contains("invalid key name 'keycode_k'") })
        assertTrue(errors.any { it.contains("KEYCODE_J: empty label") })
        assertTrue(errors.any { it.contains("collides with built-in RELOAD") })
        assertTrue(errors.any { it.startsWith("NEW_WINDOW_SHORTCUT") && it.contains("ZOOM_IN") })
    }

    @Test
    fun appShortcutMayNotReuseNewWindowShortcut() {
        val config = fixture.copy(
            appShortcuts = listOf(ShortcutSpec("Window clash", "KEYCODE_N", ctrl)),
            newWindowShortcut = newWindow,
            enableMultiWindow = true,
        )
        val errors = controller(config = config).validateShortcuts()
        assertEquals(listOf("APP_SHORTCUTS 'Window clash' collides with built-in NEW_WINDOW"), errors)
    }

    // ---------- h. Colors ----------

    @Test
    fun parsesHexColors() {
        assertEquals(0x1E1F22, DesktopSessionController.parseHex("#1E1F22"))
        assertEquals(0xFFFFFF, DesktopSessionController.parseHex("#ffffff"))
        assertNull(DesktopSessionController.parseHex("1E1F22"))
        assertNull(DesktopSessionController.parseHex("#FFF"))
        assertNull(DesktopSessionController.parseHex("#GGGGGG"))
        assertNull(DesktopSessionController.parseHex(""))
    }

    @Test
    fun classifiesDarkAndLightColors() {
        assertTrue(DesktopSessionController.isDarkColor("#1E1F22"))
        assertTrue(DesktopSessionController.isDarkColor("#000000"))
        assertTrue(DesktopSessionController.isDarkColor("#5865F2"))
        assertFalse(DesktopSessionController.isDarkColor("#FFFFFF"))
        assertFalse(DesktopSessionController.isDarkColor("#FFEB3B"))
        assertFalse(DesktopSessionController.isDarkColor("#777777"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun isDarkColorRejectsInvalidHex() {
        DesktopSessionController.isDarkColor("nope")
    }

    @Test
    fun exposesUaStore() {
        val store = FakeUaStore(userChoice = UaMode.DESKTOP, learnedDesktop = true)
        val c = DesktopSessionController(FakeZoomStore(), store, fixture)
        assertEquals(UaMode.DESKTOP, c.uaStore.userChoice)
        assertTrue(c.uaStore.learnedDesktop)
    }
}
