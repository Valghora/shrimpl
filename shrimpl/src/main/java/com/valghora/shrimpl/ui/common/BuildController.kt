package com.valghora.shrimpl.ui.common

import com.valghora.shrimpl.AppContainer
import com.valghora.shrimpl.build.BuildOutcome
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.build.WebAppFactory
import com.valghora.shrimpl.install.Installer
import com.valghora.shrimpl.core.records.WebAppRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the build sheet shows (docs/SHRIMPL_SPEC.md R3.4). */
sealed interface BuildState {
    /** [batch]: which app of an Update all is being built; null for a single build. */
    data class Running(val stage: WebAppFactory.Stage, val batch: BatchProgress? = null) : BuildState

    data class Done(val record: WebAppRecord) : BuildState

    /** Update all finished: the apps that were updated, and the ones that failed with why. */
    data class BatchDone(val updated: List<String>, val failed: List<Pair<String, String>>) : BuildState

    /** The system installer needs "Install unknown apps" for Shrimpl (R6.3). */
    data object NeedsUnknownSources : BuildState

    /** [offerSystemInstaller]: Shizuku or root stopped working; the system installer can do this one (R6.2). */
    data class Failed(val message: String, val offerSystemInstaller: Boolean) : BuildState
}

/** The app being built during Update all: [index] (from 1) of [total]. */
data class BatchProgress(val index: Int, val total: Int, val name: String)

/** Runs builds for a screen's ViewModel and exposes their progress. */
class BuildController(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<BuildState?>(null)
    val state: StateFlow<BuildState?> = _state.asStateFlow()

    private var job: Job? = null
    private var lastRequest: BuildRequest? = null

    /** Update all's builds not done yet, with what is done so far, to resume after a permission prompt. */
    private var pendingBatch: List<BuildRequest>? = null
    private var batchUpdated = emptyList<String>()
    private var batchFailed = emptyList<Pair<String, String>>()
    private var batchTotal = 0

    /** Called with the record after each successful install. */
    var onInstalled: (WebAppRecord) -> Unit = {}

    fun start(request: BuildRequest, installer: Installer? = null) {
        if (job?.isActive == true) return
        lastRequest = request
        pendingBatch = null
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

    /**
     * Update all (R2.4): builds [requests] one after another. A failure doesn't stop the rest; the
     * sheet ends with what was updated and what failed. Only "Install unknown apps" pauses the run,
     * which [retry] then resumes.
     */
    fun startAll(requests: List<BuildRequest>) {
        if (job?.isActive == true || requests.isEmpty()) return
        batchUpdated = emptyList()
        batchFailed = emptyList()
        batchTotal = requests.size
        runBatch(requests)
    }

    private fun runBatch(requests: List<BuildRequest>) {
        pendingBatch = requests
        job = scope.launch {
            val installer = container.installers.preferred()
            for ((i, request) in requests.withIndex()) {
                val progress = BatchProgress(batchTotal - requests.size + i + 1, batchTotal, request.profile.name)
                _state.value = BuildState.Running(WebAppFactory.Stage.RENDERING_ICON, progress)
                val outcome = container.factory.build(request, installer) { _state.value = BuildState.Running(it, progress) }
                when (outcome) {
                    is BuildOutcome.Installed -> {
                        batchUpdated = batchUpdated + request.profile.name
                        onInstalled(outcome.record)
                    }
                    BuildOutcome.NeedsUnknownSources -> {
                        pendingBatch = requests.drop(i)
                        _state.value = BuildState.NeedsUnknownSources
                        return@launch
                    }
                    is BuildOutcome.Failed -> batchFailed = batchFailed + (request.profile.name to outcome.message)
                }
            }
            pendingBatch = null
            _state.value = BuildState.BatchDone(batchUpdated, batchFailed)
        }
    }

    /** Runs the last request again (or resumes Update all), optionally with the system installer. */
    fun retry(withSystemInstaller: Boolean = false) {
        pendingBatch?.let {
            if (job?.isActive != true) runBatch(it)
            return
        }
        val request = lastRequest ?: return
        start(request, if (withSystemInstaller) container.installers.session else null)
    }

    /** Stops a build before it is handed to the installer. */
    fun cancel() {
        job?.cancel()
        pendingBatch = null
        _state.value = null
    }

    fun dismiss() {
        if (job?.isActive != true) {
            pendingBatch = null
            _state.value = null
        }
    }
}
