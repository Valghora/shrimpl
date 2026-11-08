package com.valghora.shrimpl

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.valghora.shrimpl.build.BuildOutcome
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.core.build.WebAppBuilder
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.install.SessionInstaller
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole point of Shrimpl, on a real Android system (docs/SHRIMPL_SPEC.md R6, R8, R11): build a web
 * app from the bundled template, sign it with Shrimpl's key, install it through the system installer
 * (the test taps Install, as a user does), find it signed and recorded, launch it, and see the site.
 * Until now only the product owner could check this, by hand.
 */
@RunWith(AndroidJUnit4::class)
class BuildAndInstallTest {
    // Shrimpl stays in front, as it is when a user builds an app: the installer's screen opens over it.
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = (context.applicationContext as ShrimplApp).container

    private val profile = Profile(
        name = "CI Example",
        appId = "ciexample",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#3367D6",
    )
    private val packageName = WebAppBuilder.packageNameFor(profile.appId)

    @After
    fun cleanUp() {
        // A confirmation nobody answered would otherwise block the next test's install.
        (container.installers.preferred() as? SessionInstaller)?.cancelConfirmation()
        device.executeShellCommand("pm uninstall $packageName")
        runBlocking { container.store.delete(profile.appId) }
    }

    private val robot = InstallRobot(device)

    private fun acceptInstallDialog() = robot.acceptSystemDialogs()

    private fun fingerprintOf(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { "%02X".format(it) }

    /** The emulator reaches the internet: without it the failure would read as a bug in the web app. */
    private fun assertInternet() {
        val code = runCatching {
            (java.net.URL("https://example.com/").openConnection() as java.net.HttpURLConnection).run {
                connectTimeout = 15_000
                readTimeout = 15_000
                responseCode
            }
        }
        assertTrue("the emulator can't reach https://example.com/: $code", code.getOrNull() == 200)
    }

    /**
     * Waits for [text] on screen, tapping Allow when Android asks whether the web app may send
     * notifications: it asks on a fresh launch (docs/TEMPLATE_SPEC.md T7.5), over the page, as it does for a user.
     */
    private fun awaitPageAllowingNotifications(text: String, timeoutMs: Long = 120_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.textContains(text))) return true
            device.findObject(By.text(Pattern.compile("(?i)^allow$")).clickable(true))?.click()
            Thread.sleep(1_000)
        }
        return false
    }

    /**
     * Rebuilding an installed web app with the same key installs over it (R6.1, R11.4): this is how Update
     * and Update all work. Android asks the user to confirm each update (R2.4), so the test taps Update.
     */
    @Test
    fun anInstalledAppIsUpdatedInPlace() {
        try {
            robot.allowInstalls(context.packageName)
            val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter('C'))
            val confirmer = acceptInstallDialog()
            val first = runBlocking { withTimeout(240_000) { container.factory.build(BuildRequest(profile, icons, emptyMap(), existing = null), container.installers.preferred()) {} } }
            confirmer.interrupt()
            assertTrue("the first install ended with $first", first is BuildOutcome.Installed)
            val installed = (first as BuildOutcome.Installed).record
            fun versionCode() = context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode

            val before = versionCode()
            val updater = acceptInstallDialog()
            val second = runBlocking { withTimeout(180_000) { container.factory.build(BuildRequest(profile, icons, emptyMap(), existing = installed), container.installers.preferred()) {} } }
            updater.interrupt()
            assertTrue("the update ended with $second", second is BuildOutcome.Installed)
            assertTrue("the version code went from $before to ${versionCode()}", versionCode() > before)
            assertEquals(installed.nextBuildNumber, (second as BuildOutcome.Installed).record.buildNumber)
        } catch (e: Throwable) {
            Evidence.capture("update-in-place")
            throw e
        }
    }

    @Test
    fun shrimplBuildsInstallsAndLaunchesAWebApp() {
        try {
            assertInternet()
            // A user allows "Install unknown apps" for Shrimpl once in system settings; the test does it directly.
            robot.allowInstalls(context.packageName)
            val request = BuildRequest(profile, IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter('C')), emptyMap(), existing = null)
            val confirmer = acceptInstallDialog()
            val outcome = runBlocking { withTimeout(240_000) { container.factory.build(request, container.installers.preferred()) {} } }
            confirmer.interrupt()

            assertTrue("the build ended with $outcome", outcome is BuildOutcome.Installed)
            val record = (outcome as BuildOutcome.Installed).record
            assertEquals(packageName, record.packageName)
            assertNotNull("the record was saved", container.store.get(profile.appId))

            // Installed, and signed with Shrimpl's key (R5): the signer is the one the record names.
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            val signer = fingerprintOf(requireNotNull(info.signingInfo).apkContentsSigners.single().toByteArray())
            assertEquals(requireNotNull(container.keys.current()).fingerprint, signer)
            assertEquals(signer, record.signerFingerprint)

            // It launches and loads its site (R7, T3): the page's heading is on screen.
            val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            assertTrue("the web app didn't come to the front", device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 30_000))
            assertTrue("the web app never showed Example Domain", awaitPageAllowingNotifications("Example Domain"))
        } catch (e: Throwable) {
            Evidence.capture("build-and-install")
            throw e
        }
    }
}
