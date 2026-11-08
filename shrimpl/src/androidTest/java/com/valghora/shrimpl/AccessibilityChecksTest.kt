package com.valghora.shrimpl

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import com.valghora.shrimpl.data.HomeLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Google's Accessibility Test Framework on Shrimpl's real screens (docs/SKILLS.md, accessibility
 * skill): touch target sizes, text contrast, missing labels and duplicate bounds, measured on a real
 * display with real fonts and colors. Robolectric can't run these checks (docs/BUILD_NOTES.md), so until
 * the emulator they were left to TalkBack on the owner's devices.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ShrimplApp).container
    private val seeded = listOf("Aurora", "Basalt", "Cinder")

    @Before
    fun seedApps() {
        runBlocking {
            seeded.forEach { name ->
                val profile = Profile(name = name, appId = "a11y${name.lowercase()}", startUrl = "https://${name.lowercase()}.example/", internalDomains = listOf("${name.lowercase()}.example"), iconBackground = "#3367D6")
                val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter(name.first()))
                container.store.save(WebAppRecord.create(profile, icons, templateVersion = 1, signerFingerprint = "AA:BB", now = 0L), emptyMap())
            }
        }
        compose.enableAccessibilityChecks()
    }

    @After
    fun removeApps() {
        runBlocking { seeded.forEach { container.store.delete("a11y${it.lowercase()}") } }
        container.settings.setHomeLayout(HomeLayout.LIST)
    }

    private fun waitForApps() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(seeded.first()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun homeAsAList() {
        container.settings.setHomeLayout(HomeLayout.LIST)
        waitForApps()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun homeAsTiles() {
        container.settings.setHomeLayout(HomeLayout.TILES)
        waitForApps()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun settings() {
        waitForApps()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_title)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(R.string.settings_install)).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun newAppPage() {
        waitForApps()
        compose.onNodeWithText(context.getString(R.string.action_new_web_app)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(context.getString(R.string.action_back)).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
