package com.valghora.uniwrap.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valghora.uniwrap.R
import com.valghora.uniwrap.container
import com.valghora.uniwrap.data.InstalledApp
import com.valghora.uniwrap.data.InstalledApps
import com.valghora.uniwrap.ui.common.BuildSheet
import com.valghora.uniwrap.ui.edit.EditScreen
import com.valghora.uniwrap.ui.icons.AdaptiveIcon
import com.valghora.uniwrap.ui.icons.IconSelection
import com.valghora.uniwrap.ui.icons.rememberIconLayers
import com.valghora.wrapper.core.profile.Profile
import com.valghora.wrapper.core.records.WebAppRecord
import kotlinx.coroutines.launch

/**
 * The home screen (docs/UNIWRAP_SPEC.md R2): "My Web Apps" with the edit page as the detail pane, side
 * by side on wide screens (R2.4).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(onCreate: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val model = viewModel { HomeViewModel(context.container) }
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val records by model.records.collectAsStateWithLifecycle()
    val installed by model.installed.collectAsStateWithLifecycle()
    val buildState by model.build.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        model.refresh(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(records, buildState) { model.refresh(context) }

    val selected = navigator.currentDestination?.contentKey
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                AppList(
                    records = records,
                    installed = installed,
                    templateVersion = model.templateVersion,
                    selected = selected.takeIf { navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded },
                    onCreate = onCreate,
                    onSettings = onSettings,
                    onOpen = { record ->
                        if (record.packageName in installed) {
                            InstalledApps.open(context, record.packageName)
                        } else {
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, record.appId) }
                        }
                    },
                    onEdit = { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it.appId) } },
                    onRebuild = model::rebuild,
                    onRemove = model::remove,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val appId = navigator.currentDestination?.contentKey
                if (appId != null) {
                    EditScreen(
                        appId = appId,
                        showBack = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Expanded,
                        onBack = { scope.launch { navigator.navigateBack() } },
                    )
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.home_pick_app), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppList(
    records: List<WebAppRecord>,
    installed: Map<String, InstalledApp>,
    templateVersion: Int,
    selected: String?,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (WebAppRecord) -> Unit,
    onEdit: (WebAppRecord) -> Unit,
    onRebuild: (WebAppRecord) -> Unit,
    onRemove: (WebAppRecord) -> Unit,
) {
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            },
            floatingActionButton = {
                // R2.3: the button that opens the configuration page.
                if (wide) {
                    ExtendedFloatingActionButton(
                        onClick = onCreate,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_new_web_app)) },
                    )
                } else {
                    FloatingActionButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_web_app))
                    }
                }
            },
        ) { padding ->
            if (records.isEmpty()) {
                Column(
                    Modifier.padding(padding).fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.home_empty_text),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp)) {
                    items(records, key = { it.appId }) { record ->
                        AppRow(
                            record = record,
                            installed = installed[record.packageName],
                            updateAvailable = record.updateAvailable(templateVersion),
                            selected = record.appId == selected,
                            onOpen = { onOpen(record) },
                            onEdit = { onEdit(record) },
                            onRebuild = { onRebuild(record) },
                            onRemove = { onRemove(record) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    record: WebAppRecord,
    installed: InstalledApp?,
    updateAvailable: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.clickable(onClick = onOpen)) {
        ListItem(
            leadingContent = { RowIcon(record, installed) },
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
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit_app, record.profile.name)) }
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
}

@Composable
private fun RowIcon(record: WebAppRecord, installed: InstalledApp?) {
    if (installed != null) {
        val bitmap = remember(installed) { installed.icon.toBitmap(144, 144).asImageBitmap() }
        Image(bitmap, contentDescription = null, modifier = Modifier.size(48.dp))
        return
    }
    val context = LocalContext.current
    val selection = IconSelection(record.icon, null, record.profile.iconBackground, record.profile.iconForeground.takeUnless { it == Profile.AUTO }.orEmpty())
    val layers by rememberIconLayers(selection, record.profile.name) { context.container.store.image(record.appId) }
    layers?.let { AdaptiveIcon(it, CircleShape, 48.dp) }
}
