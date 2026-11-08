# Deliberate deviations

Every place the project departs from its specs, Android's adaptive guidance, a vendored skill or a
vendored reference, and why. A deviation that isn't written here is a bug: add the row in the same
commit as the change. Anything not listed is followed as written.

## From the specs (docs/SHRIMPL_SPEC.md, docs/TEMPLATE_SPEC.md)

| Requirement | What we do instead | Why |
|---|---|---|
| T3.3 (header color from the first frame) | The starting window uses the system surface color until the app applies the profile's header color at launch. | The window background is a compiled resource; making it per app would mean patching `resources.arsc`. |
| R9.3 | Request types come from resource timing (`xhr`, `fetch`, `script`, ...); WebSocket hosts aren't seen. | WebView has no per-request resource type. |
| R9.3 | The rendered visit's HTTP status is known only when it is an error (>= 400). | WebView reports main-document errors, not successes. |
| R10.3 | Letter icons use the system bold font. | Native rendering; no bundled font. |
| R6.1 | Package visibility uses `<queries>` (launcher apps, icon packs, Shizuku), not `QUERY_ALL_PACKAGES`. | Narrower, and enough: every web app has a launcher entry. |
| Rules (lint) | `:shrimpl` lint keeps one warning: `SetJavaScriptEnabled` (the hidden discovery WebView must run page scripts). | `@SuppressLint` is allowed only in the template. |

## From Android's adaptive guidance

The adaptive skill (`.claude/skills/adaptive/`) and the Adaptive app quality guidelines; how the rest
is met is in docs/ADAPTIVE.md.

| Guidance | What this project does | Why |
|---|---|---|
| Material's list-detail: two panes from the Expanded width class (840 dp) | Filling the screen (a tablet, an unfolded foldable), two panes from 840 dp, as Material. Sharing it (`isInMultiWindowMode`: snapped, split screen, a desktop window), two panes from 1000 dp. A separating hinge still splits the panes. | Shrimpl's primary users are on Googlebooks (docs/GOOGLEBOOKS.md), where a window snapped to half the screen is 849 dp (Dell XPS, confirmed) to about 956 dp (the 2880 px models). Beside Material's 412 dp list those widths leave the settings narrower than a phone or barely wider; from 1000 dp every snapped Googlebook window shows one pane and every full screen (1707 dp and up) two. Unfolded foldables fill their screen, so they keep Material's split. |
| Skill step 6: never add `android:configChanges` | The template declares `configChanges` for size, orientation, density, keyboard, UI mode and font scale, and handles them in `onConfigurationChanged`. | Recreating the activity would reload the WebView and lose page state (scroll, forms, media, sign-in flows) that `saveState` can't restore. The window still resizes freely and re-applies colors and insets. Shrimpl itself never opts out. |
| Skill prerequisites: Compose and Navigation 3 everywhere | The template stays on AndroidX Views. | Every web app is a copy of the template; Compose would add megabytes to each one, and the UI is one WebView. |
| Adaptive skill: detail screens in a list-detail layout show no back arrow | Every settings page keeps its back arrow beside the list, on the line of its large title, where it closes the detail pane (`PageTopBar`). | The owner's design: one bar for every settings page in every layout, the arrow lined up with the page's content. Back still closes the detail first, as before. |
| Skill step 2: `NavigationSuiteScaffold` | Not used. | Shrimpl has one top-level destination (the home list); there is no navigation bar to adapt. |
| Skill step 4.2: `Grid` for non-lazy lists | Settings and form sections stay `Column`s. | Their items are different kinds of settings, not repeated items; `Grid` is also still experimental. |
| Skill step 8: tabletop posture | Not used. | No media, camera or video-call screens in Shrimpl; web apps get the page's own layout. |
| Skill step 7: custom cursors | Default cursors. | Material components already show the right pointer; nothing is draggable or resizable in Shrimpl. |
| Transient `remember` state | Snackbar hosts, in-flight search status, a context menu's pointer position and derived bitmaps use plain `remember`. | They are not user input; losing them on a configuration change is correct. |

## From the vendored skills

### testing-setup

