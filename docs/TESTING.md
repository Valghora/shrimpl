# Testing

What is tested, where it runs, and what is still left to the product owner's devices. Keep this in
step with the tests: a feature that gets a new kind of check updates the table below in the same
commit. Toolchain versions and platform findings are in [BUILD_NOTES.md](BUILD_NOTES.md).

## The layers

| Layer | Where it runs | When | What it is good at |
|---|---|---|---|
| `:core` unit tests (JUnit, JaCoCo, 95 % line coverage) | JVM, in CI and the cloud container | before a merge (by hand) | Pure logic: profiles, the manifest patcher and web app builder (against the real template APK), discovery rules, icons, the page download, file access, share and camera-choice protocols |
| Shrimpl Robolectric tests (`shrimpl/src/test`) | JVM, in CI and the cloud container | before a merge (by hand) | Compose layout at the nine window sizes, what a state shows, simple interactions, state surviving recreation |
| Template unit test (`template/src/test`) | JVM | before a merge (by hand) | The profile asset |
| Static checks | CI | before a merge (by hand) | Lint (0 errors), no native code (R0), 16 KB page alignment, and the comment and document checks (`scripts/check_comments.sh`, `scripts/check_docs.py`): references resolve and every requirement is traced |
| **Emulator tests** (`src/androidTest` in `:template` and `:shrimpl`) | A real Android 37 system image (x86_64, KVM) in the Emulator workflow | by hand, on `develop` before a merge into `main` (R12.5) | What only a real system does: the real WebView and its bridges, real installs, real accessibility checks, real window manager |
| The owner's devices | A Dell XPS Googlebook and others | when the owner tries a build | Everything in the last section |

Robolectric runs in the cloud container; the emulator can't (no KVM), so it runs on GitHub's runners
("The emulator's environment", below).

## The Emulator workflow

`.github/workflows/emulator.yml` runs by hand only (`workflow_dispatch`); when to run it and what a
merge needs are in CLAUDE.md. It builds the APKs and test APKs, stops Gradle, boots the emulator
(`scripts/start_emulator.sh`), runs `connectedDebugAndroidTest` for both modules
(`scripts/run_instrumented_tests.sh`), and uploads `instrumented-results`, kept for one day
(`scripts/collect_emulator_artifacts.sh`): the JUnit XML, `emulator-out/logcat.txt`, a screenshot, the UI
hierarchy, and what failed tests saved (`emulator-out/evidence/`, and in logcat under the tag
`SHRIMPL_EVIDENCE`).

Writing an emulator test:

- **The template's WebView.** `PageDriver` (template `androidTest`) launches the real `MainActivity` and
  loads a test page at the neutral template's own origin (`https://example.com`), so the bridges' document
  scripts and message listeners apply as they do for a real site, then runs JavaScript in it. Android's
  dialogs (share sheet, file pickers) are answered with Espresso-Intents stubs, so the test checks what
  the template asked for and what it did with the answer.
