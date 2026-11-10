package com.valghora.shrimpl.webapp

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A one-off probe, deleted once the real folder picker test is written: what Android's folder picker
 * (DocumentsUI) shows on the CI image at each step of picking a folder on shared storage. It only logs
 * (tag PICKER_PROBE) and always passes.
 */
@RunWith(AndroidJUnit4::class)
class FolderPickerProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun log(label: String, text: String) {
        text.chunked(3000).forEachIndexed { i, part -> Log.i("PICKER_PROBE", "$label [$i] $part") }
    }

    /** The screen's nodes that carry text, a description or an ID, one per line. */
    private fun screen(label: String) {
        Thread.sleep(2_500)
        val out = ByteArrayOutputStream()
        device.dumpWindowHierarchy(out)
        val nodes = Regex("<node [^>]*>").findAll(out.toString()).map { it.value }.mapNotNull { node ->
            fun attr(name: String) = Regex("$name=\"([^\"]*)\"").find(node)?.groupValues?.get(1).orEmpty()
            val text = attr("text")
            val desc = attr("content-desc")
            val id = attr("resource-id")
            if (text.isEmpty() && desc.isEmpty() && id.isEmpty()) null
            else "pkg=${attr("package")} id=$id text=\"$text\" desc=\"$desc\" click=${attr("clickable")} bounds=${attr("bounds")}"
        }
        log(label, nodes.joinToString("\n"))
    }

    @Test
    fun probe() {
        device.executeShellCommand("mkdir -p /sdcard/Documents/ShrimplPick")
        device.executeShellCommand("sh -c 'echo hello > /sdcard/Documents/ShrimplPick/a.txt'")
        log("ls", device.executeShellCommand("ls -la /sdcard/Documents/ShrimplPick"))
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(intent)
        screen("1 opened")

        device.findObject(By.desc(Pattern.compile("(?i)show roots|open navigation drawer|roots")))?.click()
        screen("2 roots")

        device.findObject(By.text(Pattern.compile("(?i).*(sdk_gphone|internal storage|phone|emulator|android).*")))?.click()
        screen("3 storage")

        device.findObject(By.text("Documents"))?.click()
        screen("4 documents")

        device.findObject(By.text("ShrimplPick"))?.click()
        screen("5 folder")

        device.findObject(By.text(Pattern.compile("(?i)use this folder|select|allow")))?.click()
        screen("6 use")

        device.findObject(By.text(Pattern.compile("(?i)allow")).clickable(true))?.click()
        screen("7 confirmed")
        device.pressBack()
        device.pressBack()
    }
}
