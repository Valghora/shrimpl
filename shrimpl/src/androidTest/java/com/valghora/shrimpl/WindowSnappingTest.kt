package com.valghora.shrimpl

import android.app.Activity
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shrimpl in a window that shares the screen (docs/SHRIMPL_SPEC.md R1.1): from 1000 dp it shows two
 * panes, below that one, so every Googlebook window snapped to half the screen shows one
 * (docs/GOOGLEBOOKS.md). The emulator can't snap like Googlebook OS, but two of Android's own ways to
 * share the screen put the app in multi-window mode just the same: split screen, which halves the Dell
 * XPS's screen (2560 x 1600 at 1.5) into an 848 dp window, as snapping it does (849 dp, confirmed on the
 * device); and a freeform window resized to an exact width on either side of 1000 dp.
 */
@RunWith(AndroidJUnit4::class)
class WindowSnappingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    companion object {
        /** Freeform windows stay on for the whole class: switched on right before a launch, they weren't always used. */
        @BeforeClass
        @JvmStatic
        fun allowFreeformWindows() {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("settings put global enable_freeform_support 1")
            Thread.sleep(2_000)
        }

        @AfterClass
        @JvmStatic
        fun restoreFreeformWindows() {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("settings delete global enable_freeform_support")
        }
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = (context.applicationContext as ShrimplApp).container
    private val names = listOf("Snapa", "Snapb")

    @Before
    fun seedAppsOnTheDellsScreen() {
        runBlocking {
            names.forEach { name ->
                val profile = Profile(name = name, appId = "snap${name.lowercase()}", startUrl = "https://${name.lowercase()}.example/", internalDomains = listOf("${name.lowercase()}.example"), iconBackground = "#3367D6")
                val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter(name.first()))
                container.store.save(WebAppRecord.create(profile, icons, templateVersion = 1, signerFingerprint = "AA:BB", now = 0L), emptyMap())
            }
        }
        device.executeShellCommand("wm size 2560x1600")
        device.executeShellCommand("wm density 240")
        Thread.sleep(1_500)
    }

    @After
    fun cleanUp() {
        device.executeShellCommand("am force-stop com.android.settings")
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
        runBlocking { names.forEach { container.store.delete("snap${it.lowercase()}") } }
    }

    private fun resumed(): Activity? {
        var found: Activity? = null
        instrumentation.runOnMainSync { found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull { it is MainActivity } }
        return found
    }

    /** The window's width in dp and whether it shares the screen, read on the main thread. */
    private fun window(activity: Activity): Pair<Int, Boolean> {
        var reading = 0 to false
        instrumentation.runOnMainSync {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            reading = (bounds.width() / activity.resources.displayMetrics.density).toInt() to activity.isInMultiWindowMode
        }
        return reading
    }

    /** Whether two panes show, once the list is up and two readings in a row agree. */
    private fun twoPanes(): Boolean {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(names.first()).fetchSemanticsNodes().isNotEmpty() }
        var last: Boolean? = null
        var stable: Boolean? = null
        compose.waitUntil(15_000) {
            val reading = compose.onAllNodesWithText(context.getString(R.string.home_pick_app)).fetchSemanticsNodes().isNotEmpty()
            if (reading == last) stable = reading
            last = reading
            Thread.sleep(300)
            stable != null
        }
        return requireNotNull(stable)
    }

    @Test
    fun halfTheDellsScreenInSplitScreenShowsOnePane() {
        device.executeShellCommand("am start -W -n com.android.settings/.Settings")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var task = 0
            scenario.onActivity { task = it.taskId }
            device.executeShellCommand("dumpsys activity service SystemUIService WMShell splitscreen moveToSideStage $task 1")
            compose.waitUntil(15_000) { resumed()?.let { window(it).second } == true }
            val (widthDp, sharing) = window(requireNotNull(resumed()))
            assertTrue("split screen puts Shrimpl in multi-window mode", sharing)
            assertTrue("half the Dell's screen is about 849 dp, not $widthDp", abs(widthDp - 849) <= 12)
            assertEquals("panes in half the Dell's screen", false, twoPanes())
        }
    }

    /**
     * Opens Shrimpl in a freeform window. Android sometimes opens it full screen all the same (it did
     * for the second launch of a run), so a full-screen window is closed with its task and opened again.
     */
    private fun openFreeform(): Activity {
        repeat(3) {
            device.executeShellCommand("am start -W --windowingMode 5 -n ${context.packageName}/.MainActivity")
            compose.waitUntil(15_000) { resumed() != null }
            val activity = requireNotNull(resumed())
            if (window(activity).second) return activity
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
            Thread.sleep(2_000)
        }
        throw AssertionError("Android opened Shrimpl full screen three times instead of in a freeform window")
    }

    /** Opens Shrimpl in a freeform window [widthDp] wide on the Dell's screen and returns whether two panes show. */
    private fun freeformPanes(widthDp: Int): Boolean {
        val activity = openFreeform()
        try {
            val widthPx = (widthDp * 1.5f).toInt()
            device.executeShellCommand("am task resize ${activity.taskId} 100 100 ${100 + widthPx} 1300")
            // Record what the window reports while it resizes, so a miss says what it saw.
            val seen = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                val reading = resumed()?.let { open ->
                    var configDp = 0
                    instrumentation.runOnMainSync { configDp = open.resources.configuration.screenWidthDp }
                    window(open).first to configDp
                }
                seen += "metrics=${reading?.first} config=${reading?.second}"
                if (reading != null && abs(reading.first - widthDp) <= 2) break
                Thread.sleep(500)
            }
            val bounds = device.executeShellCommand("dumpsys activity activities").lines()
                .filter { "shrimpl" in it && ("Task{" in it || "bounds" in it.lowercase()) }.take(6)
            val (actualDp, sharing) = window(requireNotNull(resumed()) { "no MainActivity resumed; seen ${seen.takeLast(5)}" })
            assertTrue("the window is $widthDp dp wide, not $actualDp; seen ${seen.distinct().takeLast(6)}; tasks $bounds", abs(actualDp - widthDp) <= 2)
            assertTrue("a freeform window is in multi-window mode", sharing)
            return twoPanes()
        } finally {
            // Remove the task too: a later launch would reuse it, and a reused task keeps its windowing mode.
            resumed()?.let { open -> instrumentation.runOnMainSync { open.finishAndRemoveTask() } }
        }
    }

    @Test
    fun aSharedWindowJustUnder1000DpShowsOnePane() {
        // The 2880 px Googlebooks snapped at the Dell's scale: about 956 dp, one pane (docs/GOOGLEBOOKS.md).
        assertEquals("panes in a shared 956 dp window", false, freeformPanes(956))
    }

    @Test
    fun aSharedWindowFrom1000DpShowsTwoPanes() {
        assertEquals("panes in a shared 1100 dp window", true, freeformPanes(1100))
    }
}
