package com.valghora.shrimpl.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.valghora.shrimpl.data.InstallMethod
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The system installer (docs/SHRIMPL_SPEC.md R6.1): a PackageInstaller session, confirmed by the user.
 * Updates of web apps Shrimpl installed itself can skip the confirmation (USER_ACTION_NOT_REQUIRED).
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

    /** The install or uninstall waiting for the user to confirm it in the system's screen, if any. */
    val confirmation: StateFlow<Confirmation?> get() = InstallStatusReceiver.waiting

    /** Brings the system's confirmation screen back, for when the user left it unfinished (R6.5). */
    fun showConfirmation() {
        InstallStatusReceiver.waiting.value?.let { context.startActivity(Intent(it.screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Stops waiting for the confirmation (R6.5): abandons the install session, so it can't finish
     * later without a record, and reports the install as cancelled.
     */
    fun cancelConfirmation() {
        val waiting = InstallStatusReceiver.waiting.value ?: return
        if (waiting.sessionId != NO_SESSION) {
            try {
                context.packageManager.packageInstaller.abandonSession(waiting.sessionId)
            } catch (_: SecurityException) {
                // Already finished or abandoned by the system; nothing is left to stop.
            }
        }
        InstallStatusReceiver.finish(waiting.id, InstallResult.Cancelled)
    }

    override suspend fun uninstall(packageName: String): InstallResult {
        val result = InstallStatusReceiver.register()
        context.packageManager.packageInstaller.uninstall(packageName, InstallStatusReceiver.sender(context, result.first))
        return result.second.await()
    }
}

/** A system confirmation screen ([screen]) waiting for the user; [sessionId] is the install session, if any. */
data class Confirmation(val id: Int, val sessionId: Int, val screen: Intent)

internal const val NO_SESSION = -1

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
                    finish(id, InstallResult.Failure("The installer asked for confirmation but gave no screen"))
                } else if (pending.containsKey(id)) {
                    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, NO_SESSION)
                    _waiting.value = Confirmation(id, sessionId, Intent(confirm))
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> finish(id, InstallResult.Success)
            PackageInstaller.STATUS_FAILURE_ABORTED -> finish(id, InstallResult.Cancelled)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installer status $status"
                finish(id, InstallResult.Failure(message))
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "com.valghora.shrimpl.INSTALL_ID"
        private val ids = AtomicInteger()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()
        private val _waiting = MutableStateFlow<Confirmation?>(null)
        val waiting: StateFlow<Confirmation?> = _waiting.asStateFlow()

        /** Completes the waiting install or uninstall [id] once; later statuses for it are ignored. */
        fun finish(id: Int, result: InstallResult) {
            _waiting.update { if (it?.id == id) null else it }
            pending.remove(id)?.complete(result)
        }

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
