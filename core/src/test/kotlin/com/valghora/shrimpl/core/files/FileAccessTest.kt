package com.valghora.shrimpl.core.files

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** File picker and file handle requests from a page (docs/TEMPLATE_SPEC.md T6.17). */
class FileAccessTest {
    @Test
    fun pickerRequests() {
        assertEquals(FileRequest.Open(1, multiple = true), FileAccess.parse("""{"t":"open","id":1,"multiple":true}"""))
        assertEquals(FileRequest.Open(2, multiple = false), FileAccess.parse("""{"t":"open","id":2}"""))
        assertEquals(
            FileRequest.Save(3, "drawing.excalidraw", "application/json"),
            FileAccess.parse("""{"t":"save","id":3,"name":"drawing.excalidraw","type":"application/json"}"""),
        )
    }

    @Test
    fun aSaveGetsASafeNameAndAPlainType() {
        assertEquals(FileRequest.Save(1, "notes.txt", "text/plain"), FileAccess.parse("""{"t":"save","id":1,"name":"../notes","type":"Text/Plain"}"""))
        assertEquals(FileRequest.Save(2, "download", "application/octet-stream"), FileAccess.parse("""{"t":"save","id":2,"type":"image/*"}"""))
        assertEquals(FileRequest.Save(3, "a.md", "application/octet-stream"), FileAccess.parse("""{"t":"save","id":3,"name":"a.md","type":"not a type"}"""))
    }

    @Test
    fun handleRequests() {
        assertEquals(FileRequest.Read(4, "k1"), FileAccess.parse("""{"t":"read","id":4,"handle":"k1"}"""))
        assertEquals(FileRequest.Commit(5, "k1"), FileAccess.parse("""{"t":"commit","id":5,"handle":"k1"}"""))
        val write = FileAccess.parse("""{"t":"write","id":5,"handle":"k1","data":"${Base64.getEncoder().encodeToString(byteArrayOf(7, 8))}"}""")
        assertTrue(write is FileRequest.Write)
        write as FileRequest.Write
        assertEquals(5, write.id)
        assertEquals("k1", write.handle)
        assertArrayEquals(byteArrayOf(7, 8), write.bytes)
    }

    @Test
    fun aBadPartFailsItsWrite() {
        assertEquals(FileRequest.Invalid(6), FileAccess.parse("""{"t":"write","id":6,"handle":"k1","data":"%%%"}"""))
    }

    @Test
    fun malformedRequestsAreIgnored() {
        assertNull(FileAccess.parse("not json"))
        assertNull(FileAccess.parse("""{"t":"open"}"""))
        assertNull(FileAccess.parse("""{"t":"delete","id":1}"""))
        assertNull(FileAccess.parse("""{"t":"read","id":1}"""))
        assertNull(FileAccess.parse("""{"t":"write","id":1,"data":"AA=="}"""))
        assertNull(FileAccess.parse("""{"t":"commit","id":1}"""))
    }
}
