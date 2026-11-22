package com.valghora.shrimpl.webapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.os.bundleOf
import com.valghora.shrimpl.core.records.AppOptionsSync
import java.util.concurrent.Executors

/**
 * Tells the Shrimpl that built this app what its App options panel changed (docs/TEMPLATE_SPEC.md T5.8),
 * so the app's edit page in Shrimpl shows it (docs/SHRIMPL_SPEC.md R11.9). The app doesn't know which
 * Shrimpl built it (a release and a debug build can both be installed), so it asks each one, and only
 * the builder saves it: each checks the caller against its own records. Without Shrimpl the choice still
 * holds in the app until its next build.
 */
class AppOptionsSender(private val context: Context) {
    private val io = Executors.newSingleThreadExecutor()

    /** Sends the switches that changed; null leaves a setting as it is. Off the main thread, never failing. */
    fun send(desktopSite: Boolean? = null, linkMenu: Boolean? = null) {
        val extras = bundleOf().apply {
            desktopSite?.let { putBoolean(AppOptionsSync.KEY_DESKTOP_SITE, it) }
            linkMenu?.let { putBoolean(AppOptionsSync.KEY_LINK_MENU, it) }
        }
        io.execute {
            val providers = context.packageManager.queryIntentContentProviders(
                Intent(AppOptionsSync.ACTION),
                PackageManager.ResolveInfoFlags.of(0),
            )
            val saved = providers.mapNotNull { it.providerInfo?.authority }.any { authority ->
                try {
                    context.contentResolver.call(authority, AppOptionsSync.METHOD, null, extras)
                        ?.getBoolean(AppOptionsSync.KEY_SAVED) == true
                } catch (e: SecurityException) {
                    Log.w(WebApp.profile.appId, "Shrimpl at $authority refused App options: ${e.message}")
                    false
                } catch (e: IllegalArgumentException) {
                    Log.w(WebApp.profile.appId, "Shrimpl at $authority is gone: ${e.message}")
                    false
                }
            }
            if (!saved) Log.i(WebApp.profile.appId, "No Shrimpl saved the App options change; it holds in this build")
        }
    }

    /** Stops the sender's thread once the activity is gone. */
    fun shutdown() = io.shutdown()
}
