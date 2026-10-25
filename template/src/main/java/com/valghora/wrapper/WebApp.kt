package com.valghora.wrapper

import android.app.Application
import android.content.Context
import com.valghora.wrapper.core.profile.Profile
import com.valghora.wrapper.core.profile.ProfileCodec
import com.valghora.wrapper.core.profile.ProfileException
import java.io.FileNotFoundException

/** Reads this web app's profile once, before any activity starts (docs/UNIWRAP_SPEC.md R7.1). */
class WebApp : Application() {
    override fun onCreate() {
        super.onCreate()
        profile = loadProfile(this)
    }

    companion object {
        const val PROFILE_ASSET = "uniwrap/profile.json"

        /** The site this app wraps. */
        lateinit var profile: Profile
            private set

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
