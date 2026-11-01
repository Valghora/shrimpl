# Build notes

Baseline of 2026-10-28: the toolchain, platform findings that shape the code, and every deviation
from docs/SHRIMPL_SPEC.md and docs/TEMPLATE_SPEC.md. Deviations from Google's adaptive guidance are
in docs/ADAPTIVE.md. The log of how the project got here is in `docs/history/`.

## Versions

All in `gradle/libs.versions.toml` (latest stable when checked on maven.google.com, Maven Central
and services.gradle.org).

| Component | Version |
|---|---|
| Android Gradle Plugin, apksig | 9.4.1 |
| Gradle | 9.8.2 |
| Kotlin (AGP built-in, `kotlin.jvm`, serialization and Compose plugins) | 2.4.21 |
| JDK toolchain / bytecode | 21 / Java 17 |
| compileSdk, minSdk, targetSdk | 37 |
| kotlinx-serialization-json | 1.12.0 |
| Compose BOM (Compose 1.12.1, Material 3 1.4.0) | 2026.09.00 |
| material3-adaptive (adaptive, adaptive-layout, adaptive-navigation3) | 1.3.0 |
| Navigation 3 (runtime, ui) | 1.2.0 |
| lifecycle (incl. viewmodel-navigation3) | 2.11.0 |
| activity-compose, activity-ktx | 1.13.0 |
| androidx.webkit | 1.18.0 |
| androidx.core, appcompat, material | 1.19.1, 1.8.0, 1.14.0 |
| Shizuku api / provider | 13.1.5 |
| junit | 4.13.2 |

## Architecture neutrality (R0)

- `scripts/check_native_code.sh` checks APKs: `--none` (template: no `.so` at all) and `--all-abis`
  (Shrimpl: each library present for arm64-v8a, armeabi-v7a, x86_64 and x86, nothing else).
- Shrimpl's only native library is `libandroidx.graphics.path.so` (androidx.graphics:graphics-path,
  pulled in by Compose UI). Its `PathIteratorImpl` loads it unconditionally on Android, also in
  older versions, so it can't be excluded; it ships for all four ABIs.
- The Shizuku libraries, apksig, kotlinx-serialization and the AndroidX libraries the template uses
  contain no native code.
- The build host's aapt2 (from Maven) is an x86_64 Linux binary; R0 applies to APKs, not hosts.

## Toolchain findings

- **SDK setup.** cmdline-tools 16111833 ship the `android` CLI (`android --sdk=$ANDROID_HOME sdk
  install|list`); `sdkmanager` is deprecated. API 37 platforms have minor versions (android-37.0,
  37.1, ...); `compileSdk = 37` builds against any of them.
- **JDK.** Only JDK 21 is installed in the cloud container; every module uses `jvmToolchain(21)`
  and Java 17 bytecode so `:core` is consumable by the Android modules.
- **AGP 9 built-in Kotlin.** Apply only `com.android.application`, never `kotlin-android`.
- **Maven Central rate limits.** Parallel downloads through the session proxy got HTTP 429;
  `settings.gradle.kts` lists Google's Central mirror first.
- **Configuration cache.** Gradle can't serialize a `doFirst` that reads a build-script value; copy
  it into a local `val` first.
- **`.gitignore` and `build/`.** `core/.../build/` is a source package; `!**/src/**/build/` keeps it
  tracked.
- **KDoc gotcha.** `https://*.d` inside `/** */` opens a nested comment in Kotlin.
- **Library sources.** The Maven sources jars of some AndroidX KMP libraries lack `androidMain`;
  inspect the AAR's `classes.jar` with `javap` instead.

## Platform findings

- **Resource path shortening.** AGP release builds rename resources to short paths, which would
  leave the icon files without a fixed path; `android.enableResourceOptimizations=false` keeps
  `res/drawable-nodpi-v4/ic_launcher_*.png` and `ic_stat_notify.png` (R7.4).
- **Binary manifest values.** Typed values as aapt2 writes them: ints `0x10`, booleans `0x12`
  (`-1`/`0`), dimensions `0x05` with integer dp encoded as `value << 8 | 1` (400dp = `0x00019001`).
- **apksig at minSdk 37** signs with scheme v3 (and v2).
- **Certificates.** The JDK has no public certificate builder, so `SigningKeys` DER-encodes the
  X.509 v3 certificate itself (30 years, GeneralizedTime after 2049); identical on Android.
