package com.valghora.shrimpl.ui.home

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.data.InstalledApp
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.CenteredEmptyState
import com.valghora.shrimpl.ui.common.EmptyStateText
import com.valghora.shrimpl.ui.common.PitchText
import com.valghora.shrimpl.ui.common.NewWebAppButton
import com.valghora.shrimpl.ui.common.LocalInPane
import com.valghora.shrimpl.ui.common.NEW_APP_BUTTON_HEIGHT
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.icons.AdaptiveIcon
import com.valghora.shrimpl.ui.icons.IconSelection
import com.valghora.shrimpl.ui.icons.rememberIconLayers
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.records.WebAppRecord

/**
 * "My Web Apps" (docs/SHRIMPL_SPEC.md R2), the list pane of the list-detail layout. The list flows
 * into columns on wide windows (adaptive skill step 4), and each web app has a context menu on
 * right-click or long press (Tier 2 Context_Menus).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selected: String?,
    showCreateButton: Boolean,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val context = LocalContext.current
    val model = viewModel { HomeViewModel(context.container) }
    val records by model.records.collectAsStateWithLifecycle()
    val installed by model.installed.collectAsStateWithLifecycle()
    val buildState by model.build.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var confirmUninstall by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // In a pane card the top bar takes the card's color; full screen it matches Settings.
    val inPane = LocalInPane.current
    val barColor = if (inPane) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
    // The app bar slides away while scrolling down and returns on scroll up (adaptive skill step 5).
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LifecycleResumeEffect(Unit) {
        model.refresh(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(records, buildState) { model.refresh(context) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_shrimpl_glyph), contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_title))
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    TooltipIconButton(stringResource(R.string.settings_title), onSettings, tooltip = stringResource(R.string.tooltip_settings)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = barColor),
            )
        },
        floatingActionButton = {
            // R2.4. Beside the detail pane it shows only while the detail holds something else; the
            // empty detail pane has its own.
            if (showCreateButton) NewWebAppButton(onCreate)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (records.isEmpty()) {
            if (inPane) {
                // Spans the whole card, so it lines up with the detail pane's text.
                CenteredEmptyState { EmptyStateText(stringResource(R.string.home_empty_title), title = true) }
            } else {
                // One pane: the title and pitch are centered together, as one block, between the top
                // bar and the top of the New app button.
                Column(
                    Modifier.padding(padding).fillMaxSize()
                        .padding(start = 32.dp, end = 32.dp, bottom = FAB_MARGIN + NEW_APP_BUTTON_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyStateText(stringResource(R.string.home_empty_title), title = true)
                    PitchText()
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(MIN_CELL_WIDTH),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp),
            ) {
                items(records, key = { it.appId }) { record ->
                    val app = installed[record.packageName]
                    AppItem(
                        record = record,
                        installed = app,
                        updateAvailable = record.updateAvailable(model.templateVersion),
                        selected = record.appId == selected,
                        onOpen = { if (app != null) InstalledApps.open(context, record.packageName) else onEdit(record.appId) },
                        onEdit = { onEdit(record.appId) },
                        onRebuild = { model.rebuild(record) },
                        onUninstall = { confirmUninstall = record.appId },
                        onRemove = { model.remove(record) },
                    )
                }
            }
        }
    }

    confirmUninstall?.let { appId -> records.firstOrNull { it.appId == appId } }?.let { record ->
        UninstallDialog(record.profile.name, onConfirm = {
            confirmUninstall = null
            model.uninstall(context, record)
        }, onDismiss = { confirmUninstall = null })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppItem(
    record: WebAppRecord,
    installed: InstalledApp?,
    updateAvailable: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    val density = LocalDensity.current
    fun showMenu(at: Offset) {
        menuAt = with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }
    }
    Box(
        Modifier
            .pointerInput(record.appId) {
                // Right-click (secondary mouse button or two-finger trackpad tap) opens the menu at the pointer.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.firstOrNull()?.let { change ->
                                showMenu(change.position)
                                change.consume()
                            }
                        }
                    }
                }
            }
            .combinedClickable(onClick = onOpen, onLongClick = { menuAt = DpOffset(24.dp, 48.dp) }),
    ) {
        Column {
            ListItem(
                leadingContent = { ItemIcon(record, installed) },
                headlineContent = { Text(record.profile.name) },
                supportingContent = {
                    val status = when {
                        installed == null -> stringResource(R.string.home_not_installed)
                        updateAvailable -> stringResource(R.string.home_update_available)
                        else -> null
                    }
                    Text(listOfNotNull(stringResource(R.string.home_build, record.buildNumber), status).joinToString(" · "))
                },
                trailingContent = {
                    val label = stringResource(R.string.action_edit_app, record.profile.name)
                    // Shifted so it lines up with the top bar's settings button above it.
                    TooltipIconButton(label, onEdit, Modifier.offset(x = TRAILING_ALIGNMENT_SHIFT)) {
                        Icon(Icons.Filled.Edit, contentDescription = label)
                    }
                },
                tonalElevation = if (selected) 4.dp else 0.dp,
            )
            if (installed == null || updateAvailable) {
                Row(Modifier.padding(start = 72.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed == null) {
                        TextButton(onClick = onRebuild) { Text(stringResource(R.string.action_reinstall)) }
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
                    } else {
                        TextButton(onClick = onRebuild) { Text(stringResource(R.string.action_update)) }
                    }
                }
            }
        }
        DropdownMenu(expanded = menuAt != null, onDismissRequest = { menuAt = null }, offset = menuAt ?: DpOffset.Zero) {
            fun item(label: Int, action: () -> Unit) = Pair(label, action)
            val items = buildList {
                if (installed != null) add(item(R.string.action_open, onOpen))
                add(item(R.string.action_edit, onEdit))
                add(item(if (installed == null) R.string.action_reinstall else if (updateAvailable) R.string.action_update else R.string.action_rebuild, onRebuild))
                add(if (installed != null) item(R.string.action_uninstall, onUninstall) else item(R.string.action_remove, onRemove))
            }
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    menuAt = null
                    action()
                })
            }
        }
    }
}

@Composable
private fun UninstallDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.uninstall_title, name)) },
        text = { Text(stringResource(R.string.uninstall_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_uninstall)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ItemIcon(record: WebAppRecord, installed: InstalledApp?) {
    if (installed != null) {
        val bitmap = remember(installed) { installed.icon.toBitmap(144, 144).asImageBitmap() }
        Image(bitmap, contentDescription = null, modifier = Modifier.size(48.dp))
        return
    }
    val context = LocalContext.current
    val selection = IconSelection(record.icon, null, record.profile.iconBackground, record.profile.iconForeground.takeUnless { it == Profile.AUTO }.orEmpty())
    val layers by rememberIconLayers(selection, record.profile.name) { context.storedImage(record.appId) }
    layers?.let { AdaptiveIcon(it, CircleShape, 48.dp) }
}

private fun Context.storedImage(appId: String) = container.store.image(appId)

/** The narrowest a web app item gets before the grid drops a column. */
private val MIN_CELL_WIDTH = 360.dp

/**
 * A list item's trailing button sits 16 dp from the end (ListItem's end padding); a top bar's action
 * 4 dp (its horizontal padding). Moving the item's button 12 dp outward puts it under the action.
 */
private val TRAILING_ALIGNMENT_SHIFT = 12.dp

/** The space Scaffold leaves between the floating action button and the bottom of the content. */
private val FAB_MARGIN = 16.dp
