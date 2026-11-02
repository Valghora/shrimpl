package com.valghora.shrimpl.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.valghora.shrimpl.ui.common.asHeading
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
    val method by model.installMethod.collectAsStateWithLifecycle()
    val signingKey by model.signingKey.collectAsStateWithLifecycle()
    val layout by model.layout.collectAsStateWithLifecycle()
    val tiles = layout == HomeLayout.TILES
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
                        Text(stringResource(R.string.home_title), Modifier.asHeading())
                    }
                },
                scrollBehavior = scrollBehavior,
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
            // Tiles sit 12 dp in from the sides; rows run edge to edge with their own inset.
            val gridInset = if (tiles) TILE_INSET else 0.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (tiles) MIN_TILE_WIDTH else MIN_CELL_WIDTH),
                // The sides keep clear of a side navigation bar or camera cutout (landscape phones), and
                // the bottom leaves room so the last card can scroll clear of the floating buttons.
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + gridInset,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = padding.calculateEndPadding(layoutDirection) + gridInset,
                    bottom = 168.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(if (tiles) 8.dp else 0.dp),
            ) {
                // R11.6, R11.7: apps from a previous install, signed with a key Shrimpl no longer has.
                if (showKeyNotice) {
                    item(key = OTHER_KEY_NOTICE, span = { GridItemSpan(maxLineSpan) }, contentType = OTHER_KEY_NOTICE) {
                        NoticeCard(
                            Notice(Notice.Kind.CAUTION, stringResource(R.string.other_key_notice)),
                            modifier = Modifier.padding(horizontal = 16.dp - gridInset, vertical = 8.dp),
                            action = stringResource(R.string.action_open_settings) to onSettings,
                            endAction = stringResource(R.string.action_dismiss) to { model.dismissKeyNotice(otherKey) },
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
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onFix: () -> Unit,
    onRemove: () -> Unit,
) {
    // R11.7: installed but signed with another key; it can only be fixed (uninstalled, then rebuilt) or removed.
    val locked = installed != null && otherKey
    // The menu opens where the item was pressed. Its place is kept after it closes, so it doesn't move
    // while it fades out.
    var menuOpen by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf(DpOffset.Zero) }
    val openMenu = { at: DpOffset ->
        menuAt = at
        menuOpen = true
    }
    val showOptions = stringResource(R.string.action_show_options)
    val showMenu = { at: Offset -> openMenu(with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }) }
    // A tap opens or edits the app, as chosen in Settings (R2.5). An uninstalled app, or one signed with
    // another key, does nothing on a tap, so it offers no tap to accessibility services either.
    val onTap = when {
        locked || installed == null -> null
        clickAction == ClickAction.EDIT -> onEdit
        else -> onOpen
    }
    // Tiles have no ⋮ button or row buttons, so their menu also offers Fix (R2.6).
    val actions = menuActions(locked, installed != null, updateAvailable, withFix = tile, onOpen, onEdit, onRebuild, onUninstall, onFix, onRemove)
    // A dropdown menu opens below its anchor, so the anchor is an empty box at the pressed point.
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
    if (tile) {
        Box {
            AppTile(record, installed, loadImage, updateAvailable, locked, selected, Modifier.appGestures(record.appId, onTap, showOptions, showMenu) { openMenu(DpOffset(16.dp, 64.dp)) })
            menu()
        }
        return
    }
    Box(
        Modifier
            // The highlight sits 4 dp in from each side, so its rounded corners don't meet the edge.
            .padding(horizontal = ROW_INSET)
            // Borderless rows: the settings items' rounded shape shows only as the press, hover and
            // (in two panes) selection highlight.
            .clip(ItemShape)
            // The selected row takes the hover tint, not secondaryContainer, so its tonal Update pill stays
            // visible on it.
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
            // In two panes the highlighted row is announced as selected.
            .semantics { if (selected) this.selected = true }
            .appGestures(record.appId, onTap, showOptions, showMenu) { openMenu(DpOffset(24.dp, 48.dp)) },
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
 * Right-click (secondary mouse button or two-finger trackpad tap) opens the menu at the pointer;
 * a long press opens it there too, and [onTap] (null: the item does nothing on a tap) runs on a tap.
 * Accessibility services get the long press as "Show options", at [menuForAccessibility].
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.appGestures(
    key: String,
    onTap: (() -> Unit)?,
    showOptions: String,
    showMenu: (Offset) -> Unit,
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
            .pointerInput(key) { detectTapGestures(onLongPress = { showMenu(it) }) }
            .semantics(mergeDescendants = true) { onLongClick(showOptions) { menuForAccessibility(); true } }
    } else {
        Modifier.combinedClickable(onClick = onTap, onLongClickLabel = showOptions, onLongClick = menuForAccessibility)
    },
)

/**
 * R2.6: a tile in the theme's container color: the icon (56 dp), the name, and a status line (Needs
 * fix, Update ready or Not installed). A badge on the icon marks an app that needs something: a
 * wrench in the theme's error color (signed with another key, R11.7) or a refresh arrow in its
 * primary color (a template update, R11.4).
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
    val status = when {
        greyed -> R.string.home_not_installed
        locked -> R.string.status_needs_fix
        update -> R.string.status_update_ready
        else -> null
    }
    val tileColor = if (selected) colors.surfaceContainerHighest else colors.surfaceContainer
    Column(
        Modifier
            .clip(TileShape)
            .background(tileColor)
            .semantics { if (selected) this.selected = true }
            .then(modifier)
            .fillMaxWidth()
            .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Box(if (greyed) Modifier.greyedOut() else Modifier) { ItemIcon(record, installed, loadImage, TILE_ICON_SIZE) }
            when {
                locked -> StatusBadge(Icons.Filled.Build, colors.error, colors.onError, tileColor, Modifier.align(Alignment.TopEnd))
                update -> StatusBadge(Icons.Filled.Refresh, colors.primary, colors.onPrimary, tileColor, Modifier.align(Alignment.TopEnd))
            }
        }
        Text(
            record.profile.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (greyed) Modifier.alpha(GREYED_ALPHA) else Modifier,
        )
        // Every tile keeps the status line's height, so the tiles in a line match.
        Text(
            status?.let { stringResource(it) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (status == null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}

/**
 * A round badge on a tile's icon, ringed in the tile's color so it stands clear of the icon. It
 * repeats the status line, so accessibility services skip it.
 */
@Composable
private fun StatusBadge(icon: ImageVector, container: Color, content: Color, ring: Color, modifier: Modifier) {
    Box(
        modifier
            .offset(x = BADGE_OVERHANG, y = -BADGE_OVERHANG)
            .size(BADGE_SIZE)
            .border(BADGE_RING, ring, CircleShape)
            .padding(BADGE_RING)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp)) }
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
    layers?.let { AdaptiveIcon(it, CircleShape, size) }
}

