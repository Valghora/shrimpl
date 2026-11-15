package com.valghora.shrimpl.ui.common

import com.valghora.shrimpl.AppContainer
import com.valghora.shrimpl.build.BuildOutcome
import com.valghora.shrimpl.build.BuildRequest
import com.valghora.shrimpl.build.WebAppFactory
import com.valghora.shrimpl.core.records.WebAppRecord
import com.valghora.shrimpl.core.testing.TestTag
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.install.Installer
import com.valghora.shrimpl.testing.TestMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the build sheet shows (docs/SHRIMPL_SPEC.md R3.4). */
sealed interface BuildState {
    /**
     * Building [name], as a new app, an update or a reinstall ([kind]). [batch]: which app of an
     * Update all or Fix all it is; null for a single build. [uninstalling]: for Fix (R11.7), whether
     * the old copy is still being uninstalled (true) or already is (false); null without that step.
     * [waitingForUser]: installing or uninstalling, and the system is waiting for the user to
     * confirm (R6.5).
     */
    data class Running(
        val stage: WebAppFactory.Stage,
        val name: String,
        val kind: BuildKind,
        val batch: BatchProgress? = null,
        val uninstalling: Boolean? = null,
        val waitingForUser: Boolean = false,
    ) : BuildState {
        /** The system's own screen is in charge: installing, or uninstalling for Fix. */
        val withSystem: Boolean get() = stage == WebAppFactory.Stage.INSTALLING || uninstalling == true
    }

    data class Done(val record: WebAppRecord) : BuildState

    /** Update all or Fix all ([fixed]) finished: the apps that were updated or fixed, and the ones that failed with why. */
    data class BatchDone(val updated: List<String>, val failed: List<Pair<String, String>>, val fixed: Boolean = false) : BuildState

    /** The system installer needs "Install unknown apps" for Shrimpl (R6.3). */
    data object NeedsUnknownSources : BuildState

    /** [offerSystemInstaller]: Shevery or root stopped working; the system installer can do this one (R6.2). */
    data class Failed(val message: String, val offerSystemInstaller: Boolean) : BuildState
}

/** What a build does: makes a new app, updates an installed one, or installs a known one again. */
enum class BuildKind { CREATE, UPDATE, REINSTALL }

/** The app being built during Update all or Fix all: [index] (from 1) of [total]. */
data class BatchProgress(val index: Int, val total: Int)

/** One build of a batch; [uninstallFirst] for Fix (R11.7): the installed copy is uninstalled first. */
private data class BatchItem(val request: BuildRequest, val uninstallFirst: Boolean)

