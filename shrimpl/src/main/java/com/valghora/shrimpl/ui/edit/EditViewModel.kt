package com.valghora.shrimpl.ui.edit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.shrimpl.AppContainer
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.ui.common.BuildController
import com.valghora.shrimpl.core.records.WebAppDraft
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.WebAppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A web app's settings page (docs/SHRIMPL_SPEC.md R2.3, R4): edits rebuild and update in place. */
class EditViewModel(private val container: AppContainer, val appId: String) : ViewModel() {
    private val _record = MutableStateFlow(container.store.get(appId))
    val record: StateFlow<WebAppRecord?> = _record.asStateFlow()

    private val _draft = MutableStateFlow(_record.value?.let(::draftOf))
    val draft: StateFlow<WebAppDraft?> = _draft.asStateFlow()

    private val _installed = MutableStateFlow<Boolean?>(null)

    /** Null until checked. */
    val installed: StateFlow<Boolean?> = _installed.asStateFlow()

    /** The outcome of the last uninstall, shown once. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Uninstall finished: offer to forget the web app too. */
    private val _offerRemove = MutableStateFlow(false)
    val offerRemove: StateFlow<Boolean> = _offerRemove.asStateFlow()

    val build = BuildController(container, viewModelScope).apply {
        onInstalled = { saved ->
            _record.value = saved
            _draft.value = draftOf(saved)
            _installed.value = true
        }
    }

    val dirty: Boolean get() = _record.value?.let { _draft.value != draftOf(it) } ?: false

    /** A stored icon image, by file name. */
    fun storedImage(file: String): ByteArray = container.store.image(appId, file)

    private val _locked = MutableStateFlow(false)

    /**
     * R11.7: installed but signed with a key other than Shrimpl's current one, so it can't be updated;
     * its settings are read-only until it is uninstalled and reinstalled.
     */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun refresh(context: Context) {
        val record = _record.value ?: return
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { InstalledApps.find(context, record.packageName) != null }
            _installed.value = installed
            _locked.value = installed && withContext(Dispatchers.IO) { container.keys.current()?.fingerprint } != record.signerFingerprint
        }
    }

    fun update(draft: WebAppDraft) {
        // The app id is fixed once the web app exists (R11.2).
        _draft.value = draft.copy(form = draft.form.copy(appId = appId))
    }

    fun reset() {
        _draft.value = _record.value?.let(::draftOf)
    }

    /** Saves by building the next version and installing it over the current one (R4.2); false on form errors. */
    fun save(): Boolean {
        val record = _record.value ?: return false
        val draft = _draft.value ?: return false
        val profile = draft.form.result().profile ?: return false
        build.start(BuildRequest(profile, draft.icons, draft.newImages, existing = record))
        return true
    }

    /** Builds the stored settings again, e.g. after an uninstall or with a newer template. */
    fun rebuild() {
        val record = _record.value ?: return
        build.start(BuildRequest(record.profile, record.iconOptions, newImages = emptyMap(), existing = record))
    }

    fun uninstall() {
        val record = _record.value ?: return
        viewModelScope.launch {
            when (val result = container.installers.preferred().uninstall(record.packageName)) {
                InstallResult.Success -> {
                    _installed.value = false
                    _locked.value = false
                    _offerRemove.value = true
                }
                InstallResult.NeedsUnknownSources, InstallResult.Cancelled -> Unit
                is InstallResult.Failure -> _message.value = result.message
            }
        }
    }

    /** Forgets the web app (R2.3); its installed copy, if any, stays. */
    fun remove(onRemoved: () -> Unit) {
        viewModelScope.launch {
            container.store.delete(appId)
            _offerRemove.value = false
            onRemoved()
        }
    }

    fun dismissRemove() {
        _offerRemove.value = false
    }

    fun messageShown() {
        _message.value = null
    }

    private fun draftOf(record: WebAppRecord) = WebAppDraft(ProfileForm.from(record.profile), record.iconOptions)
}
