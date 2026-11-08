package com.valghora.shrimpl.webapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Folder access (docs/TEMPLATE_SPEC.md T6.18) in the real WebView, against Android's real
 * `DocumentsContract`: the folder the user "picks" is the debug build's [TestTreeProvider], a document
 * provider over a folder on disk, so listing, creating, deleting, renaming and moving run through the same
 * calls as with the system's storage. Only the folder picker's dialog is stubbed.
 */
@RunWith(AndroidJUnit4::class)
class FolderAccessTest {
    @get:Rule
    val intents = IntentsRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TestTreeProvider.TREE_DIR)
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(TestTreeProvider.authority(context.packageName), TestTreeProvider.ROOT_ID)

    @Before
    fun freshFolder() {
        root.deleteRecursively()
        File(root, "src").mkdirs()
        File(root, "src/a.txt").writeText("hello")
        File(root, "README.md").writeText("# readme")
    }

    @After
    fun removeFolder() {
        root.deleteRecursively()
    }

    private fun withPickedFolder(block: (PageDriver) -> Unit) {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(tree)))
        block(template.page())
    }

    companion object {
        /** One activity and page for the class (see [TemplatePageRule]). */
        @ClassRule
        @JvmField
        val template = TemplatePageRule()
    }

    @Test
    fun theFolderListsAndReadsItsFilesAndSubfolders() = withPickedFolder { page ->
        assertEquals(
            "README.md:file,src:directory|hello|[\"src\",\"a.txt\"]|test-tree",
            page.runAsync(
                """
                const dir = await showDirectoryPicker();
                const seen = [];
                for await (const [name, handle] of dir) seen.push(name + ':' + handle.kind);
                seen.sort();
                const file = await (await dir.getDirectoryHandle('src')).getFileHandle('a.txt');
                window.__r = seen.join(',') + '|' + await (await file.getFile()).text() + '|' + JSON.stringify(await dir.resolve(file)) + '|' + dir.name;
                """,
            ),
        )
    }

    @Test
    fun filesAndSubfoldersCanBeCreatedAndWritten() = withPickedFolder { page ->
        assertEquals(
            "ok",
            page.runAsync(
                """
                const dir = await showDirectoryPicker();
                const file = await dir.getFileHandle('new.txt', { create: true });
                const w = await file.createWritable();
                await w.write('created');
                await w.close();
                const deep = await dir.getDirectoryHandle('deep', { create: true });
                const inner = await deep.getFileHandle('inner.txt', { create: true });
                const w2 = await inner.createWritable();
                await w2.write('inner');
                await w2.close();
                window.__r = 'ok';
                """,
            ),
        )
        assertEquals("created", File(root, "new.txt").readText())
        assertEquals("inner", File(root, "deep/inner.txt").readText())
    }

    @Test
    fun theStandardsErrorsAndRemovalRules() = withPickedFolder { page ->
        assertEquals(
            "missing:NotFoundError,kind:TypeMismatchError,name:TypeError,notEmpty:InvalidModificationError,removeFile:ok,recursive:ok",
            page.runAsync(
                """
                const dir = await showDirectoryPicker();
                const out = [];
                async function t(label, fn) {
                  try { await fn(); out.push(label + ':ok'); } catch (e) { out.push(label + ':' + e.name); }
                }
                await t('missing', () => dir.getFileHandle('nope.txt'));
                await t('kind', () => dir.getFileHandle('src'));
                await t('name', () => dir.getFileHandle('../x'));
                await t('notEmpty', () => dir.removeEntry('src'));
                await t('removeFile', () => dir.removeEntry('README.md'));
                await t('recursive', () => dir.removeEntry('src', { recursive: true }));
                window.__r = out.join(',');
                """,
            ),
        )
        assertFalse(File(root, "README.md").exists())
        assertFalse(File(root, "src").exists())
    }

    @Test
    fun aFileCanBeRenamedAndMovedAndKeepsWorking() = withPickedFolder { page ->
        assertEquals(
            "[\"src\",\"b.txt\"]:hello|[\"b.txt\"]:hello|[\"src\",\"c.txt\"]:hello",
            page.runAsync(
                """
                const dir = await showDirectoryPicker();
                const src = await dir.getDirectoryHandle('src');
                const file = await src.getFileHandle('a.txt');
                async function state() { return JSON.stringify(await dir.resolve(file)) + ':' + await (await file.getFile()).text(); }
                await file.move('b.txt');
                const renamed = await state();
                await file.move(dir);
                const moved = await state();
                await file.move(src, 'c.txt');
                window.__r = renamed + '|' + moved + '|' + await state();
                """,
            ),
        )
        assertEquals("hello", File(root, "src/c.txt").readText())
        assertFalse(File(root, "src/a.txt").exists())
        assertFalse(File(root, "b.txt").exists())
    }

    /**
     * Known limit (docs/TEMPLATE_SPEC.md T6.18): after a folder is renamed, handles to files that were inside
     * it are stale. The page gets an error it can handle, not a crash; looking the files up again works.
     */
    @Test
    fun aRenamedFolderMakesOldFileHandlesFailCleanlyAndNewLookupsWork() = withPickedFolder { page ->
        assertEquals(
            "error:NotReadableError|hello",
            page.runAsync(
                """
                const dir = await showDirectoryPicker();
                const src = await dir.getDirectoryHandle('src');
                const old = await src.getFileHandle('a.txt');
                await src.move('source');
                let stale = 'readable';
                try { await old.getFile(); } catch (e) { stale = 'error:' + e.name; }
                const fresh = await (await dir.getDirectoryHandle('source')).getFileHandle('a.txt');
                window.__r = stale + '|' + await (await fresh.getFile()).text();
                """,
            ),
        )
        assertTrue(File(root, "source/a.txt").exists())
    }

    @Test
    fun backingOutOfTheFolderPickerIsAnAbortError() {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        assertEquals("error:AbortError:The user aborted a request", template.page().runAsync("await showDirectoryPicker(); window.__r = 'picked';"))
    }
}
