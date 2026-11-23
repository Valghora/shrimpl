package com.valghora.shrimpl.webapp

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.LiveSettings
import com.valghora.shrimpl.core.profile.ProfileCodec
import com.valghora.shrimpl.core.profile.ProfileException
import java.io.FileNotFoundException

/**
 * Reads this web app's profile once, before any activity starts (docs/SHRIMPL_SPEC.md R7.1), and gives
 * every activity the user's Material You colors (T3.1).
 */
class WebApp : Application() {
    override fun onCreate() {
        super.onCreate()
        profile = loadProfile(this)
        // Without this, menus, dialogs and the App options panel use Material's baseline purple, not the
        // wallpaper colors Shrimpl and the header already use.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    companion object {
        const val PROFILE_ASSET = "shrimpl/profile.json"

        /** The site this app wraps. */
        lateinit var profile: Profile
            private set

        /**
         * Takes the live settings Shrimpl has now (docs/TEMPLATE_SPEC.md T5.9); everything else stays as
         * this build's profile has it.
         */
        fun takeLiveSettings(settings: LiveSettings) {
            profile = settings.applyTo(profile)
        }

        /** Fails loudly: an app without a valid profile cannot do anything useful. */
        private fun loadProfile(context: Context): Profile = try {
            context.assets.open(PROFILE_ASSET).bufferedReader().use { ProfileCodec.decode(it.readText()) }
        } catch (e: FileNotFoundException) {
            throw IllegalStateException("This APK has no $PROFILE_ASSET", e)
        } catch (e: ProfileException) {
            throw IllegalStateException("$PROFILE_ASSET in this APK is invalid: ${e.message}", e)
        }
    }
}