- **aapt2 37 badging.** The min SDK line is `minSdkVersion:'37'`; adaptive icons in
  `mipmap-anydpi/` are reported as `mipmap-anydpi-v21/`.
- **WebView features.** webkit 1.18.0 has `USER_AGENT_METADATA`, `WEB_MESSAGE_LISTENER` and
  `DOCUMENT_START_SCRIPT`. Lint's RequiresFeature doesn't follow `a && b`, or early returns; nest
  the checks.
- **Desktop windowing.** Every app gets a caption bar; transparent caption appearance and
  caption-bar insets give the header color (T3). From API 36, `resizeableActivity` is ignored on
  displays of 600 dp and wider, and API 37 ignores orientation and aspect ratio limits on large
  screens. `singleInstancePerTask` plus `FLAG_ACTIVITY_MULTIPLE_TASK` gives multiple windows.
- **Data extraction.** With targetSdk 31+, `allowBackup="false"` doesn't stop device-to-device
  transfer; `data_extraction_rules.xml` excludes everything.
- **No emulator in the cloud container** (no KVM): UI changes are checked on devices.

## Deviations

| Requirement | What we do instead | Why |
|---|---|---|
| T3.3 (header color from the first frame) | The starting window uses the system surface color until the app applies the profile's header color at launch. | The window background is a compiled resource; making it per app would mean patching `resources.arsc`. |
| R9.3 | Request types come from resource timing (`xhr`, `fetch`, `script`, ...); WebSocket hosts aren't seen. | WebView has no per-request resource type. |
| R9.3 | The rendered visit's HTTP status is known only when it is an error (>= 400). | WebView reports main-document errors, not successes. |
| R10.3 | Letter icons use the system bold font. | Native rendering; no bundled font. |
| R6.1 | Package visibility uses `<queries>` (launcher apps, icon packs, Shizuku), not `QUERY_ALL_PACKAGES`. | Narrower, and enough: every web app has a launcher entry. |
| Rules (lint) | `:shrimpl` lint keeps one warning: `SetJavaScriptEnabled` (the hidden discovery WebView must run page scripts). | `@SuppressLint` is allowed only in the template. |

## Rename to Shrimpl (2026-10-29)

UniWrap became Shrimpl everywhere except `docs/history/`: the app (`com.valghora.shrimpl`, module
`:shrimpl`), the core package (`com.valghora.shrimpl.core`), the template's namespace
(`com.valghora.shrimpl.webapp`) and placeholder package (`com.valghora.shrimpl.template`), the
apps' package prefix (`com.valghora.shrimpl.app`), the profile asset (`assets/shrimpl/`), the
signing key's alias and common name, the launcher icon (a white shrimp on #CC5500, with a themed monochrome layer) and the repository (`Valghora/shrimpl`; the cloud session's git
access still uses the old URL, which GitHub redirects). An interim name, Shrimple, never shipped
beyond test builds. Shrimpl is a new
package, so it installs beside UniWrap rather than updating it, and can't update apps UniWrap made
(their packages start `com.valghora.uniwrap.app`). Template version 4.

## Icon options (2026-10-30)

- Records keep every icon option (`IconOptions`: brand glyph, website icon, custom icon, selected),
  next to `icon`, the one the last build used. Records saved before keep working: a glyph becomes
  Brand, a letter Website, and an image Website when its origin names one of the site's icons, else
  Custom (its file stays `icon-image`). Without a stored site image, their Website option is a letter.
- `:core` holds the editing model (`WebAppDraft`: form, options and unsaved images, with the edits the
  form makes), so the form takes one draft and one change callback.
- Discovery's core rules only list the site's icons; `Discovery.websiteIcon` fetches one, after the
  draft, when the best icon was a brand glyph, so the recorded fixtures keep their fetch order.
- Images are stored as centered square PNGs of at most 512 px (never enlarged); saving a record
  deletes icon images no option uses any more.
- Previews draw only the foreground layer straight to a bitmap over the background color; the full
  renderer (four PNG layers) runs only for builds.
