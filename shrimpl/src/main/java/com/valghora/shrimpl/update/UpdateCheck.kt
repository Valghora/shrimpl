package com.valghora.shrimpl.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.valghora.shrimpl.MainActivity
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.core.update.Release
import com.valghora.shrimpl.core.update.UpdateFrequency
import java.util.concurrent.TimeUnit

/**
 * The background check (docs/SHRIMPL_SPEC.md R14.2): asks for the latest release and, for a newer one
 * not yet announced, posts the update notification (R14.5).
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updates = applicationContext.container.updates
        val result = updates.check()
        if (result is UpdateResult.Available && result.release.code > updates.notifiedCode) {
            if (UpdateNotification.show(applicationContext, result.release)) updates.notifiedCode = result.release.code
        }
        return Result.success()
    }
}

/** Schedules the background check from Check for updates (R14.2); Disabled stops it. */
object UpdateSchedule {
    private const val WORK = "update-check"

    fun apply(context: Context, frequency: UpdateFrequency) {
        val work = WorkManager.getInstance(context)
        if (frequency == UpdateFrequency.DISABLED) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(frequency.hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE keeps the schedule's timing when nothing changed and takes a new frequency when it did.
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/**
 * The update notification (R14.5), on its own "Updates" channel: selecting it opens Shrimpl's
 * Settings, and its Update action opens them and starts the update.
 */
object UpdateNotification {
    const val CHANNEL = "updates"
    private const val ID = 1

    /** Whether the notification could be posted; Android drops it without the notification permission. */
    fun show(context: Context, release: Release): Boolean {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
        fun open(action: String, request: Int) = PendingIntent.getActivity(
            context,
            request,
            Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shrimpl)
            .setContentTitle(context.getString(R.string.update_notification_title, release.version))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(open(MainActivity.ACTION_SHOW_UPDATES, 0))
            .addAction(0, context.getString(R.string.action_update), open(MainActivity.ACTION_INSTALL_UPDATE, 1))
            .setAutoCancel(true)
            .build()
        manager.notify(ID, notification)
        return true
    }
}
