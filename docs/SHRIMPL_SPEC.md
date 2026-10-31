# Shrimpl: specification

Shrimpl is an Android app in which the user enters a website address and, entirely on the device,
gets an installable app for that site. Each generated app is a separate Android package (its own
process, storage, launcher entry and windows) and behaves like an installed PWA on Android desktops
(desktop windowing, Googlebook OS), tablets, foldables and phones.

This file is the baseline as of 2026-10-28. It describes what the project must be now; how it got
here is in `docs/history/`. Companion documents:

- [docs/TEMPLATE_SPEC.md](TEMPLATE_SPEC.md): what every web app does (requirements T1 to T8).
- [docs/ADAPTIVE.md](ADAPTIVE.md): how Google's adaptive skill and app quality guidelines apply,
  with the deliberate deviations.
- [docs/BUILD_NOTES.md](BUILD_NOTES.md): toolchain versions, platform findings and the deviations
  table for this spec.

## Terms

- **Shrimpl** (the manager): the app the user installs. Package `com.valghora.shrimpl`.
- **Template**: a prebuilt web app APK bundled inside Shrimpl, with all web app code and every
  capability any site may need.
- **Profile**: one site's settings (name, address, domains, colors, permissions, ...) as JSON.
- **Web app**: an APK Shrimpl produced by patching the template with a profile and icon, then
  signing it.
- **Build number**: a web app's version, starting at 1 and incremented on every rebuild.

## Reading this spec

- MUST is mandatory. SHOULD means do it unless there is a technical conflict, and record why in
  docs/BUILD_NOTES.md's deviations table if you don't.
- Precedence when instructions conflict: R0; then platform correctness (builds, installs and runs on
  API 37); then this spec and TEMPLATE_SPEC; then Google's guidance (docs/ADAPTIVE.md). Every
  deviation from the last two is written down.
- Non-goals: Play Store distribution, analytics, crash reporting, cloud sync or accounts, push
  delivery while a web app isn't running, foreground services, building web apps on a computer, and
  evading sites' WebView sign-in blocks.

## R0 Architecture neutrality (first and foremost)

Every APK this project produces MUST run natively on every Android CPU architecture, ARM and x86,
32 and 64 bit, with no translation layer (NativeBridge, Houdini, libndk_translation or any other
binary translation) on any device.

- R0.1 The template, and so every web app, contains **no native code**: zero `.so` files, no NDK,
  no JNI, no `abiFilters`, no ABI splits. Only dependencies without native code may be added to it.
