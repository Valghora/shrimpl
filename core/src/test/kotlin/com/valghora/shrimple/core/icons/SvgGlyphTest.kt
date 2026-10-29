package com.valghora.shrimple.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Glyph extraction and PathParser-safe path data. */
class SvgGlyphTest {
    private fun svg(body: String) = """<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">$body</svg>""".toByteArray()

    @Test
    fun arcFlagsAndChainedDecimals() {
        assertEquals(
            "M 20.317 4.3698 a 19.7913 19.7913 0 0 0 -4.8851 -1.5152 a 0.0741 0.0741 0 0 0 -0.0785 0.0371",
            PathData.androidSafe(listOf("M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371")),
        )
    }

    @Test
    fun implicitLinetoAndFirstRelativeMoveto() {
        assertEquals("M 1 1 l 2 2 l 3 3 M 0.5 0.5 L 100 -0.15", PathData.androidSafe(listOf("m1 1 2 2 3 3", "M.5.5L1e2-1.5e-1")))
    }

    @Test
    fun numberFormatting() {
        assertEquals(listOf("0", "0", "1.2346", "-3", "0.0001"), listOf(-0.0, 0.00001, 1.234567, -3.0, 0.0001).map(PathData::format))
    }

    @Test
    fun rejectsBadPathData() {
        for (d in listOf("1 2 L 3 4", "M 1", "A 1 1 0 2 0 3 3", "M 1 2 X 3", "Z 1")) {
            assertThrows(d, IconException::class.java) { PathData.androidSafe(listOf(d)) }
        }
    }

    @Test
    fun acceptsGlyphAndReadsFillRule() {
        val glyph = SvgGlyph.parse(svg("""<title>x</title><g style="fill-rule:evenodd"><path fill="#fff" d="M0 0h1v1z"/></g>"""))
        assertEquals(SvgGlyph(ViewBox(0.0, 0.0, 24.0, 24.0), "M 0 0 h 1 v 1 z", evenOdd = true), glyph)
        assertFalse(SvgGlyph.parse(svg("""<path d="M0 0h1v1z" fill-rule="inherit"/>""")).evenOdd)
    }

    @Test
    fun concatenatesPathsAndReadsCommaViewBox() {
        val data = """<svg viewBox="-2,-2,28,20"><desc>d</desc><metadata/><path d="M0 0h1z"/><path d="m5 5h1z"/></svg>""".toByteArray()
        val glyph = SvgGlyph.parse(data)
        assertEquals(ViewBox(-2.0, -2.0, 28.0, 20.0), glyph.viewBox)
        assertEquals("M 0 0 h 1 z M 5 5 h 1 z", glyph.pathData)
    }

    @Test
    fun rejectsNonGlyphs() {
        val bodies = listOf(
            """<circle r="1"/><path d="M0 0h1v1z"/>""",
            """<path transform="scale(2)" d="M0 0h1z"/>""",
            """<path style="transform: scale(2)" d="M0 0h1z"/>""",
            """<path fill="none" stroke="#000" d="M0 0h1z"/>""",
            """<defs/><path d="M0 0h1z"/>""",
            """<path fill="url(#g)" d="M0 0h1z"/>""",
            "",
        )
        for (body in bodies) assertThrows(body, IconException::class.java) { SvgGlyph.parse(svg(body)) }
    }

    @Test
    fun rejectsBadDocuments() {
        val documents = listOf(
            "not xml",
            "<html/>",
            """<svg><path d="M0 0h1z"/></svg>""",
            """<svg viewBox="0 0 0 24"><path d="M0 0h1z"/></svg>""",
            """<svg viewBox="a b c d"><path d="M0 0h1z"/></svg>""",
            """<!DOCTYPE svg [<!ENTITY x "y">]><svg viewBox="0 0 1 1"><path d="M0 0h1z"/></svg>""",
        )
        for (doc in documents) assertThrows(doc, IconException::class.java) { SvgGlyph.parse(doc.toByteArray()) }
    }

    @Test
    fun simpleIconsDiscordGlyphNormalizes() {
        // The arc-heavy minified path that once rendered blank on Android.
        val discord = """<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Discord</title><path d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"/></svg>""".toByteArray()
        val glyph = SvgGlyph.parse(discord)
        assertTrue(glyph.pathData.startsWith("M 20.317 4.3698 a 19.7913 19.7913 0 0 0 -4.8851 -1.5152 a 0.0741 0.0741 0 0 0 -0.0785 0.0371"))
        // Every number has a leading digit, and parameters are single-space separated.
        assertFalse(Regex("(^|[\\s-])\\.\\d").containsMatchIn(glyph.pathData))
        assertFalse("  " in glyph.pathData)
        assertFalse(glyph.evenOdd)
    }
}
