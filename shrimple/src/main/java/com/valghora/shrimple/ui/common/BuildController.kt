package com.valghora.shrimple.ui.common

import com.valghora.shrimple.AppContainer
import com.valghora.shrimple.build.BuildOutcome
import com.valghora.shrimple.build.BuildRequest
import com.valghora.shrimple.build.WebAppFactory
import com.valghora.shrimple.install.Installer
import com.valghora.shrimple.core.records.WebAppRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the build sheet shows (docs/SHRIMPLE_SPEC.md R3.4). */
sealed interface BuildState {
    data class Running(val stage: WebAppFactory.Stage) : BuildState

    data class Done(val record: WebAppRecord) : BuildState

    /** The system installer needs "Install unknown apps" for Shrimple (R6.3). */
    data object NeedsUnknownSources : BuildState

    /** [offerSystemInstaller]: Shizuku or root stopped working; the system installer can do this one (R6.2). */
    data class Failed(val message: String, val offerSystemInstaller: Boolean) : BuildState
}

/** Runs builds for a screen's ViewModel and exposes their progress. */
class BuildController(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<BuildState?>(null)
    val state: StateFlow<BuildState?> = _state.asStateFlow()

    private var job: Job? = null
    private var lastRequest: BuildRequest? = null

    /** Called with the record after each successful install. */
    var onInstalled: (WebAppRecord) -> Unit = {}

    fun start(request: BuildRequest, installer: Installer? = null) {
        if (job?.isActive == true) return
        lastRequest = request
        _state.value = BuildState.Running(WebAppFactory.Stage.RENDERING_ICON)
        job = scope.launch {
            val chosen = installer ?: container.installers.preferred()
            val outcome = container.factory.build(request, chosen) { _state.value = BuildState.Running(it) }
            _state.value = when (outcome) {
                is BuildOutcome.Installed -> BuildState.Done(outcome.record).also { onInstalled(outcome.record) }
                BuildOutcome.NeedsUnknownSources -> BuildState.NeedsUnknownSources
                is BuildOutcome.Failed -> BuildState.Failed(outcome.message, outcome.installMethodUnavailable)
            }
        }
    }

    /** Runs the last request again, optionally with the system installer. */
    fun retry(withSystemInstaller: Boolean = false) {
        val request = lastRequest ?: return
        start(request, if (withSystemInstaller) container.installers.session else null)
    }

    /** Stops a build before it is handed to the installer. */
    fun cancel() {
        job?.cancel()
        _state.value = null
    }

    fun dismiss() {
        if (job?.isActive != true) _state.value = null
    }
}
