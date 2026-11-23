package com.valghora.shrimpl.data

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.update.Changelog
import com.valghora.shrimpl.ui.TestApps
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.WhatsNewSheet
import com.valghora.shrimpl.ui.theme.ShrimplTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What's new (docs/SHRIMPL_SPEC.md R14.7): an update shows the bundled notes since the last ones seen
 * until Done, a first install shows none, and the sheet lists them under the installed version.
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = TestApps.app

    @Test
    fun anUpdateShowsTheNotesUntilDone() {
        val settings = AppSettings(app)
        settings.setNotesSeen(109)
        val whatsNew = WhatsNew(app, settings, versionCode = 116)
        assertTrue("an update showed nothing", whatsNew.pending.value.isNotEmpty())
        whatsNew.dismiss()
        assertEquals(116, settings.notesSeen)
        assertTrue(whatsNew.pending.value.isEmpty())
        assertTrue("seen notes came back", WhatsNew(app, settings, versionCode = 116).pending.value.isEmpty())
    }

    @Test
    fun aFirstInstallShowsNothingAndStartsFromItsVersion() {
        // The application's own What's new ran at start and recorded its version; start from none.
        app.getSharedPreferences("shrimpl_settings", android.content.Context.MODE_PRIVATE).edit().remove("notes_seen").commit()
        val settings = AppSettings(app)
        // Robolectric installs the app once, so its first install and last update times are equal.
        val whatsNew = WhatsNew(app, settings, versionCode = 116)
        assertTrue(whatsNew.pending.value.isEmpty())
        assertEquals(116, settings.notesSeen)
    }

    @Test
    fun theSheetListsTheNotesUnderTheVersion() {
        val releases = listOf(Changelog.Release(null, listOf(Changelog.Section("New", listOf("**Ad blocking.** Choose Standard or Strict.")))))
        var done = false
        compose.setContent { ShrimplTheme(ThemeSetting.LIGHT) { WhatsNewSheet(releases, "1.1.6") { done = true } } }
        compose.onNodeWithText(string(R.string.whats_new_title, "1.1.6")).assertExists()
        compose.onNodeWithText("New").assertExists()
        compose.onNodeWithText("Ad blocking. Choose Standard or Strict.").assertExists()
        compose.onNodeWithText(string(R.string.action_done)).performClick()
        assertTrue("Done didn't close it", done)
    }
}