| Guidance | What this project does | Why |
|---|---|---|
| Step 2: install a DI framework (Hilt) | Manual dependency injection: `AppContainer` builds the graph; tests construct ViewModels and fakes directly. | One activity and a small graph; Hilt would add KSP processing, generated code and build time without making the tests simpler. |
| Steps 3 and 8, and the adaptive skill's step 1: screenshot tests (Compose Preview Screenshot Testing, per form factor, at nine window sizes) | No screenshot comparison. `WindowSizeTest` (Robolectric) runs the whole app at the nine sizes (400, 610 and 900 dp wide by 400, 500 and 1000 dp high) and checks what each shows: one or two panes, the back arrow, New app inside the window; layout tests measure bounds. | Test suites need AGP 9.5.0-alpha03 or later (this project uses stable 9.4.1), and the standalone plugin is deprecated; reference images also need reviewing on a desktop, and the owner judges how it looks. Revisit when AGP 9.5 is stable. |
| Steps 7, 12 and 13: instrumented tests, Dropshots, UI Automator | Instrumented tests and UI Automator run in the Emulator workflow (docs/TESTING.md). No Dropshots. | The cloud container has no KVM, so the emulator runs only in CI; screenshot comparison isn't used (the owner judges how it looks, and layout is measured in Robolectric tests). |
| Step 9: Espresso tests for Views | None for the template. | The template's UI is one WebView whose behavior depends on a real Chromium, which Robolectric doesn't run; its pure logic is unit tested. |
| Step 9: always verify state restoration | `StateRestorationTester` for screen state (an open menu), and activity recreation for a whole flow (`EditChangesTest`). Dialogs with a text field aren't tested. | Robolectric never goes idle once a text field in a dialog window can take focus ([TESTING.md](TESTING.md), "Robolectric"); those stay a device check. |
| Step 11: `DeviceConfigurationOverride` for window sizes | Window-size tests set Robolectric's screen qualifiers and launch `MainActivity`; `DeviceConfigurationOverride` is for font scale, dark theme and locale. Layout tests of one screen give it a pane width with a sized `Box`. | `ForcedSize` doesn't change `LocalWindowInfo`, which the window size class (and so the list-detail switch) comes from ([TESTING.md](TESTING.md), "Robolectric"). |
| Step 14: the `jacoco` plugin | Shrimpl's debug build sets `enableUnitTestCoverage` (AGP's JaCoCo integration), with no threshold. `:core` keeps its own JaCoCo report and 95 % rule. The template collects none. | AGP wires JaCoCo to the variant's classes. The template's one unit test checks the profile it ships; its code runs in a WebView, on devices. |
| Final touches: `AGENTS.md` and `docs/testing.md` | The testing rules are in CLAUDE.md; commands and setup are in [TESTING.md](TESTING.md) ("Robolectric"). | Claude Code reads CLAUDE.md; one place for the rules avoids two copies drifting. |

### android-intent-security and android-permissions-security

| Guidance | What this project does | Why |
|---|---|---|
| Intent security 3: never launch a nested intent without an allowlist (`IntentSanitizer`) | Shrimpl launches the confirmation screen PackageInstaller hands back in `Intent.EXTRA_INTENT` as it is (`SessionInstaller`). | It is the platform's documented install flow. The receiver isn't exported and its mutable `PendingIntent` is explicit (component and package), and only the system installer holds it, so the nested intent can only come from the system. |
| Intent security 3: sanitize with an allowlist of components | The template's `intent:` links (a page's links to other apps) clear the component and selector, strip URI permission grant flags and add `CATEGORY_BROWSABLE`, as Chrome does, but name no allowed apps (TEMPLATE_SPEC T6.6). | A page may link to any app, as in a browser; the target must already accept browsable links, and the page can't reach the web app's own components or files. |
| Permissions 6: ask in context, with a rationale | The notification permission is asked when a web app first starts, if the site uses notifications (TEMPLATE_SPEC T7). | Pages assume notifications are allowed (the polyfill reports "granted"), and asking only when the first one arrives would lose it. Camera, microphone and location are asked for when the page asks. |

### edge-to-edge

| Guidance | What this project does | Why |
|---|---|---|
| Prerequisites: Compose | Applies to Shrimpl only. The template (Views) handles insets itself ([TEMPLATE_SPEC.md](TEMPLATE_SPEC.md)). | The skill covers Compose only; the template stays on Views (T1.1). |

### navigation-3

| Guidance | What this project does | Why |
|---|---|---|
| Recipes: dialogs and bottom sheets as scenes | Confirmation dialogs and the build sheet are screen or ViewModel state, not destinations. | The build sheet follows a build that keeps running whatever the user opens, and dialogs belong to the screen that asked; both survive configuration changes (ADAPTIVE.md). |

### anr-audit-and-fix

| Guidance | What this project does | Why |
|---|---|---|
| Path B: diagnose ANR traces | Path A (static audit) only, unless the owner sends a trace. | No device here to produce traces. |

### r8-analyzer

| Guidance | What this project does | Why |
|---|---|---|
| Write analysis files under `tmp/` | `tmp/` is ignored by git. | Keeps the skill unchanged without committing its output. |
| CONFIGURATION.md: full resource optimization | `android.enableResourceOptimizations=false`. | Resource path shortening would move the template's icon files, which Shrimpl replaces by fixed path (BUILD_NOTES.md, R7.4). |

### jetpack-compose-performance

| Guidance | What this project does | Why |
|---|---|---|
| Prerequisite 3: Baseline Profiles | None. | Generating them needs a device or emulator (Macrobenchmark); none here. Revisit if cold start or the home list feels slow on devices. |

## From the accessibility reference

Drjacky's reference behind the accessibility skill (`.claude/references/claude-android-ninja/`).

| Guidance | What this project does | Why |
|---|---|---|
| The rest of claude-android-ninja (Hilt, Room 3, multi-module, Retrofit) | Only the two accessibility files are vendored, behind this project's own skill. | The full skill would apply to all Android work and contradict the architecture and the Google skills in use. |
| Links to `INDEX-sections.md` and `android-i18n.md` | Not vendored; the table of contents in `android-accessibility.md` replaces the index. | Only accessibility is taken. |
| Testing: Espresso accessibility checks, Accessibility Scanner | Google's Accessibility Test Framework runs on the emulator against the real screens (`AccessibilityChecksTest`, with a negative control); Robolectric semantics tests (labels, roles, toggle state, headings, touch size); TalkBack on the owner's devices. | ATF doesn't run under Robolectric ([TESTING.md](TESTING.md), "Robolectric"); the emulator can't judge reading order or announcements, which TalkBack on a device does. |
| Compose only | The template's own Views follow Android's View accessibility guidance; web content is the site's. | The template stays on Views (T1.1). |
