package com.valghora.shrimpl.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.valghora.shrimpl.R
import com.valghora.shrimpl.build.WebAppFactory

/**
 * Build progress, result and recovery (docs/SHRIMPL_SPEC.md R3.4, R6.2, R6.3): the stages with the
 * current one spinning, then Open and Done, or the failure with what can be done about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSheet(
    state: BuildState,
    onCancel: () -> Unit,
    onRetry: (withSystemInstaller: Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onDone: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val running = state is BuildState.Running
    ModalBottomSheet(
        onDismissRequest = { if (!running) onDone() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is BuildState.Running -> {
                    val title = state.batch?.let { stringResource(R.string.build_title_updating, it.name, it.index, it.total) }
                        ?: stringResource(R.string.build_title_running)
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    WebAppFactory.Stage.entries.forEach { stage -> StageRow(stage, state.stage) }
                    if (state.stage != WebAppFactory.Stage.INSTALLING) {
                        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
                is BuildState.Done -> {
                    Text(stringResource(R.string.build_title_done, state.record.profile.name), style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
                        Button(onClick = { onOpen(state.record.packageName) }) { Text(stringResource(R.string.action_open)) }
                    }
                }
                is BuildState.BatchDone -> {
                    Text(
                        pluralStringResource(R.plurals.batch_updated, state.updated.size, state.updated.size),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (state.updated.isNotEmpty()) Text(state.updated.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    if (state.failed.isNotEmpty()) {
                        Text(stringResource(R.string.batch_failed), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        state.failed.forEach { (name, why) -> Text(stringResource(R.string.batch_failed_item, name, why), style = MaterialTheme.typography.bodyMedium) }
                    }
                    TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_done)) }
                }
                BuildState.NeedsUnknownSources -> UnknownSources(onRetry = { onRetry(false) }, onDone = onDone)
                is BuildState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.build_title_failed), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    if (state.offerSystemInstaller) Text(stringResource(R.string.build_method_unavailable))
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.action_close)) }
                        if (state.offerSystemInstaller) {
                            OutlinedButton(onClick = { onRetry(true) }) { Text(stringResource(R.string.action_use_system_installer)) }
                        }
                        Button(onClick = { onRetry(false) }) { Text(stringResource(R.string.action_try_again)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(stage: WebAppFactory.Stage, current: WebAppFactory.Stage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            stage.ordinal < current.ordinal -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            stage == current -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            else -> Spacer(Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        val label = when (stage) {
            WebAppFactory.Stage.RENDERING_ICON -> R.string.stage_rendering_icon
            WebAppFactory.Stage.BUILDING -> R.string.stage_building
            WebAppFactory.Stage.SIGNING -> R.string.stage_signing
            WebAppFactory.Stage.VERIFYING -> R.string.stage_verifying
            WebAppFactory.Stage.INSTALLING -> R.string.stage_installing
        }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = if (stage.ordinal <= current.ordinal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** R6.3: explains the permission, opens its settings page, and continues on return. */
@Composable
private fun UnknownSources(onRetry: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (opened && context.packageManager.canRequestPackageInstalls()) onRetry()
        onPauseOrDispose { }
    }
    Text(stringResource(R.string.build_title_permission), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.build_permission_detail))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            opened = true
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
            )
        }) { Text(stringResource(R.string.action_open_settings)) }
    }
}
