package com.valghora.shrimpl.install

import android.content.Context
import com.valghora.shrimpl.data.AppSettings
import com.valghora.shrimpl.data.InstallMethod

/** The three install methods, and the choice between them (docs/SHRIMPL_SPEC.md R6.2). */
class Installers(context: Context, private val settings: AppSettings) {
    val session = SessionInstaller(context)
    val shevery = SheveryInstaller(context)
    val root = RootInstaller()

    /** The installer for [method]. */
    fun of(method: InstallMethod): Installer = when (method) {
        InstallMethod.SESSION -> session
        InstallMethod.SHEVERY -> shevery
        InstallMethod.ROOT -> root
    }

    /** The method chosen in Settings (the system installer by default). */
    fun preferred(): Installer = of(settings.installMethod.value)
}
