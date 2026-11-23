# Build notes

The toolchain and the platform findings that shape the code. Testing setups and their quirks are in
docs/TESTING.md; deviations from the specs and from Google's guidance are in docs/DEVIATIONS.md.

## Versions

Every version is in `gradle/libs.versions.toml`, each the latest stable when last checked (maven.google.com,
Maven Central, services.gradle.org). compileSdk, minSdk and targetSdk are 37; the JDK toolchain is 21 with
Java 17 bytecode.

## Lint and native code

- Lint reports 0 errors for both apps. `:shrimpl` keeps one warning, `SetJavaScriptEnabled`: the hidden
  discovery WebView must run page scripts, and `@SuppressLint` is allowed only in the template
  (AGENTS.md rule 3).
- Checked for native code (R0) and found to have none: the Shevery libraries, apksig,
  kotlinx-serialization and the AndroidX libraries the template uses. `scripts/check_native_code.sh`
  enforces R0 on the APKs.

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
- **Git remote.** The cloud session's git remote uses the repository's former URL; GitHub redirects
  it to `Valghora/shrimpl` ("This repository moved" on every push is expected).
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
- **Dark Reader (T6.21).** `template/src/main/assets/darkreader/darkreader.js` is the npm package's
  own `darkreader.js` 4.9.135, unchanged (tarball sha1 `2dbcb11b6eb0eae383af28a9b214eb1caa24064c`,
  as the registry lists it), with its `LICENSE`. It is plain JavaScript, so R0 holds; 362 KB, about
  70 KB compressed in the APK. Its UMD wrapper takes the `exports` and `module` the app's script
  declares, so it sets no global. Run in a page, it can't read style sheets on other domains (CORS),
  hence the app's fetch bridge. WebView reports `prefers-color-scheme` from the theme's
  `isLightTheme`, so AppCompat's local night mode sets it; to update it later, update the copy with
  its license and `shrimpl.templateVersion`.
- **Package visibility.** `<queries>` (launcher apps, icon packs, Shevery) instead of
  `QUERY_ALL_PACKAGES` (R6.1): narrower, and enough, since every web app has a launcher entry.
- **Network state.** `ACCESS_NETWORK_STATE` (a normal permission) lets discovery tell "No internet
  connection" from a site that can't be reached (R3.2).
- **Data extraction.** With targetSdk 31+, `allowBackup="false"` doesn't stop device-to-device
  transfer; `data_extraction_rules.xml` excludes everything.
- **WebView's own file pickers.** This WebView defines `showOpenFilePicker` and `showSaveFilePicker`
  itself; they throw a SecurityError without a user gesture and open no dialog, so the template's
  bridge replaces them rather than filling a gap (T6.17).
- **Providers without `last_modified`.** MediaStore's document provider has no `last_modified` column;
  querying it throws, so the template queries each column on its own and falls back when one is
  missing (the modification time becomes now; T6.17).
- **Back in full screen.** While WebView's full-screen view shows, a Back key (the emulator's, a
  keyboard's, a 3-button bar's) reaches neither the activity's `OnBackPressedDispatcher` nor a window
  `OnBackInvokedCallback`, even at `PRIORITY_OVERLAY`: it comes as a key event and the full-screen view
  swallows it (`FullScreenVideoTest`, 2026-11-10). The template catches `KEYCODE_BACK` in
  `dispatchKeyEvent` while full screen shows, as it does Esc, and keeps an overlay callback for the back
  gesture, which comes the other way.
- **Shevery for silent installs.** Shevery (github.com/HmnDev-Tech/shevery, Apache 2.0) replaced the
  Shizuku privileged installer, which was unmaintained and broken on Android 17 (2026-11-15). Its API
  (github.com/HmnDev-Tech/Shevery-API, MIT) has no native code (R0) and keeps Shizuku's user-service
  protocol, so `ShellService` and its `destroy` transaction are unchanged. It is published only on
  its own Maven repository (`raw.githubusercontent.com/HmnDev-Tech/Shevery-API/maven`), which Gradle
  uses for `com.hamondev.shevery.api` alone. The API bundles its own copy of Shizuku's client classes,
  so it can't sit beside Shizuku's library, and it brings `androidx.annotation` 1.11.0-alpha01.
  A saved install method of `SHIZUKU` reads as Shevery (`installMethodOf`).
