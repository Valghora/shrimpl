package com.valghora.uniwrap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.valghora.uniwrap.R
import com.valghora.uniwrap.container
import com.valghora.uniwrap.ui.common.PaneCard
import com.valghora.uniwrap.ui.common.PaneSide
import com.valghora.uniwrap.ui.common.paneBackground
import com.valghora.uniwrap.ui.create.CreateScreen
import com.valghora.uniwrap.ui.edit.EditScreen
import com.valghora.uniwrap.ui.home.HomeScreen
import com.valghora.uniwrap.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Destinations (docs/UNIWRAP_SPEC.md R2). The back stack is saved, so the open screen survives
// rotation, folding, resizing and process death.

@Serializable
data object HomeKey : NavKey

/** A web app's settings: the detail pane next to the list on wide windows. */
@Serializable
data class EditKey(val appId: String) : NavKey

@Serializable
data object CreateKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** App-wide keyboard shortcuts (docs/ADAPTIVE.md, Tier 2 Keyboard_Shortcuts). */
enum class AppShortcut { NEW_WEB_APP, SETTINGS }

/**
 * Navigation 3 with Material's list-detail scene strategy (adaptive skill step 3): "My Web Apps" is
 * the list pane and a web app's settings the detail pane, side by side when the window has room and
 * one at a time otherwise. Each entry gets its own saved state and ViewModels.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun UniWrapNavigation(shortcuts: Flow<AppShortcut>) {
    val backStack = rememberNavBackStack(HomeKey)
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val listDetail = rememberListDetailSceneStrategy<NavKey>(
        directive = directive,
        // Desktop and tablet users can widen or narrow the list by dragging the gap between panes.
        paneExpansionDragHandle = { state ->
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(state, LocalMinimumInteractiveComponentSize.current, interactionSource),
                interactionSource = interactionSource,
            )
        },
    )
    // With two panes the detail has no back arrow (adaptive skill: detail screens in a list-detail
    // layout must not show one), and both panes are cards on the window background.
    val twoPane = directive.maxHorizontalPartitions > 1

    LaunchedEffect(backStack) {
        shortcuts.collect { shortcut ->
            when (shortcut) {
                AppShortcut.NEW_WEB_APP -> backStack.open(CreateKey)
                AppShortcut.SETTINGS -> backStack.open(SettingsKey)
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.background(paneBackground()),
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        sceneStrategies = listOf(listDetail),
        entryProvider = entryProvider {
            val listPane = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    PaneCard(twoPane, PaneSide.END) { DetailPlaceholder(onCreate = { backStack.open(CreateKey) }) }
                },
            )
            entry<HomeKey>(metadata = listPane) {
                PaneCard(twoPane, PaneSide.START) {
                    HomeScreen(
                        selected = (backStack.lastOrNull() as? EditKey)?.appId?.takeIf { twoPane },
                        onCreate = { backStack.open(CreateKey) },
                        onSettings = { backStack.open(SettingsKey) },
                        onEdit = { appId -> backStack.openDetail(EditKey(appId)) },
                    )
                }
            }
            entry<EditKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                PaneCard(twoPane, PaneSide.END) {
                    EditScreen(
                        appId = key.appId,
                        showBack = !twoPane,
                        onBack = { backStack.remove(key) },
                    )
                }
            }
            entry<CreateKey> {
                CreateScreen(
                    onClose = { backStack.remove(CreateKey) },
                    onBackUpKey = {
                        backStack.remove(CreateKey)
                        backStack.open(SettingsKey)
                    },
                )
            }
            entry<SettingsKey> {
                SettingsScreen(onBack = { backStack.remove(SettingsKey) })
            }
        },
    )
}

/** Pushes [key] unless it is already on top. */
private fun NavBackStack<NavKey>.open(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Shows [key] in the detail pane: replaces the open web app instead of stacking another one. */
private fun NavBackStack<NavKey>.openDetail(key: EditKey) {
    if (lastOrNull() is EditKey) set(lastIndex, key) else add(key)
}

/**
 * The detail pane before a web app is selected. Without any web apps yet it explains how to start,
 * with the same action as the list's button.
 */
@Composable
private fun DetailPlaceholder(onCreate: () -> Unit) {
    val records by LocalContext.current.container.store.records.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (records.isEmpty()) {
            Text(stringResource(R.string.home_start_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.home_start_text),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).widthIn(max = 480.dp),
            )
            Button(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_new_web_app))
            }
        } else {
            Text(stringResource(R.string.home_pick_app), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
