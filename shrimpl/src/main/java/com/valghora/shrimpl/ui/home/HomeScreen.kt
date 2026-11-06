package com.valghora.shrimpl.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
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
import com.valghora.shrimpl.data.HomeLayout
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.data.InstalledApp
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.ui.common.AppMenu
import com.valghora.shrimpl.ui.common.AppMenuItem
import com.valghora.shrimpl.ui.common.BuildSheet
import com.valghora.shrimpl.ui.common.COLLAPSE_DISTANCE
import com.valghora.shrimpl.ui.common.ReplayIcon
import com.valghora.shrimpl.ui.common.CenteredEmptyState
import com.valghora.shrimpl.ui.common.EmptyStateText
import com.valghora.shrimpl.ui.common.LargeTitleBar
import com.valghora.shrimpl.ui.common.LocalInPane
import com.valghora.shrimpl.ui.common.MainActionButton
import com.valghora.shrimpl.ui.common.NEW_APP_BUTTON_HEIGHT
import com.valghora.shrimpl.ui.common.NewWebAppButton
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.common.NoticeCard
import com.valghora.shrimpl.ui.common.PitchText
import com.valghora.shrimpl.ui.common.Tone
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.common.UpdateIcon
import com.valghora.shrimpl.ui.common.WithTooltip
import com.valghora.shrimpl.ui.common.asHeading
import com.valghora.shrimpl.ui.common.isMediumWidth
import com.valghora.shrimpl.ui.icons.AdaptiveIcon
import com.valghora.shrimpl.ui.icons.rememberIconLayers
import com.valghora.shrimpl.ui.theme.AppShapes

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
    // Read once here, not in each list item (jetpack-compose-performance skill: lazy layouts).
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val store = context.container.store
    val model = viewModel { HomeViewModel(context.container) }
    val records by model.records.collectAsStateWithLifecycle()
    val installedApps by model.installed.collectAsStateWithLifecycle()
    val buildState by model.build.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val clickAction by model.clickAction.collectAsStateWithLifecycle()
    val resolveOnTap by model.resolveOnTap.collectAsStateWithLifecycle()
    val hapticFeedback by model.hapticFeedback.collectAsStateWithLifecycle()
    val method by model.installMethod.collectAsStateWithLifecycle()
    val signingKey by model.signingKey.collectAsStateWithLifecycle()
    val layout by model.layout.collectAsStateWithLifecycle()
    val tiles = layout == HomeLayout.TILES
    // R13: test tags are applied here, once; everything below sees the states they name.
    val installed = model.shownInstalled(records, installedApps)
    val otherKey = model.otherKey(records, installed, signingKey)
    val outdated = model.outdated(records, installed, otherKey)
    // R11.7: the notice shows until dismissed, and again when another such app turns up.
    val keyNoticeDismissed by model.keyNoticeDismissed.collectAsStateWithLifecycle()
    val showKeyNotice = otherKey.isNotEmpty() && !keyNoticeDismissed.containsAll(otherKey)
    val toFix = records.filter { it.appId in otherKey }
    var confirmUpdateAll by rememberSaveable { mutableStateOf(false) }
    var confirmUninstall by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val inPane = LocalInPane.current
    // R2.1: with one pane the large title shrinks to the usual bar size as the list scrolls, as on the
    // settings pages; beside the detail pane it stays put while the list scrolls under it.
    val grid = rememberLazyGridState()
    val distance = with(density) { COLLAPSE_DISTANCE.toPx() }
    val collapsed by remember(grid, distance, inPane) {
        derivedStateOf {
            when {
                inPane -> 0f
                grid.firstVisibleItemIndex > 0 -> 1f
                else -> (grid.firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)
            }
        }
    }

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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTitleBar(
                title = stringResource(R.string.home_title),
                collapsed = collapsed,
                // No navigation: with the bar's own padding the title starts 16 dp in, over the rows' icons.
                navigation = { Spacer(Modifier.width(4.dp)) },
                actions = {
                    // R2.1: switches between rows and tiles; the choice is kept.
                    val switchLabel = stringResource(if (tiles) R.string.action_show_list else R.string.action_show_tiles)
                    TooltipIconButton(switchLabel, { model.setLayout(if (tiles) HomeLayout.LIST else HomeLayout.TILES) }) {
                        Icon(painterResource(if (tiles) R.drawable.ic_view_list else R.drawable.ic_view_tiles), contentDescription = switchLabel)
                    }
                    TooltipIconButton(stringResource(R.string.settings_title), onSettings, tooltip = stringResource(R.string.tooltip_settings)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            // R2.4: Update all (two or more outdated apps) above New app, the same width. Beside the
            // detail pane, New app shows only while the detail holds something else; the empty detail
            // pane has its own.
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // R11.7: two or more apps signed with another key.
                if (toFix.size >= 2) {
                    MainActionButton(
                        stringResource(R.string.action_fix_all),
                        Icons.Filled.Build,
                        onClick = { model.fix(toFix) },
                        modifier = Modifier.fillMaxWidth(),
                        secondary = true,
                    )
                }
                if (outdated.size >= 2) {
                    MainActionButton(
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
            // Tiles sit 12 dp in from the sides; rows run edge to edge with their own inset.
            val gridInset = if (tiles) TILE_INSET else 0.dp
            val mediumWidth = isMediumWidth()
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(if (!tiles) MIN_CELL_WIDTH else if (mediumWidth) WIDE_MIN_TILE_WIDTH else MIN_TILE_WIDTH),
                // The sides keep clear of a side navigation bar or camera cutout (landscape phones), and
                // the bottom leaves room so the last card can scroll clear of the floating buttons.
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + gridInset,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = padding.calculateEndPadding(layoutDirection) + gridInset,
                    bottom = 168.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                verticalArrangement = Arrangement.spacedBy(if (tiles) 8.dp else 0.dp),
            ) {
                // R11.6, R11.7: apps from a previous install, signed with a key Shrimpl no longer has.
                if (showKeyNotice) {
                    item(key = OTHER_KEY_NOTICE, span = { GridItemSpan(maxLineSpan) }, contentType = OTHER_KEY_NOTICE) {
                        NoticeCard(
                            Notice(Notice.Kind.CAUTION, stringResource(R.string.other_key_notice)),
                            modifier = Modifier.padding(horizontal = 16.dp - gridInset, vertical = 8.dp),
                            onDismiss = { model.dismissKeyNotice(otherKey) },
                            action = stringResource(R.string.action_open_settings) to onSettings,
                        )
                    }
                }
                // R2.2: uninstalled apps sit at the bottom (in rows, under a "Not installed" label), each
                // group in name order.
                val (present, absent) = records.partition { it.packageName in installed }
                val row: @Composable (WebAppRecord) -> Unit = { record ->
                    val app = installed[record.packageName]
                    AppItem(
                        tile = tiles,
                        record = record,
                        installed = app,
                        density = density,
                        loadImage = { file -> store.image(record.appId, file) },
                        updateAvailable = model.updateAvailable(record),
                        otherKey = record.appId in otherKey,
                        selected = record.appId == selected,
                        clickAction = clickAction,
                        resolveOnTap = resolveOnTap,
                        hapticFeedback = hapticFeedback,
                        onOpen = { if (app != null) InstalledApps.open(context, record.packageName) else onEdit(record.appId) },
                        onEdit = { onEdit(record.appId) },
                        onRebuild = { model.rebuild(record) },
                        onUninstall = { confirmUninstall = record.appId },
                        onFix = { model.fix(listOf(record)) },
                        onRemove = { model.remove(record) },
                    )
                }
                val contentType = if (tiles) APP_TILE else APP_ROW
                items(present, key = { it.appId }, contentType = { contentType }) { row(it) }
                if (tiles) {
                    items(absent, key = { it.appId }, contentType = { contentType }) { row(it) }
                } else if (absent.isNotEmpty()) {
                    item(key = NOT_INSTALLED_LABEL, span = { GridItemSpan(maxLineSpan) }, contentType = NOT_INSTALLED_LABEL) {
                        Text(
                            stringResource(R.string.home_not_installed),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp).asHeading(),
                        )
                    }
                    items(absent, key = { it.appId }, contentType = { APP_ROW }) { row(it) }
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
            text = { Text(stringResource(R.string.update_all_text)) },
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

/** One web app, as a row or a tile (R2.1, R2.6). */
@Composable
private fun AppItem(
    tile: Boolean,
    record: WebAppRecord,
    installed: InstalledApp?,
    density: Density,
    loadImage: (String) -> ByteArray,
    updateAvailable: Boolean,
    otherKey: Boolean,
    selected: Boolean,
    clickAction: ClickAction,
    resolveOnTap: Boolean,
    hapticFeedback: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onFix: () -> Unit,
    onRemove: () -> Unit,
) {
    // R11.7: installed but signed with another key; it can only be fixed (uninstalled, then rebuilt) or removed.
    val locked = otherKey
    // The menu opens where the item was pressed.
    // Invariant: the menu keeps its place after it closes, so it doesn't jump while it fades out
    // (found on a device, docs/BUILD_NOTES.md "Robolectric").
    var menuOpen by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf(DpOffset.Zero) }
    val openMenu = { at: DpOffset ->
        menuAt = at
        menuOpen = true
    }
    val showOptions = stringResource(R.string.action_show_options)
    val showMenu = { at: Offset -> openMenu(with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }) }
    val haptic = LocalHapticFeedback.current
    val vibrate = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    // The action its row button offers (R2.2, R11.7): Fix or Update, with its label.
    val pending = when {
        locked -> R.string.action_fix_app to onFix
        installed != null && updateAvailable -> R.string.action_update_app to onRebuild
        else -> null
    }
    // A tap opens or edits the app, as chosen in Settings (R2.5), or with Resolve on tap does its
    // pending action, unless the app isn't installed. Otherwise an uninstalled app, or one signed with
    // another key, does nothing on a tap, so it offers no tap to accessibility services either.
    val resolve = pending?.takeIf { resolveOnTap && installed != null }
    val onTap = when {
        resolve != null -> resolve.second
        locked || installed == null -> null
        clickAction == ClickAction.EDIT -> onEdit
        else -> onOpen
    }
    val tapLabel = resolve?.let { stringResource(it.first, record.profile.name) }
    val actions = menuActions(locked, installed != null, updateAvailable, onOpen, onEdit, onRebuild, onUninstall, onFix, onRemove)
    // Invariant: the anchor is an empty box at the pressed point. A dropdown menu opens below its anchor
    // and adds its offset to the anchor's bottom, so offsetting the menu itself put it far below a tile
    // (found on a device, docs/BUILD_NOTES.md "Robolectric").
    val menu: @Composable () -> Unit = {
        Box(Modifier.offset { IntOffset(menuAt.x.roundToPx(), menuAt.y.roundToPx()) }) {
            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                actions.forEach { (label, action) ->
                    AppMenuItem(text = { Text(stringResource(label)) }, onClick = {
                        menuOpen = false
                        action()
                    })
                }
            }
        }
    }
    val status = appStatus(installed == null, locked, updateAvailable)?.let { stringResource(it) }
    if (tile) {
        Box {
            AppTile(record, installed, loadImage, updateAvailable, locked, selected, Modifier.appGestures(record.appId, onTap, tapLabel, showOptions, hapticFeedback, showMenu, vibrate) { openMenu(DpOffset(16.dp, 64.dp)) })
            menu()
        }
        return
    }
    Box(
        Modifier
            // The highlight sits 4 dp in from each side, so its rounded corners don't meet the edge.
            .padding(horizontal = ROW_INSET)
            // Borderless rows: the row's pill shows only as the press, hover and (in two panes)
            // selection highlight.
            .clip(AppShapes.Row)
            // The selected row takes the hover tint, not secondaryContainer, so its tonal Update button stays
            // visible on it.
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
            // In two panes the highlighted row is announced as selected.
            .semantics { if (selected) this.selected = true }
            .statusForAccessibility(status)
            .appGestures(record.appId, onTap, tapLabel, showOptions, hapticFeedback, showMenu, vibrate) { openMenu(DpOffset(24.dp, 48.dp)) },
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
            Box(if (greyed) Modifier.greyedOut() else Modifier) { ItemIcon(record, installed, loadImage) }
            Column(Modifier.weight(1f)) {
                Text(record.profile.name, style = MaterialTheme.typography.titleMedium, modifier = if (greyed) Modifier.alpha(GREYED_ALPHA) else Modifier)
            }
            // R2.2: a newer template is bundled; Update rebuilds this app with it. R11.7: an app signed
            // with another key offers Fix (uninstall, then rebuild with the current key).
            val rowAction = when {
                locked -> RowAction(R.string.action_fix_app, Icons.Filled.Build, Tone.ERROR, onFix)
                installed == null -> RowAction(R.string.action_reinstall_app, ReplayIcon, Tone.CAUTION, onRebuild)
                updateAvailable -> RowAction(R.string.action_update_app, UpdateIcon, Tone.SUCCESS, onRebuild)
                else -> null
            }
            rowAction?.let { RowActionButton(it, record.profile.name) }
            val label = stringResource(R.string.action_app_options, record.profile.name)
            var optionsOpen by rememberSaveable { mutableStateOf(false) }
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
        menu()
    }
}

/**
 * Accessibility services hear an app's [status] (null: none) as its row's or tile's state (R2.2,
 * R2.6): no text shows it, and the badge and row button don't say it.
 */
private fun Modifier.statusForAccessibility(status: String?): Modifier =
    if (status == null) this else semantics { stateDescription = status }

/** An app's status (R2.2, R2.6), for accessibility services, in the order its badge or row button picks. */
private fun appStatus(notInstalled: Boolean, locked: Boolean, updateAvailable: Boolean): Int? = when {
    notInstalled -> R.string.home_not_installed
    locked -> R.string.status_fix_required
    updateAvailable -> R.string.status_update_ready
    else -> null
}

/**
 * Right-click (secondary mouse button or two-finger trackpad tap) opens the menu at the pointer;
 * a long press opens it there too, and [onTap] (null: the item does nothing on a tap) runs on a tap,
 * named [tapLabel] for accessibility services when it isn't the usual open or edit. They get the
 * long press as "Show options", at [menuForAccessibility]. With [haptics] (the Haptic feedback setting,
 * R2.5) a long press vibrates as the menu opens, through [vibrate]; a right-click and an accessibility
 * action don't.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.appGestures(
    key: String,
    onTap: (() -> Unit)?,
    tapLabel: String?,
    showOptions: String,
    haptics: Boolean,
    showMenu: (Offset) -> Unit,
    vibrate: () -> Unit,
    menuForAccessibility: () -> Unit,
): Modifier = pointerInput(key) {
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
}.then(
    if (onTap == null) {
        Modifier
            .pointerInput(key, haptics) {
                detectTapGestures(onLongPress = {
                    if (haptics) vibrate()
                    showMenu(it)
                })
            }
            .semantics(mergeDescendants = true) { onLongClick(showOptions) { menuForAccessibility(); true } }
    } else {
        // combinedClickable vibrates for a long press itself, and only for the gesture.
        Modifier.combinedClickable(
            onClick = onTap,
            onClickLabel = tapLabel,
            onLongClickLabel = showOptions,
            onLongClick = menuForAccessibility,
            hapticFeedbackEnabled = haptics,
        )
    },
)

/**
 * R2.6: a tile in the theme's container color: the icon (56 dp) and the name. A badge on the icon
 * marks an app that needs something (R2.2's pastels): a red wrench (signed with another key, R11.7), a
 * green up arrow (a template update, R11.4) or a yellow replay (not installed); accessibility services
 * hear it as the tile's state.
 */
@Composable
private fun AppTile(
    record: WebAppRecord,
    installed: InstalledApp?,
    loadImage: (String) -> ByteArray,
    updateAvailable: Boolean,
    locked: Boolean,
    selected: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val greyed = installed == null
    val update = !greyed && !locked && updateAvailable
    val status = appStatus(greyed, locked, updateAvailable)
    val tileColor = if (selected) colors.surfaceContainerHighest else colors.surfaceContainer
    val nameLines = if (LocalDensity.current.fontScale > 1f) 2 else 1
    Column(
        Modifier
            .clip(AppShapes.Item)
            .background(tileColor)
            .semantics { if (selected) this.selected = true }
            .statusForAccessibility(status?.let { stringResource(it) })
            .then(modifier)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Box(if (greyed) Modifier.greyedOut() else Modifier) { ItemIcon(record, installed, loadImage, TILE_ICON_SIZE) }
            when {
                // The badge sits outside the dimmed icon, so a not-installed app's badge keeps its colors.
                greyed -> StatusBadge(ReplayIcon, Tone.CAUTION, tileColor, Modifier.align(Alignment.TopEnd))
                locked -> StatusBadge(Icons.Filled.Build, Tone.ERROR, tileColor, Modifier.align(Alignment.TopEnd))
                update -> StatusBadge(UpdateIcon, Tone.SUCCESS, tileColor, Modifier.align(Alignment.TopEnd))
            }
        }
        // R2.6: with a larger font size a name may take a second line rather than be cut short; every
        // tile then keeps two lines' height, so the tiles in a line still match, and a one-line name
        // sits centred in them. An empty text of that many lines sets the height for the font size.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("", style = MaterialTheme.typography.bodyMedium, minLines = nameLines, modifier = Modifier.clearAndSetSemantics {})
            Text(
                record.profile.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
                modifier = if (greyed) Modifier.alpha(GREYED_ALPHA) else Modifier,
            )
        }
    }
}

