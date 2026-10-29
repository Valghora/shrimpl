package com.valghora.shrimple.install

import android.content.Context
import com.valghora.shrimple.data.AppSettings
import com.valghora.shrimple.data.InstallMethod

/** The three install methods, and the choice between them (docs/SHRIMPLE_SPEC.md R6.2). */
class Installers(context: Context, private val settings: AppSettings) {
    val session = SessionInstaller(context)
    val shizuku = ShizukuInstaller(context)
    val root = RootInstaller()

    fun of(method: InstallMethod): Installer = when (method) {
        InstallMethod.SESSION -> session
        InstallMethod.SHIZUKU -> shizuku
        InstallMethod.ROOT -> root
    }

    /** The method chosen in Settings (the system installer by default). */
    fun preferred(): Installer = of(settings.installMethod.value)
}
