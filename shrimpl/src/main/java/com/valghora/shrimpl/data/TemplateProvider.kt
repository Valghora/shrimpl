package com.valghora.shrimpl.data

import android.content.Context
import java.io.File

/** The bundled web app template (docs/SHRIMPL_SPEC.md R8.1), copied out of the APK's assets once per install of Shrimpl. */
class TemplateProvider(private val context: Context) {
    /** The bundled template's version (R11.4). */
    val version: Int by lazy {
        context.assets.open("template/version").bufferedReader().use { it.readText().trim().toInt() }
    }

    /**
     * The bundled template APK as a file, since the builder reads it as a zip. It is copied out of the
     * assets once per install of Shrimpl, not per template version: builds of one branch share a
     * version (R11.8) but not a template, and a copy kept by version would build web apps from an older
     * one. Older copies are deleted.
     */
    @Synchronized
    fun file(): File {
        val dir = File(context.noBackupFilesDir, "template")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val file = File(dir, "template-$version-$installed.apk")
        if (!file.exists()) {
            dir.listFiles()?.forEach { it.delete() } // older versions
            dir.mkdirs()
            val partial = File(dir, "${file.name}.part")
            context.assets.open("template/template.apk").use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.renameTo(file)) { "could not store the template" }
        }
        return file
    }
}