- **Shrimpl.** `createAndroidComposeRule<MainActivity>()` for screens; `UiAutomator` for what is outside
  the app (the system installer's Install button). Call `Evidence.capture("name")` when a flow fails, so
  the run keeps a screenshot and the window hierarchy.
- **The real folder picker.** DocumentsUI opens at the top of shared storage, where no folder can be
  used; `RealFolderPickerTest` taps Documents, the folder, USE THIS FOLDER and ALLOW. The shell that
  UiAutomator runs has no redirects, so seed files are copied (`cp`), not written with `>`.
- **Folders.** `template/src/debug` holds `TestTreeProvider`, a document provider over a folder in the
  cache, so a test can hand the template a folder as if the user had picked it. It is in the debug build
  only: the release APK Shrimpl bundles never has it.
- **Sharing the screen.** `WindowSnappingTest` puts Shrimpl in split screen with the system UI's shell
  command (`dumpsys activity service SystemUIService WMShell splitscreen moveToSideStage <task> 1`, beside
  Settings) and in freeform windows (`am start --windowingMode 5`, then `am task resize`). Freeform
  windows need `enable_freeform_support`, set once for the class: switched on just before a launch, it
  wasn't always used. Close a freeform window with `finishAndRemoveTask`: a later launch reuses a
  leftover task, and a reused task keeps its windowing mode.
- **Trying a new test.** Dispatch the Emulator workflow with `-f "inputs[tests]=module:class"` (for
  example `shrimpl:com.valghora.shrimpl.WindowSnappingTest`) to run only that class, in about half the
  time. A merge still needs a run of the whole suite.
- **Display sizes.** `WindowSizesTest` resizes the display with `wm size` and `wm density` and always
  resets them, so other tests see the emulator's own screen.
- **System dialogs.** `InstallRobot` answers the installer's Install, Update and Uninstall screens.
- **User gestures.** Where a page needs one (a file chooser opens only after a real tap),
  `PageDriver.tap` sends touch events to the WebView at the element and waits for the page to see the
  click; pages may open popups from a script.
- **Profiles.** The neutral profile allows notifications (the default) but not the camera, microphone
  or location. `withProfile(profileAllowingEverything()) { ... }` (`TestProfile.kt`) runs a block with
  another profile, swapped in before the activity starts; `answerPermissionPrompt` answers Android's own
  permission prompts. A fresh launch asks for the notification permission until it is granted (T7.5),
  so a test that needs the screen to itself answers that prompt first.
- **Dialogs.** Espresso can't find a dialog the template shows (it finds no resumed activity, likely because
  the notification prompt of a fresh launch is the resumed one), so tests look for its window with
  `WindowInspector`.
- **Memory.** Don't add workflow steps that build while the emulator runs (below).

## The emulator's environment

Why the workflow and the scripts look as they do; each rule fixed a run that went wrong.

- **Where it runs.** The cloud container has no `/dev/kvm` and no `vmx`/`svm` CPU flags, so x86_64
  images fail ("KVM requires a CPU that supports vmx or svm"), and ARM images can't run on an x86 host
  ("Avd's CPU Architecture 'arm64' is not supported"). GitHub's `ubuntu-latest` runners (2 cores, 7 GB,
  14 GB free disk) have `/dev/kvm`, also for a private repository. The image is
  `system-images;android-37.2;google_apis_ps16k;x86_64`; every 37.x image has 16 KB pages, so a native
  library that isn't 16 KB aligned can't load (`scripts/check_page_alignment.sh`).
- **Memory.** The emulator takes 4 GB of the runner's 7: building while it runs killed the runner
  ("AAPT2 daemon unexpectedly exit", then a shutdown signal). Everything, the app APKs included, is
  built first and the Gradle daemons are stopped before it boots.
- **Disk.** The workflow frees about 20 GB first and the data partition is 3 GB. With too little disk
  the emulator exits at once and `adb wait-for-device` blocks forever, so `scripts/start_emulator.sh`
  watches the emulator process.
- **A fresh boot.** A freshly booted 2-core emulator makes system apps miss deadlines, and an "isn't
  responding" dialog can cover a test's screen: the script hides error dialogs (`hide_error_dialogs`)
  and waits 30 s after boot, and `InstallRobot` taps Wait if one still shows.
- **Logcat.** The buffer is 256 MB (`scripts/start_emulator.sh`): at its default size, the template's
  tests, which run first, had scrolled out of it by the end of the run.
- **Notification prompts.** A fresh launch of the template, and of a web app, asks for the notification
  permission (T7.5), and the prompt can appear after the page does. A test that reads the screen or
  presses Back first answers it and waits for its own window (`BuildAndInstallTest`,
  `FullScreenVideoTest`): left on top, the prompt took the Back and covered the page.
- **Evidence.** Gradle uninstalls the app at the end of a run, taking a failed test's files with it, so
  `Evidence` writes to `/data/local/tmp/evidence` and to logcat (`SHRIMPL_EVIDENCE`).
- **Zero tests is not a pass.** Gradle reports success when the instrumentation dies at once
  (`tests="0"`), so `scripts/run_instrumented_tests.sh` reruns a module that reports no tests, alone,
  and fails the job if it still reports none.
- **One Gradle run, not parallel.** Both modules run in one invocation (a second costs a minute of
  startup), with `--no-parallel`: run side by side on the one emulator, they uninstall each other's app.
