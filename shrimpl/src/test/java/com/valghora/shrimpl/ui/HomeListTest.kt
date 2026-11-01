package com.valghora.shrimpl.ui

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.pm.PackageInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ShrimplApp
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import com.valghora.shrimpl.ui.home.HomeScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The app list (docs/SHRIMPL_SPEC.md R2.2, R11.7). Discord is installed, Squoosh isn't. Tests run
 * without a signing key (Robolectric has no Android Keystore), so both apps count as signed with
 * another key: Discord offers Fix, Squoosh Rebuild.
 */
@Config(qualifiers = "w420dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class HomeListTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = RuntimeEnvironment.getApplication() as ShrimplApp

    private fun record(name: String): WebAppRecord {
        val id = name.lowercase()
        val profile = Profile(name = name, appId = id, startUrl = "https://$id.example/", internalDomains = listOf("$id.example"), iconBackground = "#3367D6")
        val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter(name.first()))
        return WebAppRecord.create(profile, icons, templateVersion = 1, signerFingerprint = "AA:BB", now = 0L)
    }

    private val discord = record("Discord")
    private val squoosh = record("Squoosh")

    @Before
    fun setUp() {
        runBlocking {
            app.container.store.save(discord, emptyMap())
            app.container.store.save(squoosh, emptyMap())
        }
        val installed = PackageInfo().apply {
            packageName = discord.packageName
            applicationInfo = ApplicationInfo().apply { packageName = discord.packageName }
        }
        shadowOf(app.packageManager).installPackage(installed)
        // Robolectric needs an icon registered for an installed package; Shrimpl reads it for the row.
        shadowOf(app.packageManager).setApplicationIcon(discord.packageName, ColorDrawable(Color.BLUE))
        compose.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        // Home reads the installed apps and the signing key off the main thread; wait until Discord shows
        // as installed (its Fix button) before checking anything.
        compose.waitUntil(LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasContentDescription(string(R.string.action_fix_app, "Discord"))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    /** The labels of the open menu's items. */
    private fun menuItems(): List<String> = compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }

    @Test
    fun uninstalledAppsSitBelowANotInstalledLabel() {
        val label = string(R.string.home_not_installed)
        compose.onNodeWithText(label).assertExists()
        assertTrue("Discord is above the label", top("Discord") < top(label))
        assertTrue("Squoosh is below the label", top("Squoosh") > top(label))
    }

    @Test
    fun installedAppWithAnotherKeyOffersFixAndOnlyRemove() {
        compose.onNodeWithContentDescription(string(R.string.action_fix_app, "Discord")).assertExists()
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Discord")).performClick()
        assertEquals(listOf(string(R.string.action_remove)), menuItems())
    }

    @Test
    fun uninstalledAppOffersRebuild() {
        compose.onNodeWithContentDescription(string(R.string.action_rebuild_app, "Squoosh")).assertExists()
    }

    @Test
    fun optionsMenuAndLongPressMenuOfferTheSameActions() {
        val expected = listOf(R.string.action_edit, R.string.action_reinstall, R.string.action_remove).map { string(it) }
        compose.onNodeWithContentDescription(string(R.string.action_app_options, "Squoosh")).performClick()
        assertEquals("the ⋮ menu", expected, menuItems())
        // Edit closes the menu (the screen's onEdit does nothing here).
        compose.onNodeWithText(string(R.string.action_edit)).performClick()
        compose.onNodeWithText("Squoosh").performTouchInput { longClick() }
        assertEquals("the long-press menu", expected, menuItems())
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
