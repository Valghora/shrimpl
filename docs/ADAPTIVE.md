# Adaptive apps: how this project applies Google's guidance

Every UI change to Shrimpl or the template MUST be checked against two sources, and this file
records how each applies here:

1. **The adaptive skill**, vendored at [`.claude/skills/adaptive/`](../.claude/skills/adaptive/SKILL.md)
   (Google's [android/skills](https://github.com/android/skills) `jetpack-compose/adaptive`, see
   `UPSTREAM.md` for the commit). Claude Code loads it as a project skill. Read it, and the reference
   it points to for the feature at hand (keyboard shortcuts, tooltips, cursors, list-detail, saved
   state), before changing a screen.
2. **The [Adaptive app quality guidelines](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality)**
   (Tier 3 adaptive ready, Tier 2 adaptive optimized, Tier 1 adaptive differentiated) and the
   [desktop app quality checklist](https://developer.android.com/docs/quality-guidelines/large-screen-app-quality).

When a change can't follow them, add a row to **Deliberate deviations** below with the reason. A
deviation that isn't written down is a bug.

## Shrimpl (Compose)

| Guideline | How Shrimpl meets it | Where |
|---|---|---|
| Skill step 3, list-detail | Navigation 3 `NavDisplay` with `ListDetailSceneStrategy`. Home is the list pane (with a placeholder), an app's edit page the detail pane. In two panes the detail has no back arrow and the selected row is highlighted. Not `ListDetailPaneScaffold`, as the skill requires. | `ui/ShrimplNavigation.kt` |
| Two-pane presentation | Each pane is a rounded `surface` card (24 dp corners) on the `surfaceContainer` window background, 16 dp from the window edges, with a 16 dp gap between them. The list pane is a fixed 360 dp and the detail takes the rest; there is no drag handle (a deliberate simplification: the list holds only icons, names and small buttons). On a separating hinge (a dual-screen phone, a foldable half-open like a book) the default hinge policy puts one pane on each side of it. The cards take the window insets, so the screens inside don't pad twice. Edit, Create and Settings all open in the detail pane, replacing each other. With nothing open the detail pane prompts (or, with no web apps yet, explains how to start) and holds the only New app button; empty-state text is exactly centered in each card, so both panes line up. With one pane, screens stay edge to edge. | `ui/common/PaneCard.kt`, `ShrimplNavigation.kt` |
| Skill step 4, adaptive lists | Home is a `LazyVerticalGrid(GridCells.Adaptive(360.dp))`: one column on phones, more on wide windows. | `ui/home/HomeScreen.kt` |
| Skill step 5, app bars | Home's top bar uses `enterAlwaysScrollBehavior`. | `HomeScreen.kt` |
| Skill step 6 / Tier 3 Config_Changes | No `android:configChanges` or orientation locks. Dialog flags, field drafts, menus, picker state and the open icon source sheet survive rotation, folding and resizing (`rememberSaveable` with the savers in `ui/common/Savers.kt`, or the ViewModel). Navigation state is `rememberNavBackStack` with `@Serializable` keys; entries get saved-state and ViewModel decorators. | `MainActivity.kt`, `Savers.kt`, ViewModels |
| Tier 2 responsive layouts | Layout decisions use window size classes (`isMediumWidth()`, the pane directive, the form's column measured against its own pane), never device type. | `ui/common/Adaptive.kt` |
| Tier 2 Touch_Targets | Icon-only buttons are `IconButton`s (48 dp), including the notice dismiss button. | `TooltipIconButton` |
| Tier 2 Hover_States / desktop Hover_Parity | Every icon-only button has a plain tooltip above it, shown on hover, long press and focus, with its shortcut where one exists ("Settings (Ctrl+,)"). | `TooltipIconButton`, `WithTooltip` |
| Tier 2 Context_Menus | Right-click (and long press) on a web app opens Open / Edit / Reinstall-Update-Rebuild / Uninstall-Remove at the pointer. | `HomeScreen.kt` `AppItem` |
| Tier 2 Keyboard_Shortcuts | Ctrl+N (new web app) and Ctrl+, (settings) from anywhere, Ctrl alone, ignoring key repeat; both listed in the Keyboard Shortcuts Helper. | `MainActivity.kt` |
| Tier 2 Keyboard_Navigation | Material components are focusable and Tab-traversable. Esc closes dialogs and menus (platform behavior); it is not used as a general back key. | — |
| Multi-window | `resizeableActivity` default, no orientation or aspect-ratio limits; Shrimpl is single-instance by design (one manager). | `AndroidManifest.xml` |

## Template (the web app, Views)

| Guideline | How the template meets it | Where |
|---|---|---|
| Multi-window / multi-instance | Freely resizable, `<layout>` window sizes, optional multi-instance (`singleInstancePerTask` and `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI`) with a New Window shortcut. | `AndroidManifest.xml`, `MainActivity.kt` |
| Desktop header | Transparent caption bar with matching icon contrast, padded for caption-bar insets; header color modes (spec R7.7). | `MainActivity.kt`, `PageColors.kt` |
| Keyboard_Input / Shortcuts | Ctrl+R / F5, Ctrl+= / - / 0, Ctrl+P, optional New Window; site shortcuts listed in the helper and passed through to the page. | `DesktopSessionController`, `MainActivity.kt` |
| Mouse / Content_Zoom | Ctrl+scroll wheel zooms the page text, like the keyboard zoom; pinch zoom on touch. | `MainActivity.kt` |
| Context_Menus | Native menu for links and images (copy link, open link, save image). | `MainActivity.kt` |
| Drag and drop | Cross-window drops are allowed (`requestDragAndDropPermissions`). | `MainActivity.kt` |
| Desktop Request_Fullscreen_Mode | Fullscreen video hides the system bars and, in a desktop window, asks for fullscreen with `Activity.requestFullscreenMode`; Esc or Back leaves it. | `MainActivity.kt` |
| Desktop Printing_Support | Ctrl+P prints the page through `PrintManager`. | `MainActivity.kt` |
| Scrollbars | The WebView draws the page's own scrollbars. | — |

## Deliberate deviations

| Guidance | What we do instead | Why |
|---|---|---|
| Skill step 6: never add `android:configChanges` | The template declares `configChanges` for size, orientation, density, keyboard, UI mode and font scale, and handles them in `onConfigurationChanged`. | Recreating the activity would reload the WebView and lose page state (scroll, forms, media, sign-in flows) that `saveState` can't restore. The window still resizes freely and re-applies colors and insets. Shrimpl itself never opts out. |
| Skill prerequisites: Compose and Navigation 3 everywhere | The template stays on AndroidX Views. | Every web app is a copy of the template; Compose would add megabytes to each one, and the UI is one WebView. |
| Skill step 1: screenshot tests per form factor | Not yet added. | The Compose Preview Screenshot Testing plugin needs reference images reviewed on a desktop; tracked as a gap. Layout logic is size-class driven and checked on devices. |
| Skill step 2: `NavigationSuiteScaffold` | Not used. | Shrimpl has one top-level destination (the home list); there is no navigation bar to adapt. |
| Skill step 4.2: `Grid` for non-lazy lists | Settings and form sections stay `Column`s. | Their items are different kinds of settings, not repeated items; `Grid` is also still experimental. |
| Material list-detail: draggable pane divider | A fixed 360 dp list pane, no drag handle (the default hinge policy still splits the panes along a separating hinge). | The list holds only icons, names and small buttons; widening it added empty space, and the detail form is a centred column that doesn't need it. |
| Skill step 8: tabletop posture | Not used. | No media, camera or video-call screens in Shrimpl; web apps get the page's own layout. |
| Skill step 7: custom cursors | Default cursors. | Material components already show the right pointer; nothing is draggable or resizable in Shrimpl. |
| Transient `remember` state | Snackbar hosts, in-flight search status, a context menu's pointer position and derived bitmaps use plain `remember`. | They are not user input; losing them on a configuration change is correct. |
