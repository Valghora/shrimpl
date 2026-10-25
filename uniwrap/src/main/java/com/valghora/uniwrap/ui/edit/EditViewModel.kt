package com.valghora.uniwrap.ui.edit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.uniwrap.AppContainer
import com.valghora.uniwrap.build.BuildRequest
import com.valghora.uniwrap.data.InstalledApps
import com.valghora.uniwrap.install.InstallResult
import com.valghora.uniwrap.ui.common.BuildController
import com.valghora.uniwrap.ui.form.WebAppDraft
import com.valghora.uniwrap.ui.icons.IconSelection
import com.valghora.wrapper.core.profile.ProfileForm
import com.valghora.wrapper.core.records.WebAppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A web app's settings page (docs/UNIWRAP_SPEC.md R2.3, R4): edits rebuild and update in place. */
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

    fun storedImage(): ByteArray = container.store.image(appId)

    fun refresh(context: Context) {
        val record = _record.value ?: return
        viewModelScope.launch {
            _installed.value = withContext(Dispatchers.IO) { InstalledApps.find(context, record.packageName) != null }
        }
    }

    fun update(form: ProfileForm) {
        _draft.value = _draft.value?.copy(form = form.copy(appId = appId))
    }

    fun setIcon(selection: IconSelection) {
        _draft.value = _draft.value?.withIcon(selection)
    }

    fun reset() {
        _draft.value = _record.value?.let(::draftOf)
    }

    /** Saves by building the next version and installing it over the current one (R4.2); false on form errors. */
    fun save(): Boolean {
        val record = _record.value ?: return false
        val draft = _draft.value ?: return false
        val profile = draft.form.result().profile ?: return false
        build.start(BuildRequest(profile, draft.icon, draft.newImage, existing = record))
        return true
    }

    /** Builds the stored settings again, e.g. after an uninstall or with a newer template. */
    fun rebuild() {
        val record = _record.value ?: return
        build.start(BuildRequest(record.profile, record.icon, newImage = null, existing = record))
    }

    fun uninstall() {
        val record = _record.value ?: return
        viewModelScope.launch {
            when (val result = container.installers.preferred().uninstall(record.packageName)) {
                InstallResult.Success -> {
                    _installed.value = false
                    _offerRemove.value = true
                }
                InstallResult.NeedsUnknownSources -> Unit
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

    private fun draftOf(record: WebAppRecord) = WebAppDraft(ProfileForm.from(record.profile), record.icon, null)
}
