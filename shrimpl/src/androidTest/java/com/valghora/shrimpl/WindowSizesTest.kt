package com.valghora.shrimpl

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import com.valghora.shrimpl.data.HomeLayout
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shrimpl on the real window manager at the screen sizes it is designed for (docs/SHRIMPL_SPEC.md R1.1,
 * R2.6, docs/GOOGLEBOOKS.md): the display is resized with `wm size` and `wm density`, so the window's
 * width in dp is what a device of that size reports. Robolectric checks the layout rules; this checks
 * that the real window gives them the numbers they expect.
 *
 * - The Dell XPS Googlebook at full screen, 2560 x 1600 at 1.5 (1707 dp): two panes, four tiles.
 * - The other Googlebooks (Acer, ASUS, HP and Lenovo), 2880 x 1800: nobody has confirmed their default
 *   scale, so both guesses in docs/GOOGLEBOOKS.md are tested, the Dell's 1.5 (1920 dp) and one scaled
 *   for their higher density, 1.625 (1772 dp): two panes, four tiles. The HP's 1920 x 1200 option at 1.25
 *   has the same 1536 dp width as the laptop below.
 * - A common laptop, 1920 x 1080 at 125 % (1536 dp): two panes, three tiles.
 * - A phone, 1080 x 2400 at 2.625 (411 dp): one pane, three tiles.
 */
@RunWith(AndroidJUnit4::class)
class WindowSizesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = (context.applicationContext as ShrimplApp).container
    private val names = ('a'..'i').map { "Tile$it" }

    @Before
    fun seedApps() {
        resetDisplay()
        runBlocking {
            names.forEach { name ->
                val profile = Profile(name = name, appId = "wsz${name.lowercase()}", startUrl = "https://${name.lowercase()}.example/", internalDomains = listOf("${name.lowercase()}.example"), iconBackground = "#3367D6")
                val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter(name.first()))
                container.store.save(WebAppRecord.create(profile, icons, templateVersion = 1, signerFingerprint = "AA:BB", now = 0L), emptyMap())
            }
        }
        container.settings.setHomeLayout(HomeLayout.TILES)
    }

    @After
    fun cleanUp() {
        resetDisplay()
        runBlocking { names.forEach { container.store.delete("wsz${it.lowercase()}") } }
        container.settings.setHomeLayout(HomeLayout.LIST)
    }

    private fun resetDisplay() {
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    /** What Shrimpl shows at [widthPx] x [heightPx] pixels and [dpi]: the tiles in its first row, and whether two panes show. */
    private fun layoutAt(widthPx: Int, heightPx: Int, dpi: Int): Triple<Int, Boolean, Float> {
        device.executeShellCommand("wm size ${widthPx}x$heightPx")
        device.executeShellCommand("wm density $dpi")
        Thread.sleep(1_500)
        return try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Tilea").fetchSemanticsNodes().isNotEmpty() }
                val tiles = compose.onAllNodes(hasText("Tile", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
                    .fetchSemanticsNodes().map { it.boundsInRoot }
                val firstRow = tiles.minOf { it.top }
                val across = tiles.count { abs(it.top - firstRow) < 2f }
                val twoPanes = compose.onAllNodesWithText(context.getString(R.string.home_pick_app)).fetchSemanticsNodes().isNotEmpty()
                val tileWidthDp = tiles.first().width / (dpi / 160f)
                Triple(across, twoPanes, tileWidthDp)
            }
        } finally {
            resetDisplay()
        }
    }

    @Test
    fun theXpsGooglebookFullScreenShowsTwoPanesAndFourTiles() {
        val (across, twoPanes, tileWidth) = layoutAt(2560, 1600, 240)
        assertTrue("two panes", twoPanes)
        assertEquals("tiles across", 4, across)
        assertTrue("tiles keep their size ($tileWidth dp)", tileWidth >= 118f)
    }

    @Test
    fun the2880x1800GooglebooksAtTheDellsScaleShowTwoPanesAndFourTiles() {
        val (across, twoPanes, tileWidth) = layoutAt(2880, 1800, 240)
        assertTrue("two panes", twoPanes)
        assertEquals("tiles across", 4, across)
        assertTrue("tiles keep their size ($tileWidth dp)", tileWidth >= 118f)
    }

    @Test
    fun the2880x1800GooglebooksScaledForTheirDensityShowTwoPanesAndFourTiles() {
        val (across, twoPanes, tileWidth) = layoutAt(2880, 1800, 260)
        assertTrue("two panes", twoPanes)
        assertEquals("tiles across", 4, across)
        assertTrue("tiles keep their size ($tileWidth dp)", tileWidth >= 118f)
    }

    @Test
    fun aCommonLaptopShowsTwoPanesAndThreeTiles() {
        val (across, twoPanes, tileWidth) = layoutAt(1920, 1080, 200)
        assertTrue("two panes", twoPanes)
        assertEquals("tiles across", 3, across)
        assertTrue("tiles keep their size ($tileWidth dp)", tileWidth >= 118f)
    }

    @Test
    fun aPhoneShowsOnePaneAndThreeTiles() {
        val (across, twoPanes, tileWidth) = layoutAt(1080, 2400, 420)
        assertTrue("one pane", !twoPanes)
        assertEquals("tiles across", 3, across)
        assertTrue("tiles are at least 100 dp wide ($tileWidth dp)", tileWidth >= 100f)
    }
}
