package com.valghora.uniwrap.ui.edit

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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.valghora.uniwrap.R
import com.valghora.uniwrap.container
import com.valghora.uniwrap.data.InstalledApps
import com.valghora.uniwrap.ui.common.BuildSheet
import com.valghora.uniwrap.ui.common.Notice
import com.valghora.uniwrap.ui.form.ProfileFormFields
import com.valghora.uniwrap.ui.icons.IconEditor
import com.valghora.uniwrap.ui.icons.rememberIconLayers
import com.valghora.wrapper.core.records.IconRecord
import kotlinx.coroutines.launch

/** A web app's settings (docs/UNIWRAP_SPEC.md R2.3): change, save as the next build, uninstall. */
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
    var editingIcon by rememberSaveable { mutableStateOf(false) }
    var confirmUninstall by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
    val storedImage: (() -> ByteArray)? = if (current.icon.kind == IconRecord.Kind.IMAGE) model::storedImage else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.profile.name) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (installed == true) {
                        TextButton(onClick = { InstalledApps.open(context, current.packageName) }) { Text(stringResource(R.string.action_open)) }
                    }
                    IconButton(onClick = { menu = true }) {
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
                ExtendedFloatingActionButton(
                    onClick = { if (!model.save()) scope.launch { snackbar.showSnackbar(invalidMessage) } },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_save_update, current.nextBuildNumber)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val layers by rememberIconLayers(form.iconSelection, form.form.name, storedImage)
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            ProfileFormFields(
                form = form.form,
                onChange = model::update,
                hints = emptyMap(),
                notes = if (installed == false) listOf(Notice(Notice.Kind.INFO, stringResource(R.string.edit_not_installed))) else emptyList(),
                iconLayers = layers,
                iconOrigin = form.icon.origin,
                onEditIcon = { editingIcon = true },
                wide = maxWidth >= 840.dp,
                appIdEditable = false,
            )
        }
    }

    if (editingIcon) {
        IconEditor(
            initial = form.iconSelection,
            appName = form.form.name,
            siteIcon = null,
            storedImage = storedImage,
            onDone = {
                model.setIcon(it)
                editingIcon = false
            },
            onDismiss = { editingIcon = false },
        )
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
            onRetry = model.build::retry,
            onOpen = { InstalledApps.open(context, it) },
            onDone = model.build::dismiss,
        )
    }
}
