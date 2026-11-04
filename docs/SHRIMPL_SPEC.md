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
    beside a detail pane as rounded cards on the window background, 24 dp apart: the list's width
    follows Material's window size classes: in an expanded window (840 to 1199 dp) Material's 360 dp
    list pane, three tiles across (R2.6), and from a large window (1200 dp) just wide enough for four
    tiles. The detail takes the rest, so an app's settings always get at least a phone's width. The
    panes can't be resized, and resizing the window keeps what each pane shows and any unsaved
    changes. On a separating hinge (a dual-screen
    phone, a foldable half-open like a book) the panes split along the hinge, one on each side. The
    detail pane shows a web app's edit page, the configuration page
    (R3) or Shrimpl's settings (R2.5), each replacing the previous one, so the list is always one
    Back away: Back closes what the detail pane shows and leaves the empty detail pane, it doesn't
    leave Shrimpl. Every page in the detail pane has a back arrow, which closes it (R3.5). With
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
- R1.5 Shapes: rounded corners from Material's shape scale, one size per role, all defined in one
  place (`ui/theme/Shapes.kt`, which also sets Material's theme shapes so dialogs, sheets, chips,
  tooltips and snackbars follow):
  - Input, 12 dp: text fields. Search, fully round: the website field on Create (R3.1).
  - Control, fully round: buttons with a container (New app, Create, Save, Update all, Fix all, row
    buttons, the search button, buttons in sheets and settings). Chips take Material's 8 dp.
  - Item, 20 dp outside and 4 dp where grouped items meet: home rows and tiles, settings groups.
  - Notice: a one-line notice is a pill at any text size; a taller one has 28 dp corners (R3.3).
  - Menu, 16 dp, with 12 dp for an item's highlight inside it (also the press highlight of icon
    option tiles and custom icon sources).
  - Pane, 16 dp: the two-pane cards, tighter than sheets and dialogs so the desktop layout stays crisp.
  - Surface, 28 dp: bottom sheets and dialogs.
  - Round: app icons, badges, color swatches, progress marks and icon-only buttons without a
    container.

## R2 Home

- R2.1 A top app bar in the settings pages' large-title style (R3.5): the shrimp glyph (in the
  theme's primary color) and the title "Apps" in large type on one line, with a layout switch ("Show as
  tiles" / "Show as list", R2.6) and a settings button at its end. With one pane the title shrinks
  to the usual bar size as the list scrolls; beside the detail pane the bar stays put. It sits over an adaptive grid of
  the web apps Shrimpl created (one column on phones, more on wide windows). Each app is a
  borderless row on the page background; the settings items' rounded shape shows only as its press
  and hover highlight, and in two panes as the selection (the hover tint, so a tonal button on it stays visible): the installed icon
  (48 dp), the name, an Update button when a newer template is bundled (R2.2), and a more-options
  (three vertical dots) button (`Options for <name>`), lined up under the settings button,
  offering Open (when installed), Edit, Reinstall (when not installed), and Uninstall (or Remove
  when not installed). Build numbers are never shown. Tapping an item edits it, or opens the app,
  as the Default click action setting says (R2.5); one that isn't installed is always edited.
  Right-click or long press opens a menu with Open, Edit, Reinstall / Update / Rebuild, and
  Uninstall or Remove.
