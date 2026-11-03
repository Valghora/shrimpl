package com.valghora.shrimpl.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.shrimpl.AppContainer
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.core.records.WebAppRecord
import com.valghora.shrimpl.core.testing.TestTag
import com.valghora.shrimpl.data.ClickAction
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.data.InstalledApp
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.testing.TestMode
import com.valghora.shrimpl.ui.common.BuildController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "My Web Apps" (docs/SHRIMPL_SPEC.md R2): the records, and what Android says about each. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val records: StateFlow<List<WebAppRecord>> = container.store.records

    private val _installed = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())

    /** Installed web apps by package name; refreshed whenever the screen resumes (R2.2). */
    val installed: StateFlow<Map<String, InstalledApp>> = _installed.asStateFlow()

    val templateVersion: Int get() = container.template.version

    /** What tapping an app does (R2.5). */
    val clickAction: StateFlow<ClickAction> = container.settings.clickAction
    val layout: StateFlow<HomeLayout> = container.settings.homeLayout

    /** Tapping an installed app with a pending action does that action (R2.5). */
    val resolveOnTap: StateFlow<Boolean> = container.settings.resolveOnTap

    /** Switches between rows and tiles, and keeps the choice (R2.6). */
    fun setLayout(layout: HomeLayout) = container.settings.setHomeLayout(layout)

    val build = BuildController(container, viewModelScope)

    private val _signingKey = MutableStateFlow<SigningKeyCheck>(SigningKeyCheck.Unknown)

    /** Shrimpl's current signing key, once read; apps signed with another one can't be updated (R11.6). */
    val signingKey: StateFlow<SigningKeyCheck> = _signingKey.asStateFlow()

    init {
        // R11.6: after a reinstall, list the web apps the previous install built.
        viewModelScope.launch { container.recovery.runOnce() }
    }

    /** Reads which apps are installed and the current signing key again, off the main thread (R2.2). */
    fun refresh(context: Context) {
        viewModelScope.launch {
            _installed.value = withContext(Dispatchers.IO) {
                records.value.mapNotNull { InstalledApps.find(context, it.packageName) }.associateBy { it.packageName }
            }
            _signingKey.value = SigningKeyCheck.Read(withContext(Dispatchers.IO) { container.keys.current()?.fingerprint })
        }
    }

    /**
     * The [installed] apps last signed with a key other than Shrimpl's current one (none until the key
     * is read, R11.7): they can't be updated, only uninstalled and rebuilt. An app that isn't installed
     * is left out, since any build installs fresh with the current key.
     */
    fun otherKey(records: List<WebAppRecord>, installed: Map<String, InstalledApp>, key: SigningKeyCheck): Set<String> {
        val real = if (key is SigningKeyCheck.Read) records.filter { it.signerFingerprint != key.fingerprint } else emptyList()
        // R13: apps tagged cert behave as if signed with another key.
        return (real + records.filter { TestTag.CERT in TestMode.tags(it.profile.name) })
            .filter { it.packageName in installed }
            .mapTo(mutableSetOf()) { it.appId }
    }

    /** R13: the installed apps as the list shows them; apps tagged missing count as not installed. */
    fun shownInstalled(records: List<WebAppRecord>, installed: Map<String, InstalledApp>): Map<String, InstalledApp> {
        val missing = records.filter { TestTag.MISSING in TestMode.tags(it.profile.name) }.mapTo(mutableSetOf()) { it.packageName }
        return if (missing.isEmpty()) installed else installed - missing
    }

    /** Built with an older template than the bundled one (R11.4), or tagged update (R13). */
    fun updateAvailable(record: WebAppRecord): Boolean =
        record.updateAvailable(templateVersion) || TestTag.UPDATE in TestMode.tags(record.profile.name)

    /** Fix and Fix all (R11.7): uninstalls [records], then rebuilds them signed with the current key. */
    fun fix(records: List<WebAppRecord>) {
        build.startFix(records.map(::requestFor))
    }

    /** The apps the different-key notice was dismissed for (R11.7). */
    val keyNoticeDismissed: StateFlow<Set<String>> = container.settings.keyNoticeDismissed

    /** Hides the different-key notice for [appIds] (R11.7). */
    fun dismissKeyNotice(appIds: Set<String>) = container.settings.dismissKeyNotice(appIds)

    /** Rebuilds [record] from its saved settings with the next build number: Reinstall, Update or Rebuild (R11.3, R11.4). */
    fun rebuild(record: WebAppRecord) {
        build.start(requestFor(record))
    }

    /** The install method; Update all asks first when it is the system installer, which prompts for each app. */
    val installMethod: StateFlow<InstallMethod> = container.settings.installMethod

    /** Installed apps built with an older template than the bundled one (R11.4), except those [otherKey] can't update. */
    fun outdated(records: List<WebAppRecord>, installed: Map<String, InstalledApp>, otherKey: Set<String>): List<WebAppRecord> =
        records.filter { updateAvailable(it) && it.packageName in installed && it.appId !in otherKey }

    /** Update all (R2.4): rebuilds [records] with the bundled template, one after another. */
    fun updateAll(records: List<WebAppRecord>) {
        build.startAll(records.map(::requestFor))
    }

    private fun requestFor(record: WebAppRecord) = BuildRequest(record.profile, record.iconOptions, newImages = emptyMap(), existing = record)

    /** Removes [record] from Shrimpl; the installed app, if any, stays. */
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
                InstallResult.NeedsUnknownSources, InstallResult.Cancelled -> Unit
                is InstallResult.Failure -> _message.value = result.message
            }
        }
    }

    /** The message was shown; it isn't shown again. */
    fun messageShown() {
        _message.value = null
    }
}

/** Shrimpl's signing key as Home knows it: not read yet, or read ([fingerprint] null when there is none yet). */
sealed interface SigningKeyCheck {
    data object Unknown : SigningKeyCheck

    data class Read(val fingerprint: String?) : SigningKeyCheck
}
