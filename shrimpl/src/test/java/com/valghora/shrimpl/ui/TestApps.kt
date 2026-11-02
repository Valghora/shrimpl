package com.valghora.shrimpl.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ShrimplApp
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Two saved web apps for screen tests: Example is installed, Squoosh isn't. Tests run without a
 * signing key (Robolectric has no Android Keystore), so both count as signed with another key
 * (R11.7): Example offers Fix, Squoosh Rebuild.
 */
internal object TestApps {
    val app: ShrimplApp get() = RuntimeEnvironment.getApplication() as ShrimplApp

    val example = record("Example")
    val squoosh = record("Squoosh")

    private fun record(name: String): WebAppRecord {
        val id = name.lowercase()
        val profile = Profile(name = name, appId = id, startUrl = "https://$id.example/", internalDomains = listOf("$id.example"), iconBackground = "#3367D6")
        val icons = IconOptions(IconOptions.Option.WEBSITE, website = IconRecord.letter(name.first()))
        return WebAppRecord.create(profile, icons, templateVersion = 1, signerFingerprint = "AA:BB", now = 0L)
    }

    /** Saves both apps and fakes Example's install. */
    fun save() {
        runBlocking {
            app.container.store.save(example, emptyMap())
            app.container.store.save(squoosh, emptyMap())
        }
        val installed = PackageInfo().apply {
            packageName = example.packageName
            applicationInfo = ApplicationInfo().apply { packageName = example.packageName }
        }
        shadowOf(app.packageManager).installPackage(installed)
        // Robolectric needs an icon registered for an installed package; Shrimpl reads it for the row.
        shadowOf(app.packageManager).setApplicationIcon(example.packageName, ColorDrawable(Color.BLUE))
    }

    fun string(id: Int, vararg args: Any): String = app.getString(id, *args)

    /**
     * Home reads the installed apps and the signing key off the main thread; waits until Example
     * shows as installed (its Fix button) before a test checks anything.
     */
    fun ComposeTestRule.awaitHome() = waitUntil(LOAD_TIMEOUT_MS) {
        onAllNodes(hasContentDescription(string(R.string.action_fix_app, "Example"))).fetchSemanticsNodes().isNotEmpty()
    }

    private const val LOAD_TIMEOUT_MS = 5_000L
}
