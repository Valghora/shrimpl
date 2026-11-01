package com.valghora.shrimpl.ui.create

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.shrimpl.AppContainer
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.discovery.Discovered
import com.valghora.shrimpl.discovery.SearchFailure
import com.valghora.shrimpl.discovery.SiteDiscovery
import com.valghora.shrimpl.ui.common.BuildController
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.core.records.WebAppDraft
import com.valghora.shrimpl.core.discovery.Confidence
import com.valghora.shrimpl.core.discovery.Discovery
import com.valghora.shrimpl.core.discovery.DiscoveryException
import com.valghora.shrimpl.core.discovery.Field
import com.valghora.shrimpl.core.profile.AppIds
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.IconRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The configuration page (docs/SHRIMPL_SPEC.md R3): URL, discovery, review, create. */
class CreateViewModel(private val container: AppContainer) : ViewModel() {
    sealed interface Step {
        data object EnterUrl : Step

        /** Searching, at [stage] (R3.2). */
        data class Discovering(val stage: SiteDiscovery.Stage) : Step

        /** The search stopped at [stage] with [message]. */
        data class Failed(val stage: SiteDiscovery.Stage, val message: String) : Step

        /** Continue after a failed search: only the brand icon is looked up. */
        data object Continuing : Step

        data object Review : Step
    }

    private val _step = MutableStateFlow<Step>(Step.EnterUrl)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    /** The address isn't a web address (caught before searching, shown under the field). */
    private val _urlInvalid = MutableStateFlow(false)
    val urlInvalid: StateFlow<Boolean> = _urlInvalid.asStateFlow()

    /** Set once the page is in review. */
    private val _draft = MutableStateFlow<WebAppDraft?>(null)
    val draft: StateFlow<WebAppDraft?> = _draft.asStateFlow()

    /** Field key -> where discovery got a low- or medium-confidence value (R3.3). */
    var hints: Map<String, String> = emptyMap()
        private set
    var notes: List<Notice> = emptyList()
        private set

    /** R5.3: after the first web app, ask once to back up the signing key. */
    private val _showBackupPrompt = MutableStateFlow(false)
    val showBackupPrompt: StateFlow<Boolean> = _showBackupPrompt.asStateFlow()

    val build = BuildController(container, viewModelScope).apply {
        onInstalled = {
            if (!container.settings.keyBackedUp.value && !container.settings.backupPromptShown) {
                container.settings.backupPromptShown = true
                _showBackupPrompt.value = true
            }
        }
    }

    private var discovery: Job? = null

    fun setUrl(value: String) {
        _url.value = value
        _urlInvalid.value = false
        // A changed address starts over: an old failure no longer applies to it.
        if (_step.value is Step.Failed) _step.value = Step.EnterUrl
    }

    fun search() {
        if (SiteDiscovery.normalizeUrl(_url.value) == null) {
            _urlInvalid.value = true
            return
        }
        discovery?.cancel()
        val pacer = StepPacer()
        discovery = viewModelScope.launch {
            var stage = SiteDiscovery.Stage.FETCHING_PAGE
            try {
                val found = container.discovery.discover(_url.value) {
                    stage = it
                    pacer.show { _step.value = Step.Discovering(it) }
                }
                pacer.finish()
                apply(found)
            } catch (e: SearchFailure) {
                pacer.finish()
                _step.value = Step.Failed(e.stage, e.message.orEmpty())
            } catch (e: DiscoveryException) {
                pacer.finish()
                _step.value = Step.Failed(stage, e.message.orEmpty())
            }
        }
    }

    fun cancelSearch() {
        discovery?.cancel()
        _step.value = Step.EnterUrl
    }

    /** R3.2: each search step stays on screen for at least [MIN_STEP_MS], so none flashes past. */
    private class StepPacer {
        private var shownAt = 0L

        private fun remaining() = if (shownAt == 0L) 0L else (shownAt + MIN_STEP_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

        suspend fun show(update: () -> Unit) {
            delay(remaining())
            shownAt = SystemClock.elapsedRealtime()
            update()
        }

        suspend fun finish() = delay(remaining())
    }

    fun backToUrl() {
        _step.value = Step.EnterUrl
    }

    /** R3.2: discovery failed, so start from defaults for the address, with the simple-icons brand if one matches. */
    fun continueWithoutDiscovery() {
        val url = SiteDiscovery.normalizeUrl(_url.value) ?: return
        val host = requireNotNull(java.net.URI(url).host).lowercase().removePrefix("www.")
        val name = host.substringBefore('.').replaceFirstChar(Char::uppercaseChar)
        discovery?.cancel()
        _step.value = Step.Continuing
        discovery = viewModelScope.launch {
            val brand = container.discovery.simpleIcon(name)
            val profile = Profile(
                name = brand?.title ?: name,
                appId = AppIds.unique(brand?.title ?: name, container.store.takenIds()),
                startUrl = url,
                internalDomains = listOf(host),
                iconBackground = brand?.color ?: Discovery.DEFAULT_BRAND,
            )
            hints = emptyMap()
            notes = listOf(Notice(Notice.Kind.CAUTION, "Discovery didn't finish, so some of these settings are defaults. Verify their accuracy before building."))
            val brandIcon = brand?.let { IconRecord.glyph(it.svg, "simple-icons '${it.slug}'") }
            _draft.value = WebAppDraft.create(ProfileForm.from(profile), brandIcon, website = null)
            _step.value = Step.Review
        }
    }

    private fun apply(discovered: Discovered) {
        val found = discovered.draft
        val profile = found.toProfile().copy(appId = AppIds.unique(found.name.value, container.store.takenIds()))
        hints = buildMap {
            fun hint(key: String, field: Field<*>) {
                if (field.confidence != Confidence.HIGH) put(key, field.source)
            }
            hint("name", found.name)
            hint("startUrl", found.startUrl)
            hint("internalDomains", found.internalDomains)
            hint("iconBackground", found.iconBackground)
            hint("allowMicrophone", found.allowMicrophone)
            hint("allowCamera", found.allowCamera)
            hint("allowNotifications", found.allowNotifications)
            hint("allowLocation", found.allowLocation)
        }
        notes = found.notes.map(Notice::of)
        // R10.1: the brand glyph (when found) and the site's own icon (else a letter) are both offered.
        _draft.value = WebAppDraft.create(ProfileForm.from(profile), WebAppDraft.brandOf(found.icon), discovered.websiteIcon)
        _step.value = Step.Review
    }

    fun update(draft: WebAppDraft) {
        val current = _draft.value ?: return
        val form = draft.form
        // The app id follows the name until the web app is created (R3.3).
        val appId = if (form.name != current.form.name) AppIds.unique(form.name, container.store.takenIds()) else form.appId
        _draft.value = draft.copy(form = form.copy(appId = appId))
    }

    /** Builds and installs; false if the form has errors (they are shown next to the fields). */
    fun create(): Boolean {
        val draft = _draft.value ?: return false
        val profile = draft.form.result().profile ?: return false
        build.start(BuildRequest(profile, draft.icons, draft.newImages, existing = null))
        return true
    }

    fun backupPromptHandled() {
        _showBackupPrompt.value = false
    }

    private companion object {
        const val MIN_STEP_MS = 500L
    }
}
