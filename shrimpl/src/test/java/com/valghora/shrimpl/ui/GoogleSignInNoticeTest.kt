package com.valghora.shrimpl.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.discovery.Discovery
import com.valghora.shrimpl.core.discovery.Note
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.WebAppDraft
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.form.ProfileFormFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * docs/SHRIMPL_SPEC.md R3.3: discovery's Google sign-in note shows Shrimpl's wording with Open in
 * Chrome, which opens the site in Chrome; without Chrome it names the browser and opens the default one.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class GoogleSignInNoticeTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val address = "https://mail.google.com/mail/"
    private val draft = WebAppDraft.create(
        ProfileForm.from(Profile(name = "Gmail", appId = "gmail", startUrl = address, internalDomains = listOf("google.com"), iconBackground = "#3367D6")),
        brand = null,
        website = null,
    )
    private val note = Notice.of(Note(Note.Level.CAUTION, Discovery.GOOGLE_SIGN_IN, address))

    private fun installChrome() {
        val chrome = ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main")
        shadowOf(app.packageManager).addActivityIfNotPresent(chrome)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            chrome,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
    }

    private fun form(): @Composable () -> Unit = {
        MaterialTheme {
            Box(Modifier.width(420.dp).height(1600.dp)) {
                ProfileFormFields(draft, onChange = {}, hints = emptyMap(), notes = listOf(note), storedImage = null)
            }
        }
    }

    private fun opened(): Intent = requireNotNull(shadowOf(app).nextStartedActivity) { "nothing was opened" }

    @Test
    fun withChromeTheNoticeOpensTheSiteInChrome() {
        installChrome()
        compose.setContent(form())
        compose.onNodeWithText(string(R.string.google_sign_in_notice)).assertExists()
        compose.onNodeWithText(string(R.string.action_open_in_chrome)).performClick()
        val intent = opened()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(address, intent.dataString)
        assertEquals("com.android.chrome", intent.`package`)
    }

    @Test
    fun withoutChromeTheNoticeNamesAndOpensTheBrowser() {
        compose.setContent(form())
        compose.onNodeWithText(string(R.string.google_sign_in_notice_browser)).assertExists()
        compose.onNodeWithText(string(R.string.action_open_in_browser)).performClick()
        val intent = opened()
        assertEquals(address, intent.dataString)
        assertNull("any browser", intent.`package`)
    }

    @Test
    fun aDismissedNoticeStaysDismissedAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(form())
        compose.onNodeWithText(string(R.string.action_dismiss)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(string(R.string.google_sign_in_notice_browser)).assertDoesNotExist()
    }
}
