package com.valghora.wrapper.core.profile

import com.valghora.wrapper.core.Modifier
import com.valghora.wrapper.core.ShortcutSpec
import com.valghora.wrapper.core.UaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the retired scripts/tests/test_site_config.py, plus the JSON codec. */
class ProfileTest {
    private val discordJson = javaClass.getResource("/profiles/discord.json")!!.readText()
    private val discord = ProfileCodec.decode(discordJson)

    private val minimal = Profile(
        name = "Example",
        appId = "example",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#5865F2",
        headerMode = HeaderMode.CUSTOM,
        headerColor = "#1E1F22",
    )

    private fun fields(profile: Profile) = ProfileValidator.validate(profile).map { it.field }

    private fun messages(profile: Profile) = ProfileValidator.validate(profile).joinToString("\n") { "${it.field}: ${it.message}" }

    // ---------- Codec ----------

    @Test
    fun decodesDiscordFixture() {
        assertEquals("Discord", discord.name)
        assertEquals("discord", discord.appId)
        assertEquals(5, discord.internalDomains.size)
        assertEquals(UaMode.AUTO, discord.userAgentMode)
        assertTrue(discord.allowMicrophone && discord.allowCamera)
        assertEquals(ShortcutSpec("Toggle Mute", "KEYCODE_M", setOf(Modifier.CTRL, Modifier.SHIFT)), discord.appShortcuts[1])
        assertEquals("#1E1F22", discord.headerColor)
        assertEquals(HeaderMode.CUSTOM, discord.headerMode) // upgraded: a color without a mode was always used
    }

    @Test
    fun roundTrips() {
        assertEquals(discord, ProfileCodec.decode(ProfileCodec.encode(discord)))
        val full = minimal.copy(headerColor = "#121212", newWindowShortcut = KeyCombo("KEYCODE_N", setOf(Modifier.CTRL, Modifier.SHIFT)))
        assertEquals(full, ProfileCodec.decode(ProfileCodec.encode(full)))
    }

    @Test
    fun encodesDefaultsAndOmitsNulls() {
        val json = ProfileCodec.encode(minimal.copy(headerMode = HeaderMode.DYNAMIC))
        assertTrue(json.contains("\"allowNotifications\": true"))
        assertTrue(json.contains("\"userAgentMode\": \"auto\""))
        assertTrue(json.contains("\"headerMode\": \"dynamic\""))
        assertFalse(json.contains("newWindowShortcut"))
    }

    @Test
    fun fillsDefaultsForMissingOptionalKeys() {
        val profile = ProfileCodec.decode(
            """{"name":"Example","appId":"example","startUrl":"https://example.com/","internalDomains":["example.com"],
               "iconBackground":"#5865F2","headerColor":"#1E1F22"}""",
        )
        assertEquals(minimal, profile)
        assertEquals(Profile.DEFAULT_OAUTH_DOMAINS, profile.oauthDomains)
    }

    @Test
    fun rejectsUnknownKeys() {
        val e = assertThrows(ProfileException::class.java) {
            ProfileCodec.decode(discordJson.replace("\"allowCamera\"", "\"allowCamra\""))
        }
        assertTrue(e.message!!, e.message!!.contains("allowCamra"))
    }

    @Test
    fun rejectsMissingRequiredKeys() {
        val e = assertThrows(ProfileException::class.java) { ProfileCodec.decode("""{"name":"Example"}""") }
        assertTrue(e.message!!, e.message!!.contains("startUrl"))
    }

    @Test
    fun rejectsBadEnumValueAndMalformedJson() {
        assertThrows(ProfileException::class.java) {
            ProfileCodec.decode(discordJson.replace("\"userAgentMode\": \"auto\"", "\"userAgentMode\": \"tablet\""))
        }
        assertThrows(ProfileException::class.java) { ProfileCodec.decode("{not json") }
    }

    @Test
    fun decodeReportsEveryValidationError() {
        val e = assertThrows(ProfileException::class.java) {
            ProfileCodec.decode(ProfileCodec.encode(minimal.copy(name = " ", iconBackground = "blue")))
        }
        assertEquals(setOf("name", "iconBackground"), e.errors.map { it.field }.toSet())
        assertTrue(e.message!!.contains("iconBackground: must be #RRGGBB"))
    }

    // ---------- Validation ----------

    @Test
    fun validProfilesHaveNoErrors() {
        assertEquals(emptyList<FieldError>(), ProfileValidator.validate(minimal))
        assertEquals(emptyList<FieldError>(), ProfileValidator.validate(discord))
    }