/** Runs builds for a screen's ViewModel and exposes their progress. */
class BuildController(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<BuildState?>(null)
    val state: StateFlow<BuildState?> = combine(_state, container.installers.session.confirmation) { state, confirmation ->
        if (state is BuildState.Running && state.withSystem && confirmation != null) {
            state.copy(waitingForUser = true)
        } else {
            state
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var job: Job? = null
    private var lastRequest: BuildRequest? = null

    /** Update all's or Fix all's builds not done yet, with what is done so far, to resume after a permission prompt. */
    private var pendingBatch: List<BatchItem>? = null
    private var batchUpdated = emptyList<String>()
    private var batchFailed = emptyList<Pair<String, String>>()
    private var batchTotal = 0

    /** The batch is Fix all, kept across a resume (whose first item is no longer uninstalled first). */
    private var batchFixing = false

    /** Called with the record after each successful install. */
    var onInstalled: (WebAppRecord) -> Unit = {}

    /**
     * Builds and installs one app with [installer], or the method
     * chosen in Settings; does nothing while a build runs.
     */
    fun start(request: BuildRequest, installer: Installer? = null) {
        if (job?.isActive == true) return
        lastRequest = request
        pendingBatch = null
        job = scope.launch {
            val kind = kindOf(request)
            fun running(stage: WebAppFactory.Stage) = BuildState.Running(stage, request.profile.name, kind)
            _state.value = running(WebAppFactory.Stage.RENDERING_ICON)
            val chosen = installer ?: container.installers.preferred()
            val tags = simulated(request)
            val outcome = if (tags != null) {
                simulate(requireNotNull(request.existing), tags, uninstallFirst = false) { stage, _ -> _state.value = running(stage) }
            } else {
                container.factory.build(request, chosen) { _state.value = running(it) }
            }
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
    fun startAll(requests: List<BuildRequest>) = startBatch(requests.map { BatchItem(it, uninstallFirst = false) })

    /**
     * Fix and Fix all (R11.7): for each app signed with another key, uninstalls the installed copy,
     * then builds it again from its record with the current key. Cancelling an uninstall stops the
     * run; one app alone ends like a single build.
     */
    fun startFix(requests: List<BuildRequest>) = startBatch(requests.map { BatchItem(it, uninstallFirst = true) })

    private fun startBatch(items: List<BatchItem>) {
        if (job?.isActive == true || items.isEmpty()) return
        lastRequest = items.singleOrNull()?.request
        batchUpdated = emptyList()
        batchFailed = emptyList()
        batchTotal = items.size
        batchFixing = items.any { it.uninstallFirst }
        runBatch(items)
    }

    private fun runBatch(items: List<BatchItem>) {
        pendingBatch = items
        job = scope.launch {
            val installer = container.installers.preferred()
            val single = batchTotal == 1
            var lastInstalled: WebAppRecord? = null
            for ((i, item) in items.withIndex()) {
                val request = item.request
                val progress = if (single) null else BatchProgress(batchTotal - items.size + i + 1, batchTotal)
                val kind = if (item.uninstallFirst) BuildKind.REINSTALL else kindOf(request)
                val uninstallStep = if (item.uninstallFirst) false else null
                fun running(stage: WebAppFactory.Stage, uninstalling: Boolean? = uninstallStep) =
                    BuildState.Running(stage, request.profile.name, kind, batch = progress, uninstalling = uninstalling)
                val tags = simulated(request)
                if (item.uninstallFirst && tags == null) {
                    _state.value = running(WebAppFactory.Stage.RENDERING_ICON, uninstalling = true)
                    when (val result = installer.uninstall(requireNotNull(request.existing) { "Fix needs an existing app" }.packageName)) {
                        InstallResult.Success -> Unit
                        // Turning the uninstall down stops the run; this app stays as it was.
                        InstallResult.NeedsUnknownSources, InstallResult.Cancelled -> break
                        is InstallResult.Failure -> {
                            batchFailed = batchFailed + (request.profile.name to result.message)
                            continue
                        }
                    }
                }
                val outcome = if (tags != null) {
                    simulate(requireNotNull(request.existing), tags, item.uninstallFirst) { stage, uninstalling -> _state.value = running(stage, uninstalling) }
                } else {
                    _state.value = running(WebAppFactory.Stage.RENDERING_ICON)
                    container.factory.build(request, installer) { _state.value = running(it) }
                }
                when (outcome) {
                    is BuildOutcome.Installed -> {
                        batchUpdated = batchUpdated + request.profile.name
                        lastInstalled = outcome.record
                        onInstalled(outcome.record)
                    }
                    BuildOutcome.NeedsUnknownSources -> {
                        // Resumed by retry(); this app is already uninstalled, so it isn't uninstalled again.
                        pendingBatch = listOf(item.copy(uninstallFirst = false)) + items.drop(i + 1)
                        _state.value = BuildState.NeedsUnknownSources
                        return@launch
                    }
                    // Cancelling one install stops the run; the rest keep their buttons.
                    BuildOutcome.Cancelled -> break
                    is BuildOutcome.Failed -> batchFailed = batchFailed + (request.profile.name to outcome.message)
                }
            }
            pendingBatch = null
            _state.value = when {
                !single -> BuildState.BatchDone(batchUpdated, batchFailed, fixed = batchFixing)
                lastInstalled != null -> BuildState.Done(lastInstalled)
                batchFailed.isNotEmpty() -> BuildState.Failed(batchFailed.first().second, offerSystemInstaller = false)
                else -> null
            }
        }
    }

    /**
     * R13.2: the test tags of a build that plays back instead of building: an existing app whose name
     * has tags and keeps them. Creating an app, or saving one with its tags removed, builds for real.
     */
    private fun simulated(request: BuildRequest): Set<TestTag>? {
        val existing = request.existing ?: return null
        val tags = TestMode.tags(existing.profile.name)
        return tags.takeIf { it.isNotEmpty() && TestMode.tags(request.profile.name).isNotEmpty() }
    }

    /**
     * R13.2: walks the sheet for [record] through the stages ([uninstallFirst] adds Uninstalling) at the usual pace
     * without touching the app or its record, then ends as the [tags] say: "Install unknown apps",
     * waiting for confirmation (until Cancel), turned down, failed, or installed.
     */
    private suspend fun simulate(
        record: WebAppRecord,
        tags: Set<TestTag>,
        uninstallFirst: Boolean,
        show: (WebAppFactory.Stage, Boolean?) -> Unit,
    ): BuildOutcome {
        if (uninstallFirst) {
            show(WebAppFactory.Stage.RENDERING_ICON, true)
            delay(SIMULATED_STEP_MS)
        }
        WebAppFactory.Stage.entries.forEach { stage ->
            show(stage, if (uninstallFirst) false else null)
            delay(SIMULATED_STEP_MS)
        }
        return when {
            TestTag.UNKNOWN_SOURCES in tags -> BuildOutcome.NeedsUnknownSources
            TestTag.WAIT in tags -> {
                (_state.value as? BuildState.Running)?.let { _state.value = it.copy(waitingForUser = true) }
                awaitCancellation()
            }
            TestTag.CANCEL in tags -> BuildOutcome.Cancelled
            TestTag.FAIL in tags -> BuildOutcome.Failed("Simulated failure: this app is tagged fail")
            else -> BuildOutcome.Installed(record)
        }
    }

    /** Asks the package manager off the main thread (anr-audit-and-fix skill). */
    private suspend fun kindOf(request: BuildRequest): BuildKind = when {
        request.existing == null -> BuildKind.CREATE
        withContext(Dispatchers.IO) { container.isInstalled(request.existing.packageName) } -> BuildKind.UPDATE
        else -> BuildKind.REINSTALL
    }

    /** Runs the last request again (or resumes Update all or Fix all), optionally with the system installer. */
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

    /** Closes the build sheet once nothing is running; a running build keeps it. */
    fun dismiss() {
        if (job?.isActive != true) {
            pendingBatch = null
            _state.value = null
        }
    }
}

/** R13: how long each simulated stage shows, as real stages do at least. */
private const val SIMULATED_STEP_MS = 500L
