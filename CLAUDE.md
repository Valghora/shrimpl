# Shrimple: rules for every change

Shrimple (`:shrimple`, Compose) builds, signs and installs web apps on the device from a bundled
template (`:template`, AndroidX Views + WebView), using pure-JVM logic in `:core`. Read
`docs/SHRIMPLE_SPEC.md` (the requirements), `docs/TEMPLATE_SPEC.md` (web app behavior) and
`docs/ADAPTIVE.md` before changing behavior. Keep those documents in step with the code: a change
that alters a requirement updates the spec in the same commit.

## Non-negotiables

1. **Architecture neutrality first (spec R0).** Every APK must run natively on arm64-v8a,
   armeabi-v7a, x86_64 and x86 with no translation layer.
   - The template, and every web app, contains no native code: no `.so`, NDK, JNI, `abiFilters` or
     ABI splits. Check every new template dependency for native code.
   - Shrimple adds no native code of its own; a third-party native library is allowed only if it
     ships for all four ABIs. Building web apps on the device stays pure Kotlin/Java (apksig, the
     manifest patcher); never run or bundle native tools such as aapt2.
   - Verify with `scripts/check_native_code.sh` (below).
2. **Adaptive by default.** For any UI change, follow the adaptive skill in
   `.claude/skills/adaptive/SKILL.md` (and the reference it names for the feature) and the Adaptive
   app quality guidelines, as applied in `docs/ADAPTIVE.md`:
   - layout from window size classes, never device type or hard-coded widths;
   - user input and dialog state in `rememberSaveable` or a ViewModel; never add `configChanges` to
     Shrimple;
   - Navigation 3 with scene strategies for multi-pane layouts (no `ListDetailPaneScaffold`);
   - 48 dp touch targets, a tooltip on every icon-only button (`TooltipIconButton`), right-click
     menus where long press has one, keyboard shortcuts listed in the Keyboard Shortcuts Helper.

   A deliberate exception gets a row in `docs/ADAPTIVE.md`'s deviations table.
3. **No suppressions.** No Kotlin `@Suppress`, no `@SuppressLint` (the single exception:
   `SetJavaScriptEnabled` on the template's `applyWebSettings`), no lint baselines, disabled checks,
   lowered thresholds, or deleted or weakened tests. Lint must report 0 errors. After three failed
   fix attempts, stop and report the log.
4. **`:core` is pure JVM.** No `android.*`, `androidx.*` or `com.google.android.*` imports; line
   coverage stays at or above 85 %.
5. **The template stays small.** AndroidX Views only (T1.1), no site values in its code or
   resources. Bump `shrimple.templateVersion` in `gradle.properties` whenever template code or
   resources change.
6. SDK checks: minSdk is 37, so never guard APIs at or below 37. Each androidx.webkit call sits
   directly inside its own nested `WebViewFeature.isFeatureSupported` check.

## Verify

```bash
./gradlew $GRADLE_FLAGS --console=plain :core:check :template:testDebugUnitTest :template:lintRelease \
  :template:assembleRelease :shrimple:testDebugUnitTest :shrimple:lintDebug :shrimple:assembleDebug \
  :shrimple:assembleRelease
scripts/check_native_code.sh --none template/build/outputs/apk/release/template-release.apk
scripts/check_native_code.sh --all-abis shrimple/build/outputs/apk/debug/shrimple-debug.apk \
  shrimple/build/outputs/apk/release/shrimple-release.apk
```

There is no emulator in the cloud container; UI changes are confirmed on the owner's devices.

## Working practice

- Commit work locally as it's done. Build, push and send the APK only when asked ("Build and push
  it, then send the APK"); then push the branch, check CI, and send `shrimple-debug.apk`.
- Never force-push. Branch from the default branch for new work.
- Write user-facing text without ending punctuation in item descriptions, and with the palette rules
  in `ui/common/Notice.kt` and the theme (pastel containers with dark text in dark theme, the inverse
  in light theme).
- Record new toolchain or platform findings, and any deviation from the specs, in
  `docs/BUILD_NOTES.md`.
