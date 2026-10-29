package com.valghora.uniwrap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.valghora.uniwrap.ui.common.CenteredEmptyState
import com.valghora.uniwrap.ui.common.NewWebAppButton
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

// Edit, Create and Settings are detail panes: beside the list on wide windows, full screen otherwise.

/** A web app's settings. */
@Serializable
data class EditKey(val appId: String) : NavKey

/** The configuration page for a new web app. */
@Serializable
data object CreateKey : NavKey

/** UniWrap's own settings. */
@Serializable
data object SettingsKey : NavKey

/** App-wide keyboard shortcuts (docs/ADAPTIVE.md, Tier 2 Keyboard_Shortcuts). */
enum class AppShortcut { NEW_WEB_APP, SETTINGS }

/**
 * Navigation 3 with Material's list-detail scene strategy (adaptive skill step 3): "My Web Apps" is
 * the list pane, and a web app's settings, a new web app or UniWrap's settings the detail pane, side
 * by side when the window has room and one at a time otherwise. Each entry gets its own saved state
 * and ViewModels.
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
    val currentTwoPane by rememberUpdatedState(twoPane)
    val show: (NavKey) -> Unit = { key -> backStack.showDetail(key, replace = currentTwoPane) }

    LaunchedEffect(backStack) {
        shortcuts.collect { shortcut ->
            when (shortcut) {
                AppShortcut.NEW_WEB_APP -> show(CreateKey)
                AppShortcut.SETTINGS -> show(SettingsKey)
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
                    PaneCard(twoPane, PaneSide.END) { DetailPlaceholder(onCreate = { show(CreateKey) }) }
                },
            )
            entry<HomeKey>(metadata = listPane) {
                PaneCard(twoPane, PaneSide.START) {
                    val detail = backStack.lastOrNull()
                    HomeScreen(
                        selected = (detail as? EditKey)?.appId?.takeIf { twoPane },
                        // Beside the detail pane, the list's button shows only while the detail holds
                        // settings; the empty detail pane and the configuration page have their own.
                        showCreateButton = !twoPane || detail is EditKey || detail is SettingsKey,
                        onCreate = { show(CreateKey) },
                        onSettings = { show(SettingsKey) },
                        onEdit = { appId -> show(EditKey(appId)) },
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
            entry<CreateKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                PaneCard(twoPane, PaneSide.END) {
                    CreateScreen(
                        onClose = { backStack.remove(CreateKey) },
                        onBackUpKey = {
                            backStack.remove(CreateKey)
                            show(SettingsKey)
                        },
                    )
                }
            }
            entry<SettingsKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                PaneCard(twoPane, PaneSide.END) {
                    SettingsScreen(inPane = twoPane, onBack = { backStack.remove(SettingsKey) })
                }
            }
        },
    )
}

/**
 * Shows [key] in the detail pane. With two panes ([replace]) it takes the place of whatever the
 * detail pane showed, so the list is always one Back away; with one pane it is pushed, unless it is
 * already on top.
 */
private fun NavBackStack<NavKey>.showDetail(key: NavKey, replace: Boolean) {
    val top = lastOrNull()
    when {
        top == key -> Unit
        replace && top != null && top != HomeKey -> set(lastIndex, key)
        top is EditKey && key is EditKey -> set(lastIndex, key)
        else -> add(key)
    }
}

/**
 * The detail pane before anything is opened in it: how to start when there are no web apps yet,
 * else a prompt to pick one. Both offer New web app, the list's button being hidden meanwhile.
 */
@Composable
private fun DetailPlaceholder(onCreate: () -> Unit) {
    val records by LocalContext.current.container.store.records.collectAsStateWithLifecycle()
    val empty = records.isEmpty()
    CenteredEmptyState(stringResource(if (empty) R.string.home_start_title else R.string.home_pick_app)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (empty) {
                Text(
                    stringResource(R.string.home_start_text),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            NewWebAppButton(onCreate, Modifier.padding(top = 24.dp))
        }
    }
}
