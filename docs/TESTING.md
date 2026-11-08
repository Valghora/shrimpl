# Testing

What is tested, where it runs, and what is still left to the product owner's devices. Keep this in
step with the tests: a feature that gets a new kind of check updates the table below in the same
commit. Build details (versions, quirks, workarounds) are in [BUILD_NOTES.md](BUILD_NOTES.md).

## The layers

| Layer | Where it runs | When | What it is good at |
|---|---|---|---|
| `:core` unit tests (JUnit, JaCoCo, 95 % line coverage) | JVM, in CI and the cloud container | every push | Pure logic: profiles, the manifest patcher and web app builder (against the real template APK), discovery rules, icons, the page download, file access, share and camera-choice protocols |
| Shrimpl Robolectric tests (`shrimpl/src/test`) | JVM, in CI and the cloud container | every push | Compose layout at the nine window sizes, what a state shows, simple interactions, state surviving recreation |
| Template unit test (`template/src/test`) | JVM | every push | The profile asset |
| Static checks | CI | every push | Lint (0 errors), no native code (R0), 16 KB page alignment, comment references |
| **Emulator tests** (`src/androidTest` in `:template` and `:shrimpl`) | A real Android 37 system image (x86_64, KVM) in the Emulator workflow | every push to a branch other than `main` | What only a real system does: the real WebView and its bridges, real installs, real accessibility checks, real window manager |
| The owner's devices | A Dell XPS Googlebook and others | when the owner tries a build | Everything in the last section |

Robolectric runs in the cloud container; the emulator does not, and cannot: the container has no KVM
(no `/dev/kvm`, no virtualization flags in the CPU) and Android's emulator can't run ARM images on an x86
host. GitHub's Linux runners do expose KVM, so the emulator runs there (BUILD_NOTES, "Emulator").

## The Emulator workflow

`.github/workflows/emulator.yml`, on every push except to `main` (it only fast-forwards to commits that
already passed here) and except pushes that change only `docs/` or Markdown files, and by hand. It builds the APKs and test APKs first, stops Gradle, boots the
emulator (`scripts/start_emulator.sh`), runs `connectedDebugAndroidTest` for both modules in one Gradle run, and uploads
`instrumented-results`: the JUnit XML, `emulator-out/logcat.txt`, a screenshot, the UI hierarchy, and the
screenshots and hierarchy dumps that failed tests saved (`emulator-out/evidence/`, and in logcat under
the tag `SHRIMPL_EVIDENCE`). A fast-forward merge to `main` needs this workflow green on the branch's
last commit that changed anything outside docs, as well as the Android workflow.

Writing an emulator test:

- **The template's WebView.** `PageDriver` (template `androidTest`) launches the real `MainActivity` and
  loads a test page at the neutral template's own origin (`https://example.com`), so the bridges' document
  scripts and message listeners apply as they do for a real site, then runs JavaScript in it. Android's
  dialogs (share sheet, file pickers) are answered with Espresso-Intents stubs, so the test checks what
  the template asked for and what it did with the answer.
- **Shrimpl.** `createAndroidComposeRule<MainActivity>()` for screens; `UiAutomator` for what is outside
  the app (the system installer's Install button). Call `Evidence.capture("name")` when a flow fails, so
  the run keeps a screenshot and the window hierarchy.
- **Folders.** `template/src/debug` holds `TestTreeProvider`, a document provider over a folder in the
  cache, so a test can hand the template a folder as if the user had picked it. It is in the debug build
  only: the release APK Shrimpl bundles never has it.
- **Display sizes.** `WindowSizesTest` resizes the display with `wm size` and `wm density` and always
  resets them, so other tests see the emulator's own screen.
- **System dialogs.** `InstallRobot` answers the installer's Install, Update and Uninstall screens.
- **Memory.** The runner has 7 GB and the emulator 4: don't add steps that build while it runs.

## What the emulator tests cover

| Requirement | Test | Before |
|---|---|---|
| T6.15 page-made downloads (blob, data, 3 MB, safe names) | `PageDownloadsTest` | by hand |
| T6.16 Web Share API | `ShareBridgeTest` | by hand |
| T6.17 single-file Open and Save, write commands | `FileAccessTest` | by hand |
| T6.18 folders: list, read, create, write, remove, rename, move, the standard's errors, the stale-handle limit | `FolderAccessTest` (against the debug build's `TestTreeProvider`) | by hand |
| R2.1 Shrimpl starts on a real system | `HomeLaunchTest` | by hand |
| R6, R8, R11.3: build, sign, install through the system installer, launch, see the site | `BuildAndInstallTest` | by hand |
| R6.1, R11.4: an installed web app updates in place (the test taps Update) | `BuildAndInstallTest` | by hand |
| R11.7: Fix replaces an app signed with another key (uninstall, rebuild with the new key; the test answers both system dialogs) | `FixFlowTest` | by hand |
| R1.1, R2.6: two panes and four tiles at full screen on the Dell XPS (2560 x 1600) and on the 2880 x 1800 Googlebooks (Acer, ASUS, HP, Lenovo) at both guessed scales, two panes and three on a common 1536 dp laptop (also the HP's 1920 x 1200 option), one pane and three on a phone, on the real window manager | `WindowSizesTest` | by hand |
| T2a, T6.6: a link to another site opens in the browser | `ExternalLinksTest` | by hand |
| Accessibility (touch targets, contrast, labels) on the real screens, with a control that must fail | `AccessibilityChecksTest`, `AccessibilityChecksAreLiveTest` | TalkBack only |
| 16 KB page alignment of every APK | `scripts/check_page_alignment.sh` (Android workflow) | not checked |

## Still the owner's devices

These need hardware, a person, or a service an emulator doesn't have:

- **Shizuku and Root installs** (R6): no Shizuku on an emulator; `su` isn't available to apps on its images.
- **Hinges and foldable postures**, and **window snapping** on a Googlebook (docs/GOOGLEBOOKS.md): the
  emulator's windowing is not Googlebook OS's. Layout at the right widths is covered by Robolectric.
- **TalkBack** itself (reading order, announcements), and how haptics, animation and scrolling *feel*.
- **Sign-in flows** that block embedded WebViews (Google, others), and background push (not possible).
- **Cameras, microphones and location** with real hardware (the emulator has virtual ones).
- **Performance** on real silicon: cold start, a long home list.
- **How it looks**: the owner is the only judge of spacing, color and polish. A mock-up comes first.

## Not covered yet, and could be

Each is feasible on the emulator and not written yet; the order is by how often it has cost a manual check.

1. The file chooser's camera options (T6.4), which need a real tap on the page to count as a user gesture.
2. Pop-up windows in the real WebView (T6.6).
3. Dialog drafts surviving activity recreation (BUILD_NOTES, "Robolectric").
4. Notifications, permissions (camera, location) through the template's flow, full-screen video, printing.
5. Window snapping: `wm size` gives the right widths, but not a half-screen window (R1.1's sharing rule).
6. Folder access against Android's real storage provider and picker (the tests use a fixture provider).