- **One activity per test.** AndroidJUnitRunner finishes every open activity after each test, so a test
  class can't share one. The template's tests save the 10 to 20 s of waiting for the start page
  instead: `PageDriver` stops its load and puts the test page in at once.
- **No SDK cache.** Restoring the SDK and system image from the Actions cache took 136 s; downloading
  them takes about 70.
- **Stub intents by action.** Espresso-Intents also intercepts the intent that launches the activity
  under test, so `intending(anyIntent())` stops `MainActivity` from ever starting and the test waits
  until the job times out (it did, at 45 minutes). Stub the actions the test expects instead.
- **Readings on a busy emulator.** Reading the layout the instant the first tile appeared once saw 2 of
  4 across, so `WindowSizesTest` waits for every tile and two agreeing readings.
- **Folders.** `TestTreeProvider` (debug build only) is a real `DocumentsProvider`, which insists on
  `MANAGE_DOCUMENTS`, exported and `grantUriPermissions`; calls from the app's own uid are allowed
  anyway.
- **Run time.** About 14 minutes: 97 s to free disk, 12 s of SDK setup, 36 s to build, 3.7 minutes to
  download the system image and boot, 7.5 minutes of tests. Not done: freeing disk in the background
  (90 s) and a shorter settle.
- **Known flakes,** all in `FixFlowTest` and all passing on a rerun:
  - the system Uninstall dialog showed and was never tapped; if it recurs, look at `InstallRobot`'s
    selector for that dialog first;
  - Android's PackageManager crashed during install verification (`DirectoryIteratorException`, a
    `/data/app/vmdl*.tmp` staging folder gone while it was measured), restarting the system: the run
    then reports one empty failure and stops early, without the tests after it. That is the image's
    bug, not Shrimpl's.
  - the system installer refused the first install with `INSTALL_FAILED_INTERNAL_ERROR: Session files in
    use` (2026-11-14);
  - the rebuilt app installed with the new key, but Shrimpl's record wasn't saved within the test's
    30 s, the install's success arriving late while the installer service logged errors (2026-11-14).
    Both runs were on a busy emulator, and the same commit then passed all 50 tests. Until
    2026-11-14 an install confirmer could outlive its test and tap the next test's dialogs (see
    docs/BUILD_NOTES.md, "UiAutomator swallows interrupts"), which may explain these two.

## Robolectric

Shrimpl's Compose screens run in unit tests under Robolectric (`shrimpl/src/test`).

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
  switches and menus instead; dialog drafts surviving recreation are tested on the emulator (`DialogDraftTest`).
- Coverage: debug builds set `enableUnitTestCoverage`, so
  `./gradlew :shrimpl:createDebugUnitTestCoverageReport` writes a JaCoCo report of the unit and
  Robolectric tests to `shrimpl/build/reports/coverage/test/debug/`. JaCoCo has to include classes
  without a source location (Robolectric loads the app's classes itself) and leave out `jdk.internal`.
  There is no threshold for Shrimpl; the 95 % rule is for `:core`.
- Google's Accessibility Test Framework checks for Compose (`enableAccessibilityChecks()` from
  `androidx.compose.ui:ui-test-junit4-accessibility` 1.12.1, ATF 4.1.1) don't work under Robolectric:
  with them enabled, an unlabelled, clickable 10 dp box with near-invisible text passes, whether the
  checks are enabled before or after the content, with Robolectric's native graphics and with its
  accessibility manager switched on. Accessibility is tested with semantics assertions instead
  (docs/DEVIATIONS.md); ATF runs on the emulator (`AccessibilityChecksTest`, with a negative control).
- A Compose popup (a dropdown menu) is its own window under Robolectric, placed at the window's
  origin, and its nodes' `boundsInWindow` are relative to that window, so where a menu opens on
  screen can't be tested there (BUILD_NOTES, "Compose dropdown menus").

## What the emulator tests cover

