package com.valghora.shrimpl.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.common.PillButton
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.form.ProfileFormFields
import kotlinx.coroutines.launch

/** A web app's settings (docs/SHRIMPL_SPEC.md R2.3): change, save as the next build, uninstall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(appId: String, showBack: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val model = viewModel(key = "edit-$appId") { EditViewModel(context.container, appId) }
    val record by model.record.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val installed by model.installed.collectAsStateWithLifecycle()
    val buildState by model.build.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val offerRemove by model.offerRemove.collectAsStateWithLifecycle()
    var confirmUninstall by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val invalidMessage = stringResource(R.string.create_fix_fields)

    LifecycleResumeEffect(appId) {
        model.refresh(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.messageShown()
        }
    }

    val current = record
    val form = draft
    if (current == null || form == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.edit_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.profile.name) },
                navigationIcon = {
                    if (showBack) {
                        TooltipIconButton(stringResource(R.string.action_back), onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (installed == true) {
                        TextButton(onClick = { InstalledApps.open(context, current.packageName) }) { Text(stringResource(R.string.action_open)) }
                    }
                    TooltipIconButton(stringResource(R.string.action_more), { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (installed == false) R.string.action_reinstall else R.string.action_rebuild)) },
                            onClick = {
                                menu = false
                                model.rebuild()
                            },
                        )
                        if (model.dirty) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_discard)) }, onClick = {
                                menu = false
                                model.reset()
                            })
                        }
                        if (installed == true) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_uninstall)) }, onClick = {
                                menu = false
                                confirmUninstall = true
                            })
                        } else {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove)) }, onClick = {
                                menu = false
                                model.remove(onBack)
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (model.dirty) {
                PillButton(
                    text = stringResource(R.string.action_save_update),
                    icon = Icons.Filled.Check,
                    onClick = { if (!model.save()) scope.launch { snackbar.showSnackbar(invalidMessage) } },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            ProfileFormFields(
                draft = form,
                onChange = model::update,
                hints = emptyMap(),
                notes = if (installed == false) listOf(Notice(Notice.Kind.INFO, stringResource(R.string.edit_not_installed))) else emptyList(),
                storedImage = model::storedImage,
                wide = maxWidth >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.dp,
                appIdEditable = false,
            )
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(stringResource(R.string.uninstall_title, current.profile.name)) },
            text = { Text(stringResource(R.string.uninstall_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    model.uninstall()
                }) { Text(stringResource(R.string.action_uninstall)) }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (offerRemove) {
        AlertDialog(
            onDismissRequest = model::dismissRemove,
            title = { Text(stringResource(R.string.remove_title, current.profile.name)) },
            text = { Text(stringResource(R.string.remove_text)) },
            confirmButton = { TextButton(onClick = { model.remove(onBack) }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = model::dismissRemove) { Text(stringResource(R.string.action_keep)) } },
        )
    }
    buildState?.let { state ->
        BuildSheet(
            state = state,
            onCancel = model.build::cancel,
            onShowInstaller = model.build::showInstaller,
            onRetry = model.build::retry,
            onOpen = { InstalledApps.open(context, it) },
            onDone = model.build::dismiss,
        )
    }
}
