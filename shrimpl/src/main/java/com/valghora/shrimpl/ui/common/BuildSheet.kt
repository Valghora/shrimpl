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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.valghora.shrimpl.ui.theme.AppShapes
import kotlinx.coroutines.delay

/**
 * Build progress, result and recovery (docs/SHRIMPL_SPEC.md R3.4, R6.2, R6.3): the stages with the
 * current one spinning, then Open and Done, or the failure with what can be done about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSheet(
    state: BuildState,
    onCancel: () -> Unit,
    onShowInstaller: () -> Unit,
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
                    val title = when (state.kind) {
                        BuildKind.CREATE -> stringResource(R.string.build_title_creating, state.name)
                        BuildKind.UPDATE -> state.batch?.let { stringResource(R.string.build_title_updating, state.name, it.index, it.total) }
                            ?: stringResource(R.string.build_title_updating_one, state.name)
                        BuildKind.REINSTALL -> state.batch?.let { stringResource(R.string.build_title_reinstalling, state.name, it.index, it.total) }
                            ?: stringResource(R.string.build_title_reinstalling_one, state.name)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
                    // Fix (R11.7) uninstalls the old copy first; the build's stages wait until it's gone.
                    state.uninstalling?.let { inProgress ->
                        StepRow(stringResource(R.string.stage_uninstalling), done = !inProgress, current = inProgress, reached = true)
                    }
                    WebAppFactory.Stage.entries.forEach { stage ->
                        val current = if (state.uninstalling == true) null else state.stage
                        StageRow(stage, current)
                    }
                    if (!state.withSystem) {
                        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_cancel)) }
                    } else if (state.waitingForUser) {
                        WaitingForUser(uninstalling = state.uninstalling == true, onShowInstaller = onShowInstaller, onCancel = onCancel)
                    }
                }
                is BuildState.Done -> {
                    Text(stringResource(R.string.build_title_done, state.record.profile.name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
                        Button(onClick = { onOpen(state.record.packageName) }, shape = AppShapes.Control) { Text(stringResource(R.string.action_open)) }
                    }
                }
                is BuildState.BatchDone -> {
                    Text(
                        pluralStringResource(if (state.fixed) R.plurals.batch_fixed else R.plurals.batch_updated, state.updated.size, state.updated.size),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.asHeading(),
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
                        Text(stringResource(R.string.build_title_failed), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
                    }
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    if (state.offerSystemInstaller) Text(stringResource(R.string.build_method_unavailable))
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.action_close)) }
                        if (state.offerSystemInstaller) {
                            OutlinedButton(onClick = { onRetry(true) }, shape = AppShapes.Control) { Text(stringResource(R.string.action_use_system_installer)) }
                        }
                        Button(onClick = { onRetry(false) }, shape = AppShapes.Control) { Text(stringResource(R.string.action_try_again)) }
                    }
                }
            }
        }
    }
}

/** One build stage; [current] null: the build hasn't started (Fix is still uninstalling). */
@Composable
private fun StageRow(stage: WebAppFactory.Stage, current: WebAppFactory.Stage?) {
    val label = when (stage) {
        WebAppFactory.Stage.RENDERING_ICON -> R.string.stage_rendering_icon
        WebAppFactory.Stage.BUILDING -> R.string.stage_building
        WebAppFactory.Stage.SIGNING -> R.string.stage_signing
        WebAppFactory.Stage.VERIFYING -> R.string.stage_verifying
        WebAppFactory.Stage.INSTALLING -> R.string.stage_installing
    }
    StepRow(
        stringResource(label),
        done = current != null && stage.ordinal < current.ordinal,
        current = stage == current,
        reached = current != null && stage.ordinal <= current.ordinal,
    )
}

/** A step of the build sheet: a check when [done], a spinner when [current], dimmed until [reached]. */
@Composable
private fun StepRow(label: String, done: Boolean, current: Boolean, reached: Boolean) {
    val state = stringResource(
        when {
            done -> R.string.step_done
            current -> R.string.step_in_progress
            else -> R.string.step_not_started
        },
    )
    Row(Modifier.progressStep(state), verticalAlignment = Alignment.CenterVertically) {
        when {
            done -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            current -> StepSpinner(Modifier.size(24.dp))
            else -> Spacer(Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * R6.5: the system installer is waiting for the user's confirmation. Once Shrimpl is back in front
 * (the user left the installer unfinished), offers to show the installer again or cancel the install.
 * The short delay keeps this hidden while the installer's own screen is still opening.
 */
@Composable
private fun WaitingForUser(uninstalling: Boolean, onShowInstaller: () -> Unit, onCancel: () -> Unit) {
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val stalled by produceState(false, resumed) {
        value = false
        if (resumed) {
            delay(STALL_MS)
            value = true
        }
    }
    if (!stalled) return
    Text(stringResource(if (uninstalling) R.string.build_waiting_for_uninstall else R.string.build_waiting_for_user), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onShowInstaller, shape = AppShapes.Control) { Text(stringResource(R.string.action_show_installer)) }
    }
}

private const val STALL_MS = 600L

/** R6.3: explains the permission, opens its settings page, and continues on return. */
@Composable
private fun UnknownSources(onRetry: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (opened && context.packageManager.canRequestPackageInstalls()) onRetry()
        onPauseOrDispose { }
    }
    Text(stringResource(R.string.build_title_permission), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
    Text(stringResource(R.string.build_permission_detail))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            opened = true
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
            )
        }, shape = AppShapes.Control) { Text(stringResource(R.string.action_open_settings)) }
    }
}
