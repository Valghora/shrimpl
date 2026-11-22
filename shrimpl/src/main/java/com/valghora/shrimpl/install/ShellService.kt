package com.valghora.shrimpl.install

import android.content.Context
import android.os.Binder
import android.os.ParcelFileDescriptor
import kotlin.system.exitProcess

/**
 * Shevery's user service (docs/SHRIMPL_SPEC.md R6.1): Shevery starts this class in its own process
 * as the shell user, so `pm` runs with the shell's install permissions and needs no prompt. Shevery
 * calls the constructor with Shrimpl's own package context, which gives the one caller allowed:
 * Shrimpl itself. R8 must keep that constructor (`src/main/keepRules/shell-service.keep`).
 */
class ShellService(context: Context) : IShellService.Stub() {
    private val shrimplUid = context.applicationInfo.uid

    /** Shevery calls this when it unbinds; ending the process gives no one any power, so anyone may. */
    override fun destroy() {
        exitProcess(0)
    }

    override fun install(apk: ParcelFileDescriptor, size: Long): String {
        checkCaller(Binder.getCallingUid())
        return PmShell.run(PmShell.installCommand(size), ParcelFileDescriptor.AutoCloseInputStream(apk))
    }

    override fun uninstall(packageName: String): String {
        checkCaller(Binder.getCallingUid())
        return PmShell.run(PmShell.uninstallCommand(packageName))
    }

    /**
     * R6.1: only Shrimpl may install or uninstall with the shell's permissions (the
     * android-intent-security skill, service caller verification).
     * Invariant: [callingUid] must be Shrimpl's own UID. A call made outside a binder transaction
     * reads this process's own UID, the shell's, so it never passes.
     */
    internal fun checkCaller(callingUid: Int) {
        if (callingUid != shrimplUid) throw SecurityException("Only Shrimpl can use its shell service")
    }
}
