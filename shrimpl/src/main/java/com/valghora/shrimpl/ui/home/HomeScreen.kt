package com.valghora.shrimpl.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.core.profile.resolvedIconForeground
import com.valghora.shrimpl.core.records.WebAppRecord
import com.valghora.shrimpl.data.ClickAction
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.data.InstalledApp
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.CenteredEmptyState
import com.valghora.shrimpl.ui.common.EmptyStateText
import com.valghora.shrimpl.ui.common.ItemShape
import com.valghora.shrimpl.ui.common.LocalInPane
import com.valghora.shrimpl.ui.common.NEW_APP_BUTTON_HEIGHT
import com.valghora.shrimpl.ui.common.NewWebAppButton
import com.valghora.shrimpl.ui.common.PillButton
import com.valghora.shrimpl.ui.common.PitchText
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.icons.AdaptiveIcon
import com.valghora.shrimpl.ui.icons.rememberIconLayers

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
    val clickAction by model.clickAction.collectAsStateWithLifecycle()
    val method by model.installMethod.collectAsStateWithLifecycle()
    val outdated = model.outdated(records, installed)
    var confirmUpdateAll by rememberSaveable { mutableStateOf(false) }
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
                        // The theme's primary color, so it follows the user's Material You palette.
                        Icon(
                            painterResource(R.drawable.ic_shrimpl_glyph),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_title))
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    TooltipIconButton(
                        stringResource(R.string.settings_title),
                        onSettings,
                        Modifier.padding(end = SETTINGS_ALIGNMENT_PADDING),
                        tooltip = stringResource(R.string.tooltip_settings),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = barColor),
            )
        },
        floatingActionButton = {
            // R2.4: Update all (two or more outdated apps) above New app, the same width. Beside the
            // detail pane, New app shows only while the detail holds something else; the empty detail
            // pane has its own.
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (outdated.size >= 2) {
                    PillButton(
                        stringResource(R.string.action_update_all),
                        Icons.Filled.Refresh,
                        onClick = { if (method == InstallMethod.SESSION) confirmUpdateAll = true else model.updateAll(outdated) },
                        modifier = Modifier.fillMaxWidth(),
                        secondary = true,
                    )
                }
                if (showCreateButton) NewWebAppButton(onCreate, Modifier.fillMaxWidth(), fillWidth = true)
            }
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
                // Room at the bottom so the last card can scroll clear of both floating buttons.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 168.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.appId }) { record ->
                    val app = installed[record.packageName]
                    AppItem(
                        record = record,
                        installed = app,
                        updateAvailable = record.updateAvailable(model.templateVersion),
                        selected = record.appId == selected,
                        clickAction = clickAction,
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
    // The system installer asks for each app, so Update all says so first and can be cancelled.
    if (confirmUpdateAll) {
        AlertDialog(
            onDismissRequest = { confirmUpdateAll = false },
            title = { Text(pluralStringResource(R.plurals.update_all_title, outdated.size, outdated.size)) },
            text = { Text(pluralStringResource(R.plurals.update_all_text, outdated.size, outdated.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdateAll = false
                    model.updateAll(outdated)
                }) { Text(stringResource(R.string.action_update_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmUpdateAll = false }) { Text(stringResource(R.string.action_cancel)) } },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppItem(
    record: WebAppRecord,
    installed: InstalledApp?,
    updateAvailable: Boolean,
    selected: Boolean,
    clickAction: ClickAction,
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
            .clip(ItemShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            // A tap opens or edits the app, as chosen in Settings (R2.5).
            .combinedClickable(onClick = if (clickAction == ClickAction.EDIT) onEdit else onOpen, onLongClick = { menuAt = DpOffset(24.dp, 48.dp) }),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ItemIcon(record, installed)
            Column(Modifier.weight(1f)) {
                Text(record.profile.name, style = MaterialTheme.typography.titleMedium)
                if (installed == null) {
                    Text(stringResource(R.string.home_not_installed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // R2.2: a newer template is bundled; rebuild this app with it.
            if (installed != null && updateAvailable) {
                FilledTonalButton(
                    onClick = onRebuild,
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_update))
                }
            }
            val label = stringResource(R.string.action_app_options, record.profile.name)
            var optionsOpen by rememberSaveable { mutableStateOf(false) }
            Box {
                TooltipIconButton(label, { optionsOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = label) }
                DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    val options = buildList {
                        if (installed != null) add(R.string.action_open to onOpen)
                        add(R.string.action_edit to onEdit)
                        if (installed == null) add(R.string.action_reinstall to onRebuild)
                        add(if (installed != null) R.string.action_uninstall to onUninstall else R.string.action_remove to onRemove)
                    }
                    options.forEach { (option, action) ->
                        DropdownMenuItem(text = { Text(stringResource(option)) }, onClick = {
                            optionsOpen = false
                            action()
                        })
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
    val layers by rememberIconLayers(record.icon, record.profile.iconBackground, record.profile.resolvedIconForeground, record.updatedAt) { file ->
        context.container.store.image(record.appId, file)
    }
    layers?.let { AdaptiveIcon(it, CircleShape, 48.dp) }
}

/** The narrowest a web app item gets before the grid drops a column. */
private val MIN_CELL_WIDTH = 360.dp

/**
 * Space between the top bar's settings button and the window edge, so it lines up with the ⋮ buttons
 * inside the cards (list padding plus the card's end padding).
 */
private val SETTINGS_ALIGNMENT_PADDING = 16.dp

/** The space Scaffold leaves between the floating action button and the bottom of the content. */
private val FAB_MARGIN = 16.dp
