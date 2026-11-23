package com.valghora.shrimpl.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.security.MessageDigest

/** What Android says about an installed web app (docs/SHRIMPL_SPEC.md R2.2). */
data class InstalledApp(val packageName: String, val versionCode: Long, val icon: Drawable)

/** Installed web apps, as the package manager reports them. */
object InstalledApps {
    /** The installed app [packageName] with its version code and icon, or null when it isn't installed. */
    fun find(context: Context, packageName: String): InstalledApp? = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(packageName, 0)
        InstalledApp(packageName, info.longVersionCode, pm.getApplicationIcon(packageName))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Whether [packageName] is installed, without loading its icon as [find] does. */
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * The SHA-256 fingerprint of [info]'s signing certificate, in the signing key's form (colon-separated
     * upper-case hex), or empty when Android didn't report one. [info] needs GET_SIGNING_CERTIFICATES.
     */
    fun signerFingerprint(info: PackageInfo): String =
        info.signingInfo?.apkContentsSigners?.firstOrNull()?.let { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString(":") { "%02X".format(it) }
        }.orEmpty()

    /** Opens the web app; false if it isn't installed or has no launcher activity. */
    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
