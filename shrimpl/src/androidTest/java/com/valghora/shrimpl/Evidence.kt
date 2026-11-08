package com.valghora.shrimpl

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * What a failed instrumented test leaves behind for whoever reads the CI run (docs/BUILD_NOTES.md,
 * "Emulator"): a screenshot and the window hierarchy, in the app's external files directory, which
 * the workflow's evidence step pulls off the emulator.
 */
object Evidence {
    fun capture(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir("test-evidence")), "").apply { mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        runCatching { device.takeScreenshot(File(dir, "$label.png")) }
        runCatching { device.dumpWindowHierarchy(File(dir, "$label.xml")) }
    }
}
