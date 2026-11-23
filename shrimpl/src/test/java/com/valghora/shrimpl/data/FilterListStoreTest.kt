package com.valghora.shrimpl.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.valghora.shrimpl.core.AdBlocking
import com.valghora.shrimpl.core.adblock.FilterList
import com.valghora.shrimpl.ui.TestApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Shrimpl keeps the lists its apps use (docs/SHRIMPL_SPEC.md R15.1), and none before a good download. */
@RunWith(RobolectricTestRunner::class)
class FilterListStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theListsKeptAreThoseSomeAppsLevelUses() {
        val off = TestApps.example
        val ads = TestApps.squoosh.copy(profile = TestApps.squoosh.profile.copy(adBlocking = AdBlocking.ADS))
        val trackers = TestApps.squoosh.copy(profile = TestApps.squoosh.profile.copy(adBlocking = AdBlocking.ADS_AND_TRACKERS))
        assertEquals(emptySet<FilterList>(), FilterListStore.needed(listOf(off)))
        assertEquals(setOf(FilterList.EASYLIST, FilterList.WARNINGS), FilterListStore.needed(listOf(off, ads)))
        assertEquals(FilterList.entries.toSet(), FilterListStore.needed(listOf(ads, trackers)))
    }

    @Test
    fun beforeTheFirstGoodDownloadThereIsNoCopy() {
        val store = FilterListStore(context)
        assertNull(store.file(FilterList.EASYLIST))
        assertEquals(0L, store.version(FilterList.EASYLIST))
    }
}
