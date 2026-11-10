package com.valghora.shrimpl.webapp

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Folder access (docs/TEMPLATE_SPEC.md T6.18) through Android's real folder picker and shared storage:
 * the page asks for a folder, the test picks `Documents/ShrimplPick` in DocumentsUI as a user would, and
 * the page lists, reads, writes, creates and removes in it, each checked on disk. FolderAccessTest covers
 * the standard's details against a fixture provider; this checks the same calls against the system's own
 * storage provider and its grant.
 */
@RunWith(AndroidJUnit4::class)
class RealFolderPickerTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val folder = "/sdcard/Documents/ShrimplPick"

    private fun sh(command: String) = device.executeShellCommand(command)

    @Before
    fun freshFolder() {
        // The shell runs commands without a shell, so no redirects: the seed file is a copy.
        sh("rm -rf $folder")
        sh("mkdir -p $folder/sub")
        sh("cp /system/etc/hosts $folder/hosts.txt")
    }

    @After
    fun removeFolder() {
        sh("rm -rf $folder")
    }

    /** Taps [text] in Android's folder picker once it shows. */
    private fun tapInPicker(text: String) {
        val node = device.wait(Until.findObject(By.pkg(Pattern.compile(".*documentsui.*")).text(text)), 15_000)
        requireNotNull(node) { "the folder picker shows no \"$text\"" }.click()
    }

    /** Picks Documents/ShrimplPick in Android's folder picker, which opens at the top of shared storage. */
    private fun pickTheFolder() {
        tapInPicker("Documents")
        tapInPicker("ShrimplPick")
        assertTrue("the picker didn't open ShrimplPick", device.wait(Until.hasObject(By.text("Files in ShrimplPick")), 15_000) == true)
        tapInPicker("USE THIS FOLDER")
        tapInPicker("ALLOW")
    }

    @Test
    fun aFolderPickedInAndroidsPickerIsReadAndWrittenOnSharedStorage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            answerPermissionPrompt("^allow$", 5_000) // The notification prompt of a fresh launch (T7.5), if it shows.
            val page = PageDriver(scenario).also { it.loadTestPage() }
            page.js(
                "window.__r = undefined; (async () => { try { const d = await showDirectoryPicker({ mode: 'readwrite' }); " +
                    "window.__dir = d; const names = []; for await (const [n, h] of d.entries()) names.push(n + ':' + h.kind); " +
                    "window.__r = names.sort().join(','); } catch (e) { window.__r = 'error:' + e.name + ':' + e.message; } })(); 0",
            )
            pickTheFolder()
            page.awaitJs("window.__r !== undefined", "the page to get the picked folder", 20_000)
            assertEquals("hosts.txt:file,sub:directory", page.js("window.__r"))

            // Read: the page sees the file as it is on disk.
            val onDisk = sh("cat $folder/hosts.txt").trim()
            assertEquals(onDisk, page.runAsync("const f = await (await window.__dir.getFileHandle('hosts.txt')).getFile(); window.__r = (await f.text()).trim();"))

            // Write: a new file reaches shared storage.
            page.runAsync(
                "const h = await window.__dir.getFileHandle('from-page.txt', { create: true }); const w = await h.createWritable(); " +
                    "await w.write('written by the page'); await w.close(); window.__r = 'ok';",
            )
            assertEquals("written by the page", sh("cat $folder/from-page.txt").trim())

            // Create and remove a folder.
            page.runAsync("await window.__dir.getDirectoryHandle('made', { create: true }); window.__r = 'ok';")
            assertTrue("the page's new folder isn't on disk", "made" in sh("ls $folder").lines())
            page.runAsync("await window.__dir.removeEntry('made'); window.__r = 'ok';")
            assertTrue("the page's removed folder is still on disk", "made" !in sh("ls $folder").lines())
        }
    }
}
