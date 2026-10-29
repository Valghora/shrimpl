package com.valghora.shrimpl.install

import com.valghora.shrimpl.data.InstallMethod
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Installs as root (docs/SHRIMPL_SPEC.md R6.1): `pm install` through `su`, the APK on stdin. */
class RootInstaller : Installer {
    override val method = InstallMethod.ROOT

    /** Asking `su` may show the root manager's grant dialog; it is only asked when root is wanted. */
    override suspend fun availability(): Availability = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Availability.UNAVAILABLE
            } else if (process.inputStream.bufferedReader().readText().contains("uid=0")) {
                Availability.AVAILABLE
            } else {
                Availability.UNAVAILABLE
            }
        } catch (_: IOException) {
            Availability.UNAVAILABLE // no su binary
        }
    }

    override suspend fun install(apk: File): InstallResult = su(PmShell.installCommand(apk.length()), apk)

    override suspend fun uninstall(packageName: String): InstallResult = su(PmShell.uninstallCommand(packageName), null)

    private suspend fun su(command: List<String>, stdin: File?): InstallResult = withContext(Dispatchers.IO) {
        try {
            // Every argument is a number, a fixed word or a validated package name, so joining is safe.
            PmShell.result(PmShell.run(listOf("su", "-c", command.joinToString(" ")), stdin?.inputStream()))
        } catch (e: IOException) {
            InstallResult.Failure("Root is not available: ${e.message}", methodUnavailable = true)
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 20L
    }
}