private val ROW_ICON_SIZE = 48.dp
private val TILE_ICON_SIZE = 56.dp

/** The narrowest a tile gets before the grid drops a column: three across on phones. */
private val MIN_TILE_WIDTH = 100.dp

/** How far the tiles sit in from the sides of the list. */
private val TILE_INSET = 12.dp

private val TileShape = RoundedCornerShape(22.dp)

/** A tile's status badge: 22 dp across, with a 2 dp ring, overhanging the icon's corner. */
private val BADGE_SIZE = 22.dp
private val BADGE_RING = 2.dp
private val BADGE_OVERHANG = 5.dp

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
 * R2.2: an app's actions, the same in its ⋮ menu and its right-click and long-press menu: Open,
 * Edit, then Update (an update is available), Reinstall (not installed) or Rebuild, then Uninstall
 * or Remove. An app signed with another key ([locked], R11.7) offers only Remove, after Fix
 * [withFix] (a tile, which has no Fix button, R2.6).
 */
private fun menuActions(
    locked: Boolean,
    installed: Boolean,
    updateAvailable: Boolean,
    withFix: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onUninstall: () -> Unit,
    onFix: () -> Unit,
    onRemove: () -> Unit,
): List<Pair<Int, () -> Unit>> = if (locked) {
    listOfNotNull(if (withFix) R.string.action_fix to onFix else null, R.string.action_remove to onRemove)
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
