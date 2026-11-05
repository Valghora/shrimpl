package com.valghora.shrimpl.core.download

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Page-made downloads (docs/TEMPLATE_SPEC.md T6.15): file names and the chunked transfer. */
class PageDownloadsTest {

    private class Sink(val name: String, val type: String, private val failOn: String? = null) : PageFileSink {
        val bytes = ByteArrayOutputStream()
        var finished = false
        var abandoned = 0

        override fun write(bytes: ByteArray) {
            if (failOn == "write") throw IOException("disk full")
            this.bytes.write(bytes)
        }

        override fun finish() {
            if (failOn == "finish") throw IOException("can't publish")
            finished = true
        }

        override fun abandon() {
            abandoned++
        }
    }

    private val opened = mutableListOf<Sink>()

    private fun downloads(failOn: String? = null, refuse: Boolean = false) =
        PageDownloads { name, type -> if (refuse) null else Sink(name, type, failOn).also(opened::add) }

    private fun start(id: Int, name: String, type: String) = """{"t":"start","id":$id,"name":"$name","type":"$type"}"""
    private fun chunk(id: Int, bytes: ByteArray) = """{"t":"chunk","id":$id,"data":"${Base64.getEncoder().encodeToString(bytes)}"}"""
    private fun end(id: Int) = """{"t":"end","id":$id}"""

    @Test
    fun aFileArrivesInChunksAndIsSavedWhole() {
        val downloads = downloads()
        assertEquals(TransferEvent.None, downloads.handle(start(1, "drawing.png", "image/png")))
        assertEquals(TransferEvent.None, downloads.handle(chunk(1, byteArrayOf(1, 2, 3))))
        assertEquals(TransferEvent.None, downloads.handle(chunk(1, byteArrayOf(4))))
        val saved = downloads.handle(end(1))
        val sink = opened.single()
        assertEquals(TransferEvent.Saved("drawing.png", sink), saved)
        assertEquals("image/png", sink.type)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), sink.bytes.toByteArray())
        assertTrue(sink.finished)
    }

    @Test
    fun transfersAreKeptApart() {
        val downloads = downloads()
        downloads.handle(start(1, "a.txt", "text/plain"))
        downloads.handle(start(2, "b.txt", "text/plain"))
        downloads.handle(chunk(2, byteArrayOf(2)))
        downloads.handle(chunk(1, byteArrayOf(1)))
        assertEquals(TransferEvent.Saved("b.txt", opened[1]), downloads.handle(end(2)))
        assertArrayEquals(byteArrayOf(1), opened[0].bytes.toByteArray())
    }

    @Test
    fun aNamelessUntypedFileGetsAFallbackNameAndType() {
        val downloads = downloads()
        downloads.handle("""{"t":"start","id":1}""")
        assertEquals("download", opened.single().name)
        assertEquals("application/octet-stream", opened.single().type)
    }

    @Test
    fun thePageReportingAnErrorAbandonsTheFile() {
        val downloads = downloads()
        downloads.handle(start(1, "a.png", "image/png"))
        assertEquals(TransferEvent.Failed("a.png"), downloads.handle("""{"t":"error","id":1}"""))
        assertEquals(1, opened.single().abandoned)
        // An error before the file started (the page couldn't read it) still names it.
        assertEquals(TransferEvent.Failed("b.svg"), downloads.handle("""{"t":"error","id":2,"name":"b","type":"image/svg+xml"}"""))
    }

    @Test
    fun aFailedWriteOrFinishAbandonsTheFile() {
        val writes = downloads(failOn = "write")
        writes.handle(start(1, "a.png", "image/png"))
        assertEquals(TransferEvent.Failed("a.png"), writes.handle(chunk(1, byteArrayOf(1))))
        assertEquals(TransferEvent.None, writes.handle(end(1)))
        val finishes = downloads(failOn = "finish")
        finishes.handle(start(1, "b.png", "image/png"))
        assertEquals(TransferEvent.Failed("b.png"), finishes.handle(end(1)))
        assertEquals(listOf(1, 1), opened.map { it.abandoned })
    }

    @Test
    fun badDataAbandonsTheFile() {
        val downloads = downloads()
        downloads.handle(start(1, "a.png", "image/png"))
        assertEquals(TransferEvent.Failed("a.png"), downloads.handle("""{"t":"chunk","id":1,"data":"%%%"}"""))
        assertEquals(1, opened.single().abandoned)
    }

    @Test
    fun aSinkThatCannotOpenFailsAtOnce() {
        assertEquals(TransferEvent.Failed("a.png"), downloads(refuse = true).handle(start(1, "a.png", "image/png")))
    }

    @Test
    fun aRestartedTransferAbandonsTheEarlierFile() {
        val downloads = downloads()
        downloads.handle(start(1, "a.png", "image/png"))
        downloads.handle(start(1, "a.png", "image/png"))
        assertEquals(listOf(1, 0), opened.map { it.abandoned })
    }

    @Test
    fun malformedOrUnknownMessagesAreIgnored() {
        val downloads = downloads()
        assertEquals(TransferEvent.None, downloads.handle("not json"))
        assertEquals(TransferEvent.None, downloads.handle("""{"t":"start"}"""))
        assertEquals(TransferEvent.None, downloads.handle("""{"t":"pause","id":1}"""))
        assertEquals(TransferEvent.None, downloads.handle(chunk(9, byteArrayOf(1))))
        assertEquals(TransferEvent.None, downloads.handle(end(9)))
        assertTrue(opened.isEmpty())
    }

    @Test
    fun namesAreMadeSafe() {
        assertEquals("drawing.png", PageDownloads.fileName("drawing.png", "image/png"))
        assertEquals("drawing.png", PageDownloads.fileName("drawing", "image/png"))
        assertEquals("report.csv", PageDownloads.fileName("report", "text/csv; charset=utf-8"))
        assertEquals("notes", PageDownloads.fileName("notes", "application/x-unknown"))
        assertEquals("evil.sh", PageDownloads.fileName("../../evil.sh", null))
        assertEquals("evil.sh", PageDownloads.fileName("C:\\temp\\evil.sh", null))
        assertEquals("a_b_c.txt", PageDownloads.fileName("a:b?c.txt", null))
        assertEquals("tab_.txt", PageDownloads.fileName("tab\t.txt", null))
        assertEquals("download.svg", PageDownloads.fileName("  ..  ", "image/svg+xml"))
        assertEquals("download", PageDownloads.fileName(null, null))
    }

    @Test
    fun longNamesKeepTheirExtension() {
        val long = PageDownloads.fileName("x".repeat(300) + ".excalidraw", null)
        assertEquals(120, long.length)
        assertTrue(long.endsWith(".excalidraw"))
        val bare = PageDownloads.fileName("y".repeat(300), null)
        assertEquals("y".repeat(120), bare)
        val short = PageDownloads.fileName("z".repeat(200) + ".png", null)
        assertEquals("z".repeat(116) + ".png", short)
    }
}
