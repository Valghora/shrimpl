package com.valghora.shrimpl

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A dialog's unsaved text survives the activity being recreated, as on a rotation or resize (CLAUDE.md
 * rule 2; R2.3: changes not saved are kept when the window is recreated). Robolectric can't test this: it
 * never goes idle once a dialog's text field can take focus (docs/TESTING.md, "Robolectric").
 */
@RunWith(AndroidJUnit4::class)
class DialogDraftTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ShrimplApp).container
    private val name = "Draftable"
    private val appId = "dialogdraft"

    @Before
    fun seedApp() {
        runBlocking {
            val profile = Profile(name = name, appId = appId, startUrl = "https://draftable.example/", internalDomains = listOf("draftable.example"), iconBackground = "#3367D6")
            val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter(name.first()))
            container.store.save(WebAppRecord.create(profile, icons, templateVersion = 1, signerFingerprint = "AA:BB", now = 0L), emptyMap())
        }
    }

    @After
    fun removeApp() {
        runBlocking { container.store.delete(appId) }
    }

    private fun textFieldShows(text: String) =
        compose.onAllNodes(hasSetTextAction() and hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aDialogsTypedTextSurvivesRecreation() {
        // The seeded app isn't installed, so a tap does nothing (R2.2); its menu opens the edit page.
        compose.waitUntil(20_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.action_app_options, name)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_edit)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(R.string.field_name)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText(context.getString(R.string.field_name)).performClick()
        compose.waitUntil(10_000) { textFieldShows(name) }
        compose.onNode(hasSetTextAction() and hasText(name), useUnmergedTree = true).performTextReplacement("Half-typed name")

        compose.activityRule.scenario.recreate()

        compose.waitUntil(15_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { textFieldShows("Half-typed name") }

        // Cancel discards the draft: the record keeps its name.
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        assertEquals(name, runBlocking { container.store.get(appId) }?.profile?.name)
    }
}
