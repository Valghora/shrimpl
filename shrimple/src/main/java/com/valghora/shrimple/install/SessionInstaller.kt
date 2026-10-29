package com.valghora.shrimple.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.valghora.shrimple.data.InstallMethod
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The system installer (docs/SHRIMPLE_SPEC.md R6.1): a PackageInstaller session, confirmed by the user.
 * Updates of web apps Shrimple installed itself can skip the confirmation (USER_ACTION_NOT_REQUIRED).
 */
class SessionInstaller(private val context: Context) : Installer {
    override val method = InstallMethod.SESSION

    override suspend fun availability() = Availability.AVAILABLE

    override suspend fun install(apk: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) return InstallResult.NeedsUnknownSources
        val installer = context.packageManager.packageInstaller
        val result = InstallStatusReceiver.register()
        withContext(Dispatchers.IO) {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(InstallStatusReceiver.sender(context, result.first))
            }
        }
        return result.second.await()
    }

    override suspend fun uninstall(packageName: String): InstallResult {
        val result = InstallStatusReceiver.register()
        context.packageManager.packageInstaller.uninstall(packageName, InstallStatusReceiver.sender(context, result.first))
        return result.second.await()
    }
}

/**
 * Receives PackageInstaller results (not exported). A confirmation request starts the system dialog;
 * the final status completes the waiting install or uninstall.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    pending.remove(id)?.complete(InstallResult.Failure("The installer asked for confirmation but gave no screen"))
                } else {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> pending.remove(id)?.complete(InstallResult.Success)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Cancelled" else "Installer status $status"
                pending.remove(id)?.complete(InstallResult.Failure(message))
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "com.valghora.shrimple.INSTALL_ID"
        private val ids = AtomicInteger()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()

        fun register(): Pair<Int, CompletableDeferred<InstallResult>> {
            val id = ids.incrementAndGet()
            val deferred = CompletableDeferred<InstallResult>()
            pending[id] = deferred
            return id to deferred
        }

        /** Mutable: the installer adds the status extras to this explicit intent. */
        fun sender(context: Context, id: Int): IntentSender {
            val intent = Intent(context, InstallStatusReceiver::class.java).setPackage(context.packageName).putExtra(EXTRA_ID, id)
            return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender
        }
    }
}
