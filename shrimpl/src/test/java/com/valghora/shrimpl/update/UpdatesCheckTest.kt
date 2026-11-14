package com.valghora.shrimpl.update

import androidx.test.core.app.ApplicationProvider
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ShrimplApp
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * docs/SHRIMPL_SPEC.md R14.1: what a check makes of GitHub's answers, with a fake GitHub. This build is
 * code 102 (1.0.2) here, so 1.0.3 is newer and 1.0.2 isn't.
 */
@RunWith(RobolectricTestRunner::class)
class UpdatesCheckTest {
    private val app = ApplicationProvider.getApplicationContext<ShrimplApp>()

    private fun answer(tag: String) = """
        {"tag_name": "$tag", "html_url": "https://github.com/Valghora/shrimpl/releases/tag/$tag", "assets": [
          {"name": "shrimpl-${tag.removePrefix("v")}.apk", "browser_download_url": "https://example.com/a.apk"},
          {"name": "shrimpl-${tag.removePrefix("v")}.apk.sha256", "browser_download_url": "https://example.com/a.apk.sha256"}]}
    """.trimIndent()

    private fun updates(fetch: (String) -> Updates.Fetched) =
        Updates(app, app.container.installers, installedCode = 102, debug = false, fetch = fetch)

    private fun check(fetch: (String) -> Updates.Fetched) = runBlocking { updates(fetch).check() }

    private fun ok(body: String) = Updates.Fetched(200, body.toByteArray())

    @Test
    fun aNewerReleaseIsAvailable() {
        val result = check { ok(answer("v1.0.3")) }
        assertTrue(result.toString(), result is UpdateResult.Available && result.release.code == 103)
    }

    @Test
    fun theSameOrAnOlderReleaseIsUpToDate() {
        assertEquals(UpdateResult.UpToDate, check { ok(answer("v1.0.2")) })
        assertEquals(UpdateResult.UpToDate, check { ok(answer("v1.0.1")) })
    }

    @Test
    fun eachFailureSaysWhy() {
        assertEquals(UpdateResult.Failed(app.getString(R.string.update_error_no_release)), check { Updates.Fetched(404, ByteArray(0)) })
        assertEquals(UpdateResult.Failed(app.getString(R.string.update_error_http, 500)), check { Updates.Fetched(500, ByteArray(0)) })
        assertEquals(UpdateResult.Failed(app.getString(R.string.update_error_unreachable)), check { throw IOException("offline") })
        assertEquals(UpdateResult.Failed(app.getString(R.string.update_error_no_apk)), check { ok("""{"tag_name": "v1.0.3", "assets": []}""") })
    }

    @Test
    fun whatACheckFoundSurvivesARestart() {
        val first = updates { ok(answer("v1.0.3")) }
        runBlocking { first.check() }
        val again = updates { error("no fetch on load") }
        assertEquals(first.status.value.result, again.status.value.result)
        assertEquals(first.status.value.lastChecked, again.status.value.lastChecked)
    }

    @Test
    fun aDismissedReleaseHasNoNoticeUntilANewerOne() {
        val updates = updates { ok(answer("v1.0.3")) }
        runBlocking { updates.check() }
        val found = (updates.status.value.result as UpdateResult.Available).release
        updates.dismiss(found)
        assertEquals(null, updates.status.value.notice)
        val newer = updates { ok(answer("v1.0.4")) }
        runBlocking { newer.check() }
        assertEquals("1.0.4", newer.status.value.notice?.version)
    }

    @Test
    fun aDebugBuildDoesNotInstallARelease() {
        val updates = Updates(app, app.container.installers, installedCode = 102, debug = true, fetch = { ok(answer("v1.0.3")) })
        runBlocking { updates.check() }
        updates.install((updates.status.value.result as UpdateResult.Available).release)
        ShadowLooper.idleMainLooper()
        assertEquals(app.getString(R.string.update_error_debug), updates.status.value.installError)
    }

    @Test
    fun aDownloadThatDoesNotMatchItsChecksumIsNotInstalled() {
        val sums = "0".repeat(64) + "  shrimpl-1.0.3.apk"
        val updates = updates { url ->
            when {
                url.endsWith(".sha256") -> ok(sums)
                url.endsWith(".apk") -> ok("not the right bytes")
                else -> ok(answer("v1.0.3"))
            }
        }
        runBlocking { updates.check() }
        updates.install((updates.status.value.result as UpdateResult.Available).release)
        val deadline = System.currentTimeMillis() + 5_000
        while (updates.status.value.installError == null && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals(app.getString(R.string.update_error_checksum), updates.status.value.installError)
    }
}
