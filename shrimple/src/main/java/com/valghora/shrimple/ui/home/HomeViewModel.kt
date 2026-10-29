package com.valghora.shrimple.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.shrimple.AppContainer
import com.valghora.shrimple.build.BuildRequest
import com.valghora.shrimple.data.InstalledApp
import com.valghora.shrimple.data.InstalledApps
import com.valghora.shrimple.install.InstallResult
import com.valghora.shrimple.ui.common.BuildController
import com.valghora.shrimple.core.records.WebAppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "My Web Apps" (docs/SHRIMPLE_SPEC.md R2): the records, and what Android says about each. */
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

    private val _message = MutableStateFlow<String?>(null)

    /** The outcome of a failed uninstall, shown once. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Uninstalls the web app with the chosen install method; the record stays (R2.3). */
    fun uninstall(context: Context, record: WebAppRecord) {
        viewModelScope.launch {
            when (val result = container.installers.preferred().uninstall(record.packageName)) {
                InstallResult.Success -> refresh(context)
                InstallResult.NeedsUnknownSources -> Unit
                is InstallResult.Failure -> _message.value = result.message
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
