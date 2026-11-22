package com.valghora.shrimpl.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import com.valghora.shrimpl.core.build.WebAppBuilder
import com.valghora.shrimpl.core.records.EmbeddedRecord
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists the web apps a previous install of Shrimpl built (docs/SHRIMPL_SPEC.md R11.6). Uninstalling
 * Shrimpl deletes its records, but each web app's APK carries its profile and icon options; on the
 * first start without records, every installed `com.valghora.shrimpl.app.*` package gets its record back.
 */
class WebAppRecovery(private val context: Context, private val store: WebAppStore, private val settings: AppSettings) {
    /** Runs once: only when Shrimpl has no records yet, so apps the user removed from the list stay removed. */
    suspend fun runOnce() = withContext(Dispatchers.IO) {
        if (settings.recoveryDone) return@withContext
        if (store.records.value.isEmpty()) recoverAll()
        settings.recoveryDone = true
    }

    private suspend fun recoverAll() {
        val pm = context.packageManager
        val prefix = WebAppBuilder.PACKAGE_PREFIX + "."
        // Web apps are found through their launcher entry, which Shrimpl's <queries> makes visible.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName }
            .filter { it.startsWith(prefix) }
            .distinct()
            .mapNotNull { name ->
                try {
                    pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .forEach { info ->
                try {
                    val recovered = ZipFile(requireNotNull(info.applicationInfo).sourceDir).use { apk ->
                        EmbeddedRecord.recover(apk, installed(info), ::drawnIcon)
                    }
                    if (store.get(recovered.record.appId) == null) store.save(recovered.record, recovered.images)
                } catch (e: IOException) {
                    Log.w(TAG, "Skipping ${info.packageName}: ${e.message}")
                } catch (e: EmbeddedRecord.RecoveryException) {
                    Log.w(TAG, "Skipping ${info.packageName}: ${e.message}")
                }
            }
    }

    private fun installed(info: PackageInfo) = EmbeddedRecord.Installed(
        packageName = info.packageName,
        versionCode = info.longVersionCode,
        versionName = info.versionName.orEmpty(),
        signerFingerprint = InstalledApps.signerFingerprint(info),
        firstInstalled = info.firstInstallTime,
        lastUpdated = info.lastUpdateTime,
    )

    /** The launcher icon's two layers drawn into one square PNG; null when either can't be decoded. */
    private fun drawnIcon(background: ByteArray, foreground: ByteArray): ByteArray? {
        val back = BitmapFactory.decodeByteArray(background, 0, background.size) ?: return null
        val front = BitmapFactory.decodeByteArray(foreground, 0, foreground.size) ?: return null
        val size = maxOf(back.width, back.height, front.width, front.height)
        val out = createBitmap(size, size)
        Canvas(out).apply {
            val bounds = Rect(0, 0, size, size)
            drawBitmap(back, null, bounds, null)
            drawBitmap(front, null, bounds, null)
        }
        return ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    private companion object {
        const val TAG = "Shrimpl"
    }
}
