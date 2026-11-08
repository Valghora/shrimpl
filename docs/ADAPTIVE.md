# Adaptive apps: how this project applies Google's guidance

Every UI change to Shrimpl or the template MUST be checked against two sources, and this file
records how each applies here:

1. **The adaptive skill**, vendored at [`.claude/skills/adaptive/`](../.claude/skills/adaptive/SKILL.md)
   (Google's [android/skills](https://github.com/android/skills) `jetpack-compose/adaptive`, see
   `UPSTREAM.md` for the commit; [SKILLS.md](SKILLS.md) covers how it is kept current). Claude Code
   loads it as a project skill. Read it, and the reference it points to for the feature at hand
   (keyboard shortcuts, tooltips, cursors, list-detail, saved state), before changing a screen.
2. **The [Adaptive app quality guidelines](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality)**
   (Tier 3 adaptive ready, Tier 2 adaptive optimized, Tier 1 adaptive differentiated) and the
   [desktop app quality checklist](https://developer.android.com/docs/quality-guidelines/large-screen-app-quality).

When a change can't follow them, add a row to docs/DEVIATIONS.md with the reason. A deviation that
isn't written down is a bug.

Behavior is specified in the specs; this file maps each guideline to the requirements that meet it and
the code that implements them, plus the implementation details the specs leave out.

## Shrimpl (Compose)

| Guideline | Met by | How, beyond the spec | Where |
|---|---|---|---|
| Skill step 3, list-detail | R1.1, R1.3 | Navigation 3 `NavDisplay` with `ListDetailSceneStrategy`, not `ListDetailPaneScaffold`. Back closes the detail first (`BackNavigationBehavior.PopUntilContentChange`; Material's default would leave the app, since both panes stay). | `ui/ShrimplNavigation.kt` |
| Two-pane presentation | R1.1, R1.5, R3.5 | Pane cards sit 16 dp from the window edges and take the window insets, so the screens inside don't pad twice. The default hinge policy puts one pane on each side of a separating hinge. Settings pages share one column layout. | `ui/common/PaneCard.kt`, `ui/common/FormColumn.kt` |
| Skill step 4, adaptive lists | R2.1, R2.6 | `LazyVerticalGrid` with `GridCells.Adaptive`: 360 dp rows; 100 dp tiles, 118 dp from the Medium width class. | `ui/home/HomeScreen.kt` |
| Skill step 5, app bars | R2.1, R3.5 | One large-title bar (`LargeTitleBar`) for home and the settings pages. | `ui/common/FormColumn.kt` |
| Skill step 6 / Tier 3 Config_Changes | R1.1 | No `android:configChanges`. Dialog flags, field drafts, menus, picker state and the open icon source sheet are in `rememberSaveable` (savers in `ui/common/Savers.kt`) or a ViewModel; the back stack is `rememberNavBackStack` with `@Serializable` keys, and entries get saved-state and ViewModel decorators. | `MainActivity.kt`, `ui/common/Savers.kt` |
| Tier 2 responsive layouts | R1.1 | Window size classes only (`isMediumWidth()`, the pane directive, the form column measured against its own pane), never device type. | `ui/common/Adaptive.kt` |
| Tier 2 Touch_Targets | R1.1 | Icon-only buttons are 48 dp `IconButton`s, the notice's Dismiss included. | `TooltipIconButton` |
| Tier 2 Hover_States / desktop Hover_Parity | R1.1 | A plain tooltip on every icon-only button, on hover, long press and focus, naming its shortcut ("Settings (Ctrl+,)"). | `TooltipIconButton`, `WithTooltip` |
| Tier 2 Context_Menus | R2.1, R2.2 | Right-click and long press open the app's menu at the pointer. | `ui/home/HomeScreen.kt` |
| Tier 2 Keyboard_Shortcuts | R1.1 | Ctrl+N and Ctrl+, from anywhere, Ctrl alone, ignoring key repeat; listed in the Keyboard Shortcuts Helper. | `MainActivity.kt` |
| Tier 2 Keyboard_Navigation | R1.1 | Material components are focusable and Tab-traversable; Esc closes dialogs and menus (platform behavior) and is not a general back key. | — |
| Multi-window | R1.1 | Resizable, no orientation or aspect-ratio limits; one instance by design (one manager). | `AndroidManifest.xml` |

## Template (the web app, Views)

| Guideline | Met by | Where |
|---|---|---|
| Multi-window / multi-instance | T4.1, T4.5, R7.5 | `AndroidManifest.xml`, `MainActivity.kt` |
| Desktop header | T3.2, T3.3 | `MainActivity.kt`, `PageColors.kt` |
| Keyboard_Input / Shortcuts | T2 (c), T5.3, T5.4 | `DesktopSessionController`, `MainActivity.kt` |
| Mouse / Content_Zoom | T5.7 | `MainActivity.kt` |
| Context_Menus | T5.5 | `MainActivity.kt` |
| Drag and drop | T5.2 | `MainActivity.kt` |
| Desktop Request_Fullscreen_Mode | T6.13 | `MainActivity.kt` |
| Desktop Printing_Support | T6.14 | `MainActivity.kt` |
| Scrollbars | The WebView draws the page's own scrollbars. | — |
