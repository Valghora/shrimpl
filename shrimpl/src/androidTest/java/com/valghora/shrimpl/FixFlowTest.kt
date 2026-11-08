package com.valghora.shrimpl

import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.valghora.shrimpl.build.BuildOutcome
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.core.build.SigningKey
import com.valghora.shrimpl.core.build.SigningKeys
import com.valghora.shrimpl.core.build.WebAppBuilder
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.install.SessionInstaller
import java.security.MessageDigest
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
 * Fix (docs/SHRIMPL_SPEC.md R11.7) with the real installer: an installed web app signed with a key
 * Shrimpl no longer has can't be updated, so Home offers Fix, which uninstalls it (the system asks) and
 * builds it again from its saved settings with the current key (the system asks again). The test
 * changes the key as importing a backup of another one would, taps Fix, answers the system's screens, and
 * checks the app that comes back is signed with the new key and recorded so.
 */
@RunWith(AndroidJUnit4::class)
class FixFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val robot = InstallRobot(device)
    private val container get() = (context.applicationContext as ShrimplApp).container

    private val profile = Profile(
        name = "CI Fixme",
        appId = "cifixme",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#3367D6",
    )
    private val packageName = WebAppBuilder.packageNameFor(profile.appId)
    private var originalKey: SigningKey? = null

    @After
    fun cleanUp() {
        (container.installers.preferred() as? SessionInstaller)?.cancelConfirmation()
        device.executeShellCommand("pm uninstall $packageName")
        runBlocking { container.store.delete(profile.appId) }
        // Other tests expect the key they started with.
        originalKey?.let { container.keys.replace(it) }
    }

    private fun signerOf(pkg: String): String? = try {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        MessageDigest.getInstance("SHA-256").digest(requireNotNull(info.signingInfo).apkContentsSigners.single().toByteArray())
            .joinToString(":") { "%02X".format(it) }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Test
    fun fixReplacesAnAppSignedWithAnotherKey() {
        try {
            robot.allowInstalls(context.packageName)
            val current = container.keys.getOrCreate().also { originalKey = it }

            // An app signed with the key Shrimpl has now.
            val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter('C'))
            val installing = robot.acceptSystemDialogs()
            val built = runBlocking { withTimeout(150_000) { container.factory.build(BuildRequest(profile, icons, emptyMap(), existing = null), container.installers.preferred()) {} } }
            installing.interrupt()
            assertTrue("the first install ended with $built", built is BuildOutcome.Installed)
            assertEquals(current.fingerprint, signerOf(packageName))

            // The user imports a backup of a different key: the installed app is now signed with one Shrimpl doesn't have.
            val other = SigningKeys.generate()
            container.keys.replace(other)
            compose.activityRule.scenario.recreate()
            val fix = context.getString(R.string.action_fix_app, profile.name)
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription(fix).fetchSemanticsNodes().isNotEmpty() }

            // Fix: the system asks to uninstall, then to install the rebuilt app.
            compose.onNodeWithContentDescription(fix).performClick()
            val fixing = robot.acceptSystemDialogs()
            awaitUntil("the app to be signed with the new key", 180_000) { signerOf(packageName) == other.fingerprint }
            fixing.interrupt()

            val record = container.store.get(profile.appId)
            assertNotNull("the record survived the fix", record)
            assertEquals(other.fingerprint, record?.signerFingerprint)
        } catch (e: Throwable) {
            Evidence.capture("fix-flow")
            throw e
        }
    }
}