    @Test
    fun schemaVersionMustBeCurrent() {
        assertEquals(listOf("schemaVersion"), fields(minimal.copy(schemaVersion = 99)))
    }

    @Test
    fun nameRules() {
        assertEquals(listOf("name"), fields(minimal.copy(name = "  ")))
        assertEquals(listOf("name"), fields(minimal.copy(name = "x".repeat(51))))
    }

    @Test
    fun appIdRules() {
        assertEquals(listOf("appId"), fields(minimal.copy(appId = "3dprint")))
        assertEquals(listOf("appId"), fields(minimal.copy(appId = "My-App")))
    }

    @Test
    fun startUrlMustBeHttps() {
        assertEquals(listOf("startUrl"), fields(minimal.copy(startUrl = "http://example.com/")))
        assertEquals(listOf("startUrl"), fields(minimal.copy(startUrl = "https://user@example.com/")))
        assertEquals(listOf("startUrl"), fields(minimal.copy(startUrl = "not a url")))
        assertEquals(listOf("startUrl"), fields(minimal.copy(startUrl = "https:///path")))
    }

    @Test
    fun startUrlMustBeInternal() {
        assertTrue(messages(minimal.copy(startUrl = "https://other.org/")).contains("not covered by the internal domains"))
        assertEquals(emptyList<String>(), fields(minimal.copy(startUrl = "https://app.example.com./path")))
    }

    @Test
    fun domainsMustBeBareLowercaseHostnames() {
        val errors = messages(minimal.copy(internalDomains = listOf("example.com", "https://example.com", "Example.com")))
        assertTrue(errors, errors.contains("'https://example.com' must be a bare lowercase hostname"))
        assertTrue(errors, errors.contains("'Example.com' must be a bare lowercase hostname"))
        assertEquals(listOf("oauthDomains"), fields(minimal.copy(oauthDomains = listOf("localhost"))))
        assertTrue(fields(minimal.copy(internalDomains = emptyList())).contains("internalDomains"))
    }

    @Test
    fun userAgentOverrideIsOneLine() {
        assertEquals(listOf("userAgentOverride"), fields(minimal.copy(userAgentOverride = "a\nb")))
    }

    @Test
    fun colorRules() {
        assertEquals(listOf("iconBackground"), fields(minimal.copy(iconBackground = "#12345")))
        assertEquals(listOf("iconForeground"), fields(minimal.copy(iconForeground = "white")))
        assertEquals(listOf("headerColor"), fields(minimal.copy(headerColor = "123456")))
        assertEquals(listOf("headerColor"), fields(minimal.copy(headerColor = Profile.SYSTEM)))
        assertEquals(emptyList<String>(), fields(minimal.copy(iconForeground = "#ffffff", headerColor = "#000000")))
        assertEquals(emptyList<String>(), fields(minimal.copy(iconForeground = Profile.AUTO, headerColor = Profile.SYSTEM)))
    }

    @Test
    fun windowSizeRules() {
        assertEquals(listOf("minWindowDp"), fields(minimal.copy(minWindowDp = WindowSize(1300, 500))))
        assertTrue(fields(minimal.copy(defaultWindowDp = WindowSize(100, 800))).contains("defaultWindowDp"))
        assertTrue(fields(minimal.copy(minWindowDp = WindowSize(400, 20000))).contains("minWindowDp"))
    }

    @Test
    fun shortcutFieldRules() {
        val bad = minimal.copy(appShortcuts = listOf(ShortcutSpec(" ", "k", emptySet())))
        val errors = messages(bad)
        assertTrue(errors, errors.contains("shortcut 1: label must not be empty"))
        assertTrue(errors, errors.contains("shortcut 1: key must look like KEYCODE_K"))
        assertEquals(listOf("newWindowShortcut"), fields(minimal.copy(newWindowShortcut = KeyCombo("n"))))
    }

    @Test
    fun shortcutsMustNotClashWithBuiltIns() {
        val reload = minimal.copy(appShortcuts = listOf(ShortcutSpec("Refresh", "KEYCODE_R", setOf(Modifier.CTRL))))
        assertEquals(listOf(FieldError("appShortcuts", "'Refresh' collides with built-in RELOAD")), ProfileValidator.validate(reload))
        val zoomWindow = minimal.copy(newWindowShortcut = KeyCombo("KEYCODE_0", setOf(Modifier.CTRL)))
        assertEquals(listOf("newWindowShortcut"), fields(zoomWindow))
        val sameAsNewWindow = minimal.copy(
            appShortcuts = listOf(ShortcutSpec("Window", "KEYCODE_N", setOf(Modifier.CTRL))),
            newWindowShortcut = KeyCombo("KEYCODE_N", setOf(Modifier.CTRL)),
        )
        assertEquals(listOf("appShortcuts"), fields(sameAsNewWindow))
    }

