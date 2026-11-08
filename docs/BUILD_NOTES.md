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
- **Emulator (2026-11-08).** The cloud container can't run one: no `/dev/kvm` and no `vmx`/`svm` CPU flags,
  so x86_64 images fail ("KVM requires a CPU that supports vmx or svm"), and ARM images can't run on an
  x86 host at all ("Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64
  host"). GitHub's `ubuntu-latest` runners (2 cores, 7 GB, 14 GB free disk) do have `/dev/kvm`, even for a
  private repository, so the Emulator workflow runs `system-images;android-37.2;google_apis_ps16k;x86_64`
  there (`scripts/start_emulator.sh`); every 37.x image is a 16 KB page one, so a native library that isn't
  16 KB aligned can't load on it (`scripts/check_page_alignment.sh`). Things that went wrong and why the
  workflow looks as it does: the first run had too little disk for a 6 GB data partition (the emulator
  exits at once, `adb wait-for-device` then blocks forever, so the script watches the process; the
  workflow frees ~20 GB first); building with the 4 GB emulator running made the runner run out of
  memory and die ("AAPT2 daemon unexpectedly exit", "received a shutdown signal"), so everything is built
  and the Gradle daemon stopped first; a failed test's files vanish with Gradle's uninstall, so evidence
  goes to `/data/local/tmp/evidence` and logcat; and a freshly booted 2-core emulator makes system apps
  (launcher, Play services, Settings) miss deadlines, so an "isn't responding" dialog can cover a test's
  screen: the script hides error dialogs (`hide_error_dialogs`) and waits 30 s after boot, and the install
  robot taps Wait if one still shows. A new workflow can't be dispatched from a branch until it
  is on the default branch, so the Emulator workflow runs on push. Run times (before the speed-ups below):
  about 4 minutes to boot and settle, 7 minutes of tests, 16.5 for the whole job. Speed-ups: the template's
  tests stop the start page's load and put the test page in at once instead of waiting for the start
  page (10 to 20 s per test; sharing one activity per class doesn't work, because AndroidJUnitRunner
  finishes every open activity after each test); both modules run in one Gradle invocation; the SDK and system image are not
  cached (restoring them took 136 s, downloading about 70); and a push that changes only `docs/**` or
  Markdown doesn't run the workflow. Not done: freeing disk in the background (90 s) and a shorter settle.
- **Zero tests is not a pass.** Once the template's instrumentation died within seconds of starting and
  Gradle reported success with `tests="0"`; the run looked green apart from Shrimpl's. So
  `scripts/run_instrumented_tests.sh` retries a module that reports no tests and fails the job if it still
  doesn't. Reading the layout the instant the first tile appeared saw 2 of 4 across, so
  `WindowSizesTest` waits for every tile and two agreeing readings.
- **Test fixtures.** The template's debug build (never the release APK Shrimpl bundles) has a document
  provider, `TestTreeProvider`, over `cache/test-tree`, so the folder tests run the template's
  `DocumentsContract` calls against a real provider. `DocumentsProvider` insists on `MANAGE_DOCUMENTS`
  and exported + grantUriPermissions; calls from the app's own uid are allowed anyway.
- **What the emulator found** that Robolectric and hands-on testing hadn't: this WebView already defines
  `showOpenFilePicker`/`showSaveFilePicker` (they throw a SecurityError without a user gesture and have no
  dialog), so a bridge that skipped pages that had them never installed; and the file bridge crashed the
  whole web app when a document's provider had no `last_modified` column (MediaStore's).

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

- Template version 9 (2026-11-01): fixes from the Android skills audits. `intent:` links lose URI
  permission grant flags; only the toggle shortcut's token toggles desktop/mobile; `onNewIntent`
  keeps the latest intent; cookies are flushed once per stop, off the main thread.