/**
 * A round badge on a tile's icon, ringed in the tile's color so it stands clear of the icon. The tile
 * states the status for accessibility services, so they skip the badge.
 */
@Composable
private fun StatusBadge(icon: ImageVector, tone: Tone, ring: Color, modifier: Modifier) {
    Box(
        modifier
            .offset(x = BADGE_OVERHANG, y = -BADGE_OVERHANG)
            .size(BADGE_SIZE)
            .border(BADGE_RING, ring, CircleShape)
            .padding(BADGE_RING)
            .background(tone.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(BADGE_SYMBOL)) }
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
private fun ItemIcon(record: WebAppRecord, installed: InstalledApp?, loadImage: (String) -> ByteArray, size: Dp = ROW_ICON_SIZE) {
    if (installed != null) {
        val px = with(LocalDensity.current) { size.roundToPx() }
        val bitmap = remember(installed, px) { installed.icon.toBitmap(px, px).asImageBitmap() }
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
        return
    }
    val layers by rememberIconLayers(record.icon, record.profile.iconBackground, record.profile.resolvedIconForeground, record.updatedAt, loadImage)
    // The icon's space is kept while its layers load, so a row or tile doesn't grow when they arrive.
    Box(Modifier.size(size)) { layers?.let { AdaptiveIcon(it, CircleShape, size) } }
}

private val ROW_ICON_SIZE = 48.dp
private val TILE_ICON_SIZE = 56.dp

/** The narrowest a tile gets before the grid drops a column: three across on phones, even 360 dp ones. */
private val MIN_TILE_WIDTH = 100.dp

/**
 * The narrowest a tile gets in a Medium window or wider, so tiles stay near their phone width: three
 * across beside the detail pane (about 119 dp each, with 2 dp to spare), six across a window snapped
 * to half a laptop screen (about 850 dp), where 100 dp tiles would cut names short.
 */
private val WIDE_MIN_TILE_WIDTH = 118.dp

/** How far the tiles sit in from the sides of the list. */
private val TILE_INSET = 12.dp

/** The space between columns of rows or tiles. */
private val GRID_GAP = 8.dp



/**
 * A tile's status badge (R2.6): 28 dp across with a 2 dp ring, large enough that its symbol reads at a
 * glance, overhanging the icon's corner by a quarter of its size so it covers little of the icon.
 */
private val BADGE_SIZE = 28.dp
private val BADGE_RING = 2.dp
private val BADGE_OVERHANG = 7.dp
private val BADGE_SYMBOL = 16.dp

/** How far each row's highlight sits in from the sides. */
private val ROW_INSET = 4.dp

/** The narrowest a web app item gets before the grid drops a column. */
private val MIN_CELL_WIDTH = 360.dp

/** The space Scaffold leaves between the floating action button and the bottom of the content. */
private val FAB_MARGIN = 16.dp

private const val OTHER_KEY_NOTICE = "other-key-notice"
private const val NOT_INSTALLED_LABEL = "not-installed-label"

/** The content types of web app rows and tiles, so the grid reuses rows for rows and tiles for tiles. */
private const val APP_ROW = "app-row"
private const val APP_TILE = "app-tile"

/** The dimming of an uninstalled app's name and icon: its name keeps 4.5:1 contrast in both themes. */
private const val GREYED_ALPHA = 0.65f

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
 * An app's ⋮, right-click and long-press menu (R2.1, R2.2, R11.7): its pending action first (Fix,
 * Update or Reinstall), then Open, Edit, Rebuild and Uninstall or Remove, as they apply. An app signed
 * with another key offers only Fix, Edit and Remove.
 */
