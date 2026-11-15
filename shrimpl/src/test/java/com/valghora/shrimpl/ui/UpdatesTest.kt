package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.valghora.shrimpl.BuildConfig
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.core.update.Release
import com.valghora.shrimpl.core.update.UpdateFrequency
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import com.valghora.shrimpl.ui.settings.SettingsScreen
import com.valghora.shrimpl.update.UpdateResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Shrimpl updates (docs/SHRIMPL_SPEC.md R14): the About section of Settings, and the Home notice for
 * a release the check found.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class UpdatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = TestApps.app.container
    private val release = Release("1.9.9", 199, "https://example.com/a.apk", "https://example.com/a.apk.sha256", "https://example.com/r")

    @After
    fun reset() {
        container.settings.setUpdateFrequency(UpdateFrequency.DEFAULT)
    }

    private fun settings() = compose.setContent { MaterialTheme { SettingsScreen(inPane = false, onBack = {}) } }

    private fun home() = compose.setContent {
        MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
    }

    private fun shows(text: String) = compose.onNodeWithText(text, substring = true).performScrollTo().assertExists()

    /** R14.3: About's version row before any check, with Check, and a daily background check. */
    @Test
    fun aboutShowsTheVersionUncheckedAndDaily() {
        settings()
        shows(string(R.string.update_current, BuildConfig.VERSION_NAME))
        shows(string(R.string.update_not_checked))
        shows(string(R.string.update_frequency_day_detail))
        compose.onNode(hasText(string(R.string.action_check)) and hasClickAction()).assertIsEnabled()
    }

    /** R14.2, R14.3: Disabled describes itself, and Check still works by hand. */
    @Test
    fun disabledLeavesCheckByHand() {
        container.settings.setUpdateFrequency(UpdateFrequency.DISABLED)
        settings()
        shows(string(R.string.update_frequency_disabled_detail))
        compose.onNode(hasText(string(R.string.action_check)) and hasClickAction()).assertIsEnabled()
    }

    /** R14.2: choosing an interval in Check for updates saves it, and its description follows. */
    @Test
    fun choosingAnIntervalSavesIt() {
        settings()
        compose.onNode(hasText(string(R.string.update_check)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.update_frequency_week)).performClick()
        assertEquals(UpdateFrequency.WEEK, container.settings.updateFrequency.value)
        shows(string(R.string.update_frequency_week_detail))
    }

    /** R14.3: a found release, then when it was checked, each on its own line, with Update. */
    @Test
    fun aFoundReleaseIsOfferedWithItsCheckTime() {
        container.updates.record(UpdateResult.Available(release), System.currentTimeMillis())
        settings()
        shows(string(R.string.update_available, "1.9.9") + "\n")
        compose.onNode(hasText(string(R.string.action_update)) and hasClickAction()).assertExists()
    }

    /** R14.3: a failed check says so, with its reason. */
    @Test
    fun aFailedCheckGivesItsReason() {
        container.updates.record(UpdateResult.Failed(string(R.string.update_error_unreachable)), System.currentTimeMillis())
        settings()
        shows(string(R.string.update_failed, string(R.string.update_error_unreachable)))
    }

    /** R14.4: Home shows the found release until it is dismissed, and not again for that version. */
    @Test
    fun theHomeNoticeShowsUntilDismissed() {
        container.updates.record(UpdateResult.Available(release), System.currentTimeMillis())
        home()
        val notice = string(R.string.update_notice, "1.9.9")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(notice)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(string(R.string.action_dismiss)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(notice)).fetchSemanticsNodes().isEmpty() }
        assertEquals(199, container.updates.status.value.dismissedCode)
    }

    /** R14.4: the notice also sits above a list of apps. */
    @Test
    fun theHomeNoticeSitsAboveTheApps() {
        TestApps.save()
        container.updates.record(UpdateResult.Available(release), System.currentTimeMillis())
        home()
        compose.awaitHome()
        compose.onNodeWithText(string(R.string.update_notice, "1.9.9")).assertExists()
    }

    /** R14.4: up to date, Home shows no notice. */
    @Test
    fun noNoticeWhenUpToDate() {
        container.updates.record(UpdateResult.UpToDate, System.currentTimeMillis())
        home()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.update_notice, "1.9.9")).assertDoesNotExist()
    }
}
