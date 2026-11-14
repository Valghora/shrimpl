package com.valghora.shrimpl.core.build

import java.io.File
import java.util.zip.ZipFile

/** The real template release APK, built by Gradle before these tests run. */
object TemplateFixture {
    val apk: File = File(requireNotNull(System.getProperty("templateApk")) { "templateApk not set by Gradle" })

    /** The code of shrimpl.templateVersion in gradle.properties (R11.8): the versionCode the template APK must carry. */
    val version: Int = requireNotNull(System.getProperty("templateVersion")) { "templateVersion not set by Gradle" }.toInt()

    val manifest: ByteArray by lazy { entry(apk, WebAppBuilder.Paths.MANIFEST) }

    /** One key for the whole test run: RSA 3072 generation takes a moment. */
    val key: SigningKey by lazy { SigningKeys.generate() }

    fun entry(apk: File, name: String): ByteArray = ZipFile(apk).use { zip ->
        val entry = requireNotNull(zip.getEntry(name)) { "$name not in $apk" }
        zip.getInputStream(entry).use { it.readBytes() }
    }

    /** `aapt2 dump <what>` output when the Android SDK is available, else null. */
    fun aapt2(vararg args: String): String? {
        val sdk = System.getenv("ANDROID_HOME") ?: return null
        val aapt2 = File(sdk, "build-tools").listFiles()
            ?.mapNotNull { File(it, "aapt2").takeIf(File::canExecute) }
            ?.maxByOrNull { it.parentFile.name } ?: return null
        val process = ProcessBuilder(aapt2.path, *args).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { out }
        return out
    }
}
