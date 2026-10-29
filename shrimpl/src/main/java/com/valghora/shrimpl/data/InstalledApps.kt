package com.valghora.shrimpl.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** What Android says about an installed web app (docs/SHRIMPL_SPEC.md R2.2). */
data class InstalledApp(val packageName: String, val versionCode: Long, val icon: Drawable)

object InstalledApps {
    fun find(context: Context, packageName: String): InstalledApp? = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(packageName, 0)
        InstalledApp(packageName, info.longVersionCode, pm.getApplicationIcon(packageName))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Opens the web app; false if it isn't installed or has no launcher activity. */
    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
