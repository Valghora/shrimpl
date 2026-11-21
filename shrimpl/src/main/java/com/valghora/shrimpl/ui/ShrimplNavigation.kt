package com.valghora.shrimpl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.ui.common.CenteredEmptyState
import com.valghora.shrimpl.ui.common.EmptyStateText
import com.valghora.shrimpl.ui.common.NewWebAppButton
import com.valghora.shrimpl.ui.common.PaneCard
import com.valghora.shrimpl.ui.common.PaneSide
import com.valghora.shrimpl.ui.common.PitchText
import com.valghora.shrimpl.ui.common.paneBackground
import com.valghora.shrimpl.ui.create.CreateScreen
import com.valghora.shrimpl.ui.edit.EditScreen
import com.valghora.shrimpl.ui.home.HomeScreen
import com.valghora.shrimpl.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Destinations (docs/SHRIMPL_SPEC.md R2). The back stack is saved, so the open screen survives
// rotation, folding, resizing and process death.

/** The app list, the list pane in two panes. */
@Serializable
data object HomeKey : NavKey

// Edit, Create and Settings are detail panes: beside the list on wide windows, full screen otherwise.

/** A web app's settings. */
@Serializable
data class EditKey(val appId: String) : NavKey

/** The configuration page for a new web app. */
@Serializable
data object CreateKey : NavKey

/** Shrimpl's own settings. */
@Serializable
data object SettingsKey : NavKey

/** App-wide keyboard shortcuts (R1.1, Tier 2 Keyboard_Shortcuts). */
enum class AppShortcut { NEW_WEB_APP, SETTINGS }

/**
 * Navigation 3 with Material's list-detail scene strategy (adaptive skill step 3): "My Web Apps" is
 * the list pane, and a web app's settings, a new web app or Shrimpl's settings the detail pane, side
 * by side when the window has room and one at a time otherwise. Each entry gets its own saved state
 * and ViewModels.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ShrimplNavigation(shortcuts: Flow<AppShortcut>, sharingScreen: Boolean = false) {
    val backStack = rememberNavBackStack(HomeKey)
    // R1.1: the list pane is Material's 412 dp, three tiles across as on a phone, and the detail takes
    // the rest; an Extra-large window (a Googlebook's full screen) widens it to four tiles of the same
    // size, as the detail has room to spare beside its 720 dp column. An app filling the screen (a tablet, an unfolded foldable) splits at Material's 840 dp. A
    // window sharing the screen ([sharingScreen]: snapped, split screen, a desktop window) needs
    // SHARED_TWO_PANE_MIN_WIDTH, so a window snapped to half a Googlebook's screen (849 to about 956 dp,
    // docs/GOOGLEBOOKS.md) shows one pane at a time. On a dual-screen phone or a foldable half-open like a book, the default
    // hinge policy splits the panes along the hinge, whatever the width.
    val startInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Start).asPaddingValues()
        .calculateStartPadding(LocalLayoutDirection.current)
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val materialDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val roomForTwo = !sharingScreen || windowWidth >= SHARED_TWO_PANE_MIN_WIDTH || materialDirective.excludedBounds.isNotEmpty()
    val directive = materialDirective.copy(
        maxHorizontalPartitions = if (roomForTwo) materialDirective.maxHorizontalPartitions else 1,
        defaultPanePreferredWidth = startInset + if (windowWidth >= EXTRA_LARGE_WIDTH) WIDE_LIST_PANE_WIDTH else LIST_PANE_WIDTH,
        horizontalPartitionSpacerSize = PANE_GAP,
    )
    // Back closes what the detail pane shows, even when both panes stay on screen (Material's default
    // pops until the panes change, which would leave Shrimpl from two panes). The panes can't be
    // resized (R1.1).
    val listDetail = rememberListDetailSceneStrategy<NavKey>(
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
        directive = directive,
    )
    // Two panes: both are cards on the window background, and the detail has no back arrow (adaptive
    // skill: detail screens in a list-detail layout must not show one), as the entries below set.
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
 * The detail pane before anything is opened in it: a one-line pitch when there are no web apps
 * yet, else a prompt to pick one. Both offer New app, the list's button being hidden meanwhile.
 */
@Composable
private fun DetailPlaceholder(onCreate: () -> Unit) {
    val records by LocalContext.current.container.store.records.collectAsStateWithLifecycle()
    val empty = records.isEmpty()
    CenteredEmptyState(below = { NewWebAppButton(onCreate) }) {
        if (empty) PitchText() else EmptyStateText(stringResource(R.string.home_pick_app))
    }
}

/** The gap between the two panes (Material's default spacer). */
private val PANE_GAP = 24.dp

/** Material's list pane width; with the card's 16 dp margin it fits three tiles of about 119 dp. */
private val LIST_PANE_WIDTH = 412.dp

/**
 * The list pane in an Extra-large window: four tiles of the same 119 dp. The detail pane keeps at
 * least 1020 dp, well past the settings' 720 dp column (R3.5).
 */
private val WIDE_LIST_PANE_WIDTH = 540.dp

/** Material's Extra-large width class; every Googlebook's full screen (1707 dp or more) is in it. */
private val EXTRA_LARGE_WIDTH = 1600.dp

/**
 * The narrowest window sharing the screen with two panes: wider than any Googlebook window snapped to
 * half the screen (about 956 dp at most), so snapping always gives one pane, and narrower than any
 * Googlebook's full screen (1707 dp at least). There the detail pane is 548 dp beside the list.
 * Material splits from 840 dp, as Shrimpl does when it fills the screen; docs/DEVIATIONS.md records why
 * a shared window waits.
 */
private val SHARED_TWO_PANE_MIN_WIDTH = 1000.dp
