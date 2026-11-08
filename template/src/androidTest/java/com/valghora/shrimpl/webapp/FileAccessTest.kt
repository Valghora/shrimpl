package com.valghora.shrimpl.webapp

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Single-file File System Access (docs/TEMPLATE_SPEC.md T6.17) in the real WebView: Save and Open
 * dialogs give the page handles it can read and write, later saves replace the same file whole, and
 * backing out is an AbortError. Android's dialogs are answered by the test with a real Downloads
 * document, so the template's own read and write path runs; only the dialog UI is stubbed.
 */
@RunWith(AndroidJUnit4::class)
class FileAccessTest {
    @get:Rule
    val intents = IntentsRule()

    private val resolver: ContentResolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val documents = mutableListOf<Uri>()

    @After
    fun removeDocuments() {
        documents.forEach { resolver.delete(it, null, null) }
    }

    /** A new empty Downloads document, which the stubbed dialogs hand to the page as the picked file. */
    private fun newDocument(name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            // A generic type keeps the name as given (a known type can make MediaStore add an extension).
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)).also { documents += it }
    }

    private fun read(uri: Uri): String = String(requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() })

    private fun dialogPicks(action: String, uri: Uri) {
        intending(hasAction(action)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    private fun dialogIsDismissed(action: String) {
        intending(hasAction(action)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    private fun withPage(block: (PageDriver) -> Unit) = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        block(PageDriver(scenario).also { it.loadTestPage() })
    }

    @Test
    fun saveThenSaveAgainWritesTheSameFileWhole() = withPage { page ->
        val doc = newDocument("drawing.excalidraw-${System.nanoTime()}")
        dialogPicks(Intent.ACTION_CREATE_DOCUMENT, doc)

        assertEquals(
            "file",
            page.runAsync(
                """
                window.__h = await showSaveFilePicker({
                  suggestedName: 'drawing.excalidraw',
                  types: [{ accept: { 'application/json': ['.excalidraw'] } }],
                });
                const w = await window.__h.createWritable();
                await w.write('{"version":1,"elements":[1,2,3]}');
                await w.close();
                window.__r = window.__h.kind;
                """,
            ),
        )
        assertEquals("{\"version\":1,\"elements\":[1,2,3]}", read(doc))

        // The dialog asked for the site's name and type.
        val asked = Intents.getIntents().first { it.action == Intent.ACTION_CREATE_DOCUMENT }
        assertEquals("drawing.excalidraw", asked.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("application/json", asked.type)

        // Ctrl+S: the same handle writes again, silently, and the shorter content replaces the old whole.
        assertEquals(
            "saved",
            page.runAsync(
                """
                const w = await window.__h.createWritable();
                await w.write('{"v":2}');
                await w.close();
                window.__r = 'saved';
                """,
            ),
        )
        assertEquals("{\"v\":2}", read(doc))
    }

    /**
     * A MediaStore document has no `last_modified` column; asking for it threw on the bridge's IO thread
     * and closed the whole web app (found on an emulator, 2026-11-08). It must just be read.
     */
    @Test
    fun openReadsTheChosenFile() = withPage { page ->
        val doc = newDocument("notes-${System.nanoTime()}.txt")
        requireNotNull(resolver.openOutputStream(doc, "wt")).use { it.write("opened by a page".toByteArray()) }
        dialogPicks(Intent.ACTION_OPEN_DOCUMENT, doc)

        assertEquals(
            "opened by a page|16",
            page.runAsync(
                """
                const [handle] = await showOpenFilePicker();
                const file = await handle.getFile();
                window.__r = (await file.text()) + '|' + file.size;
                """,
            ),
        )
    }

    /** Open, edit and save back: the whole point of keeping the handle. */
    @Test
    fun aFileOpenedThenEditedIsSavedBackToItself() = withPage { page ->
        val doc = newDocument("project-${System.nanoTime()}.json")
        requireNotNull(resolver.openOutputStream(doc, "wt")).use { it.write("{\"count\":1}".toByteArray()) }
        dialogPicks(Intent.ACTION_OPEN_DOCUMENT, doc)

        page.runAsync(
            """
            const [handle] = await showOpenFilePicker();
            const data = JSON.parse(await (await handle.getFile()).text());
            data.count += 1;
            const w = await handle.createWritable();
            await w.write(JSON.stringify(data));
            await w.close();
            window.__r = 'edited';
            """,
        )
        assertEquals("{\"count\":2}", read(doc))
    }

    @Test
    fun backingOutOfADialogIsAnAbortError() = withPage { page ->
        dialogIsDismissed(Intent.ACTION_OPEN_DOCUMENT)
        assertEquals(
            "error:AbortError:The user aborted a request",
            page.runAsync("await showOpenFilePicker(); window.__r = 'picked';"),
        )
    }

    @Test
    fun writeCommandsSeekAndTruncateLikeABrowser() = withPage { page ->
        val doc = newDocument("edit-${System.nanoTime()}.txt")
        requireNotNull(resolver.openOutputStream(doc, "wt")).use { it.write("0123456789".toByteArray()) }
        dialogPicks(Intent.ACTION_OPEN_DOCUMENT, doc)

        page.runAsync(
            """
            const [handle] = await showOpenFilePicker();
            const w = await handle.createWritable({ keepExistingData: true });
            await w.seek(3);
            await w.write('abc');
            await w.truncate(8);
            await w.close();
            window.__r = 'done';
            """,
        )
        assertEquals("012abc67", read(doc))
    }
}
