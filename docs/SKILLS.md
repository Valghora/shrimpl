# Android skills

Google's [android/skills](https://github.com/android/skills) are agent instructions for Android best
practices. The ones that apply to this project are copied unchanged into `.claude/skills/<name>/`,
where Claude Code loads them as project skills. Each folder keeps upstream's `LICENSE.txt` (Apache
2.0) and an `UPSTREAM.md` with the upstream path, the commit it was copied from and that copy's
`last-updated` date.

This file lists the skills in use, when to use each, and where the project deliberately departs
from them. A skill is followed as written unless a row below says otherwise; a new deliberate
deviation gets a row here in the same commit. The adaptive skill's deviations are in
[ADAPTIVE.md](ADAPTIVE.md).

## Keeping them current

- **Check.** `scripts/android_skills.sh check` compares every vendored skill with upstream. The
  SessionStart hook runs it, so a session starts with a note when a skill has an upstream update,
  differs from its recorded upstream copy, or no longer exists upstream, and when upstream adds a
  skill this file doesn't mention.
- **Update.** `scripts/android_skills.sh update <name>` (or `--all`) copies the newest upstream
  version and rewrites `UPSTREAM.md`. Review `git diff .claude/skills` against the deviations below,
  adjust this file (or ADAPTIVE.md) where the guidance changed, and commit the update on its own.
- **Adopt.** `scripts/android_skills.sh add <upstream path>` vendors another skill; add it to the
  tables below, and remove it from "Not adopted".
- **Never edit a vendored skill.** Record a departure here instead; `check` flags local edits.
- `scripts/android_skills.sh list` shows each skill's upstream date and when it was copied.

## Skills in use

| Skill | Upstream path | Use it when |
|---|---|---|
| adaptive | `jetpack-compose/adaptive` | Any Shrimpl UI change (CLAUDE.md rule 2). Deviations: [ADAPTIVE.md](ADAPTIVE.md). |
| testing-setup | `testing/testing-setup` | Adding or reviewing tests, and with every change (CLAUDE.md: look for behavior worth a Robolectric test). |
| android-intent-security | `security/android-intent-security` | Touching manifest components, intents, `PendingIntent`s, or how the template opens links and `intent:` URLs. |
| android-permissions-security | `security/android-permissions-security` | Adding or changing permissions, exported components, broadcasts, or the template's runtime permission prompts. |
| edge-to-edge | `system/edge-to-edge` | Shrimpl screens with lists, floating buttons, text fields, dialogs or sheets. |
| navigation-3 | `navigation/navigation-3` | Changing Shrimpl's navigation (`ui/ShrimplNavigation.kt`): destinations, scenes, back stack, results. |
| anr-audit-and-fix | `performance/anr-audit-and-fix` | Moving work between threads, adding file, network, signing or package manager calls, or reading an ANR trace. |
| r8-analyzer | `performance/r8-analyzer` | Changing keep rules or minify settings, or checking how much the template (copied into every web app) carries. |
| jetpack-compose-performance | `jetpack-compose/jetpack-compose-performance` | Writing or reviewing Compose code that is on a hot path: the home list, animations, custom layouts, modifiers. |

## Deliberate deviations

### testing-setup