- **WorkManager starts on first use.** Robolectric doesn't run androidx.startup's provider, so
  WorkManager was never initialised in unit tests ("WorkManager is not initialized properly"). Shrimpl's
  Application implements `Configuration.Provider` and the manifest removes `WorkManagerInitializer`, so
  WorkManager starts on first use, in the app and in tests alike (2026-11-15).
- **UiAutomator swallows interrupts.** A background thread looping over UiAutomator searches never saw
  `Thread.interrupt()` when it arrived mid-search, so `InstallRobot`'s install confirmer outlived its
  test and tapped dialogs in later ones, until a vanishing dialog crashed it inside an unrelated test
  (`WindowSnappingTest`, 2026-11-14). The confirmer now stops on a flag, `stop()` waits for it, and
  tests stop every confirmer in `@After`.
- **The full-screen confirmation.** The first time an app hides the system bars, Android shows its
  own "Viewing full screen" confirmation, and that window takes focus, so a Back key goes to it, not
  the app (`FullScreenVideoTest` in a full run, 2026-11-10: `mCurrentFocus` was
  `ImmersiveModeConfirmation`). Users see it once; the test sets
  `secure immersive_mode_confirmations` to `confirmed`, as after that first time.
- **Compose dropdown menus.** A `DropdownMenu` opens below its anchor and adds its `offset` to the
  anchor's bottom, so offsetting the menu put it far below a tile. App menus are anchored to an empty
  box at the pressed point, kept there after the menu closes so the menu doesn't jump as it fades out
  (found on a device, 2026-11-02).

## R8

Both apps use the AGP 9.3+ `optimization { enable = true }` block. Shrimpl's one keep rule (the Shevery
shell service) is in `shrimpl/src/main/keepRules/`; every broad rule comes from a library or AGP's
defaults (r8-analyzer skill, 2026-11-01).

## CI decisions

- **Lint vital is skipped** (`-x :template:lintVitalRelease`): it checks a subset of what `lintRelease`,
  which runs for both apps, finds on the same variant.
- **One Build job, a step per stage.** Parallel jobs would shorten a run to about 6 minutes, but each pays
  about 45 s of setup and rebuilds the template, so in a private repository they cost 1.5 to 2 times the
  runner minutes. Build is skipped for a commit that already passed. `org.gradle.parallel=true` is on.
- **Shrimpl's release build** (R8, about 3.5 minutes clean) runs only in the Release workflow (a
  merge into main) or when the owner asks for a release APK; a problem that only shows after
  shrinking is found then. The template's release build runs every time: debug Shrimpl bundles it, and the core tests
  build web apps from it.
- **Releases tag themselves.** The cloud sessions' GitHub proxy takes branch pushes but drops tag
  pushes ("unexpected disconnect while reading sideband packet") and refuses writes to the refs API
  (HTTP 403), so the Release workflow runs on pushes to main and creates the tag with its own token
  (R12.6).

## Debug signing key

Each machine's Android Gradle Plugin makes its own `~/.android/debug.keystore`, and a cloud session's
container makes a new one whenever it starts. A debug APK from a new session then can't update the
installed Shrimpl Debug: Android refuses an update signed with another key, and the installer only says
the app wasn't installed (found on a device, 2026-11-23). `shrimpl/debug.keystore` is committed and
signs every debug build instead (R12.3). It is a debug key (CN=Android Debug), which the Release
workflow refuses for a release.

## Debug APK size

AGP packages debug APKs incrementally and leaves replaced entries' space behind as gaps: after many
builds in one checkout the APK reached 62 MB for 35 MB of entries. Delete
`shrimpl/build/outputs/apk/debug/` before `:shrimpl:assembleDebug`. A clean debug APK is about 35 MB,
almost all of it unshrunk dex stored uncompressed; the release build shrinks and compresses it.
