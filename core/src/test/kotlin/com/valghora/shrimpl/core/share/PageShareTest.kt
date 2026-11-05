package com.valghora.shrimpl.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Share requests from a page (docs/TEMPLATE_SPEC.md T6.16). */
class PageShareTest {
    @Test
    fun theLinkFollowsTheText() {
        assertEquals(
            ShareContent(1, "Board", "Look at this\nhttps://x.example/b/1"),
            PageShare.parse("""{"id":1,"title":"Board","text":"Look at this","url":"https://x.example/b/1"}"""),
        )
    }

    @Test
    fun aLinkAlreadyInTheTextIsNotRepeated() {
        assertEquals(
            ShareContent(2, "", "See https://x.example/b/1 now"),
            PageShare.parse("""{"id":2,"text":"See https://x.example/b/1 now","url":"https://x.example/b/1"}"""),
        )
    }

    @Test
    fun aLinkAloneOrATitleAloneIsShared() {
        assertEquals(ShareContent(3, "", "https://x.example/"), PageShare.parse("""{"id":3,"url":"https://x.example/"}"""))
        assertEquals(ShareContent(4, "Only a title", "Only a title"), PageShare.parse("""{"id":4,"title":" Only a title "}"""))
    }

    @Test
    fun longContentIsCut() {
        val shared = PageShare.parse("""{"id":5,"title":"${"t".repeat(600)}","text":"${"x".repeat(100_010)}"}""")!!
        assertEquals(500, shared.subject.length)
        assertEquals(100_000, shared.text.length)
    }

    @Test
    fun emptyOrMalformedRequestsShareNothing() {
        assertNull(PageShare.parse("""{"id":6}"""))
        assertNull(PageShare.parse("""{"title":"no id"}"""))
        assertNull(PageShare.parse("not json"))
    }
}
