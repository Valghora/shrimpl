package com.valghora.shrimpl.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.discovery.SiteDiscovery
import com.valghora.shrimpl.ui.common.AppTextField
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.BuildState
import com.valghora.shrimpl.ui.common.MainActionButton
import com.valghora.shrimpl.ui.common.PageTopBar
import com.valghora.shrimpl.ui.common.StepSpinner
import com.valghora.shrimpl.ui.common.Tone
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.common.WithTooltip
import com.valghora.shrimpl.ui.common.progressStep
import com.valghora.shrimpl.ui.common.withoutBottom
import com.valghora.shrimpl.ui.form.ProfileFormFields
import com.valghora.shrimpl.ui.theme.AppShapes
import kotlinx.coroutines.launch

/** The configuration page (docs/SHRIMPL_SPEC.md R3), opened from the home screen's button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(onClose: () -> Unit, onBackUpKey: () -> Unit) {
    val context = LocalContext.current
    val model = viewModel { CreateViewModel(context.container) }
    val step by model.step.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val buildState by model.build.state.collectAsStateWithLifecycle()
    val backupPrompt by model.showBackupPrompt.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val invalidMessage = stringResource(R.string.create_fix_fields)

    val reviewing = step == CreateViewModel.Step.Review
    BackHandler(enabled = reviewing) { model.backToUrl() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneWidth = maxWidth
        val scroll = rememberScrollState()
        Scaffold(
            topBar = {
                PageTopBar(
                    title = stringResource(R.string.create_title),
                    paneWidth = paneWidth,
                    scroll = if (reviewing) scroll else null,
                    navigation = {
                        if (reviewing) {
                            TooltipIconButton(stringResource(R.string.action_back), model::backToUrl) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        } else {
                            TooltipIconButton(stringResource(R.string.action_close), onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (reviewing) {
                    MainActionButton(
                        text = stringResource(R.string.action_create),
                        icon = Icons.Filled.Check,
                        onClick = { if (!model.create()) scope.launch { snackbar.showSnackbar(invalidMessage) } },
                    )
                } else if (step is CreateViewModel.Step.Failed) {
                    // R3.2: after a failed search, Continue is the one next step (Retry is the search button).
                    MainActionButton(
                        text = stringResource(R.string.action_continue),
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = model::continueWithoutDiscovery,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val current = draft
            if (reviewing && current != null) {
                Box(Modifier.padding(padding.withoutBottom()).fillMaxSize()) {
                    ProfileFormFields(
                        draft = current,
                        onChange = model::update,
                        hints = model.hints,
                        notes = model.notes,
                        // Nothing is stored yet: every image is in the draft.
                        storedImage = null,
                        bottomInset = padding.calculateBottomPadding(),
                        scrollState = scroll,
                    )
                }
            } else {
                UrlStep(model, step, padding)
            }
        }
    }

    // R3.4: a new app that installed drops the user back on the app list, without the Done sheet
    // (the one-time backup prompt, R5.3, still comes first).
    LaunchedEffect(buildState) {
        if (buildState is BuildState.Done) {
            model.build.dismiss()
            if (!backupPrompt) onClose()
        }
    }
    buildState?.takeIf { it !is BuildState.Done }?.let { state ->
        BuildSheet(
            state = state,
            onCancel = model.build::cancel,
            onShowInstaller = model.build::showInstaller,
            onRetry = model.build::retry,
            onOpen = { InstalledApps.open(context, it) },
            onDone = {
                model.build.dismiss()
                if (!backupPrompt) onClose()
            },
        )
    }
    if (backupPrompt && buildState == null) {
        AlertDialog(
            onDismissRequest = {
                model.backupPromptHandled()
                onClose()
            },
            title = { Text(stringResource(R.string.backup_prompt_title)) },
            text = { Text(stringResource(R.string.backup_prompt_text)) },
            confirmButton = {
                TextButton(onClick = {
                    model.backupPromptHandled()
                    onBackUpKey()
                }) { Text(stringResource(R.string.action_back_up_now)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    model.backupPromptHandled()
                    onClose()
                }) { Text(stringResource(R.string.action_later)) }
            },
        )
    }
}

@Composable
private fun UrlStep(model: CreateViewModel, step: CreateViewModel.Step, padding: PaddingValues) {
    val url by model.url.collectAsStateWithLifecycle()
    val invalid by model.urlInvalid.collectAsStateWithLifecycle()
    val busy = step is CreateViewModel.Step.Discovering || step is CreateViewModel.Step.Continuing
    // The page fits above the keyboard (edge-to-edge skill: IME), so the focused address field can
    // scroll into view on a short landscape screen; it scrolls behind the navigation bar.
    Column(
        Modifier.padding(padding.withoutBottom()).fillMaxSize().fitInside(WindowInsetsRulers.Ime.current)
            .verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = padding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.create_url_intro), style = MaterialTheme.typography.bodyLarge)
            // R3.1: the address in a pill, like Material's search bars, with a round search button of the same height.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                AppTextField(
                    value = url,
                    onValueChange = model::setUrl,
                    label = stringResource(R.string.field_website),
                    placeholder = "example.com",
                    shape = AppShapes.Search,
                    enabled = !busy,
                    isError = invalid,
                    supportingText = if (invalid) stringResource(R.string.search_invalid_url) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.search() }),
                    modifier = Modifier.weight(1f),
                )
                // After a failed search the same button retries it, until the address is edited.
                val retry = step is CreateViewModel.Step.Failed
                val label = stringResource(if (retry) R.string.action_retry else R.string.action_search)
                WithTooltip(label) {
                    FilledTonalIconButton(
                        onClick = model::search,
                        enabled = !busy && url.isNotBlank(),
                        shape = AppShapes.Control,
                        modifier = Modifier.size(FIELD_HEIGHT),
                    ) { Icon(if (retry) Icons.Filled.Refresh else Icons.Filled.Search, contentDescription = label) }
                }
            }
            when (step) {
                is CreateViewModel.Step.Discovering -> {
                    SearchSteps(SiteDiscovery.Stage.entries, current = step.stage)
                    ActionRow(R.string.action_cancel, model::cancelSearch)
                }
                CreateViewModel.Step.Continuing -> SearchSteps(listOf(SiteDiscovery.Stage.FINDING_ICON), current = SiteDiscovery.Stage.FINDING_ICON)
                is CreateViewModel.Step.Failed -> SearchSteps(SiteDiscovery.Stage.entries, failed = step.stage, message = step.message)
                else -> Unit
            }
        }
    }
}

/** A text button at the end of the row (Cancel while searching). */
@Composable
private fun ActionRow(label: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) { Text(stringResource(label)) }
    }
}

