package com.valghora.shrimpl.core.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestTagsTest {
    @Test
    fun readsTagsFromTheFirstWord() {
        assertEquals(setOf(TestTag.UPDATE), TestTags.parse("update Discord"))
        assertEquals(setOf(TestTag.UPDATE, TestTag.CERT), TestTags.parse("update,cert Gmail"))
        assertEquals(setOf(TestTag.UPDATE, TestTag.CERT), TestTags.parse("update,cert"))
        assertEquals(setOf(TestTag.CERT, TestTag.UNKNOWN_SOURCES), TestTags.parse("  Cert,unknown-sources  App"))
        assertEquals(TestTag.entries.toSet(), TestTags.parse(TestTag.entries.joinToString(",") { it.key }))
    }

    @Test
    fun ignoresOrdinaryNames() {
        assertEquals(emptySet<TestTag>(), TestTags.parse("Discord"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("Updates"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("Discord update"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("update,nonsense Discord"))
        assertEquals(emptySet<TestTag>(), TestTags.parse("update, Discord"))
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