| Skill guidance | What this project does | Why |
|---|---|---|
| Step 2: install a DI framework (Hilt) | Manual dependency injection: `AppContainer` builds the graph; tests construct ViewModels and fakes directly. | One activity and a small graph; Hilt would add KSP processing, generated code and build time without making the tests simpler. |
| Step 3: Compose Preview Screenshot Testing | No screenshot tests yet; Robolectric tests check layout by measuring bounds (ADAPTIVE.md). | Test suites need AGP 9.5.0-alpha03 or later (this project uses stable 9.4.1), and the standalone plugin is deprecated. Revisit when AGP 9.5 is stable. |
| Steps 7, 12 and 13: instrumented tests, Dropshots, UI Automator | None. | The cloud container has no emulator; the owner checks real installs, the template's WebView and hinges on devices (CLAUDE.md). |
| Step 9: Espresso tests for Views | None for the template. | The template's UI is one WebView whose behavior depends on a real Chromium, which Robolectric doesn't run; its pure logic is unit tested. |
| Step 8: screenshots at nine window sizes | `WindowSizeTest` runs the whole app at the nine sizes (400, 610 and 900 dp wide by 400, 500 and 1000 dp high) and checks what each shows: one or two panes, the back arrow, New app inside the window. | No screenshot tooling yet (above); behavior at each size is what regressions have broken so far. |
| Step 9: always verify state restoration | `StateRestorationTester` for screen state (an open menu), and activity recreation for a whole flow (`EditChangesTest`). Dialogs with a text field aren't tested. | Robolectric never goes idle once a text field in a dialog window can take focus ([BUILD_NOTES.md](BUILD_NOTES.md)); those stay a device check. |
| Step 11: `DeviceConfigurationOverride` for window sizes | Window-size tests set Robolectric's screen qualifiers and launch `MainActivity`; `DeviceConfigurationOverride` is for font scale, dark theme and locale. Layout tests of one screen give it a pane width with a sized `Box`. | `ForcedSize` doesn't change `LocalWindowInfo`, which the window size class (and so the list-detail switch) comes from ([BUILD_NOTES.md](BUILD_NOTES.md)). |
| Step 14: the `jacoco` plugin | Shrimpl's debug build sets `enableUnitTestCoverage` (AGP's JaCoCo integration), with no threshold. `:core` keeps its own JaCoCo report and 85 % rule. The template collects none. | AGP wires JaCoCo to the variant's classes. The template's one unit test checks the profile it ships; its code runs in a WebView, on devices. |
| Final touches: `AGENTS.md` and `docs/testing.md` | The testing rules are in CLAUDE.md; commands and setup are in [BUILD_NOTES.md](BUILD_NOTES.md) ("Robolectric"). | Claude Code reads CLAUDE.md; one place for the rules avoids two copies drifting. |

### android-intent-security and android-permissions-security

| Skill guidance | What this project does | Why |
|---|---|---|
| Intent security 3: never launch a nested intent without an allowlist (`IntentSanitizer`) | Shrimpl launches the confirmation screen PackageInstaller hands back in `Intent.EXTRA_INTENT` as it is (`SessionInstaller`). | It is the platform's documented install flow. The receiver isn't exported and its mutable `PendingIntent` is explicit (component and package), and only the system installer holds it, so the nested intent can only come from the system. |
| Intent security 3: sanitize with an allowlist of components | The template's `intent:` links (a page's links to other apps) clear the component and selector, strip URI permission grant flags and add `CATEGORY_BROWSABLE`, as Chrome does, but name no allowed apps (TEMPLATE_SPEC T6.6). | A page may link to any app, as in a browser; the target must already accept browsable links, and the page can't reach the web app's own components or files. |
| Permissions 6: ask in context, with a rationale | The notification permission is asked when a web app first starts, if the site uses notifications (TEMPLATE_SPEC T7). | Pages assume notifications are allowed (the polyfill reports "granted"), and asking only when the first one arrives would lose it. Camera, microphone and location are asked for when the page asks. |

### edge-to-edge

| Skill guidance | What this project does | Why |
|---|---|---|
| Prerequisites: Compose | Applies to Shrimpl only. The template (Views) handles insets itself ([TEMPLATE_SPEC.md](TEMPLATE_SPEC.md)). | The skill covers Compose only; the template stays on Views (T1.1). |

### navigation-3

| Skill guidance | What this project does | Why |
|---|---|---|
| Recipes: dialogs and bottom sheets as scenes | Confirmation dialogs and the build sheet are screen or ViewModel state, not destinations. | The build sheet follows a build that keeps running whatever the user opens, and dialogs belong to the screen that asked; both survive configuration changes (ADAPTIVE.md). |

