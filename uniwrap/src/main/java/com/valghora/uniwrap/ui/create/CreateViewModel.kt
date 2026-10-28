package com.valghora.uniwrap.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.uniwrap.AppContainer
import com.valghora.uniwrap.build.BuildRequest
import com.valghora.uniwrap.discovery.SiteDiscovery
import com.valghora.uniwrap.ui.common.BuildController
import com.valghora.uniwrap.ui.common.Notice
import com.valghora.uniwrap.ui.form.WebAppDraft
import com.valghora.uniwrap.ui.icons.IconSelection
import com.valghora.wrapper.core.discovery.Confidence
import com.valghora.wrapper.core.discovery.Discovery
import com.valghora.wrapper.core.discovery.DiscoveryException
import com.valghora.wrapper.core.discovery.Draft
import com.valghora.wrapper.core.discovery.Field
import com.valghora.wrapper.core.profile.AppIds
import com.valghora.wrapper.core.profile.Profile
import com.valghora.wrapper.core.profile.ProfileForm
import com.valghora.wrapper.core.records.IconRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The configuration page (docs/UNIWRAP_SPEC.md R3): URL, discovery, review, create. */
class CreateViewModel(private val container: AppContainer) : ViewModel() {
    sealed interface Step {
        data object EnterUrl : Step

        data class Discovering(val stage: SiteDiscovery.Stage) : Step

        data class Failed(val message: String) : Step

        data object Review : Step
    }

    private val _step = MutableStateFlow<Step>(Step.EnterUrl)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    /** Set once the page is in review. */
    private val _draft = MutableStateFlow<WebAppDraft?>(null)
    val draft: StateFlow<WebAppDraft?> = _draft.asStateFlow()

    /** Field key -> where discovery got a low- or medium-confidence value (R3.3). */
    var hints: Map<String, String> = emptyMap()
        private set
    var notes: List<Notice> = emptyList()
        private set

    /** Discovery's own icon, offered again in the icon editor. */
    var siteIcon: IconSelection? = null
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
    }

    fun search() {
        if (SiteDiscovery.normalizeUrl(_url.value) == null) {
            _step.value = Step.Failed(INVALID_URL)
            return
        }
        discovery?.cancel()
        _step.value = Step.Discovering(SiteDiscovery.Stage.FETCHING_PAGE)
        discovery = viewModelScope.launch {
            try {
                val found = container.discovery.discover(_url.value) { _step.value = Step.Discovering(it) }
                apply(found)
            } catch (e: DiscoveryException) {
                _step.value = Step.Failed(e.message ?: "Discovery failed")
            }
        }
    }

    fun cancelSearch() {
        discovery?.cancel()
        _step.value = Step.EnterUrl
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
        _step.value = Step.Discovering(SiteDiscovery.Stage.FINDING_ICON)
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
            notes = listOf(Notice(Notice.Kind.CAUTION, "Discovery didn't finish, so these are defaults. Check the icon color and the domains."))
            siteIcon = null
            val icon = brand?.let { IconRecord.glyph(it.svg, "simple-icons '${it.slug}'") } ?: IconRecord.letter(name.first(), "Letter")
            _draft.value = WebAppDraft(ProfileForm.from(profile), icon, null)
            _step.value = Step.Review
        }
    }

    private fun apply(found: Draft) {
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
        val (icon, image) = IconRecord.from(found.icon)
        val draft = WebAppDraft(ProfileForm.from(profile), icon, image)
        siteIcon = draft.iconSelection
        _draft.value = draft
        _step.value = Step.Review
    }

    fun update(form: ProfileForm) {
        val current = _draft.value ?: return
        // The app id follows the name until the web app is created (R3.3).
        val appId = if (form.name != current.form.name) AppIds.unique(form.name, container.store.takenIds()) else form.appId
        _draft.value = current.copy(form = form.copy(appId = appId))
    }

    fun setIcon(selection: IconSelection) {
        _draft.value = _draft.value?.withIcon(selection)
    }

    private val _iconDraft = MutableStateFlow<IconSelection?>(null)

    /** The icon editor's selection while it is open; kept here so it survives configuration changes. */
    val iconDraft: StateFlow<IconSelection?> = _iconDraft.asStateFlow()

    fun openIconEditor() {
        _iconDraft.value = _draft.value?.iconSelection
    }

    fun changeIcon(selection: IconSelection) {
        _iconDraft.value = selection
    }

    /** Closes the icon editor, keeping its selection when [apply]. */
    fun closeIconEditor(apply: Boolean) {
        val selection = _iconDraft.value
        _iconDraft.value = null
        if (apply && selection != null) setIcon(selection)
    }

    /** Builds and installs; false if the form has errors (they are shown next to the fields). */
    fun create(): Boolean {
        val draft = _draft.value ?: return false
        val profile = draft.form.result().profile ?: return false
        build.start(BuildRequest(profile, draft.icon, draft.newImage, existing = null))
        return true
    }

    fun backupPromptHandled() {
        _showBackupPrompt.value = false
    }

    private companion object {
        const val INVALID_URL = "Enter a web address, like example.com"
    }
}
