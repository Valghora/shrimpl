package com.valghora.shrimpl.webapp

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.ClassRule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Page-made downloads (docs/TEMPLATE_SPEC.md T6.15) in the real WebView: a `blob:` or `data:` link
 * the page clicks ends up as a finished file in Downloads, with the name and bytes the page made.
 * Found by hand before: sites such as Excalidraw export this way.
 */
@RunWith(AndroidJUnit4::class)
class PageDownloadsTest {
    private val resolver: ContentResolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val created = mutableListOf<String>()

    @After
    fun removeDownloads() {
        created.forEach { resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(it)) }
    }

    /** The finished (not pending) Downloads entry called [name], once it exists. */
    private fun awaitDownload(name: String): Uri {
        created += name
        var found: Uri? = null
        awaitUntil("$name to appear in Downloads", 30_000) {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.IS_PENDING),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(1) == 0) {
                    found = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon().appendPath(cursor.getLong(0).toString()).build()
                }
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun read(uri: Uri): ByteArray = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }

    private fun withPage(block: (PageDriver) -> Unit) = block(template.page())

    companion object {
        /** One activity and page for the class (see [TemplatePageRule]). */
        @ClassRule
        @JvmField
        val template = TemplatePageRule()
    }

    /** The link is revoked right after the click, as sites do: the template must have taken the file by then. */
    @Test
    fun aBlobDownloadLandsInDownloads() = withPage { page ->
        val name = "shrimp-blob-${System.nanoTime()}.txt"
        page.runAsync(
            """
            const blob = new Blob(['hello from a blob'], { type: 'text/plain' });
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = '$name';
            document.body.appendChild(a);
            a.click();
            URL.revokeObjectURL(a.href);
            window.__r = 'clicked';
            """,
        )
        assertEquals("hello from a blob", String(read(awaitDownload(name))))
    }

    @Test
    fun aDataDownloadLandsInDownloads() = withPage { page ->
        val name = "shrimp-data-${System.nanoTime()}.csv"
        page.runAsync(
            """
            const a = document.createElement('a');
            a.href = 'data:text/csv;base64,' + btoa('a,b\n1,2\n');
            a.download = '$name';
            document.body.appendChild(a);
            a.click();
            window.__r = 'clicked';
            """,
        )
        assertEquals("a,b\n1,2\n", String(read(awaitDownload(name))))
    }

    /** More than one 512 KiB part: every byte arrives, in order. */
    @Test
    fun aLargeDownloadArrivesWhole() = withPage { page ->
        val name = "shrimp-large-${System.nanoTime()}.bin"
        val size = 3 * 1024 * 1024 + 123
        page.runAsync(
            """
            const bytes = new Uint8Array($size);
            for (let i = 0; i < bytes.length; i++) bytes[i] = i % 251;
            const a = document.createElement('a');
            a.href = URL.createObjectURL(new Blob([bytes], { type: 'application/octet-stream' }));
            a.download = '$name';
            document.body.appendChild(a);
            a.click();
            window.__r = 'clicked';
            """,
        )
        assertArrayEquals(ByteArray(size) { (it % 251).toByte() }, read(awaitDownload(name)))
    }

    /** A name with a path in it can't escape Downloads (T6.15): only its last part is used. */
    @Test
    fun aNameWithAPathIsMadeSafe() = withPage { page ->
        val name = "shrimp-safe-${System.nanoTime()}.txt"
        page.runAsync(
            """
            const a = document.createElement('a');
            a.href = URL.createObjectURL(new Blob(['x'], { type: 'text/plain' }));
            a.download = '../../evil/$name';
            document.body.appendChild(a);
            a.click();
            window.__r = 'clicked';
            """,
        )
        assertEquals("x", String(read(awaitDownload(name))))
    }
}