- R2.2 The list comes from Shrimpl's records (R11) joined with the installed state, re-read on
  resume. A record whose app isn't installed sits at the bottom of the list under a "Not installed"
  label (each group in name order), its icon and name greyed out (65 % opacity, so the name keeps
  4.5:1 contrast in both themes); tapping it does nothing (Resolve on tap doesn't apply to it, R2.5), and accessibility services aren't offered a
  tap, but its ⋮ menu, long press ("Show options"), right-click menu work as usual. It has no row button: Reinstall, in its menus, builds it signed with the current key whatever key it had (R11.7). An installed app built from
  an older template (R11.4) shows an Update button to the right of its name, which rebuilds it with
  the bundled template. Row buttons are small icon-only buttons (48 x 32 dp, Control corners, a 48 dp touch target)
  whose tooltip names the action and the app: Update (refresh icon) in the theme's tonal color, Fix
  (wrench, R11.7) in the theme's error color, matching a tile's Needs fix badge (R2.6). Each app's ⋮
  menu and its right-click and long-press menu offer the same actions: Open (when installed), Edit,
  then Update (an update is available), Reinstall (not installed) or Rebuild, then Uninstall or
  Remove. An empty list says "No apps yet". User-facing text says "app", never "web app".
- R2.3 The edit page offers every profile setting in the form of R3.3 (the app ID is not shown) and the
  icon options (R10); its ⋮ menu offers Open (when installed), Rebuild (Reinstall when not
  installed) and Uninstall (confirmed; then offers to remove the record), or Remove. Once something
  is changed, a Save button rebuilds with the next build number and installs it as an update (same
  package and key, so the app keeps its data). Changes not saved are discarded on leaving the page;
  recreating the window (rotation, resize) keeps them. Its back arrow leaves the page, in two panes too.
- R2.4 "New app" opens the configuration page (R3), titled "Create app". It is one design everywhere, shared by the
  other main-action buttons (Create, Save, Update all): a filled button with Control corners (R1.5)
  with an icon in the theme's primary color (Update all in its secondary container color); New
  app's tooltip names Ctrl+N. With one pane it sits at the bottom right of the list. With two
  panes it appears in exactly one place: in the empty detail pane, or at the bottom right of the
  list while the detail pane shows a web app's settings or Shrimpl's settings (not while it shows
  the configuration page). When two or more installed apps have a template update, "Update all"
  sits above it in the list, the two the same width: it rebuilds those apps one after another (a
  failure doesn't stop the rest; the build sheet shows "Updating <name> (i of n)…" and ends with
  what was updated and what failed). With the System install method it first asks: "Android will ask
  you for permission to perform each update. To update your apps without prompts, switch your
  installation method to Shizuku or Root via Settings.", with Cancel and Update all.
- R2.5 Settings, from the top bar button or Ctrl+,:
  - **Appearance:** Theme: System (dynamic color from the wallpaper), Light or Dark.
  - **Installation:** Method (R6.2), and the Request Root / Request Shizuku item when one is chosen.
  - **Behavior:** Default click action: Edit (the default; "Edit the application on click") or Open
    ("Launch the application on click"). Resolve on tap (a switch, off by default; "Override default tap
    preference and automatically perform pending actions" / "Always respect default tap
    preference"): on, tapping an installed app with a pending action (the action its row button
    offers: Fix or Update, R2.2, R11.7) does that action instead of the default click action, in
    rows and tiles, and accessibility services hear the tap named after it ("Fix Example"); off,
    the default click action applies (and an app signed with another key still does nothing on a
    tap). An app that isn't installed ignores the switch: tapping it does nothing, and Reinstall stays
    in its menus.
  - **Signing:** the certificate's SHA-256 fingerprint with a copy button, "Export…" and "Import…"
    (export and import the certificate, R5).
  - **About:** "App Version: x.y" and "Template Version: N.0".
- R2.6 Tiles: the layout switch shows the apps as tiles instead of rows, and the choice is kept.
  Tiles flow three across on phones (at least 100 dp wide, 8 dp apart, 12 dp in from the sides),
  more on wide windows. Each tile is a card with Item corners (R1.5) in the theme's container color,
  with no tint or color of its own: the icon (56 dp), the name, and a status line in the theme's
  variant text color: "Needs fix" (R11.7), "Update ready" (R11.4) or "Not installed". A round badge
  on the icon's corner, ringed in the tile's color, marks an app that needs something: a wrench in
  the theme's error color (Needs fix) or a refresh arrow in its primary color (Update ready); it
  repeats the status line, so accessibility services skip it. Uninstalled apps follow the installed
  ones in the same grid, greyed out as in rows, with no label. Tiles have no ⋮ button or row
  buttons: tapping one does what tapping a row does, and right-click or long press opens the
  row's menu, which for an app signed with another key offers Fix before Remove. In two panes the
  selected tile takes the highest container color.

## R3 Configuration page

- R3.1 Address: a Website field (URI keyboard, `https://` assumed without a scheme) with a search
  button beside it, also from the IME action. Text fields everywhere are filled, with Input corners (the website field a Search pill, R1.5)
  and no outline; typing draws a primary edge, an error a red one with the message below. The search
  button is a circle of the field's height, in the tonal container color. Input that isn't a web
  address shows an error under the field, before any search.
- R3.2 Discovery (R9) shows a checklist under "Searching…": Loading the site, Consulting the
  manifest, Researching permissions, Searching for icon. Finished steps get a check, the current one
  a spinner (as in the build sheet), the others an empty circle; each step shows for at least 0.5 s.
  Cancel sits at the end. Only loading the site can fail, with a plain message under the step: 404
  "The site could not be found"; other 4xx "The site responded with an error"; 5xx "The site is
  unavailable"; no connection "No internet connection"; an unknown host also "The site could not be
  found"; no answer in 15 s "The site took too long to respond"; a certificate problem "The site's
  certificate is invalid"; anything else "The site couldn't be loaded". A 403 continues to the
  rendered pass, since bot protection often refuses plain requests but lets a browser in; only if
  the rendered page is refused too does Researching permissions fail with "The site denied the
  request". On failure the heading reads "Search failed"; the search button beside the address
  becomes Retry (a refresh icon) until the address is edited, and a Continue button (defaults for the
  address) floats at the bottom right.
- R3.3 Review: a form prefilled from discovery, laid out like the system settings: titled sections
  of tonal items 3 dp apart, each group with Item corners (R1.5): 20 dp on the outside and 4 dp where
  items meet: the first item rounded at the top, the last at the bottom, a lone item all round (Settings,
  R2.5, uses the same sections); each showing its value (summary, switch, menu value or color
  swatch); text, color, domain and window size values open dialogs. Each section has one wrap line
  for its items' text (description, error, discovery hint): 24 dp before the widest value, switch or
  chevron at the end of any of its items, so no description runs up against one and the section's
  text has one right edge. The line is measured, not fixed, so a longer value or a larger font moves
  it; the icon options are not items and are exempt. Nothing is collapsed. The
  sections, in order:
  - **Identity:** the name, Address (the start address) and Domains (the internal domains).
  - **Icon:** the icon options as preview tiles, then the selected option's settings (R10.1):
    Glyph color (`#FFFFFF` by default) and Background color (the site's brand color by default) for
    Brand, Fill icon canvas for a Website image, Background color for Custom.
  - **Appearance:** Header color (System, matching the system theme, the default; Dynamic, the
    site's own colors, T3.3; Custom) and Custom header color (disabled unless Custom).
  - **Behavior:** User agent (Auto, Desktop or Mobile, its description following the choice); Menu
    interaction behavior (Shrimpl: a long press or right-click on a link or image opens Shrimpl's
    menu to copy, open or save, T5.5; Site: the site's own menu); Run in background; Pause in
    battery saver.
  - **Window:** Allow multiple windows (off by default), default and minimum window size.
  - **Permissions:** microphone, camera, notifications, location (off by default).

  Site keyboard shortcuts (`appShortcuts`) are not editable in the form;
  a profile keeps any it already has.

  Values discovered with low or medium confidence say where they came from; discovery's notes appear
  as Info, Caution or Error notices. Notices have Notice corners (R1.5), so one-line notices are pills. Every dismissable notice has a Dismiss button (never an X)
  and is also dismissed by swiping it left or right. A one-paragraph notice with no other button
  that fits on one line beside Dismiss keeps Dismiss on that line, so short notices stay one line
  tall; otherwise the buttons sit in a row under the text, their labels 12 dp below it and 12 dp
  above the card's bottom, Dismiss at the row's end. On a notice narrower than 480 dp another button
  (such as Open settings) sits under the start of the text; from 480 dp it sits beside Dismiss,
  16 dp apart, so the buttons stay together on wide cards. Every field is validated as the user types, by the core
  validator (R4.2).
- R3.4 Create builds, signs and installs (R8, R6). A bottom sheet titled "Creating an app for
  <name>…" ("Updating <name>…" for an update, "Reinstalling <name>…" for a known app that isn't
  installed, with "(n of m)" during Update all and Reinstall all) shows each stage (drawing,
  building, signing, checking, installing; each on screen for at least 0.5 s) or the failure; on
  success, Open and Done (no build number: build numbers are internal, R11.3); a new app from the
  Create app page instead drops the user back on the app list (after the one-time backup prompt,
  R5.3).
- R3.5 The form is one column, and Shrimpl's Settings (R2.5) uses the same column and top bar from
  the same code, so the pages always match. Beside the list (two panes), the column is at most 680 dp
  wide and centred; on a page with a floating button (Save, Create), if centring would put the column
  under the button at the bottom right, it moves left as far as needed (at least 24 dp from the left
  edge), so the button never covers an option. With one pane (a phone, or any window too narrow for
  two panes), or in a pane too narrow for a 360 dp column, the form takes the full width and the
  button floats over it, with room below the last section.
  The top bar holds a back arrow and the page's title (the app's name, Settings, or Create app) in
  large type on one line, with the edit page's ⋮ at the end. With one pane the arrow starts the bar
  and, as the page scrolls, the title shrinks to the usual size and the bar takes the container color.
  Beside the list the bar stays put while the page scrolls under it, with no divider: the arrow's glyph
  starts where the section labels do, the title follows it, and ⋮ sits over the items' trailing
  chevrons. There the arrow closes the detail pane.

## R4 Modules

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
- R4.3 `:core` tests reach at least 95 % line coverage (JaCoCo, verified by `:core:check`); they
  include the patcher and builder run against the real template release APK, and discovery on stored
  fixtures of three real sites and a made-up one.
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
- R6.5 With the Session method, the install waits for the system's confirmation. Turning it down
  ends the build quietly: the sheet closes and nothing is saved (Update all stops, listing what was
  updated). If the user leaves the confirmation unfinished and comes back to Shrimpl, the sheet says
  Android is waiting and offers Show installer (opens the confirmation again) and Cancel (abandons
  the install session, so it can't finish later without a record).

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
- R11.6 Each built web app carries what is needed to list it again: its profile, and its icon
  options as `assets/shrimpl/icons.json` with their images beside it (from template version 7). On
  its first start with no records (after a reinstall, say), Shrimpl reads every installed
  `com.valghora.shrimpl.app.*` APK and rebuilds its record: profile, icon options, build number
  (versionCode), template version (versionName) and signer. Apps built before icons.json come back
  with their drawn launcher icon as a Custom icon. This search runs once, so apps removed from the
  list stay removed. Apps signed with another key are handled by R11.7.
- R11.7 An app last signed with a key other than Shrimpl's current one keeps its record either way.
  Installed, its row offers only Fix (an icon-only button with a wrench in the error color, R2.2), tapping the row
  does nothing (it fixes the app with Resolve on tap, R2.5), and its menus offer only Remove (a tile's menu offers Fix, then Remove, R2.6);
  Update all skips it. Fix uninstalls the app (the
  system asks), then rebuilds it from the saved settings signed with the current key (the system
  asks again); the build sheet adds an Uninstalling step before the build's stages, and turning the
  uninstall down stops with nothing changed. With two or more such apps, Fix all (a button above New
  app, like Update all) fixes them one after another and ends with what was fixed and what failed.
  Not installed (say, its Fix stopped after the uninstall), the key no longer matters: it is shown
  and reinstalled like any app that isn't installed (R2.2). Home shows a Caution notice
  explaining both ways out (import the certificate backup, R5.2, or Fix, keeping the settings but
  not the site data), with Open settings at the start and Dismiss at the end (or a swipe, R3.3). Dismiss hides it for
  the apps it covered; another such app shows it again. Only installed apps count for the notice
  and Fix all.

## R12 Repository, CI and releases

- R12.1 The repository builds exactly two APKs, the template and Shrimpl, from `:core`, `:template`
  and `:shrimpl`. Developer scripts: `scripts/setup_android_sdk.sh` (SDK and tool check),
  `scripts/check_native_code.sh` (R0), and `scripts/dev/` (placeholder icons, Public Suffix List
  refresh).
- R12.2 CI (`.github/workflows/android.yml`) on every push and pull request runs two jobs. Checks
  (no Gradle, seconds) runs the comment check and asks whether this commit already passed the
  workflow, as it has when main is fast-forwarded to a tested branch; if so, Build is skipped.
  Build, one step per stage, runs Core (`:core:check`, tests and coverage), Template (unit tests,
  release lint, release build), Shrimpl tests, Shrimpl lint (release lint, which covers all its code)
  and Shrimpl APK (debug), then the R0 check, and uploads the APKs and reports, kept for one day. Shrimpl is tested
  as a debug build: its release APK is built, and R0-checked, only in a manual run with Release
  build ticked. The template's release APK is always built, since every Shrimpl build bundles it.
  Lint for both apps reports 0 errors. Release builds skip lint vital, a subset of the release lint
  already run on the same variant.
- R12.3 Shrimpl release builds use R8 and are signed with the debug key, for personal sideloading.

## R13 Test tags (debug builds only)

Debug builds can put the app list and the search into states that are hard to set up for real.
Release builds never read tags or test addresses.

- R13.1 An app whose name starts with a comma-separated list of tags, such as "update,cert Example",
  "update, cert Example", "[update,cert] Example" or just "update,cert", shows the states they name;
  every part must be a known tag, so an ordinary first word is never read as one. The tags override
  what Home works out for each row (installed, update available, signed with another key) in one
  place, before the usual rules, so the list shows only states real users can reach: update,cert
  shows Fix, as a real mismatch would. Nothing is stored.
  - `update`: Update is offered (R2.2), and the app counts for Update all.
  - `cert`: signed with another key (R11.7): Fix, the Remove-only menu, the notice and Fix all
    (on an installed app; with `missing`, it changes nothing).
  - `missing`: shown as not installed.
- R13.2 A build of an existing app whose name has tags and keeps them plays back: the sheet goes
  through every stage (and Uninstalling for Fix) at 0.5 s each without uninstalling, building,
  installing or saving, then ends as installed, or as its tags say:
  - `fail`: fails at installing.
  - `cancel`: the install is turned down (R6.5).
  - `wait`: waits for confirmation until Cancel (R6.5).
  - `unknown-sources`: asks for "Install unknown apps" (R6.3).
  Creating an app, or saving one with its tags removed, builds for real.
- R13.3 Searching for a test address goes through the steps up to its failing one without going
  online, then fails as a real search would (R3.2): 404.test, 400.test, 500.test, offline.test,
  nohost.test, timeout.test, certificate.test and unloaded.test fail at Loading the site; 403.test
  fails at Researching permissions. The .test domain is reserved and never resolves.

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
