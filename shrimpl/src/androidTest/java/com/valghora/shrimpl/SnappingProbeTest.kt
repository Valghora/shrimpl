package com.valghora.shrimpl

import android.app.Activity
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A one-off probe, deleted once the snapping test is written: which of Android's ways to put Shrimpl in a
 * window that shares the screen work on the CI image, and what the window then reports. It only logs
 * (tag SNAP_PROBE) and always passes.
 */
@RunWith(AndroidJUnit4::class)
class SnappingProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = instrumentation.targetContext.packageName

    private fun sh(command: String): String = device.executeShellCommand(command)

    private fun log(label: String, text: String) {
        text.chunked(3000).forEachIndexed { i, part -> Log.i("SNAP_PROBE", "$label [$i] $part") }
    }

    private fun resumed(): Activity? {
        var found: Activity? = null
        instrumentation.runOnMainSync { found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull() }
        return found
    }

    private fun state(activity: Activity?): String {
        if (activity == null) return "no resumed activity"
        var text = ""
        instrumentation.runOnMainSync {
            val config = activity.resources.configuration
            text = "${activity.javaClass.simpleName} task=${activity.taskId} multiWindow=${activity.isInMultiWindowMode} " +
                "widthDp=${config.screenWidthDp} heightDp=${config.screenHeightDp} config=$config"
        }
        return text
    }

    private fun tasks() = sh("dumpsys activity activities").lines()
        .filter { "Task{" in it || "mode=" in it || "bounds=" in it || "mWindowingMode" in it || "windowingMode" in it }
        .take(60).joinToString("\n")

    @Test
    fun probe() {
        try {
            sh("wm size 2560x1600")
            sh("wm density 240")
            Thread.sleep(2_000)
            log("wmshell help", sh("dumpsys activity service SystemUIService WMShell help"))
            log("am help", sh("cmd activity help").lines().filter { "task" in it.lowercase() || "windowing" in it.lowercase() }.joinToString("\n"))
            log("freeform setting", sh("settings get global enable_freeform_support"))
            log("desktop props", sh("getprop").lines().filter { "desktop" in it || "freeform" in it || "multi" in it.lowercase() }.joinToString("\n"))

            // A: split screen, Settings beside Shrimpl.
            log("A settings", sh("am start -W -n com.android.settings/.Settings"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var task = 0
                scenario.onActivity { task = it.taskId }
                log("A before", state(resumed()))
                log("A moveToSideStage", sh("dumpsys activity service SystemUIService WMShell splitscreen moveToSideStage $task 1"))
                Thread.sleep(5_000)
                log("A after", state(resumed()))
                log("A tasks", tasks())
            }
            sh("am force-stop com.android.settings")
            Thread.sleep(2_000)

            // B: a freeform window, resized.
            log("B enable", sh("settings put global enable_freeform_support 1") + sh("settings put global force_resizable_activities 1"))
            log("B start", sh("am start -W --windowingMode 5 -n $pkg/.MainActivity"))
            Thread.sleep(4_000)
            val activity = resumed()
            log("B started", state(activity))
            log("B tasks", tasks())
            if (activity != null) {
                log("B resize", sh("am task resize ${activity.taskId} 100 100 1500 1100"))
                Thread.sleep(4_000)
                log("B resized", state(resumed()))
                log("B tasks after", tasks())
                instrumentation.runOnMainSync { activity.finish() }
            }
        } finally {
            sh("wm size reset")
            sh("wm density reset")
        }
    }
}
