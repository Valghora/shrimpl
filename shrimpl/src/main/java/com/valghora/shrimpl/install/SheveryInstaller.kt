package com.valghora.shrimpl.install

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.valghora.shrimpl.data.InstallMethod
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.hamondev.shevery.api.SheveryPrivilege

/**
 * Installs through Shevery (docs/SHRIMPL_SPEC.md R6.1): binds [ShellService], which Shevery runs as the
 * shell user, and streams the APK into `pm install`. No prompt per install.
 */
class SheveryInstaller(private val context: Context) : Installer {
    override val method = InstallMethod.SHEVERY

    private val _state = MutableStateFlow(currentAvailability())

    /** Live availability: Shevery's binder arrives after app start, and permission can change. */
    val state: StateFlow<Availability> = _state.asStateFlow()

    private val _permissionResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** The answers to [requestPermission]: true when granted. */
    val permissionResults: SharedFlow<Boolean> = _permissionResults.asSharedFlow()

    init {
        SheveryPrivilege.addBinderReceivedListenerSticky { _state.value = currentAvailability() }
        SheveryPrivilege.addBinderDeadListener { _state.value = currentAvailability() }
        SheveryPrivilege.addPermissionResultListener { _, result ->
            _state.value = currentAvailability()
            _permissionResults.tryEmit(result == PackageManager.PERMISSION_GRANTED)
        }
    }

    override suspend fun availability(): Availability = currentAvailability().also { _state.value = it }

    /** Shows Shevery's permission dialog; [state] updates with the answer. */
    fun requestPermission() {
        if (SheveryPrivilege.pingBinder()) SheveryPrivilege.requestPermission(PERMISSION_REQUEST)
    }

    override suspend fun install(apk: File): InstallResult = withService { service ->
        val (read, write) = ParcelFileDescriptor.createPipe()
        // Write the APK into the pipe while the service reads it into `pm install`.
        val writer = Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out -> apk.inputStream().use { it.copyTo(out) } }
        }.apply { start() }
        try {
            PmShell.result(service.install(read, apk.length()))
        } finally {
            read.close()
            writer.join()
        }
    }

    override suspend fun uninstall(packageName: String): InstallResult = withService { service ->
        PmShell.result(service.uninstall(packageName))
    }

    private suspend fun withService(block: (IShellService) -> InstallResult): InstallResult {
        if (availability() != Availability.AVAILABLE) {
            return InstallResult.Failure("Shevery is not running or has not granted Shrimpl permission", methodUnavailable = true)
        }
        val args = SheveryPrivilege.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .version(SERVICE_VERSION)
        var connection: ServiceConnection? = null
        val service = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (continuation.isActive) continuation.resume(IShellService.Stub.asInterface(binder))
                    }

                    override fun onServiceDisconnected(name: ComponentName) = Unit
                }.also { SheveryPrivilege.bindUserService(args, it) }
            }
        }
        return try {
            if (service == null) {
                InstallResult.Failure("Shevery did not start Shrimpl's shell service", methodUnavailable = true)
            } else {
                withContext(Dispatchers.IO) { block(service) }
            }
        } catch (e: RemoteException) {
            InstallResult.Failure("Shevery stopped during the install: ${e.message}", methodUnavailable = true)
        } finally {
            connection?.let { runCatching { SheveryPrivilege.unbindUserService(args, it, true) } }
        }
    }

    // pingBinder first: Shevery's permission check throws while it isn't connected.
    private fun currentAvailability(): Availability = when {
        !SheveryPrivilege.pingBinder() -> Availability.UNAVAILABLE
        SheveryPrivilege.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Availability.AVAILABLE
        else -> Availability.NEEDS_PERMISSION
    }

    private companion object {
        const val PERMISSION_REQUEST = 4242
        const val SERVICE_VERSION = 1
        const val BIND_TIMEOUT_MS = 15_000L
    }
}
