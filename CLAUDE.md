# Shrimpl

Shrimpl (`:shrimpl`, Compose) builds, signs and installs web apps on the device from a bundled
template (`:template`, AndroidX Views + WebView), using pure-JVM logic in `:core`. It works on every
form factor; its primary users are on Googlebooks (Android laptops, not Chromebooks).

## What to read

| Before you… | Read |
|---|---|
| change any behavior | `docs/SHRIMPL_SPEC.md` (Shrimpl) or `docs/TEMPLATE_SPEC.md` (every web app), and `scripts/spec_map.sh <ID>` for each requirement it touches |
| change UI | `docs/ADAPTIVE.md`, the adaptive skill (`.claude/skills/adaptive/SKILL.md` and the reference it names for the feature) and the accessibility skill (`.claude/skills/accessibility/`) |
| decide anything about windows, panes or sizes | `docs/GOOGLEBOOKS.md`: size for those screens first |
| depart from a spec, a guideline or a skill | `docs/DEVIATIONS.md`: it may already be decided; if not, it gets a row |
| write or change tests | `docs/TESTING.md` |
| touch the build, Gradle or a dependency | `docs/BUILD_NOTES.md` |
| pick up planned work | `docs/TODO.md`: what the owner decided, the design and the open questions |
| use a vendored skill | `docs/SKILLS.md`: which skill for which work |

Keep the documents in step with the code: a change that alters a requirement updates the spec in the
same commit, and a deviation gets its row in the same commit.

## Non-negotiables

1. **Architecture neutrality first (R0).** Every APK runs natively on arm64-v8a, armeabi-v7a, x86_64
   and x86 with no translation layer.
   - The template, and every web app, contains no native code: no `.so`, NDK, JNI, `abiFilters` or
     ABI splits. Check every new template dependency for native code.
   - Shrimpl adds no native code of its own; a third-party native library is allowed only if it
     ships for all four ABIs.
   - Building web apps on the device stays pure Kotlin/Java (apksig, the manifest patcher); never run
     or bundle native tools such as aapt2.
2. **Adaptive by default.** Every UI change follows the adaptive skill and the Adaptive app quality
   guidelines, as applied in `docs/ADAPTIVE.md`, and the accessibility skill (labels, roles and state,
   headings, touch targets, contrast):
   - layout from window size classes, never device type or hard-coded widths;
   - user input and dialog state in `rememberSaveable` or a ViewModel; never add `configChanges` to
     Shrimpl;
   - Navigation 3 with scene strategies for multi-pane layouts (no `ListDetailPaneScaffold`);
   - 48 dp touch targets, a tooltip on every icon-only button (`TooltipIconButton`), right-click menus
     where long press has one, keyboard shortcuts listed in the Keyboard Shortcuts Helper.