- R0.2 Shrimpl adds no native code of its own. A third-party library that ships native code is
  allowed only if it ships it for all four ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`.
  Today that is one library, `libandroidx.graphics.path.so` from Compose's androidx.graphics:graphics-path,
  which is loaded unconditionally on Android and can't be excluded.
- R0.3 Building a web app on the device uses only Kotlin and Java code (apksig, the manifest
  patcher); no native tools such as aapt2 run on the device. Shrimpl MUST NOT bundle native
  executables.
- R0.4 Enforcement: `scripts/check_native_code.sh --none` on the template and `--all-abis` on both
  Shrimpl builds, in CI (R12.2), and the core test `webAppsHaveNoNativeCode` on every web app it
  builds.
- R0.5 The build host is not constrained by this rule (Android's aapt2 from Maven is x86_64 on
  Linux); only the APKs are.

## R1 Shrimpl

- R1.1 Form factors.
  - MUST work in phone portrait and landscape, on foldables, tablets, in desktop windows and on
    Googlebook OS: freely resizable, no orientation or aspect ratio limits, no `configChanges`,
    edge to edge, with keyboard and pointer support.
  - It follows docs/ADAPTIVE.md: layout from window size classes, state that survives configuration
    changes, 48 dp touch targets, tooltips on icon-only buttons, a right-click menu on web apps, and
    app-wide keyboard shortcuts (Ctrl+N new web app, Ctrl+, settings) listed in the Keyboard
    Shortcuts Helper.
  - When the window has room for two panes (expanded width, 840 dp and up), the home list sits
    beside a detail pane as rounded cards on the window background, separated by a gap with a drag
    handle that resizes them. The detail pane shows a web app's edit page, the configuration page
    (R3) or Shrimpl's settings (R2.5), each replacing the previous one, so the list is always one
    Back away. Settings has Close instead of a back arrow there; the edit page has neither. With
    nothing open, the detail pane says to pick a web app (or, with none yet, "Provide a website, get
    a custom app." with *Shrimpl as that!* in italics on its own line) and offers New app. Empty-state text is exactly centered in
    each pane, so the two panes line up. With one pane, "No apps yet" and that line are centered
    together as one block between the top bar and the top of the New app button.
- R1.2 Kotlin only. The single exception: discovery injects JavaScript into a hidden WebView to read
  what only exists in the rendered page (R9.3).
- R1.3 Material 3 in Jetpack Compose (latest stable, Expressive components where stable), dynamic
  color, light and dark themes. The status and navigation bars (including the 3-button bar) are
  transparent with no contrast scrim, so the screen's body color shows through, and their icons follow
  Shrimpl's own Theme setting. Navigation is Jetpack Navigation 3 with Material's list-detail scene
  strategy (`adaptive-navigation3`), not `ListDetailPaneScaffold`. Content descriptions on every
  icon, font scaling supported. The template stays on Views (T1.1).
- R1.4 Three install methods behind one interface (R6): System (the platform installer), Shizuku and
  Root.

## R2 Home

- R2.1 A top app bar titled with the shrimp glyph (in the theme's primary color) and "Shrimpl"
  (hides on scroll down, returns on scroll up) with a settings button, over an adaptive grid of
  the web apps Shrimpl created (one column on phones, more on wide windows). Each app is a
  borderless row on the page background; the settings items' rounded shape shows only as its press
  and hover highlight, and in two panes as the selection (secondary container): the installed icon
  (48 dp), the name, an Update button when a newer template is bundled (R2.2), and a more-options
  (three vertical dots) button (`Options for <name>`), lined up under the settings button,
  offering Open (when installed), Edit, Reinstall (when not installed), and Uninstall (or Remove
  when not installed). Build numbers are never shown. Tapping an item edits it, or opens the app,
  as the Default click action setting says (R2.5); one that isn't installed is always edited.
  Right-click or long press opens a menu with Open, Edit, Reinstall / Update / Rebuild, and
  Uninstall or Remove.
- R2.2 The list comes from Shrimpl's records (R11) joined with the installed state, re-read on
  resume. A record whose app isn't installed says "Not installed" under its name. An installed app
  built from an older template (R11.4) shows an Update button to the right of its name, which
  rebuilds it with the bundled template. An empty list says "No apps yet". User-facing text says
  "app", never "web app".
- R2.3 The edit page offers every profile setting in the form of R3.3 (the app ID read-only), the
  icon options (R10), Open, Rebuild, Discard changes, and Uninstall (confirmed; then offers to
  remove the record). "Save and update" rebuilds with the next build number and installs it as an
  update (same package and key, so the app keeps its data). In two panes the edit page has no back
  arrow.
- R2.4 "New app" opens the configuration page (R3). It is one design everywhere, shared by the
  other main-action buttons (Create, Save and update, Update all): a fully rounded filled button
  with an icon in the theme's primary color (Update all in its secondary container color); New
  app's tooltip names Ctrl+N. With one pane it sits at the bottom right of the list. With two
  panes it appears in exactly one place: in the empty detail pane, or at the bottom right of the
  list while the detail pane shows a web app's settings or Shrimpl's settings (not while it shows
  the configuration page). When two or more installed apps have a template update, "Update all"
  sits above it in the list, the two the same width: it rebuilds those apps one after another (a
  failure doesn't stop the rest; the build sheet shows "Updating <name> (i of n)…" and ends with
  what was updated and what failed). With the System install method it first explains that Android
  will ask to confirm each update, with Cancel.
- R2.5 Settings, from the top bar button or Ctrl+,:
  - **Appearance:** Theme: System (dynamic color from the wallpaper), Light or Dark.
  - **Installation:** Method (R6.2), and the Request Root / Request Shizuku item when one is chosen.
  - **Behavior:** Default click action: Edit (the default; "Edit the application on click") or Open
    ("Launch the application on click").
  - **Signing:** the certificate's SHA-256 fingerprint with a copy button, Export certificate and
    Import certificate (R5).
  - **About:** "App Version: x.y" and "Template Version: N.0".

## R3 Configuration page

- R3.1 Address: a URL field (URI keyboard, `https://` assumed without a scheme) and Search, also
  from the IME action. Invalid input shows an inline error.
- R3.2 Discovery (R9) shows its current stage (loading the page, reading the manifest, checking the
  rendered page, finding an icon) and can be cancelled. On failure: Try again, or "Continue without
  discovery" with defaults.
