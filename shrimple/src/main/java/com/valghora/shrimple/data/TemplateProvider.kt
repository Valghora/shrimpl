package com.valghora.shrimple.data

import android.content.Context
import java.io.File

/** The bundled web app template (docs/SHRIMPLE_SPEC.md R8.1), copied out of the APK's assets once per version. */
class TemplateProvider(private val context: Context) {
    /** The bundled template's version (R11.4). */
    val version: Int by lazy {
        context.assets.open("template/version").bufferedReader().use { it.readText().trim().toInt() }
    }

    /** The template APK as a file (the builder reads it as a zip). */
    @Synchronized
    fun file(): File {
        val dir = File(context.noBackupFilesDir, "template")
        val file = File(dir, "template-$version.apk")
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
