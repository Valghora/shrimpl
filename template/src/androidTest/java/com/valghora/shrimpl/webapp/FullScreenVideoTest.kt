package com.valghora.shrimpl.webapp

import android.view.ViewGroup
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A playing video going full screen (docs/TEMPLATE_SPEC.md T6.13), the way a site's own player does it:
 * a tap asks for full screen; the template shows the page's full-screen view over the window with the
 * system bars hidden, the video keeps playing, and Back leaves (T5.6: full-screen video takes Back first).
 *
 * The emulator image has no video, so the page makes one: it records an animated canvas with
 * MediaRecorder into a WebM file and checks WebView decodes it, then plays the canvas as a live stream,
 * which never ends, for the full-screen part (a recorded clip has no duration to loop on).
 */
@RunWith(AndroidJUnit4::class)
class FullScreenVideoTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Records [seconds] of an animated canvas as WebM and plays it once in #v, so WebView decodes a real
     * encoded video; resolves with "decoded" once it has played a while. The canvas keeps animating
     * afterwards, for [playLive].
     */
    private fun recordAndDecode(seconds: Int) = """
        const canvas = document.createElement('canvas'); canvas.width = 320; canvas.height = 180;
        window.__canvas = canvas;
        const g = canvas.getContext('2d');
        let frame = 0;
        const draw = () => { g.fillStyle = 'hsl(' + (frame++ * 7 % 360) + ',70%,50%)'; g.fillRect(0, 0, 320, 180);
            g.fillStyle = '#fff'; g.fillRect(frame * 4 % 320, 70, 40, 40); requestAnimationFrame(draw); };
        draw();
        const recorder = new MediaRecorder(canvas.captureStream(30), { mimeType: 'video/webm;codecs=vp8' });
        const parts = [];
        recorder.ondataavailable = e => parts.push(e.data);
        const stopped = new Promise(done => { recorder.onstop = done; });
        recorder.start();
        await new Promise(done => setTimeout(done, ${seconds * 1000}));
        recorder.stop(); await stopped;
        const v = document.getElementById('v');
        v.src = URL.createObjectURL(new Blob(parts, { type: 'video/webm' }));
        await v.play();
        const start = v.currentTime;
        await new Promise(done => setTimeout(done, 1000));
        window.__r = v.currentTime > start ? 'decoded' : 'stuck at ' + v.currentTime;
    """.trimIndent().replace("\n", " ")

    /**
     * Plays the still-animating canvas in #v as a live stream: it never ends, so the video plays for as
     * long as the test needs, unlike the short recorded clip.
     */
    private val playLive = """
        const v = document.getElementById('v');
        v.removeAttribute('src');
        v.srcObject = window.__canvas.captureStream(30);
        await v.play();
        window.__r = 'playing';
    """.trimIndent().replace("\n", " ")

    /** #v's state, for a failure message. */
    private fun PageDriver.videoState() =
        js("(v => 'paused=' + v.paused + ' ended=' + v.ended + ' time=' + v.currentTime + ' duration=' + v.duration + ' ready=' + v.readyState)(document.getElementById('v'))")

    /** Whether #v's playback position moves over half a second. */
    private fun PageDriver.videoAdvances(): Boolean {
        val before = js("document.getElementById('v').currentTime").toDouble()
        Thread.sleep(600)
        val after = js("document.getElementById('v').currentTime").toDouble()
        return after > before
    }

    @Test
    fun aPlayingVideoGoesFullScreenKeepsPlayingAndBackLeaves() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
            val page = PageDriver(scenario).also {
                it.loadTestPage("<video id=\"v\" muted playsinline style=\"display:block;width:100%;height:240px;background:#000\"></video>")
            }
            assertEquals("decoded", page.runAsync(recordAndDecode(3), timeoutMs = 60_000))
            assertEquals("playing", page.runAsync(playLive, timeoutMs = 30_000))
            assertTrue("the video doesn't play: ${page.videoState()}", page.videoAdvances())

            var decorChildren = 0
            scenario.onActivity { decorChildren = (it.window.decorView as ViewGroup).childCount }
            fun statusBarShown(): Boolean {
                var shown = true
                scenario.onActivity { shown = it.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) ?: true }
                return shown
            }

            // A site's player asks for full screen from the user's tap.
            page.js("document.getElementById('v').addEventListener('click', e => e.target.requestFullscreen()); 0")
            page.tap("#v")
            page.awaitJs("document.fullscreenElement === document.getElementById('v')", "the video to go full screen", 10_000)
            awaitUntil("the template to show the full-screen view over the window", 10_000) {
                var children = 0
                scenario.onActivity { children = (it.window.decorView as ViewGroup).childCount }
                children > decorChildren
            }
            awaitUntil("the system bars to hide", 10_000) { !statusBarShown() }
            assertTrue("the video stopped playing in full screen: ${page.videoState()}", page.videoAdvances())

            // Back leaves full screen first, before going back in the page or leaving the app.
            fun state(): String {
                var children = 0
                scenario.onActivity { children = (it.window.decorView as ViewGroup).childCount }
                return "templateView=${children > decorChildren} pageFullScreen=${page.js("document.fullscreenElement !== null")} barsShown=${statusBarShown()}"
            }
            device.pressBack()
            Thread.sleep(3_000)
            val afterSystemBack = state()
            if (page.js("document.fullscreenElement !== null") == "true") {
                // A control: the template's own Back handling, called directly, tells a Back that never
                // arrived from one that arrived and didn't leave.
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                Thread.sleep(3_000)
                throw AssertionError("Back didn't leave full screen. After the system's Back: $afterSystemBack. " +
                    "After the template's back handling was called directly: ${state()}")
            }
            awaitUntil("the full-screen view to go", 10_000) {
                var children = 0
                scenario.onActivity { children = (it.window.decorView as ViewGroup).childCount }
                children == decorChildren
            }
            awaitUntil("the system bars to come back", 10_000) { statusBarShown() }
            assertEquals("Back left the page instead of full screen", "true", page.js("document.getElementById('marker') !== null"))
        }
    }
}
