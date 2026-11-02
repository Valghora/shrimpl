package com.valghora.shrimpl.core.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestTagsTest {
    @Test
    fun readsTagsFromTheFirstWord() {
        assertEquals(setOf(TestTag.UPDATE), TestTags.parse("update Example"))
        assertEquals(setOf(TestTag.UPDATE, TestTag.CERT), TestTags.parse("update,cert Gmail"))
        assertEquals(setOf(TestTag.UPDATE, TestTag.CERT), TestTags.parse("update,cert"))
        assertEquals(setOf(TestTag.CERT, TestTag.UNKNOWN_SOURCES), TestTags.parse("  Cert,unknown-sources  App"))
        assertEquals(TestTag.entries.toSet(), TestTags.parse(TestTag.entries.joinToString(",") { it.key }))
    }

    @Test
    fun acceptsBracketsAndSpacesAfterCommas() {
        assertEquals(setOf(TestTag.MISSING), TestTags.parse("[missing] Example"))
        assertEquals(setOf(TestTag.UPDATE, TestTag.CERT), TestTags.parse("update, cert Gmail"))
        assertEquals(setOf(TestTag.UPDATE, TestTag.CERT), TestTags.parse("[ update , cert ] Gmail"))
        assertEquals(setOf(TestTag.MISSING), TestTags.parse("Missing"))
    }

    @Test
    fun ignoresOrdinaryNames() {
        assertEquals(emptySet<TestTag>(), TestTags.parse("Example"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("Updates"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("Example update"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("update,nonsense Example"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("update, Example"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("missing-link Example"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("update2 Example"))
        assertEquals(emptySet<TestTag>(), TestTags.parse(""))
    }

    @Test
    fun findsTestSearchAddresses() {
        assertEquals(TestSearch.NOT_FOUND, TestTags.search("404.test"))
        assertEquals(TestSearch.DENIED, TestTags.search("WWW.403.Test"))
        TestSearch.entries.forEach { assertEquals(it, TestTags.search(it.host)) }
        assertNull(TestTags.search("example.com"))
        assertNull(TestTags.search("other.test"))
    }
}
