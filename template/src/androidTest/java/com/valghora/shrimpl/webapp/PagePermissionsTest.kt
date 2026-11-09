package com.valghora.shrimpl.webapp

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A page asking for the microphone and for location (docs/TEMPLATE_SPEC.md T6.5), with a profile that
 * allows them ([profileAllowingEverything]) and with the neutral one, which allows neither.
 *
 * The microphone goes through all three steps of the template's flow: Android asks directly; after one
 * "Don't allow", the template explains first; after a second, the page is turned down with a snackbar.
 * It is the microphone because the camera was already granted earlier in the run (FileChooserCameraTest),
 * and a granted permission can't be taken back without ending the test process.
 */
@RunWith(AndroidJUnit4::class)
class PagePermissionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun PageDriver.askForMicrophone() =
        js("window.__mic = undefined; navigator.mediaDevices.getUserMedia({ audio: true })" +
            ".then(s => { s.getTracks().forEach(t => t.stop()); window.__mic = 'granted'; }, e => { window.__mic = 'error:' + e.name; }); 0")

    private fun PageDriver.microphoneAnswer(): String {
        awaitJs("window.__mic !== undefined", "the page's microphone request to be answered", 20_000)
        return js("window.__mic")
    }

    @Test
    fun theMicrophoneIsAskedForThenExplainedThenTurnedDown() = withProfile(profileAllowingEverything()) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
            val page = PageDriver(scenario).also { it.loadTestPage() }

            // 1. The first time, Android asks directly.
            page.askForMicrophone()
            assertTrue("Android didn't ask for the microphone", answerPermissionPrompt("^don.t allow$"))
            assertEquals("error:NotAllowedError", page.microphoneAnswer())

            // 2. After one "Don't allow", the template explains first; Continue brings Android's prompt back.
            page.askForMicrophone()
            assertTrue("the template didn't explain first", awaitText(context.getString(R.string.permission_title_microphone)))
            tapText(context.getString(R.string.action_continue))
            assertTrue("Android didn't ask again after Continue", answerPermissionPrompt("^don.t allow$"))
            assertEquals("error:NotAllowedError", page.microphoneAnswer())

            // 3. Android no longer asks: the page is turned down, and a snackbar says the microphone is off.
            page.askForMicrophone()
            assertEquals("error:NotAllowedError", page.microphoneAnswer())
            assertTrue(
                "no snackbar said the microphone is off",
                awaitText(context.getString(R.string.permission_off_microphone, WebApp.profile.name)),
            )
        }
    }

    private fun PageDriver.locate(): String {
        js("window.__loc = undefined; navigator.geolocation.getCurrentPosition(" +
            "() => { window.__loc = 'position'; }, e => { window.__loc = 'error:' + e.code; }, { timeout: 8000 }); 0")
        awaitJs("window.__loc !== undefined", "the page's location request to be answered", 20_000)
        return js("window.__loc")
    }

    @Test
    fun locationIsGivenOnlyWhenTheProfileAllowsIt() {
        // PERMISSION_DENIED is error code 1; a position, or a timeout on an emulator without a fix, means it was allowed.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000)
            val page = PageDriver(scenario).also { it.loadTestPage() }
            assertEquals("the neutral profile doesn't allow location", "error:1", page.locate())
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        withProfile(profileAllowingEverything()) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val page = PageDriver(scenario).also { it.loadTestPage() }
                assertNotEquals("a profile that allows location was turned down", "error:1", page.locate())
            }
        }
    }
}
