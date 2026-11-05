package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.valghora.shrimpl.R
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.data.StatusText
import com.valghora.shrimpl.ui.TestApps.awaitHome
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.home.HomeScreen
import com.valghora.shrimpl.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Status text (docs/SHRIMPL_SPEC.md R2.5): hidden, rows and tiles drop the status line, and
 * accessibility services still hear the status. With [TestApps], Example needs a fix and Squoosh isn't
 * installed.
 */
@Config(qualifiers = "w400dp-h900dp")
@RunWith(RobolectricTestRunner::class)
class StatusTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings get() = TestApps.app.container.settings

    private fun home(layout: HomeLayout) {
        TestApps.save()
        settings.setStatusText(StatusText.HIDE)
        settings.setHomeLayout(layout)
        compose.setContent {
            MaterialTheme { HomeScreen(selected = null, showCreateButton = true, onCreate = {}, onSettings = {}, onEdit = {}) }
        }
        compose.waitUntil(LOAD_TIMEOUT_MS) { state("Example") != null }
    }

    /** The app's row or tile: the node that offers its menu. */
    private fun item(name: String) = compose.onAllNodes(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        .fetchSemanticsNodes().singleOrNull()

    private fun state(name: String): String? = item(name)?.config?.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    /** Status text shown anywhere but the list's "Not installed" heading. */
    private fun shownStatus(): List<String> = listOf(R.string.status_fix_required, R.string.home_not_installed).map { string(it) }
        .filter { text -> compose.onAllNodes(hasText(text) and !isHeading(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun hiddenRowsDropTheLineButStillSayTheStatus() {
        home(HomeLayout.LIST)
        assertEquals(emptyList<String>(), shownStatus())
        assertEquals(string(R.string.status_fix_required), state("Example"))
        assertEquals(string(R.string.home_not_installed), state("Squoosh"))
    }

    @Test
    fun hiddenTilesDropTheLineAndStayEven() {
        home(HomeLayout.TILES)
        assertEquals(emptyList<String>(), shownStatus())
        assertEquals(string(R.string.status_fix_required), state("Example"))
        val example = requireNotNull(item("Example")).boundsInRoot
        val squoosh = requireNotNull(item("Squoosh")).boundsInRoot
        // Squoosh's icon is drawn from its saved settings, which load after the tile is laid out.
        assertEquals("tiles side by side match", example.height, squoosh.height, 0.5f)
    }

    @Test
    fun theSettingShowsByDefaultAndSaysWhatItDoes() {
        compose.setContent { MaterialTheme { SettingsScreen(inPane = false, onBack = {}) } }
        compose.onNodeWithText(string(R.string.status_text)).performScrollTo()
        compose.onNodeWithText(string(R.string.status_text_show_detail)).assertExists()
        assertTrue("Show is the default", settings.statusText.value == StatusText.SHOW)
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
