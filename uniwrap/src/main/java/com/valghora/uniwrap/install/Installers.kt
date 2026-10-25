package com.valghora.uniwrap.install

import android.content.Context
import com.valghora.uniwrap.data.AppSettings
import com.valghora.uniwrap.data.InstallMethod

/** The three install methods, and the choice between them (docs/UNIWRAP_SPEC.md R6.2). */
class Installers(context: Context, private val settings: AppSettings) {
    val session = SessionInstaller(context)
    val shizuku = ShizukuInstaller(context)
    val root = RootInstaller()

    fun of(method: InstallMethod): Installer = when (method) {
        InstallMethod.SESSION -> session
        InstallMethod.SHIZUKU -> shizuku
        InstallMethod.ROOT -> root
    }

    /** The user's choice, else Shizuku if usable, else root if granted, else the system installer. */
    suspend fun preferred(): Installer {
        settings.installMethod.value?.let { return of(it) }
        if (shizuku.availability() == Availability.AVAILABLE) return shizuku
        if (root.availability() == Availability.AVAILABLE) return root
        return session
    }
}
