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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.valghora.shrimpl.ui.common.AppMenu
import com.valghora.shrimpl.ui.common.AppMenuItem
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.CenteredEmptyState
import com.valghora.shrimpl.ui.common.EmptyStateText
import com.valghora.shrimpl.ui.common.ItemShape
import com.valghora.shrimpl.ui.common.LocalInPane
import com.valghora.shrimpl.ui.common.NEW_APP_BUTTON_HEIGHT
import com.valghora.shrimpl.ui.common.NewWebAppButton
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.common.NoticeCard
import com.valghora.shrimpl.ui.common.PillButton
import com.valghora.shrimpl.ui.common.PitchText
import com.valghora.shrimpl.ui.common.Tone
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.common.WithTooltip
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
    val installedApps by model.installed.collectAsStateWithLifecycle()
    val buildState by model.build.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val clickAction by model.clickAction.collectAsStateWithLifecycle()
    val method by model.installMethod.collectAsStateWithLifecycle()
    val signingKey by model.signingKey.collectAsStateWithLifecycle()
    // R13: test tags are applied here, once; everything below sees the states they name.
    val installed = model.shownInstalled(records, installedApps)
    val otherKey = model.otherKey(records, signingKey)
    val outdated = model.outdated(records, installed, otherKey)
    // R11.7: the notice shows until dismissed, and again when another such app turns up.
    val keyNoticeDismissed by model.keyNoticeDismissed.collectAsStateWithLifecycle()
    val showKeyNotice = otherKey.isNotEmpty() && !keyNoticeDismissed.containsAll(otherKey)
    val toFix = records.filter { it.appId in otherKey && it.packageName in installed }
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
                    TooltipIconButton(stringResource(R.string.settings_title), onSettings, tooltip = stringResource(R.string.tooltip_settings)) {
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
                // R11.7: two or more apps signed with another key.
                if (toFix.size >= 2) {
                    PillButton(
                        stringResource(R.string.action_fix_all),
                        Icons.Filled.Build,
                        onClick = { model.fix(toFix) },
                        modifier = Modifier.fillMaxWidth(),
                        secondary = true,
                    )
                }
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
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 168.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // R11.6, R11.7: apps from a previous install, signed with a key Shrimpl no longer has.
                if (showKeyNotice) {
                    item(key = OTHER_KEY_NOTICE, span = { GridItemSpan(maxLineSpan) }) {
                        NoticeCard(
                            Notice(Notice.Kind.CAUTION, stringResource(R.string.other_key_notice)),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            action = stringResource(R.string.action_open_settings) to onSettings,
                            endAction = stringResource(R.string.action_dismiss) to { model.dismissKeyNotice(otherKey) },
                        )
                    }
                }
                // R2.2: uninstalled apps sit at the bottom under a "Not installed" label, each group in name order.
                val (present, absent) = records.partition { it.packageName in installed }
                val row: @Composable (WebAppRecord) -> Unit = { record ->
                    val app = installed[record.packageName]
                    AppItem(
                        record = record,
                        installed = app,
                        updateAvailable = model.updateAvailable(record),
                        otherKey = record.appId in otherKey,
                        selected = record.appId == selected,
                        clickAction = clickAction,
                        onOpen = { if (app != null) InstalledApps.open(context, record.packageName) else onEdit(record.appId) },
                        onEdit = { onEdit(record.appId) },
                        onRebuild = { model.rebuild(record) },
                        onUninstall = { confirmUninstall = record.appId },
                        onFix = { model.fix(listOf(record)) },
                        onRemove = { model.remove(record) },
                    )
                }
                items(present, key = { it.appId }) { row(it) }
                if (absent.isNotEmpty()) {
                    item(key = NOT_INSTALLED_LABEL, span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.home_not_installed),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(absent, key = { it.appId }) { row(it) }
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
            onShowInstaller = model.build::showInstaller,
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
    otherKey: Boolean,
    selected: Boolean,
    clickAction: ClickAction,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onFix: () -> Unit,
    onRemove: () -> Unit,
) {
    // R11.7: installed but signed with another key; it can only be fixed (uninstalled, then rebuilt) or removed.
    val locked = installed != null && otherKey
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    val density = LocalDensity.current
    fun showMenu(at: Offset) {
        menuAt = with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }
    }
    Box(
        Modifier
            // The highlight sits 4 dp in from each side, so its rounded corners don't meet the edge.
            .padding(horizontal = ROW_INSET)
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
            // Borderless rows: the settings items' rounded shape shows only as the press, hover and
            // (in two panes) selection highlight.
            .clip(ItemShape)
            // The selected row takes the hover tint, not secondaryContainer, so its tonal Update pill stays
            // visible on it.
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
            // A tap opens or edits the app, as chosen in Settings (R2.5).
            .combinedClickable(onClick = if (locked || installed == null) ({}) else if (clickAction == ClickAction.EDIT) onEdit else onOpen, onLongClick = { menuAt = DpOffset(24.dp, 48.dp) }),
    ) {
        Row(
            // With the inset, the icon is 16 dp from the edge and the ⋮ button's center 28 dp from it,
            // under the top bar's settings button (a top bar keeps 4 dp beside its 48 dp actions).
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp - ROW_INSET, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // R2.2: an uninstalled app is greyed out; its menus and buttons stay as they are.
            val greyed = installed == null
            Box(if (greyed) Modifier.greyedOut() else Modifier) { ItemIcon(record, installed) }
            Column(Modifier.weight(1f)) {
                Text(record.profile.name, style = MaterialTheme.typography.titleMedium, modifier = if (greyed) Modifier.alpha(GREYED_ALPHA) else Modifier)
            }
            // R2.2: a newer template is bundled; Update rebuilds this app with it. R11.7: an app signed
            // with another key offers Fix (uninstall, then rebuild with the current key); Rebuild is
            // left for one whose Fix stopped after the uninstall.
            val rowAction = when {
                locked -> RowAction(R.string.action_fix_app, Icons.Filled.Build, Tone.CAUTION, onFix)
                installed == null && otherKey -> RowAction(R.string.action_rebuild_app, Icons.Filled.Refresh, Tone.SUCCESS, onRebuild)
                installed != null && updateAvailable -> RowAction(R.string.action_update_app, Icons.Filled.Refresh, null, onRebuild)
                else -> null
            }
            rowAction?.let { RowActionButton(it, record.profile.name) }
            val label = stringResource(R.string.action_app_options, record.profile.name)
            var optionsOpen by rememberSaveable { mutableStateOf(false) }
            val actions = menuActions(locked, installed != null, updateAvailable, onOpen, onEdit, onRebuild, onUninstall, onRemove)
            Box {
                TooltipIconButton(label, { optionsOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = label) }
                AppMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    actions.forEach { (option, action) ->
                        AppMenuItem(text = { Text(stringResource(option)) }, onClick = {
                            optionsOpen = false
                            action()
                        })
                    }
                }
            }
        }
        AppMenu(expanded = menuAt != null, onDismissRequest = { menuAt = null }, offset = menuAt ?: DpOffset.Zero) {
            menuActions(locked, installed != null, updateAvailable, onOpen, onEdit, onRebuild, onUninstall, onRemove).forEach { (label, action) ->
                AppMenuItem(text = { Text(stringResource(label)) }, onClick = {
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

/** How far each row's highlight sits in from the sides. */
private val ROW_INSET = 4.dp

/** The narrowest a web app item gets before the grid drops a column. */
private val MIN_CELL_WIDTH = 360.dp

/** The space Scaffold leaves between the floating action button and the bottom of the content. */
private val FAB_MARGIN = 16.dp

private const val OTHER_KEY_NOTICE = "other-key-notice"
private const val NOT_INSTALLED_LABEL = "not-installed-label"

private const val GREYED_ALPHA = 0.38f

/** Draws its content without color and dimmed, as Material does for disabled content. */
private fun Modifier.greyedOut(): Modifier = drawWithContent {
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        alpha = GREYED_ALPHA
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(size.toRect(), paint)
        drawContent()
        canvas.restore()
    }
}

/**
 * R2.2: an app's actions, the same in its ⋮ menu and its right-click and long-press menu: Open,
 * Edit, then Update (an update is available), Reinstall (not installed) or Rebuild, then Uninstall
 * or Remove. An app signed with another key ([locked], R11.7) offers only Remove.
 */
private fun menuActions(
    locked: Boolean,
    installed: Boolean,
    updateAvailable: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onRemove: () -> Unit,
): List<Pair<Int, () -> Unit>> = if (locked) {
    listOf(R.string.action_remove to onRemove)
} else {
    buildList {
        if (installed) add(R.string.action_open to onOpen)
        add(R.string.action_edit to onEdit)
        val rebuild = when {
            !installed -> R.string.action_reinstall
            updateAvailable -> R.string.action_update
            else -> R.string.action_rebuild
        }
        add(rebuild to onRebuild)
        add(if (installed) R.string.action_uninstall to onUninstall else R.string.action_remove to onRemove)
    }
}

/** A row's action (R2.2): its label (taking the app's name), icon, and status tone (null: the theme's tonal color). */
private class RowAction(val label: Int, val icon: ImageVector, val tone: Tone?, val onClick: () -> Unit)

/**
 * R2.2: a small icon-only pill (48 x 32 dp, a full 48 dp touch target) with a tooltip naming the
 * action. Update is in the theme's tonal color; Fix in the Caution yellow and Rebuild in the Success
 * green, as in the notices.
 */
@Composable
private fun RowActionButton(action: RowAction, appName: String) {
    val label = stringResource(action.label, appName)
    val colors = action.tone?.let { IconButtonDefaults.filledTonalIconButtonColors(containerColor = it.container, contentColor = it.content) }
        ?: IconButtonDefaults.filledTonalIconButtonColors()
    WithTooltip(label) {
        FilledTonalIconButton(
            onClick = action.onClick,
            shape = CircleShape,
            colors = colors,
            modifier = Modifier.size(width = 48.dp, height = 32.dp),
        ) { Icon(action.icon, contentDescription = label, modifier = Modifier.size(18.dp)) }
    }
}

/** Material's hover state layer: the content color at 8 %. */
private const val HOVER_ALPHA = 0.08f
