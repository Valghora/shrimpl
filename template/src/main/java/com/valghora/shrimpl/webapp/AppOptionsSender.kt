package com.valghora.shrimpl.webapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.bundleOf
import com.valghora.shrimpl.core.AdBlocking
import com.valghora.shrimpl.core.DarkMode
import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.adblock.FilterList
import com.valghora.shrimpl.core.records.AppOptionsSync
import com.valghora.shrimpl.core.records.LiveSettings
import java.util.concurrent.Executors

/**
 * The app's link to the Shrimpl that built it: tells it what the App options panel changed
 * (docs/TEMPLATE_SPEC.md T5.8), so the app's edit page in Shrimpl shows it (docs/SHRIMPL_SPEC.md R11.9),
 * and asks it for the live settings saved there (T5.9, R11.10). The app doesn't know which
 * Shrimpl built it (a release and a debug build can both be installed), so it asks each one, and only
 * the builder saves it: each checks the caller against its own records. Without Shrimpl the choice still
 * holds in the app until its next build.
 */
class AppOptionsSender(private val context: Context) {
    private val io = Executors.newSingleThreadExecutor()

    /** Sends the switches that changed; null leaves a setting as it is. Off the main thread, never failing. */
    fun send(
        desktopSite: Boolean? = null,
        linkMenu: Boolean? = null,
        adBlocking: AdBlocking? = null,
        darkMode: DarkMode? = null,
        appBanners: Boolean? = null,
    ) {
        val extras = bundleOf().apply {
            desktopSite?.let { putBoolean(AppOptionsSync.KEY_DESKTOP_SITE, it) }
            linkMenu?.let { putBoolean(AppOptionsSync.KEY_LINK_MENU, it) }
            adBlocking?.let { putString(AppOptionsSync.KEY_AD_BLOCKING, it.name) }
            darkMode?.let { putString(AppOptionsSync.KEY_DARK_MODE, it.name) }
            appBanners?.let { putBoolean(AppOptionsSync.KEY_APP_BANNERS, it) }
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

    /** Shrimpl's answer to [fetch]: the live settings, who answered, and the versions of its lists (R15.3). */
    class Answer(val settings: LiveSettings, val authority: String, val listVersions: Map<FilterList, Long>)

    /**
     * Asks the Shrimpl that built this app for its live settings (T5.9) and hands them to [onAnswer]
     * on the main thread; without an answer nothing happens. One thread for both directions, so a
     * change sent when the panel closed is saved before the settings are read back.
     */
    fun fetch(onAnswer: (Answer) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        io.execute {
            val providers = context.packageManager.queryIntentContentProviders(
                Intent(AppOptionsSync.ACTION),
                PackageManager.ResolveInfoFlags.of(0),
            )
            val answer = providers.mapNotNull { it.providerInfo?.authority }.firstNotNullOfOrNull { authority ->
                try {
                    context.contentResolver.call(authority, AppOptionsSync.METHOD_SETTINGS, null, null)?.let { bundle ->
                        liveSettings(bundle)?.let { Answer(it, authority, listVersions(bundle)) }
                    }
                } catch (e: SecurityException) {
                    Log.w(WebApp.profile.appId, "Shrimpl at $authority refused live settings: ${e.message}")
                    null
                } catch (e: IllegalArgumentException) {
                    Log.w(WebApp.profile.appId, "Shrimpl at $authority is gone: ${e.message}")
                    null
                }
            }
            if (answer != null) main.post { onAnswer(answer) }
        }
    }

    /** The settings in Shrimpl's answer, or null when it isn't complete. */
    private fun liveSettings(answer: Bundle): LiveSettings? {
        val keys = listOf(AppOptionsSync.KEY_LINK_MENU, AppOptionsSync.KEY_KEEP_ALIVE, AppOptionsSync.KEY_PAUSE_POWER_SAVE)
        if (!keys.all(answer::containsKey)) return null
        val mode = UaMode.entries.firstOrNull { it.name == answer.getString(AppOptionsSync.KEY_USER_AGENT_MODE) } ?: return null
        return LiveSettings(
            userAgentMode = mode,
            nativeContextMenu = answer.getBoolean(AppOptionsSync.KEY_LINK_MENU),
            keepAliveWhenHidden = answer.getBoolean(AppOptionsSync.KEY_KEEP_ALIVE),
            pauseOnPowerSave = answer.getBoolean(AppOptionsSync.KEY_PAUSE_POWER_SAVE),
            adBlocking = AdBlocking.entries.firstOrNull { it.name == answer.getString(AppOptionsSync.KEY_AD_BLOCKING) } ?: AdBlocking.OFF,
            // A Shrimpl from before dark mode doesn't send it: the app follows the device.
            darkMode = DarkMode.entries.firstOrNull { it.name == answer.getString(AppOptionsSync.KEY_DARK_MODE) } ?: DarkMode.SYSTEM,
            // An older Shrimpl doesn't send it either: no app banners hidden.
            hideAppBanners = answer.getBoolean(AppOptionsSync.KEY_APP_BANNERS, false),
        )
    }

    /** The version of each list the answer names; a list Shrimpl has no copy of yet is 0. */
    private fun listVersions(answer: Bundle): Map<FilterList, Long> =
        FilterList.entries.associateWith { answer.getLong(AppOptionsSync.KEY_LIST_VERSION + it.id, 0L) }

    /** Stops the sender's thread once the activity is gone. */
    fun shutdown() = io.shutdown()
}
