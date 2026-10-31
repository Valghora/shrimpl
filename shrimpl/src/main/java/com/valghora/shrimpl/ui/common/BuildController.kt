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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the build sheet shows (docs/SHRIMPL_SPEC.md R3.4). */
sealed interface BuildState {
    /**
     * Building [name]: a new app, or an update of an existing one ([update]). [batch]: which app of
     * an Update all it is; null for a single build. [waitingForUser]: installing, and the system
     * installer is waiting for the user to confirm (R6.5).
     */
    data class Running(
        val stage: WebAppFactory.Stage,
        val name: String,
        val update: Boolean,
        val batch: BatchProgress? = null,
        val waitingForUser: Boolean = false,
    ) : BuildState

    data class Done(val record: WebAppRecord) : BuildState

    /** Update all finished: the apps that were updated, and the ones that failed with why. */
    data class BatchDone(val updated: List<String>, val failed: List<Pair<String, String>>) : BuildState

    /** The system installer needs "Install unknown apps" for Shrimpl (R6.3). */
    data object NeedsUnknownSources : BuildState

    /** [offerSystemInstaller]: Shizuku or root stopped working; the system installer can do this one (R6.2). */
    data class Failed(val message: String, val offerSystemInstaller: Boolean) : BuildState
}

/** The app being built during Update all: [index] (from 1) of [total]. */
data class BatchProgress(val index: Int, val total: Int)

/** Runs builds for a screen's ViewModel and exposes their progress. */
class BuildController(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<BuildState?>(null)
    val state: StateFlow<BuildState?> = combine(_state, container.installers.session.confirmation) { state, confirmation ->
        if (state is BuildState.Running && state.stage == WebAppFactory.Stage.INSTALLING && confirmation != null) {
            state.copy(waitingForUser = true)
        } else {
            state
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

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
        fun running(stage: WebAppFactory.Stage) = BuildState.Running(stage, request.profile.name, update = request.existing != null)
        _state.value = running(WebAppFactory.Stage.RENDERING_ICON)
        job = scope.launch {
            val chosen = installer ?: container.installers.preferred()
            val outcome = container.factory.build(request, chosen) { _state.value = running(it) }
            _state.value = when (outcome) {
                is BuildOutcome.Installed -> BuildState.Done(outcome.record).also { onInstalled(outcome.record) }
                BuildOutcome.NeedsUnknownSources -> BuildState.NeedsUnknownSources
                BuildOutcome.Cancelled -> null
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
                val progress = BatchProgress(batchTotal - requests.size + i + 1, batchTotal)
                fun running(stage: WebAppFactory.Stage) = BuildState.Running(stage, request.profile.name, update = true, batch = progress)
                _state.value = running(WebAppFactory.Stage.RENDERING_ICON)
                val outcome = container.factory.build(request, installer) { _state.value = running(it) }
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
                    // Cancelling one install stops Update all; the rest keep their Update buttons.
                    BuildOutcome.Cancelled -> break
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

    /** Brings back the system installer's confirmation screen the user left unfinished (R6.5). */
    fun showInstaller() = container.installers.session.showConfirmation()

    /**
     * Stops a build before it is handed to the installer, or, while the system installer waits for
     * the user, cancels that install (R6.5); the build then ends as cancelled.
     */
    fun cancel() {
        val session = container.installers.session
        if (session.confirmation.value != null && job?.isActive == true) {
            session.cancelConfirmation()
            return
        }
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
