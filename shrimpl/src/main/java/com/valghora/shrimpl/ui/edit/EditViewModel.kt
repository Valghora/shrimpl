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
import com.valghora.shrimpl.ui.home.signedWithOtherKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A web app's settings page (docs/SHRIMPL_SPEC.md R2.3): edits rebuild and update in place. */
class EditViewModel(private val container: AppContainer, val appId: String) : ViewModel() {
    private val _record = MutableStateFlow(container.store.get(appId))
    val record: StateFlow<WebAppRecord?> = _record.asStateFlow()

    private val _draft = MutableStateFlow(_record.value?.let(::draftOf))
    val draft: StateFlow<WebAppDraft?> = _draft.asStateFlow()

    private val _installed = MutableStateFlow<Boolean?>(null)

    /** Null until checked. */
    val installed: StateFlow<Boolean?> = _installed.asStateFlow()

    private val _otherKey = MutableStateFlow(false)

    /**
     * Installed but signed with another key (R11.7): it can't be updated, so Save and Fix uninstall it
     * first, then build it with the current key. False until checked.
     */
    val otherKey: StateFlow<Boolean> = _otherKey.asStateFlow()

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
            _otherKey.value = false
        }
    }

    val dirty: Boolean get() = _record.value?.let { _draft.value != draftOf(it) } ?: false

    /** A stored icon image, by file name. */
    fun storedImage(file: String): ByteArray = container.store.image(appId, file)

    /** Checks whether the app is installed now, off the main thread. */
    fun refresh(context: Context) {
        val record = _record.value ?: return
        viewModelScope.launch {
            val (found, key) = withContext(Dispatchers.IO) {
                (InstalledApps.find(context, record.packageName) != null) to container.keys.current()?.fingerprint
            }
            _installed.value = found
            _otherKey.value = found && signedWithOtherKey(record, key)
        }
    }

    /** Takes the form's changes, unsaved until Save (R2.3). */
    fun update(draft: WebAppDraft) {
        // The app id is fixed once the web app exists (R11.2).
        _draft.value = draft.copy(form = draft.form.copy(appId = appId))
    }

    /** Discards unsaved changes: the form goes back to the saved settings (R2.3). */
    fun reset() {
        _draft.value = _record.value?.let(::draftOf)
    }

    /**
     * Saves by building the next version and installing it over the current one (R2.3), or for an app
     * signed with another key by fixing it with the changes (R11.7); false on form errors.
     */
    fun save(): Boolean {
        val record = _record.value ?: return false
        val draft = _draft.value ?: return false
        val profile = draft.form.result().profile ?: return false
        val request = BuildRequest(profile, draft.icons, draft.newImages, existing = record)
        if (_otherKey.value) build.startFix(listOf(request)) else build.start(request)
        return true
    }

    /** Fix (R11.7): uninstalls the app, then builds its saved settings with the current key. */
    fun fix() {
        val record = _record.value ?: return
        build.startFix(listOf(BuildRequest(record.profile, record.iconOptions, newImages = emptyMap(), existing = record)))
    }

    /** Builds the stored settings again, e.g. after an uninstall or with a newer template. */
    fun rebuild() {
        val record = _record.value ?: return
        build.start(BuildRequest(record.profile, record.iconOptions, newImages = emptyMap(), existing = record))
    }

    /** Uninstalls the app with the chosen method; after it, offers to remove the record too (R2.3). */
    fun uninstall() {
        val record = _record.value ?: return
        viewModelScope.launch {
            when (val result = container.installers.preferred().uninstall(record.packageName)) {
                InstallResult.Success -> {
                    _installed.value = false
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

    /** The offer to remove the record was answered. */
    fun dismissRemove() {
        _offerRemove.value = false
    }

    /** The message was shown; it isn't shown again. */
    fun messageShown() {
        _message.value = null
    }

    private fun draftOf(record: WebAppRecord) = WebAppDraft(ProfileForm.from(record.profile), record.iconOptions)
}
