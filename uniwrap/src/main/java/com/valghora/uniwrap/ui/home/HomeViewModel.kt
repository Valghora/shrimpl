package com.valghora.uniwrap.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.uniwrap.AppContainer
import com.valghora.uniwrap.build.BuildRequest
import com.valghora.uniwrap.data.InstalledApp
import com.valghora.uniwrap.data.InstalledApps
import com.valghora.uniwrap.ui.common.BuildController
import com.valghora.wrapper.core.records.WebAppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "My Web Apps" (docs/UNIWRAP_SPEC.md R2): the records, and what Android says about each. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val records: StateFlow<List<WebAppRecord>> = container.store.records

    private val _installed = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())

    /** Installed web apps by package name; refreshed whenever the screen resumes (R2.2). */
    val installed: StateFlow<Map<String, InstalledApp>> = _installed.asStateFlow()

    val templateVersion: Int get() = container.template.version

    val build = BuildController(container, viewModelScope)

    fun refresh(context: Context) {
        viewModelScope.launch {
            _installed.value = withContext(Dispatchers.IO) {
                records.value.mapNotNull { InstalledApps.find(context, it.packageName) }.associateBy { it.packageName }
            }
        }
    }

    /** Reinstall or update: the same settings as the next build (R11.3, R11.4). */
    fun rebuild(record: WebAppRecord) {
        build.start(BuildRequest(record.profile, record.icon, newImage = null, existing = record))
    }

    fun remove(record: WebAppRecord) {
        viewModelScope.launch { container.store.delete(record.appId) }
    }
}