- Custom icons come from Photos, Files or an icon pack's own picker
  (`org.adw.launcher.icons.ACTION_PICK_ICON`, result in the `icon` extra). Shrimpl no longer reads
  icon packs' `appfilter.xml` or drawables, so the `DiscouragedApi` lint warning is gone.

## Template versions (2026-10-31)

Template version 5 was used twice: by a discarded build (the first-load fade-in, never merged) and
by the app-list work. Apps built with the discarded build were recorded as version 5 and so showed
no update. The template is now version 6. A version number is never reused, even for a build that is
thrown away.

- Template version 7 has no template code change: it is bumped so every installed web app shows
  Update and, rebuilt, gains `assets/shrimpl/icons.json` (R11.6) for recovery after a reinstall of
  Shrimpl. The extra assets are added by `WebAppBuilder` (`WebAppSpec.extras`), not by the template.

- Template version 8 has no template code change either: it was bumped at the owner's request, to
  exercise Update and Update all on installed web apps.

- `ACCESS_NETWORK_STATE` (a normal permission) lets the search tell "No internet connection" apart
  from a site that can't be reached.

## Robolectric (Shrimpl's Compose screens in unit tests)

- Robolectric 4.17 (September 2026) supports API 37 with the Android 17 runtime
  `android-all-instrumented:17-robolectric-15733970-i7` (about 240 MB) and needs Java 21, which the
  build already uses.
- Robolectric's own downloader fetches that runtime at test time from Maven Central, which answered
  429 (rate limited) here. Gradle fetches it instead (the `robolectricRuntime` configuration and
  `prepareRobolectricRuntime` task in `shrimpl/build.gradle.kts`), and the tests run Robolectric
  offline against it: cached, retried, and the same repositories as every other dependency.
- On Java 17 and later the test JVM needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`
  (and `--add-opens=java.base/java.io=ALL-UNNAMED`): Robolectric emulates Android's file descriptors
  through those internals.
- `ui-test-junit4` brings Espresso 3.5.0 and androidx.test 1.5.0, which call
  `InputManager.getInstance()`, gone in API 37. The tests pin Espresso 3.7.0 and androidx.test 1.7.0.
- Robolectric's default screen is 320 dp wide; layout tests set a larger one with
  `@Config(qualifiers = "w1600dp-h1200dp")` so the pane sizes they try aren't squeezed.
- There is no Android Keystore under Robolectric, so tests run without a signing key: every saved app
  counts as signed with another key (R11.7). Installed apps are faked with `ShadowPackageManager`,
  which also needs `setApplicationIcon` for each, since Shrimpl reads the icon.
- `ui-test-manifest` is a debug-only dependency (it declares the activity Compose tests host screens
  in); it adds no code to release builds and no native code.
- Window sizes: `DeviceConfigurationOverride.ForcedSize` overrides `LocalConfiguration` and
  `LocalDensity` but not `LocalWindowInfo`, which `currentWindowAdaptiveInfo` reads, so the list-detail
  switch ignores it. `WindowSizeTest` sets the screen with `RuntimeEnvironment.setQualifiers` and then
  launches `MainActivity`, for each of the testing-setup skill's nine sizes (400, 610 and 900 dp wide
  by 400, 500 and 1000 dp high).
- Robolectric runs out of touch mode, so Compose gives keyboard focus to the first focusable item of
  each new window, as for a keyboard user. A focused icon button shows its tooltip, which appears in
  tests as a second, small root.
- A dialog with a text field never lets Compose go idle (`AppNotIdleException` after 60 s) once the
  field can take focus: opening such a dialog from a click, focusing the field or typing into it.
  Pausing the test clock, `setInTouchMode(true)` and turning off initial focus
  (`ComposeUiFlags.isInitialFocusOnFocusableAvailable`) don't help. Tests change values through
  switches and menus instead; dialog drafts surviving recreation stay a device check.
- Coverage: debug builds set `enableUnitTestCoverage`, so
  `./gradlew :shrimpl:createDebugUnitTestCoverageReport` writes a JaCoCo report of the unit and
  Robolectric tests to `shrimpl/build/reports/coverage/test/debug/`. JaCoCo has to include classes
  without a source location (Robolectric loads the app's classes itself) and leave out `jdk.internal`.
  There is no threshold for Shrimpl; the 85 % rule is for `:core`. The first report (2026-11-01)
  covered 34 % of Shrimpl's lines.
