# Shrimpl: rules for every change

Shrimpl (`:shrimpl`, Compose) builds, signs and installs web apps on the device from a bundled
template (`:template`, AndroidX Views + WebView), using pure-JVM logic in `:core`. Read
`docs/SHRIMPL_SPEC.md` (the requirements), `docs/TEMPLATE_SPEC.md` (web app behavior) and
`docs/ADAPTIVE.md` before changing behavior. Keep those documents in step with the code: a change
that alters a requirement updates the spec in the same commit.

## Non-negotiables

1. **Architecture neutrality first (spec R0).** Every APK must run natively on arm64-v8a,
   armeabi-v7a, x86_64 and x86 with no translation layer.
   - The template, and every web app, contains no native code: no `.so`, NDK, JNI, `abiFilters` or
     ABI splits. Check every new template dependency for native code.
   - Shrimpl adds no native code of its own; a third-party native library is allowed only if it
     ships for all four ABIs. Building web apps on the device stays pure Kotlin/Java (apksig, the
     manifest patcher); never run or bundle native tools such as aapt2.
   - Verify with `scripts/check_native_code.sh` (below).
2. **Adaptive by default.** For any UI change, follow the adaptive skill in
   `.claude/skills/adaptive/SKILL.md` (and the reference it names for the feature) and the Adaptive
   app quality guidelines, as applied in `docs/ADAPTIVE.md`:
   - layout from window size classes, never device type or hard-coded widths;
   - user input and dialog state in `rememberSaveable` or a ViewModel; never add `configChanges` to
     Shrimpl;
   - Navigation 3 with scene strategies for multi-pane layouts (no `ListDetailPaneScaffold`);
   - 48 dp touch targets, a tooltip on every icon-only button (`TooltipIconButton`), right-click
     menus where long press has one, keyboard shortcuts listed in the Keyboard Shortcuts Helper.

   A deliberate exception gets a row in `docs/ADAPTIVE.md`'s deviations table. Every UI change also
   follows the accessibility skill (`.claude/skills/accessibility/`): labels, roles and state,
   headings, touch targets and contrast.
3. **No suppressions.** No Kotlin `@Suppress`, no `@SuppressLint` (the single exception:
   `SetJavaScriptEnabled` on the template's `applyWebSettings`), no lint baselines, disabled checks,
   lowered thresholds, or deleted or weakened tests. Lint must report 0 errors. After three failed
   fix attempts, stop and report the log.
4. **`:core` is pure JVM.** No `android.*`, `androidx.*` or `com.google.android.*` imports; line
   coverage stays at or above 95 %.
5. **The template stays small.** AndroidX Views only (T1.1), no site values in its code or
   resources. Bump `shrimpl.templateVersion` in `gradle.properties` whenever template code or
   resources change.
6. SDK checks: minSdk is 37, so never guard APIs at or below 37. Each androidx.webkit call sits
   directly inside its own nested `WebViewFeature.isFeatureSupported` check.
7. **Comments stay true.** Write and edit comments by the code-comments skill
   (`.claude/skills/code-comments/SKILL.md`): why, not what; no history; requirement IDs in the KDoc of
   the implementing declaration; `Invariant:` markers kept true. Before changing behavior a
   requirement covers, run `scripts/spec_map.sh <ID>`.

## Verify

```bash
./gradlew $GRADLE_FLAGS --console=plain :core:check :template:testDebugUnitTest :template:lintRelease \
  :template:assembleRelease :shrimpl:testDebugUnitTest :shrimpl:lintRelease :shrimpl:assembleDebug
scripts/check_native_code.sh --none template/build/outputs/apk/release/template-release.apk
scripts/check_native_code.sh --all-abis shrimpl/build/outputs/apk/debug/shrimpl-debug.apk
scripts/check_comments.sh --diff origin/main
```

There is no emulator in the cloud container; UI changes are confirmed on the owner's devices.

## Working practice

- Commit work locally as it's done. Build, push and send the APK only when asked ("Build and push
  it, then send the APK"); then push the branch, check CI, and send `shrimpl-debug.apk`. Build
  Shrimpl's release APK (`:shrimpl:assembleRelease`, then the R0 check on it) only when the owner
  asks for a release build; in CI, run the workflow by hand with Release build ticked.
- Never force-push. Branch from the default branch for new work.
- Settings descriptions (the text under a setting) end with a period when they are two or more
  sentences, or when they contain punctuation (a comma, apostrophe, hyphen, parentheses, a period);
  a single sentence without any of these goes without one. Other text (labels, titles, errors,
  notifications, toasts, dialogs) has no ending period unless it is two or more sentences. Follow
  the palette rules in `ui/common/Notice.kt` and the theme (Info follows the Material theme; Caution, Error and Success
  use pastel containers with dark text in both themes; status text on plain surfaces is pastel in
  dark theme and deep in light theme; Success alone is harmonized toward the theme's primary).
- Shrimpl's Compose screens are tested with Robolectric in `shrimpl/src/test` (layout at the nine
  window sizes of the testing-setup skill, what a state shows, simple interactions, state surviving
  recreation; docs/BUILD_NOTES.md). With every change,
  look for behavior worth a new Robolectric test, and add it where it makes sense; a layout or state
  bug found on a device gets a test that would have caught it. The template's WebView, real installs
  and hinges still need devices.
- Android skills from Google's android/skills are vendored unchanged in `.claude/skills/`;
  `docs/SKILLS.md` says when to use each and records the deliberate deviations (the adaptive
  skill's are in `docs/ADAPTIVE.md`). Use the skill that matches the work and follow it unless a
  recorded deviation says otherwise; a new deviation gets a row in the same commit. Never edit a
  vendored skill. When a session starts with an "android skills:" note, run
  `scripts/android_skills.sh update <name>`, review the diff against the recorded deviations,
  commit it on its own and tell the owner; bring a new upstream skill to the owner with a
  recommendation before adopting it.
- Record new toolchain or platform findings, and any deviation from the specs, in
  `docs/BUILD_NOTES.md`.