    // ---------- Derived values ----------

    @Test
    fun derivedValuesForDiscord() {
        assertEquals("#FFFFFF", discord.resolvedIconForeground)
        assertEquals("discord_notifications", discord.notificationChannelId)
        assertTrue(discord.uaFeaturesActive)
        assertEquals(10, discord.allowedOriginRules.size)
        assertTrue("https://*.discordapp.net" in discord.allowedOriginRules)
    }

    @Test
    fun autoForegroundFollowsContrast() {
        assertEquals("#FFFFFF", minimal.copy(iconBackground = "#5865F2", iconForeground = Profile.AUTO).resolvedIconForeground)
        assertEquals("#000000", minimal.copy(iconBackground = "#FFEB3B", iconForeground = Profile.AUTO).resolvedIconForeground)
        assertEquals("#123456", minimal.copy(iconForeground = "#123456").resolvedIconForeground)
        assertEquals("#FFFFFF", minimal.copy(iconBackground = "#FFEB3B", iconForeground = Profile.DEFAULT_ICON_FOREGROUND).resolvedIconForeground)
    }

    @Test
    fun headerAndGlyphDefaults() {
        val plain = Profile(name = "A", appId = "a", startUrl = "https://a.com/", internalDomains = listOf("a.com"), iconBackground = "#123456")
        assertEquals(HeaderMode.SYSTEM, plain.headerMode)
        assertEquals(Profile.DEFAULT_HEADER_COLOR, plain.headerColor)
        assertEquals("#FFFFFF", plain.iconForeground)
    }

    @Test
    fun upgradesProfilesWithoutHeaderMode() {
        val base = """"name":"A","appId":"a","startUrl":"https://a.com/","internalDomains":["a.com"],"iconBackground":"#123456""""
        val system = ProfileCodec.decode("{$base,\"headerColor\":\"system\"}")
        assertEquals(HeaderMode.SYSTEM, system.headerMode)
        assertEquals(Profile.DEFAULT_HEADER_COLOR, system.headerColor)
        assertEquals(HeaderMode.CUSTOM, ProfileCodec.decode("{$base,\"headerColor\":\"#336699\"}").headerMode)
        assertEquals(HeaderMode.DYNAMIC, ProfileCodec.decode("{$base,\"headerMode\":\"dynamic\",\"headerColor\":\"#336699\"}").headerMode)
        assertEquals(HeaderMode.SYSTEM, ProfileCodec.decode("{$base}").headerMode)
        assertEquals(kotlinx.serialization.json.JsonPrimitive(1), ProfileCodec.upgrade(kotlinx.serialization.json.JsonPrimitive(1)))
    }

    @Test
    fun overrideDisablesUaFeatures() {
        assertFalse(minimal.copy(userAgentOverride = "Custom/1.0").uaFeaturesActive)
    }

    @Test
    fun sessionConfigCarriesProfileValues() {
        val profile = discord.copy(pauseOnPowerSave = true, newWindowShortcut = KeyCombo("KEYCODE_N", setOf(Modifier.CTRL)))
        val config = profile.sessionConfig()
        assertEquals(profile.internalDomains, config.internalDomains)
        assertEquals(profile.oauthDomains, config.oauthDomains)
        assertTrue(config.pauseOnPowerSave)
        assertTrue(config.enableMultiWindow)
        assertEquals(profile.appShortcuts, config.appShortcuts)
        assertEquals(ShortcutSpec(NEW_WINDOW_LABEL, "KEYCODE_N", setOf(Modifier.CTRL)), config.newWindowShortcut)
        assertNull(minimal.newWindowShortcutSpec)
    }

    // ---------- App ids ----------

    @Test
    fun appIdFromName() {
        assertEquals("discord", AppIds.fromName("Discord"))
        assertEquals("googlemaps", AppIds.fromName("Google Maps!"))
        assertEquals("app3dprint", AppIds.fromName("3D Print"))
        assertEquals("app", AppIds.fromName("日本"))
    }

    @Test
    fun uniqueAppIdAddsSuffix() {
        assertEquals("discord", AppIds.unique("Discord", emptySet()))
        assertEquals("discord2", AppIds.unique("Discord", setOf("discord")))
        assertEquals("discord3", AppIds.unique("Discord", setOf("discord", "discord2")))
    }
}
