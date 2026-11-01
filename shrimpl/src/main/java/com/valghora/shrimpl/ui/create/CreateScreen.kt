package com.valghora.shrimpl.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.discovery.SiteDiscovery
import com.valghora.shrimpl.ui.common.AppTextField
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.FieldShape
import com.valghora.shrimpl.ui.common.PillButton
import com.valghora.shrimpl.ui.common.Tone
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.common.WavyProgressCircle
import com.valghora.shrimpl.ui.common.WithTooltip
import com.valghora.shrimpl.ui.form.ProfileFormFields
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
                navigationIcon = {
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
                PillButton(
                    text = stringResource(R.string.action_create),
                    icon = Icons.Filled.Check,
                    onClick = { if (!model.create()) scope.launch { snackbar.showSnackbar(invalidMessage) } },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = draft
        if (reviewing && current != null) {
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                ProfileFormFields(
                    draft = current,
                    onChange = model::update,
                    hints = model.hints,
                    notes = model.notes,
                    // Nothing is stored yet: every image is in the draft.
                    storedImage = null,
                    wide = maxWidth >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.dp,
                )
            }
        } else {
            UrlStep(model, step, Modifier.padding(padding))
        }
    }

    buildState?.let { state ->
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
private fun UrlStep(model: CreateViewModel, step: CreateViewModel.Step, modifier: Modifier) {
    val url by model.url.collectAsStateWithLifecycle()
    val invalid by model.urlInvalid.collectAsStateWithLifecycle()
    val busy = step is CreateViewModel.Step.Discovering || step is CreateViewModel.Step.Continuing
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.create_url_intro), style = MaterialTheme.typography.bodyLarge)
            // R3.2: the address, with the search button beside it in the same shape and height.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                AppTextField(
                    value = url,
                    onValueChange = model::setUrl,
                    label = stringResource(R.string.field_website),
                    placeholder = "example.com",
                    enabled = !busy,
                    isError = invalid,
                    supportingText = if (invalid) stringResource(R.string.search_invalid_url) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.search() }),
                    modifier = Modifier.weight(1f),
                )
                val search = stringResource(R.string.action_search)
                WithTooltip(search) {
                    FilledTonalIconButton(
                        onClick = model::search,
                        enabled = !busy && url.isNotBlank(),
                        shape = FieldShape,
                        modifier = Modifier.size(FIELD_HEIGHT),
                    ) { Icon(Icons.Filled.Search, contentDescription = search) }
                }
            }
            when (step) {
                is CreateViewModel.Step.Discovering -> {
                    SearchSteps(SiteDiscovery.Stage.entries, current = step.stage)
                    ActionRow(R.string.action_cancel to model::cancelSearch)
                }
                CreateViewModel.Step.Continuing -> SearchSteps(listOf(SiteDiscovery.Stage.FINDING_ICON), current = SiteDiscovery.Stage.FINDING_ICON)
                is CreateViewModel.Step.Failed -> {
                    SearchSteps(SiteDiscovery.Stage.entries, failed = step.stage, message = step.message)
                    ActionRow(R.string.action_retry to model::search, R.string.action_continue to model::continueWithoutDiscovery)
                }
                else -> Unit
            }
        }
    }
}

/** Text buttons at the end of the row, where Cancel sits while searching. */
@Composable
private fun ActionRow(vararg actions: Pair<Int, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        actions.forEach { (label, onClick) -> TextButton(onClick = onClick) { Text(stringResource(label)) } }
    }
}

/**
 * R3.2: the search as a checklist under "Searching…" ("Search failed" once a step has failed): a check for each finished step, Material's wavy
 * progress circle on the current one (turning for as long as it runs), empty circles for the rest. A
 * [failed] step gets a red mark with [message] under it; the steps after it never started.
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val mark = Modifier.size(STEP_MARK)
        when (state) {
            StepState.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = mark)
            StepState.CURRENT -> WavyProgressCircle(diameter = STEP_MARK)
            StepState.FAILED -> Box(mark.clip(CircleShape).background(Tone.ERROR.container), contentAlignment = Alignment.Center) {
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
