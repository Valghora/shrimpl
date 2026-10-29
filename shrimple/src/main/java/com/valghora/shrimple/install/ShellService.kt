package com.valghora.shrimple.install

import android.os.ParcelFileDescriptor
import kotlin.system.exitProcess

/**
 * Shizuku's user service (docs/SHRIMPLE_SPEC.md R6.1): Shizuku starts this class in its own process
 * as the shell user, so `pm` runs with the shell's install permissions and needs no prompt.
 * Shizuku needs a public no-argument constructor, and R8 must keep it (proguard-rules.pro).
 */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun install(apk: ParcelFileDescriptor, size: Long): String =
        PmShell.run(PmShell.installCommand(size), ParcelFileDescriptor.AutoCloseInputStream(apk))

    override fun uninstall(packageName: String): String = PmShell.run(PmShell.uninstallCommand(packageName))
}