3. **No suppressions.** No Kotlin `@Suppress`, no `@SuppressLint` (the single exception:
   `SetJavaScriptEnabled` on the template's `applyWebSettings`), no lint baselines, disabled checks,
   lowered thresholds, or deleted or weakened tests. Lint reports 0 errors. After three failed fix
   attempts, stop and report the log.
4. **`:core` is pure JVM.** No `android.*`, `androidx.*` or `com.google.android.*` imports; line
   coverage stays at or above 95 %.
5. **The template stays small.** AndroidX Views only (T1.1), no site values in its code or resources.
   Raise `shrimpl.templateVersion` in `gradle.properties` whenever template code or resources change
   (R11.4).
6. **Versions (R11.8).** Each change merged to develop raises the version of each APK it changes by one
   in its last part (`shrimpl.appVersion`, `shrimpl.templateVersion`; 1.1.9, then 1.2.0); a template
   change raises both, since Shrimpl bundles the template. A new major version only on the owner's
   explicit instruction; past x.9.9, ask.
7. **SDK checks.** minSdk is 37, so never guard APIs at or below 37. Each androidx.webkit call sits
   directly inside its own nested `WebViewFeature.isFeatureSupported` check.
8. **Comments stay true.** Follow the code-comments skill (`.claude/skills/code-comments/SKILL.md`):
   why, not what; no history; requirement IDs in the KDoc of the implementing declaration;
   `Invariant:` markers kept true.

## Text and color

- Settings descriptions (the text under a setting) end with a period when they are two or more
  sentences, or contain punctuation (a comma, hyphen, parentheses, a period; an apostrophe doesn't
  count); a single sentence without any goes without one.
- Other text (labels, titles, errors, notifications, toasts, dialogs) has no ending period unless it
  is two or more sentences.
- Settings wording follows `docs/SHRIMPL_SPEC.md` R1.6: a switch's label and description stay fixed
  (the description says what it does when on); a choice describes the selected value; descriptions
  start with a present-tense verb; "select", not "tap" or "click".
- Colors follow `ui/common/Notice.kt` and the theme: Info follows the Material theme; Caution, Error,
  Success and Update (blue, Shrimpl's own updates) use pastel containers with dark text in both
  themes; status text on plain surfaces is pastel in dark theme and deep in light theme. None is
  harmonized with the theme, except home badges and row buttons under the Badge theme setting (R2.5).

## Tests

- Shrimpl's Compose screens: Robolectric in `shrimpl/src/test` (layout at the testing-setup skill's
  nine window sizes, what a state shows, simple interactions, state surviving recreation). With every
  change, look for behavior worth a new test and add it where it makes sense; a layout or state bug
  found on a device gets a test that would have caught it.
- The template's WebView and bridges, and real installs: emulator tests. A change to them adds or
  updates one. What the emulator can't do (hinges, Shevery and Root, TalkBack, how it looks) is named
  in `docs/TESTING.md`; how a screen looks and feels, and what needs hardware, is confirmed on the
  owner's devices.

## Verify

```bash
./gradlew $GRADLE_FLAGS --console=plain :core:check :template:testDebugUnitTest :template:lintRelease \
  :template:assembleRelease :shrimpl:testDebugUnitTest :shrimpl:lintRelease :shrimpl:assembleDebug
scripts/check_native_code.sh --none template/build/outputs/apk/release/template-release.apk
scripts/check_native_code.sh --all-abis shrimpl/build/outputs/apk/debug/shrimpl-debug.apk
scripts/check_comments.sh --diff origin/develop
scripts/check_docs.py --diff origin/develop
```

There is no emulator in the cloud container (no KVM, and ARM images can't run on an x86 host).
Instrumented tests run in CI, in the Emulator workflow, which takes about 14 minutes and doesn't run on
pushes; it is required only before a merge into `main`.

## Workflow

- **Branches (R12.5).** `develop` is where work lands; `main` holds releases. Branch from `develop`
  for new work and offer to merge into `develop` only. Never commit to, merge into or tag `main`
  unless the owner explicitly says so. Commit locally as work is done. Never force-push.
- **Documentation only:** a branch that changes only docs and Markdown needs no workflow run before
  either merge below; the spec rereading still applies.
- **Before a merge to develop:**
  1. Run `scripts/spec_map.sh` for each requirement the branch touched and reread those spec lines
     against the change: no check compares the spec's prose with what the code does.
  2. Only once the owner says to merge: dispatch the Android workflow on the branch
     (`gh api -X POST repos/Valghora/shrimpl/actions/workflows/android.yml/dispatches -f ref=<branch>`)
     and merge when it is green on the branch's last commit. Pushes never run CI, so don't wait on it
     before then. The Emulator workflow isn't required here; dispatch it by hand only to try a test.
- **A merge to main, only when the owner says so** (a release):
  1. Dispatch the Android and the Emulator workflow on `develop` (pushes never run CI):
     `gh api -X POST repos/Valghora/shrimpl/actions/workflows/android.yml/dispatches -f ref=develop` and
     `gh api -X POST repos/Valghora/shrimpl/actions/workflows/emulator.yml/dispatches -f ref=develop`.
     `docs/TESTING.md` says how to read a failure.
  2. Merge `develop` into `main` only with both the Android and the Emulator workflow green on
     `develop`'s last commit; if the commits since the Emulator run changed only docs and Markdown,
     the last commit that changed anything else counts for it.
- **APK, only when asked** ("Build and push it, then send the APK"): run the Verify checks locally,
  push the branch (no workflow run), and send `shrimpl-debug.apk`, built after deleting
  `shrimpl/build/outputs/apk/debug/` (docs/BUILD_NOTES.md, "Debug APK size").
- **Release APK, only when the owner asks for a release build:** `:shrimpl:assembleRelease`, then the
  R0 check on it; in CI, run the Android workflow by hand with Release build ticked.
- **Push back** before following an owner instruction that goes starkly against a standard (Android's
  adaptive and large-screen guidance, Material, accessibility such as WCAG, the vendored skills): say
  which standard, what it costs users, and the closest option that meets both; then do what the owner
  decides and record the deviation in `docs/DEVIATIONS.md`.
- **Vendored skills.** Google's android/skills are vendored unchanged in `.claude/skills/`. Use the
  one that matches the work and follow it unless `docs/DEVIATIONS.md` says otherwise. Never edit a
  vendored skill. The SessionStart hook checks every vendored skill for an
  upstream update at the start of each session. When the session starts with an "android skills:"
  note of an update, ask the owner, before other work, whether to check that skill's update against
  the project. Only on a yes: run `scripts/android_skills.sh update <name>`, review the diff against
  `docs/DEVIATIONS.md`, commit it on its own and report what changed. Bring a new upstream skill to the owner with a recommendation
  before adopting it.
- **Findings.** Record new toolchain or platform findings in `docs/BUILD_NOTES.md`.
