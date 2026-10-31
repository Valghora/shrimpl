package com.valghora.shrimpl.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.PillButton
import com.valghora.shrimpl.ui.common.TooltipIconButton
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
    val busy = step is CreateViewModel.Step.Discovering
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.create_url_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = url,
            onValueChange = model::setUrl,
            label = { Text(stringResource(R.string.field_website)) },
            placeholder = { Text("example.com") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { model.search() }),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        )
        when (step) {
            is CreateViewModel.Step.Discovering -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(stageLabel(step.stage)))
                }
                OutlinedButton(onClick = model::cancelSearch) { Text(stringResource(R.string.action_cancel)) }
            }
            is CreateViewModel.Step.Failed -> {
                Text(step.message, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::search) { Text(stringResource(R.string.action_try_again)) }
                    if (SiteDiscovery.normalizeUrl(url) != null) {
                        OutlinedButton(onClick = model::continueWithoutDiscovery) { Text(stringResource(R.string.action_continue_without)) }
                    }
                }
            }
            else -> Button(onClick = model::search, enabled = url.isNotBlank()) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text(stringResource(R.string.action_search), Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun stageLabel(stage: SiteDiscovery.Stage) = when (stage) {
    SiteDiscovery.Stage.FETCHING_PAGE -> R.string.discovery_fetching
    SiteDiscovery.Stage.READING_MANIFEST -> R.string.discovery_manifest
    SiteDiscovery.Stage.CHECKING_PAGE -> R.string.discovery_rendering
    SiteDiscovery.Stage.FINDING_ICON -> R.string.discovery_icon
}