- Template version 10 (2026-11-01): the camera, microphone and location steps of T6.5 (explain after
  one refusal, a snackbar with Settings once Android stops asking), with Material's
  `MaterialAlertDialogBuilder` and `Snackbar`, which the template already depended on for its theme.
  Android's `shouldShowRequestPermissionRationale` is false both before the first request and after
  "Don't allow" twice, so the template keeps an "asked" flag per permission in its preferences.

- Template version 11 (2026-11-02): page text follows the system font size (T5.7). WebView's own
  default text zoom already is 100 x the font scale, but the template set it to a fixed 100 %, so
  enlarged system text never reached web pages.

- Template version 12 (2026-11-02): text punctuation only. The permission explanation and the
  "can't be downloaded" toast end with a period (CLAUDE.md: sentences with punctuation in them, or
  more than one sentence, end with one).

- Template version 13 (2026-11-02): those two periods removed again: the period rule covers settings
  descriptions only, and the template has none.

- Template version 14 (2026-11-02): the permission explanation (two sentences) ends with a period
  again, per the owner's review of all strings.

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
  There is no threshold for Shrimpl; the 95 % rule is for `:core`. The first report (2026-11-01)
  covered 34 % of Shrimpl's lines.
- Google's Accessibility Test Framework checks for Compose (`enableAccessibilityChecks()` from
  `androidx.compose.ui:ui-test-junit4-accessibility` 1.12.1, ATF 4.1.1) don't work under Robolectric:
  with them enabled, an unlabelled, clickable 10 dp box with near-invisible text passes, whether the
  checks are enabled before or after the content, with Robolectric's native graphics and with its
  accessibility manager switched on. Accessibility is tested with semantics assertions instead
  (docs/SKILLS.md); ATF runs on the emulator (`AccessibilityChecksTest`, with a negative control).
- A Compose popup (a dropdown menu) is its own window under Robolectric, placed at the window's
  origin, and its nodes' `boundsInWindow` are relative to that window. Where a menu opens on screen
  can't be tested there. The tiles' long-press menu opened well below the tile on a device
  (2026-11-02): a `DropdownMenu` opens below its anchor and its `offset` is added to the anchor's
  bottom, so the menu is now anchored to an empty box at the pressed point.

## CI timing (2026-11)

A clean profile of the full CI build on 4 cores: Shrimpl R8 209 s, Shrimpl lint 196 s, Shrimpl
Robolectric tests 110 s, template R8 79 s, template lint 63 s, and lint vital for both release
builds 95 s. Lint vital checks a subset of what the full release lint finds on the same variant, so
CI excludes `lintVitalRelease` (`-x`) and runs `lintRelease` for both apps; no check stops running.
`org.gradle.parallel=true` took the clean build from 6:24 to 5:47 locally. Parallel CI jobs would
shorten a run to about 6 minutes, but each pays about 45 s of setup and rebuilds the template, so
in this private repository they cost 1.5 to 2 times the runner minutes; the workflow keeps one
Build job with a step per stage instead, and skips Build for a commit that already passed.

Shrimpl's own release build (R8, about 3.5 minutes clean) is left out of CI and local checks: the
owner tests debug builds. It runs in a manual CI run with Release build ticked, or when the owner
asks for a release APK. A problem that only shows after shrinking (such as a class R8 strips that is
loaded by reflection) is then found at that build, not on every change. The template's release
build stays in every run: debug Shrimpl bundles it, and the core tests build web apps from it.

## Debug APK size (2026-11)

AGP packages debug APKs incrementally: it rewrites changed entries inside the existing file and
leaves the old space behind as gaps. After many builds in one checkout the debug APK grew to 62 MB
while its entries held only 35 MB. Deleting `shrimpl/build/outputs/apk/debug/` before
`:shrimpl:assembleDebug` packages it fresh. A clean debug APK is about 35 MB, almost all of it dex
stored uncompressed and not shrunk (Compose, Material 3 and the other libraries, about 33 MB); the
release build shrinks and compresses that with R8.