/**
 * R3.2: the search as a checklist under "Searching…" ("Search failed" once a step has failed): a
 * check for each finished step, [StepSpinner] on the current one (as in the build sheet),
 * empty circles for the rest. A [failed] step gets a red mark with [message] under it; the steps
 * after it never started.
 */
@Composable
private fun SearchSteps(
    stages: List<SiteDiscovery.Stage>,
    current: SiteDiscovery.Stage? = null,
    failed: SiteDiscovery.Stage? = null,
    message: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(if (failed != null) R.string.search_failed else R.string.search_running), style = MaterialTheme.typography.titleLarge)
        stages.forEach { stage ->
            val state = when {
                stage == failed -> StepState.FAILED
                stage == current -> StepState.CURRENT
                (failed ?: current)?.let { stage.ordinal < it.ordinal } == true -> StepState.DONE
                else -> StepState.PENDING
            }
            StepLine(stringResource(stageLabel(stage)), state)
            if (stage == failed && message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tone.ERROR.text,
                    modifier = Modifier.padding(start = STEP_TEXT_INSET).offset(y = (-6).dp),
                )
            }
        }
    }
}

private enum class StepState { DONE, CURRENT, FAILED, PENDING }

@Composable
private fun StepLine(label: String, state: StepState) {
    val spoken = stringResource(
        when (state) {
            StepState.DONE -> R.string.step_done
            StepState.CURRENT -> R.string.step_in_progress
            StepState.FAILED -> R.string.step_failed
            StepState.PENDING -> R.string.step_not_started
        },
    )
    Row(Modifier.progressStep(spoken), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val mark = Modifier.size(STEP_MARK)
        when (state) {
            StepState.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = mark)
            StepState.CURRENT -> StepSpinner(mark)
            // The "!" is drawn only; the row's state says "Failed".
            StepState.FAILED -> Box(mark.clip(CircleShape).background(Tone.ERROR.container).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Text("!", color = Tone.ERROR.content, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            StepState.PENDING -> Box(mark.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state == StepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val STEP_MARK = 24.dp

/** Where a step's text starts: past its mark and the gap after it. */
private val STEP_TEXT_INSET = STEP_MARK + 16.dp

/** A filled text field's height, which the search button matches. */
private val FIELD_HEIGHT = 56.dp

private fun stageLabel(stage: SiteDiscovery.Stage) = when (stage) {
    SiteDiscovery.Stage.FETCHING_PAGE -> R.string.discovery_fetching
    SiteDiscovery.Stage.READING_MANIFEST -> R.string.discovery_manifest
    SiteDiscovery.Stage.CHECKING_PAGE -> R.string.discovery_rendering
    SiteDiscovery.Stage.FINDING_ICON -> R.string.discovery_icon
}
