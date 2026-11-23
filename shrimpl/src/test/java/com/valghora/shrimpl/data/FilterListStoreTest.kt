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
    fun theListsKeptAreThoseSomeAppsSettingsUse() {
        val off = TestApps.example
        val ads = TestApps.squoosh.copy(profile = TestApps.squoosh.profile.copy(adBlocking = AdBlocking.ADS))
        val trackers = TestApps.squoosh.copy(profile = TestApps.squoosh.profile.copy(adBlocking = AdBlocking.ADS_AND_TRACKERS))
        val banners = TestApps.example.copy(profile = TestApps.example.profile.copy(hideAppBanners = true))
        assertEquals(emptySet<FilterList>(), FilterListStore.needed(listOf(off)))
        assertEquals(setOf(FilterList.EASYLIST, FilterList.WARNINGS), FilterListStore.needed(listOf(off, ads)))
        assertEquals(setOf(FilterList.EASYLIST, FilterList.EASYPRIVACY, FilterList.WARNINGS), FilterListStore.needed(listOf(ads, trackers)))
        // R15.1, T6.22: Hide app banners needs its list even with Ad blocking Off.
        assertEquals(setOf(FilterList.APP_BANNERS), FilterListStore.needed(listOf(banners)))
        assertEquals(FilterList.entries.toSet(), FilterListStore.needed(listOf(trackers, banners)))
    }

    @Test
    fun beforeTheFirstGoodDownloadThereIsNoCopy() {
        val store = FilterListStore(context)
        assertNull(store.file(FilterList.EASYLIST))
        assertEquals(0L, store.version(FilterList.EASYLIST))
    }
}
