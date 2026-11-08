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
import java.security.MessageDigest
import kotlin.concurrent.thread
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
        device.executeShellCommand("pm uninstall $packageName")
        runBlocking { container.store.delete(profile.appId) }
    }

    /** Taps Install on the system installer's confirmation screen when it appears; a first install needs it. */
    private fun acceptInstallDialog() = thread(name = "install-confirmer") {
        val install = device.wait(Until.findObject(By.text("Install").clickable(true)), 120_000)
        install?.click()
    }

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

    @Test
    fun shrimplBuildsInstallsAndLaunchesAWebApp() {
        try {
            assertInternet()
            // A user allows "Install unknown apps" for Shrimpl once in system settings; the test does it directly.
            device.executeShellCommand("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")
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
            assertTrue("the web app never showed Example Domain", device.wait(Until.hasObject(By.textContains("Example Domain")), 90_000))
        } catch (e: Throwable) {
            Evidence.capture("build-and-install")
            throw e
        }
    }
}
