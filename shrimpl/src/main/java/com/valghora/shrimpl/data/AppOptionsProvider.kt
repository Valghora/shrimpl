package com.valghora.shrimpl.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.bundleOf
import com.valghora.shrimpl.container
import com.valghora.shrimpl.core.AdBlocking
import com.valghora.shrimpl.core.DarkMode
import com.valghora.shrimpl.core.adblock.FilterList
import com.valghora.shrimpl.core.records.AppOptionsSync
import com.valghora.shrimpl.core.records.LiveSettings
import com.valghora.shrimpl.core.records.WebAppRecord
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking

/**
 * A web app's link to the Shrimpl that built it: it saves what the app's App options panel changed to
 * the app's record, so its edit page shows it (docs/SHRIMPL_SPEC.md R11.9), answers the app's live
 * settings, so a change saved on the edit page reaches it without a rebuild (R11.10), and hands it the
 * ad blocking lists it uses (R15.3). The provider is
 * exported, so it answers only the caller itself, about itself: a package of one of Shrimpl's records,
 * signed with the key that record was built with (android-permissions-security skill). It holds no
 * data; [call] is its only entry point.
 */
class AppOptionsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        AppOptionsSync.METHOD -> extras?.let(::saveOptions)
        AppOptionsSync.METHOD_SETTINGS -> liveSettings()
        else -> null
    }

    /** R11.10: the caller's live settings, or nothing when Shrimpl didn't build it. */
    private fun liveSettings(): Bundle? {
        val context = context ?: return null
        val record = callerRecord(context.container.store.records.value) ?: return null
        val settings = LiveSettings.of(record.profile)
        return bundleOf(
            AppOptionsSync.KEY_USER_AGENT_MODE to settings.userAgentMode.name,
            AppOptionsSync.KEY_LINK_MENU to settings.nativeContextMenu,
            AppOptionsSync.KEY_KEEP_ALIVE to settings.keepAliveWhenHidden,
            AppOptionsSync.KEY_PAUSE_POWER_SAVE to settings.pauseOnPowerSave,
            AppOptionsSync.KEY_AD_BLOCKING to settings.adBlocking.name,
            AppOptionsSync.KEY_DARK_MODE to settings.darkMode.name,
        ).apply {
            // R15.3: the version of each list the level uses; 0 until Shrimpl has a copy.
            FilterList.forLevel(settings.adBlocking).forEach { list ->
                putLong(AppOptionsSync.KEY_LIST_VERSION + list.id, context.container.filterLists.version(list))
            }
        }
    }

    /**
     * R15.3: a list's file, read-only, for a caller whose ad blocking uses it. A list is too large for a
     * [call] answer, which Binder caps near 1 MB.
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException(uri.toString())
        val segments = uri.pathSegments
        val list = segments.takeIf { it.size == 2 && it[0] == AppOptionsSync.LIST_PATH }?.let { FilterList.byId(it[1]) }
            ?: throw FileNotFoundException(uri.toString())
        val record = callerRecord(context.container.store.records.value) ?: throw SecurityException("Not an app Shrimpl built")
        if (list !in FilterList.forLevel(record.profile.adBlocking)) throw SecurityException("${list.id} isn't on for this app")
        val file = context.container.filterLists.file(list) ?: throw FileNotFoundException(list.id)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** R11.9: saves the panel's changes to the caller's record. */
    private fun saveOptions(extras: Bundle): Bundle? {
        val context = context ?: return null
        val store = context.container.store
        val record = callerRecord(store.records.value) ?: return bundleOf(AppOptionsSync.KEY_SAVED to false)
        val desktopSite = extras.flag(AppOptionsSync.KEY_DESKTOP_SITE)
        val linkMenu = extras.flag(AppOptionsSync.KEY_LINK_MENU)
        val adBlocking = extras.getString(AppOptionsSync.KEY_AD_BLOCKING)?.let { name -> AdBlocking.entries.firstOrNull { it.name == name } }
        val darkMode = extras.getString(AppOptionsSync.KEY_DARK_MODE)?.let { name -> DarkMode.entries.firstOrNull { it.name == name } }
        val profile = AppOptionsSync.apply(record.profile, desktopSite, linkMenu, adBlocking, darkMode)
        if (profile != record.profile) {
            // A binder thread: blocking it for one small atomic write is fine, and the web app waits off
            // its main thread.
            runBlocking { store.save(record.copy(profile = profile, updatedAt = System.currentTimeMillis()), emptyMap()) }
        }
        return bundleOf(AppOptionsSync.KEY_SAVED to true)
    }

    /** The record of the app calling, if it is one Shrimpl built and still signed with that record's key. */
    private fun callerRecord(records: List<WebAppRecord>): WebAppRecord? {
        val pm = context?.packageManager ?: return null
        val packages = pm.getPackagesForUid(Binder.getCallingUid()).orEmpty().toSet()
        val record = records.firstOrNull { it.packageName in packages } ?: return null
        val info = try {
            pm.getPackageInfo(record.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return record.takeIf { InstalledApps.signerFingerprint(info) == it.signerFingerprint }
    }

    private fun Bundle.flag(key: String): Boolean? = if (containsKey(key)) getBoolean(key) else null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