### anr-audit-and-fix

| Skill guidance | What this project does | Why |
|---|---|---|
| Path B: diagnose ANR traces | Path A (static audit) only, unless the owner sends a trace. | No device here to produce traces. |

### r8-analyzer

| Skill guidance | What this project does | Why |
|---|---|---|
| Write analysis files under `tmp/` | `tmp/` is ignored by git. | Keeps the skill unchanged without committing its output. |
| CONFIGURATION.md: full resource optimization | `android.enableResourceOptimizations=false`. | Resource path shortening would move the template's icon files, which Shrimpl replaces by fixed path (BUILD_NOTES.md, R7.4). |

Both apps use the AGP 9.3+ `optimization { enable = true }` block; Shrimpl's one keep rule (the
Shizuku shell service) is in `shrimpl/src/main/keepRules/`. The first analysis (2026-11-01, before
the move, with identical APKs after it) scored Shrimpl 98 % and the template 93 % for optimization;
every broad rule comes from a library or AGP's defaults.

### jetpack-compose-performance

| Skill guidance | What this project does | Why |
|---|---|---|
| Prerequisite 3: Baseline Profiles | None. | Generating them needs a device or emulator (Macrobenchmark); none here. Revisit if cold start or the home list feels slow on devices. |

## Not adopted

Upstream skills that don't apply. `check` names any upstream skill missing from this file, so a new
one gets a decision here.

| Upstream path | Why not |
|---|---|
| `build-system/agp/agp-9-upgrade` | Already on AGP 9 with built-in Kotlin, the new DSL and no transitional flags. |
| `camera/camerax` | No camera features. |
| `cars/android-for-cars-parked-apps` | Browsers are a beta category on Android Automotive only, and not supported on Android Auto. |
| `device-ai/appfunctions` | Possible later, to let assistants create web apps. |
| `device-ai/litert/litert-lm` | No on-device models; LiteRT also ships native libraries (R0). |
| `device-ai/litert/litert-runtime` | As above. |
| `device-ai/ml-kit-genai-prompt-api` | No on-device AI features. |
| `device-ai/mlkit-agent-api` | No on-device AI features. |
| `devtools/android-cli` | Most commands need a device, and it is a binary install outside Gradle. |
| `identity/restore-credentials` | Shrimpl has no accounts; web apps' sign-in belongs to each site, and passkeys in the template's WebView would need the site to vouch for our signing key ([TEMPLATE_SPEC.md](TEMPLATE_SPEC.md)). |
| `identity/verified-email` | As above. |
| `jetpack-compose/ink/ink-app-builder` | No drawing. |
| `jetpack-compose/ink/ink-custom-brush-builder` | No drawing. |
| `jetpack-compose/migration/migrate-xml-views-to-jetpack-compose` | The template stays on Views on purpose (T1.1, ADAPTIVE.md). |
| `media/media3-cast-integration` | Media plays inside each site's page. |
| `navigation/navigation-event` | Compose-only, and it defers to Navigation 3's own back handling; the template uses `OnBackPressedDispatcher`. |
| `play/engage-sdk-integration` | Shrimpl and its web apps are sideloaded, not on Play. |
| `play/play-billing-library-version-upgrade` | No billing; not on Play. |
| `play/play-policy-insights` | Not on Play. |
| `profilers/android-profiler` | Needs a connected device. |
| `system/glance-appwidget-updates-and-memory` | No widgets. |
| `system/glance-canonical-layouts` | No widgets. |
| `tv/leanback-to-compose-tv-migration` | No TV app. |
| `wear/wear-compose-m3` | No Wear OS app. |
| `wear/wear-widgets` | No Wear OS app. |
| `xr/display-glasses-with-jetpack-compose-glimmer` | No XR app. |
| `xr/projected-hardware` | No XR app. |