private fun menuActions(
    locked: Boolean,
    installed: Boolean,
    updateAvailable: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onFix: () -> Unit,
    onRemove: () -> Unit,
): List<Pair<Int, () -> Unit>> = buildList {
    val pending = when {
        locked -> R.string.action_fix to onFix
        !installed -> R.string.action_reinstall to onRebuild
        updateAvailable -> R.string.action_update to onRebuild
        else -> null
    }
    pending?.let(::add)
    if (installed && !locked) add(R.string.action_open to onOpen)
    add(R.string.action_edit to onEdit)
    if (pending == null) add(R.string.action_rebuild to onRebuild)
    when {
        locked || !installed -> add(R.string.action_remove to onRemove)
        else -> add(R.string.action_uninstall to onUninstall)
    }
}

/** A row's action (R2.2): its label (taking the app's name), icon, and whether it is [urgent] (Fix). */
private class RowAction(val label: Int, val icon: ImageVector, val tone: Tone, val onClick: () -> Unit)

/**
 * R2.2: a small icon-only button (48 x 32 dp, a full 48 dp touch target) with a tooltip naming the
 * action, in its status's pastel with a deep icon: Fix in red, Reinstall in yellow, Update in green.
 *
 * Invariant: each button matches its status's badge on a tile ([AppTile], [StatusBadge]), so a state
 * looks the same in both layouts.
 */
@Composable
private fun RowActionButton(action: RowAction, appName: String) {
    val label = stringResource(action.label, appName)
    val colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = action.tone.container, contentColor = action.tone.content)
    WithTooltip(label) {
        FilledTonalIconButton(
            onClick = action.onClick,
            shape = AppShapes.Control,
            colors = colors,
            modifier = Modifier.size(width = 48.dp, height = 32.dp),
        ) { Icon(action.icon, contentDescription = label, modifier = Modifier.size(18.dp)) }
    }
}

/** Material's hover state layer: the content color at 8 %. */
private const val HOVER_ALPHA = 0.08f
