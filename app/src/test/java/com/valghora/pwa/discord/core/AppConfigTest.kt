package com.valghora.pwa.discord.core

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    private val controller = DesktopSessionController(
        zoomStore = object : ZoomStore {
            override fun load(): Int? = null
            override fun save(value: Int) = Unit
        },
        uaStore = object : UaStore {
            override var userChoice: UaMode? = null
            override var learnedDesktop: Boolean = false
        },
    )

    @Test
    fun startUrlIsHttpsAndInternal() {
        assertEquals("https", URI(AppConfig.START_URL).scheme)
        assertEquals(UrlClass.INTERNAL, controller.classify(AppConfig.START_URL))
    }

    @Test
    fun hexValuesAreValid() {
        listOf(
            AppConfig.ICON_BACKGROUND_HEX,
            AppConfig.ICON_FOREGROUND_HEX,
            AppConfig.HEADER_SURFACE_HEX,
            AppConfig.HEADER_NIGHT,
        ).forEach { assertNotNull("invalid color $it", DesktopSessionController.parseHex(it)) }
        val night = AppConfig.HEADER_SURFACE_HEX_NIGHT
        assertTrue(night.isEmpty() || DesktopSessionController.parseHex(night) != null)
    }

    @Test
    fun domainsAreBareLowercaseHostnames() {
        val hostname = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
        (AppConfig.INTERNAL_DOMAINS + AppConfig.OAUTH_DOMAINS).forEach {
            assertTrue("not a bare lowercase hostname: $it", hostname.matches(it))
        }
    }

    @Test
    fun shortcutsAreValid() {
        assertEquals(emptyList<String>(), controller.validateShortcuts())
    }

    @Test
    fun userAgentModeIsValid() {
        assertTrue(AppConfig.USER_AGENT_MODE in UaMode.entries)
    }

    @Test
    fun iconSourceIsHttpsOrRelativeSvg() {
        val icon = AppConfig.ICON_SVG
        val isHttps = icon.startsWith("https://")
        val isRelativeSvg = !icon.contains("://") && !icon.startsWith("/") && icon.endsWith(".svg")
        assertTrue(isHttps || isRelativeSvg)
    }

    @Test
    fun derivedValuesMatchConfig() {
        val appId = AppConfig.APP_NAME.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        assertEquals(appId, AppConfig.APP_ID)
        assertEquals("${AppConfig.PACKAGE_PREFIX}.${AppConfig.APP_ID}", AppConfig.PACKAGE_NAME)
        assertEquals("${AppConfig.PACKAGE_NAME}.core", AppConfig.CORE_PACKAGE)
        assertEquals("${AppConfig.APP_ID}_notifications", AppConfig.NOTIFICATION_CHANNEL_ID)
        assertEquals(AppConfig.HEADER_SURFACE_HEX_NIGHT.ifEmpty { AppConfig.HEADER_SURFACE_HEX }, AppConfig.HEADER_NIGHT)
        assertEquals(AppConfig.HEADER_NIGHT != AppConfig.HEADER_SURFACE_HEX, AppConfig.USES_NIGHT_RESOURCES)
        assertEquals(DesktopSessionController.isDarkColor(AppConfig.HEADER_SURFACE_HEX), AppConfig.HEADER_IS_DARK)
        assertEquals(DesktopSessionController.isDarkColor(AppConfig.HEADER_NIGHT), AppConfig.HEADER_NIGHT_IS_DARK)
        assertEquals(AppConfig.USER_AGENT_OVERRIDE.isEmpty(), AppConfig.UA_FEATURES_ACTIVE)
        assertEquals("${AppConfig.APP_ID}-debug.apk", AppConfig.DEBUG_APK_NAME)
        assertEquals("${AppConfig.APP_ID}-release-unsigned.apk", AppConfig.RELEASE_APK_NAME)
    }

    @Test
    fun allowedOriginRulesCoverEveryInternalDomain() {
        assertEquals(AppConfig.INTERNAL_DOMAINS.size * 2, AppConfig.ALLOWED_ORIGIN_RULES.size)
        AppConfig.INTERNAL_DOMAINS.forEach {
            assertTrue("https://$it" in AppConfig.ALLOWED_ORIGIN_RULES)
            assertTrue("https://*.$it" in AppConfig.ALLOWED_ORIGIN_RULES)
        }
        assertFalse(AppConfig.ALLOWED_ORIGIN_RULES.any { it.startsWith("http://") })
    }

    @Test
    fun sdkAndWindowValuesAreConsistent() {
        assertTrue(AppConfig.MIN_SDK in 26..37)
        assertTrue(AppConfig.MIN_SDK <= AppConfig.TARGET_SDK && AppConfig.TARGET_SDK <= AppConfig.COMPILE_SDK)
        assertEquals(2, AppConfig.DEFAULT_WINDOW_DP.size)
        assertEquals(2, AppConfig.MIN_WINDOW_DP.size)
        assertTrue(AppConfig.MIN_WINDOW_DP[0] <= AppConfig.DEFAULT_WINDOW_DP[0])
        assertTrue(AppConfig.MIN_WINDOW_DP[1] <= AppConfig.DEFAULT_WINDOW_DP[1])
        assertTrue(AppConfig.VERSION_CODE >= 1)
        assertTrue(AppConfig.VERSION_NAME.isNotBlank())
    }
}