| Requirement | Test |
|---|---|
| T6.15 page-made downloads (blob, data, 3 MB, safe names) | `PageDownloadsTest` |
| T6.16 Web Share API | `ShareBridgeTest` |
| T6.17 single-file Open and Save, write commands | `FileAccessTest` |
| T6.18 folders: list, read, create, write, remove, rename, move, the standard's errors, the stale-handle limit | `FolderAccessTest` (against the debug build's `TestTreeProvider`) |
| T6.18 through Android's real folder picker and shared storage: the test picks `Documents/ShrimplPick` in DocumentsUI, and the page lists, reads, writes, creates and removes, each checked on disk | `RealFolderPickerTest` |
| R2.1 Shrimpl starts on a real system | `HomeLaunchTest` |
| R6, R8, R11.3: build, sign, install through the system installer, launch, see the site | `BuildAndInstallTest` |
| R6.1, R11.4: an installed web app updates in place (the test taps Update) | `BuildAndInstallTest` |
| R11.7: Fix replaces an app signed with another key (uninstall, rebuild with the new key; the test answers both system dialogs) | `FixFlowTest` |
| R1.1, R2.6: two panes and four tiles at full screen on the Dell XPS (2560 x 1600) and on the 2880 x 1800 Googlebooks (Acer, ASUS, HP, Lenovo) at both guessed scales, two panes and three on a common 1536 dp laptop (also the HP's 1920 x 1200 option), one pane and three on a phone, on the real window manager | `WindowSizesTest` |
| T2a, T6.6: a link to another site opens in the browser | `ExternalLinksTest` |
| T6.4: the file chooser offers the camera for photo and video inputs once the camera permission is granted, opens it alone for `capture`, and leaves it out without the permission (a real tap opens the chooser) | `FileChooserCameraTest` |
| T6.6: a popup on the app's own site opens in a dialog at 90 % of the window and closes when the page closes it; one to another site goes to the browser | `PopupsTest` |
| R2.3, CLAUDE.md rule 2: a dialog's half-typed text survives the activity being recreated | `DialogDraftTest` |
| T7: a fresh launch asks for the notification permission, the page is told it may notify, and its `new Notification` becomes an Android notification with its title, body and channel | `NotificationsTest` |
| T6.5: the microphone through all three steps (Android asks; after one refusal the template explains first; after two the page is turned down with a snackbar), and location given only when the profile allows it | `PagePermissionsTest` |
| R1.1: a window sharing the screen shows two panes only from 1000 dp: split screen on half the Dell's screen (about 849 dp, as snapped) shows one, freeform windows of 956 dp (a 2880 px Googlebook snapped) and 1100 dp show one and two | `WindowSnappingTest` |
| T6.13, T5.6: a playing video (recorded in the page with MediaRecorder and decoded by WebView) goes full screen from a tap, keeps playing, and Back leaves full screen before anything else | `FullScreenVideoTest` |
| T6.14: Ctrl+P opens Android's print screen; T6.13, T5.3: a page's element goes full screen with the system bars hidden, and Esc leaves it | `PrintAndFullScreenTest` |
| Accessibility (touch targets, contrast, labels) on the real screens, with a control that must fail | `AccessibilityChecksTest`, `AccessibilityChecksAreLiveTest` |
| 16 KB page alignment of every APK | `scripts/check_page_alignment.sh` (Android workflow) |

## Still the owner's devices

These need hardware, a person, or a service an emulator doesn't have:

- **Shevery and Root installs** (R6): no Shevery on an emulator; `su` isn't available to apps on its images.
- **Hinges and foldable postures**, and **window snapping** itself on a Googlebook (docs/GOOGLEBOOKS.md):
  the emulator's windowing is not Googlebook OS's. What snapping does to Shrimpl, a window that shares
  the screen at half its width, is tested with split screen and freeform windows (`WindowSnappingTest`).
- **TalkBack** itself (reading order, announcements), and how haptics, animation and scrolling *feel*.
- **Installing a real update** (R14.6): it needs a release published with the release key, and a
  release-signed Shrimpl to install over. `UpdatesCheckTest` covers the check, the checksum and the
  debug-build refusal against a fake GitHub.
- **Sign-in flows** that block embedded WebViews (Google, others), and background push (not possible).
- **Cameras, microphones and location** with real hardware (the emulator has virtual ones).
- **Performance** on real silicon: cold start, a long home list.
- **How it looks**: the owner is the only judge of spacing, color and polish. A mock-up comes first.

## Not covered yet, and could be

Nothing known: every gap the emulator can close has a test. A new feature that only the emulator can
check adds its gap here until it has one.