- R3.3 Review: a form prefilled from discovery, laid out like the system settings: titled sections of
  rounded tonal items, each showing its value (summary, switch, menu value or color swatch); text,
  color, domain and window size values open dialogs. Nothing is collapsed. The sections, in order:
  - **Identity:** the name, on the edit page the app ID, the start address and internal domains.
  - **Icon:** the icon options as preview tiles, then the selected option's settings (R10.1):
    Glyph color (`#FFFFFF` by default) and Background color (the site's brand color by default) for
    Brand, Fill icon canvas for a Website image, Background color for Custom.
  - **Appearance:** Header color (System, matching the system theme, the default; Dynamic, the
    site's own colors, T3.3; Custom) and Custom header color (disabled unless Custom).
  - **Behavior:** browser identity (user agent mode), keep running in the background, pause in
    battery saver, long-press menu for links and images.
  - **Window:** Allow multiple windows (off by default), default and minimum window size.
  - **Permissions:** microphone, camera, notifications, location (off by default).

  On two columns (R3.5), Identity, Icon and Appearance are on the left; Behavior, Window and
  Permissions on the right. Site keyboard shortcuts (`appShortcuts`) are not editable in the form;
  a profile keeps any it already has.

  Values discovered with low or medium confidence say where they came from; discovery's notes appear
  as Info, Caution or Error notices. Every field is validated as the user types, by the core
  validator (R4.2).
- R3.4 Create builds, signs and installs (R8, R6). A bottom sheet shows each stage (drawing the icon,
  building, signing, checking, installing) or the failure; on success, Open and Done (no build
  number: build numbers are internal, R11.3).
- R3.5 When the pane is expanded width (840 dp and up), the form uses two columns 40 dp apart,
  with notices across both above them so the first sections (Identity, Behavior) line up.

## R4 Modules and core

- R4.1 Three Gradle modules: `:core` (Kotlin JVM), `:template` (Android app, the web app) and
  `:shrimpl` (Android app, the manager). Both apps depend on `:core`; `:shrimpl` bundles the
  template's release APK (R8.1).
- R4.2 `:core` MUST NOT import `android.*`, `androidx.*` or `com.google.android.*`. It holds:
  - the profile model, JSON codec (kotlinx.serialization) and validator with field-level messages
    (domains, an https start address covered by the internal domains, colors, window sizes,
    shortcuts), and derived values (header darkness, automatic glyph color, allowed origin rules,
    notification channel ID, the session config);
  - `DesktopSessionController` (T2);
  - the discovery rules (R9.2), icon geometry and SVG glyph handling (R10);
  - the web app records (R11);
  - `ManifestPatcher`, `WebAppBuilder` and `SigningKeys` (R5, R8).
- R4.3 `:core` tests reach at least 85 % line coverage (JaCoCo, verified by `:core:check`); they
  include the patcher and builder run against the real template release APK, and discovery on stored
  fixtures of four real sites.
- R4.4 Dependencies, as in `gradle/libs.versions.toml`:
  - `:core`: kotlinx-serialization-json, apksig.
  - `:template`: T1.1.
  - `:shrimpl`: Compose BOM (ui, material3, material-icons-core), material3-adaptive (adaptive,
    adaptive-layout, adaptive-navigation3), Navigation 3 (runtime, ui), activity-compose, lifecycle
    (viewmodel-compose, runtime-compose, viewmodel-navigation3), androidx.webkit, the Shizuku API
    and provider.

  New runtime dependencies MUST satisfy R0 and be recorded in docs/BUILD_NOTES.md.

## R5 Signing key

- R5.1 On first use Shrimpl generates one RSA 3072 key with a self-signed certificate
  (CN=Shrimpl apps; keys made before 2026-10-29 keep CN=Shrimpl web apps), stored as PKCS12 in app-private storage; the file's random password is
  encrypted with an AES key in the Android Keystore.
- R5.2 Export certificate writes the PKCS12 through the Storage Access Framework, protected by a
  password the user types twice (at least 8 characters). Import certificate replaces the key after
  its password, and asks for confirmation when web apps exist and the imported certificate differs
  (they could no longer be updated).
- R5.3 After the first web app is created without a backup, a one-time prompt explains why to back
  up, with "Back up now" and "Later".
- R5.4 Settings shows the certificate's SHA-256 fingerprint, with a copy button.

## R6 Install methods

- R6.1 One `Installer` interface (install, uninstall, availability) with three implementations:
  - **Session:** `PackageInstaller` sessions; REQUEST_INSTALL_PACKAGES; first installs ask the user,
    updates don't need to (`USER_ACTION_NOT_REQUIRED`).
  - **Shizuku:** a Shizuku user service running `pm install -S` with the APK piped in; no prompts.
  - **Root:** `su -c pm install -S` with the APK piped in; `pm uninstall` for uninstalls.
- R6.2 Settings has one Method choice: System (default; every install asks for confirmation),
  Shizuku (silent, through ADB) or Root (silent, through superuser). Choosing Shizuku or Root shows a
  Request item ("Click to request", button Request). Success turns the button into Success; failure
  shows "Failed to acquire root" / "Failed to acquire Shizuku" and the button becomes Retry. If the
  chosen method fails at install time, Shrimpl says so and offers the system installer for this
  install.
- R6.3 The Session method needs "Install unknown apps"; if it's missing, the build sheet explains it,
  opens the setting and continues when the user returns.
- R6.4 Uninstall uses the selected method (Session: the system's uninstall dialog).

## R7 The template in Shrimpl

The template's behavior is docs/TEMPLATE_SPEC.md. What Shrimpl relies on:

- R7.1 The template reads its profile from the asset `shrimpl/profile.json` once at application
  start and fails loudly (a crash with a clear message) if it is missing or invalid.
- R7.2 Labels come from the patched manifest (application label) and the profile (task description,
  notification channel, the site's shortcut group).
- R7.3 The manifest declares the superset of permissions and features (T4.2); code for a disabled
  capability never requests its permission.
- R7.4 The launcher icon is an adaptive icon whose background, foreground and monochrome layers, and
  the notification icon, are PNGs at fixed paths (`res/drawable-nodpi-v4/...`), placeholders in the
  template. Resource path shortening is off (`android.enableResourceOptimizations=false`), checked by
  a core test.
- R7.5 `<layout>` sizes, the launch mode and the multi-instance property are manifest values the
  patcher rewrites.
- R7.6 The template's package is the placeholder `com.valghora.shrimpl.template`; its namespace is
  fixed at `com.valghora.shrimpl.webapp`, so classes keep their names in every web app.

## R8 Building a web app (on the device)

- R8.1 `:shrimpl` bundles the template's release APK, built in the same Gradle invocation, as
  `assets/template/template.apk`, with its version (R11.4).
- R8.2 Manifest patching (binary XML, in `:core`): package `com.valghora.shrimpl.app.<appId>` (and
  every string derived from the template package), the literal label, versionCode = build number,
  versionName = `<template version>.<build>` (for example `3.12`), removal of disabled permissions
  and features, launch mode, `<layout>` sizes and the multi-instance flag.
- R8.3 Assembly replaces `assets/shrimpl/profile.json` and the icon PNGs, keeps every other entry and
  its compression, drops old signatures, and signs with the Shrimpl key using apksig (v2 and v3 as
  apksig chooses for minSdk 37).
- R8.4 Before installing, verify the signature (`ApkVerifier`) and that the package and label read
  back from the patched manifest are the intended ones; fail with a clear message otherwise.
- R8.5 Host tests in `:core`, against the real template release APK: package rename, label,
  versions, permission stripping, window values, profile and icon replacement, signature, stable icon
  paths, no native code (R0), and `aapt2 dump badging` when the SDK is present.
- R8.6 Building runs off the main thread, can be cancelled before install, and reports its stages.

## R9 Discovery (on the device)

- R9.1 Input: a URL. Output: a draft profile in which every field carries a source and a confidence
  (high, medium, low), plus notes with a level (info, caution, error).
- R9.2 Static pass (rules in `:core`, network in `:shrimpl`): fetch the page following redirects and
  read the HTML head (title, `application-name`, `apple-mobile-web-app-title`, `og:site_name`,
  `theme-color` with media, manifest link, `mask-icon`, icon links, `apple-touch-icon`) and the web
  manifest (name, short_name, start_url, theme_color, background_color, icons). Rules:
  - name priority (manifest short_name and name, then the head's names, then a cleaned title);
  - start address: the manifest's start_url on the same site, else the requested address; an
    auth redirect keeps the requested path;
  - internal domains: the registrable domain from the bundled Public Suffix List;
  - simple-icons by name, not bundled: the slugs of the site's own names, then the domain word, are
    looked up at `https://cdn.simpleicons.org/<slug>` (at most 4 requests);
  - icon priority: simple-icons glyph, mask-icon, maskable manifest icon, the largest icon, letter;
  - icon background = the brand color: the simple-icons color, else the first colorful value among
    the mask-icon color, manifest theme_color, theme-color meta, CSS brand variables (R9.3), button
    colors and manifest background_color, else any non-white theme color, else blue gray;
  - no header color is suggested (web apps default to System);
  - notes for pages that refuse WebViews and for Play Store redirects.
- R9.3 Rendered pass (SHOULD), in a hidden WebView with desktop (light and dark) and phone viewports:
  CSS custom properties named brand, primary or accent (ranked in `:core`), visible button colors,
  and use of getUserMedia, Notification, Push, WebRTC and geolocation (suggesting microphone,
  camera, notifications and location). At most 20 s in total; on timeout the static result stands.
- R9.4 Network: HTTPS only (http is upgraded), 15 s per request, 5 MB per response (scripts have a
  20 MB budget), no cookies kept.

## R10 Icons

- R10.1 Options, shown as a row of preview tiles labeled Brand, Website and Custom; tapping one
  selects it and rings it (no other mark):
  - **Brand** (the default when found): the simple-icons glyph (or the site's mask-icon glyph) drawn
    in the Glyph color on the Background color. Not shown when the site has no brand glyph.
  - **Website:** the site's own icon, the best square web manifest icon, apple-touch-icon or icon
    link of at least 192 px (R9.2); a letter on the background color when the site has none.
    Selected when there is no brand glyph.
  - **Custom:** a dashed tile with a plus until an icon is picked. Tapping it (empty, or again once
    selected) opens a sheet of sources: Photos (the Photo Picker) and Files (any image), cropped to
    a centered square and filling the icon shape, and every installed icon pack (R10.2).

  Under the tiles, the selected option's settings: Glyph color and Background color for Brand;
  Fill icon canvas for a Website image (a switch, on for maskable manifest icons, that enlarges the
  image to fill the whole icon shape instead of keeping it inside the safe zone); Background color
  for Custom (behind and around the picked image). Changing the name updates a letter Website icon.
- R10.2 Icon packs: installed apps answering the launchers' `org.adw.launcher.icons.ACTION_PICK_ICON`
  request, shown with their own app icon and name. Choosing one opens the pack's own icon picker,
  which hands back a bitmap (or an image URI); the icon keeps its own shape. Packs that don't answer
  that request aren't listed.
- R10.3 Rendering: adaptive background and foreground layers at 432x432 px (foreground inside the
  66 % safe zone, or 80 % for maskable images), a monochrome layer (the glyph or letter; a letter for
  images), and a 96x96 px white notification icon.

## R11 Data and versions

- R11.1 A record per web app (JSON in app-private storage, written atomically): the profile, the
  icon options (brand glyph, website icon, custom icon, which is selected; images as centered
  square PNGs of at most 512 px next to the record, unused ones deleted on save) and the icon the
  last build used, the package name, the build number, the template version it was built with, and
  created and updated times. Older profiles and records are upgraded when read.
- R11.2 The app ID comes from the name (lowercase letters and digits, starting with a letter),
  deduplicated with a numeric suffix, and is fixed at creation.
- R11.3 Rebuilds increment the build number; a failed install doesn't use one up.
- R11.4 The template version is one integer, `shrimpl.templateVersion` in `gradle.properties`, which
  is also the template's versionCode. It MUST be incremented whenever template code or resources
  change. Records built with an older version show "Update available"; Update rebuilds with the
  bundled template.
- R11.5 Shrimpl never reads or modifies web apps' private data.

## R12 Repository, CI and releases

- R12.1 The repository builds exactly two APKs, the template and Shrimpl, from `:core`, `:template`
  and `:shrimpl`. Developer scripts: `scripts/setup_android_sdk.sh` (SDK and tool check),
  `scripts/check_native_code.sh` (R0), and `scripts/dev/` (placeholder icons, Public Suffix List
  refresh).
- R12.2 CI (`.github/workflows/android.yml`) on every push and pull request runs `:core:check`
  (tests and coverage), the template and Shrimpl unit tests, lint for both apps (0 errors), the
  template release build, both Shrimpl builds and the R0 check, and uploads the APKs and reports.
- R12.3 Shrimpl release builds use R8 and are signed with the debug key, for personal sideloading.

## Rules for every change

Do not use lint baselines, disabled lint checks, `@SuppressLint` (except `SetJavaScriptEnabled` on
the template's `applyWebSettings`), Kotlin `@Suppress`, lowered coverage thresholds, deleted or
weakened tests, or site values hard-coded in template code. If a check still fails after three fix
attempts, stop and report it with the log.

## Known limitations

- Shizuku and Root installs are verified only on the product owner's device.
- Google's developer verification for sideloaded apps may block or warn about installs on certified
  devices.
- The WebView limitations in docs/TEMPLATE_SPEC.md (sign-in blocks, no background push).
- Losing the signing key without a backup makes web apps impossible to update.
- There is no emulator in the cloud build container, so UI changes are verified on devices.
