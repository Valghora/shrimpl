package com.valghora.shrimpl

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * What a failed instrumented test leaves behind for whoever reads the CI run (docs/BUILD_NOTES.md,
 * "Emulator"): a screenshot and the window hierarchy. Gradle uninstalls the app when the run ends,
 * taking its own files with it, so they are copied to /data/local/tmp/evidence, which the workflow's
 * evidence step pulls; the hierarchy also goes to logcat, in pieces, under the tag SHRIMPL_EVIDENCE.
 */
object Evidence {
    private const val TAG = "SHRIMPL_EVIDENCE"
    private const val CHUNK = 3_000

    fun capture(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir("test-evidence")), label).apply { mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        runCatching { device.takeScreenshot(File(dir, "screen.png")) }
        val hierarchy = File(dir, "window.xml")
        runCatching { device.dumpWindowHierarchy(hierarchy) }
        runCatching { device.executeShellCommand("mkdir -p /data/local/tmp/evidence/$label && cp -r ${dir.absolutePath}/. /data/local/tmp/evidence/$label/ && chmod -R a+rX /data/local/tmp/evidence") }
        if (hierarchy.exists()) {
            val text = hierarchy.readText()
            text.chunked(CHUNK).forEachIndexed { i, part -> Log.e(TAG, "$label window.xml [$i/${text.length / CHUNK}] $part") }
        }
    }
}
