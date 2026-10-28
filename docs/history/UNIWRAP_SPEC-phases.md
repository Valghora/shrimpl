> **Historical.** The phased UniWrap specification (Phases 0 to 7), replaced by the baseline in docs/UNIWRAP_SPEC.md on 2026-10-28.

# UniWrap: specification

<role>

You are a senior Android engineer building UniWrap: an Android app in which the user enters a website URL and, entirely on the device, gets an installable app for that site. Each generated app is a separate Android package (own process, storage, launcher entry and windows) that behaves like an installed PWA on Android desktops (desktop windowing, Googlebook OS), tablets and phones. You work on your own in a terminal with file-system and network access.

</role>

<terms>

- **UniWrap** (the manager): the app the user installs and opens. Package `com.valghora.uniwrap`, launcher name "UniWrap".
- **Template**: a prebuilt web app APK bundled inside UniWrap. It contains all web-app code and declares every capability any site may need.
- **Profile**: one site's settings (name, URL, domains, colors, permissions, ...) as JSON.
- **Web app**: an APK UniWrap produced by patching the template with a profile and icon, then signing it.
- **Build number**: a web app's internal version, starting at 1 and incremented on every rebuild.

</terms>

<how_to_read_this_spec>

- Requirements have IDs. MUST = mandatory. SHOULD = do it unless there is a technical conflict, and record why in the Deviations table if you don't.
- R1 to R3 restate the product owner's requirements (quoted) and expand them. R4 onward are the technical requirements that follow from them.
- `docs/PROMPT.md` is the previous specification (one Gradle build per site). Its requirements R1 to R8 remain the baseline for the web app's behavior; R7 below lists exactly what changes. Its computer-side tooling (R9 Python icons, R10 site tooling) is retired.
- Follow the Execution Plan in order; each phase has a gate.

</how_to_read_this_spec>

<precedence>

If two instructions conflict, the higher item wins:
1. Platform correctness: everything builds, installs and runs on API 37.
2. Requirements in this spec.
3. Inherited requirements from `docs/PROMPT.md` R1 to R8.
4. Researched Google guidance (Material 3, large screens, desktop windowing).
Every deviation from items 2 to 4 MUST appear in the Deviations table with the reason.

</precedence>

<non_goals>

Do not implement: Play Store distribution, analytics, crash reporting, cloud sync or accounts, web push delivery while a web app is not running, foreground services, building APKs on a computer (the `./wrap` flow is retired, R12), or evading sites' WebView sign-in blocks.

</non_goals>

<requirements>

## R1 Main application (UniWrap)

> R1.1 Should inherit all applicable requirements from the original prompt where applicable (Architecture, pure-JVM core, full compatibility with Android Desktop, GooglebookOS, and mobile). R1.2 MUST be fully native Kotlin code unless technically impractical limitations are present. R1.3 MUST fully adhere to the latest Material3 UI/UX specification. R1.4 Must support the following installation methods: Session, Shizuku, Root.

- R1.1 Inheritance.
  - The pure-JVM rule (`docs/PROMPT.md` R2.1) applies to the `core` module (R4): no `android.*`, `androidx.*` or `com.google.android.*` imports, unit tested, line coverage >= 0.85.
  - UniWrap MUST work in phone portrait and landscape, tablets, desktop windowing and Googlebook OS. It must be freely resizable with no orientation locks (`docs/PROMPT.md` R4.1), handle keyboard and pointer input, and use edge-to-edge insets.
  - At window widths of 840dp and above, the home page and an app's edit page MUST appear side by side (list-detail).
- R1.2 Native Kotlin. All UniWrap code is Kotlin. The single technical exception: discovery MAY inject JavaScript into a hidden WebView to read signals that only exist in the rendered page (R9.3).
- R1.3 Material 3. The UI MUST be Jetpack Compose with the latest stable Compose Material 3, including the Expressive components where stable.
  - Dynamic color with a fallback scheme.
  - Light and dark themes following the system.
  - Material 3 adaptive layouts (`material3-adaptive` list-detail scaffold, navigation suite).
  - Touch targets of at least 48dp, content descriptions on icons, and support for font scaling.
  - The web apps stay on AndroidX Views (`docs/PROMPT.md` R1.1) to keep every generated APK small; the no-Compose rule applies only to the template.
- R1.4 Install methods. UniWrap MUST support three, behind one interface (R6): Session (the system installer), Shizuku and Root.

## R2 Home page

> R2.1 Home Page should consist of the following: My Web Apps header - a list format of installed PWAs created by the application. Each list item should contain the following from left to right: App Icon > App Name > Internal App Version > Edit icon. Edit icon leads to a settings page for the given app, where the user can uninstall, edit the icon with icon pack picker support, etc. Edits will prompt a recreation and installation update of the site's apk and increment the version number automatically. R2.2 Bottom right FAB is used to create a new apk for a new site, taking the user to the initial install configuration page.

- R2.1 The home page MUST show a "My Web Apps" large top app bar and a list of the web apps UniWrap created. Each row, left to right:
  - the app icon (48dp, as installed);
  - the app name;
  - the build number, written "Build N";
  - an edit icon button with the content description `Edit <name>`.

  Tapping the row opens the web app; the edit button opens the edit page (R2.3).
- R2.2 The list comes from UniWrap's own records (R11), joined with the package manager's installed state.
  - A record whose app is no longer installed shows a "Not installed" supporting line, with Reinstall and Remove actions.
  - An installed web app built from an older template shows an "Update available" badge (R11.4).
  - When the list is empty, show an empty state explaining the FAB.
- R2.3 The edit page MUST offer:
  - every profile setting, using the same form as R3;
  - the icon editor (R10);
  - Open;
  - Rebuild;
  - Uninstall (with confirmation; it also offers to remove the record).

  Saving changes MUST rebuild the web app with build number + 1 and install it as an update (same package and key, so the app keeps its data). Settings that cannot change after creation (package name) are read-only.
- R2.4 A bottom-right "New web app" FAB (extended FAB on wide windows) opens the configuration page (R3).
- R2.5 UniWrap's settings, reached from the top app bar overflow menu:
  - install method (R6);
  - signing key backup and restore (R5);
  - theme (system, light, dark);
  - about.

## R3 Configuration page

> Here is where a user can put in their desired site and press search. Search will prefill recommended settings, but allow the user to override those settings before creating the apk.

- R3.1 Step 1, URL.
  - A URL field (keyboard type URI, `https://` assumed when no scheme is given) and a Search button (also triggered by the IME action).
  - Invalid input shows inline field errors.
- R3.2 Step 2, discovery (R9).
  - Show a progress indicator naming the current stage: fetching page, reading manifest, finding icon, checking page.
  - Cancelable.
  - On network failure, show the error with Retry and "Continue without discovery" (defaults only).
- R3.3 Step 3, review. A form prefilled from discovery:
  - Laid out like the system settings: titled sections of rounded tonal items, each showing its value (a summary, a switch, a menu value or a color swatch); text, color, domain, window-size and shortcut values open a dialog. Nothing is collapsed.
    - **Identity**: icon with its preview (opens R10), name, and the fixed app ID on the edit page.
    - **Website**: start URL, internal domains.
    - **Permissions**: microphone, camera, notifications, location (off by default; suggested when page scripts call `navigator.geolocation` or the Permissions-Policy allows it).
    - **Appearance**: Header color, one of System (the default: the system's surface color for the light or dark theme), Dynamic (the site's own colors, see R7.7) or Custom; Custom header color (greyed out unless Custom is chosen); Icon background color (the site's brand color); Icon glyph color (`#FFFFFF` by default).
    - **Behavior**: user agent mode, keep alive when hidden, pause on power save, context menu.
    - **Window**: Allow multiple windows (off by default), default and minimum window size.
    - **Keyboard shortcuts**: one item per shortcut, and Add.
  - Fields discovered with low or medium confidence show where the value came from.
  - Every field is editable and validated as the user types, using the core validator (R4.2).
- R3.4 Step 4, create.
  - A "Create app" button builds, signs and installs (R8, R6).
  - A bottom sheet shows each stage (building, signing, installing, done) or the failure with its message.
  - On success: Open, and Done (returns home).
- R3.5 Layout. On windows 840dp and wider, the form uses two columns, with the icon preview fixed in the first.

## R4 Modules and core

- R4.1 The Gradle project MUST have three modules:
  - `:core`: pure JVM (Kotlin JVM plugin).
  - `:template`: the web app, an Android application.
  - `:uniwrap`: the manager, an Android application.

  `:template` and `:uniwrap` depend on `:core`. `:uniwrap` bundles the release build of `:template` as an asset (R8.1).
- R4.2 `:core` contains:
  - **The profile model** (R11.1): a JSON codec using kotlinx.serialization, plus validation with field-level error messages (ported from `scripts/site_config.py`: domains, https start URL covered by internal domains, colors, window sizes, shortcuts).
  - **Derived values**, ported from `docs/PROMPT.md` `<derived_values>`: header darkness, auto icon foreground, allowed origin rules, notification channel id.
  - **`DesktopSessionController`** and its helpers, moved unchanged in behavior from the current app (`docs/PROMPT.md` R2.3), taking its configuration from the profile instead of generated constants.
  - **Discovery rules** (R9.2) as functions from fetched documents to a draft profile with per-field source and confidence.
  - **`ManifestPatcher`** and the APK builder (R8), from the proof of concept.
- R4.3 Tests: `:core` unit tests at line coverage >= 0.85 (JaCoCo), including the existing DesktopSessionController suite, profile validation, discovery rules on stored fixtures, and the patcher and builder run against the real template APK.
- R4.4 Dependencies.
  - `:core`: kotlinx-serialization-json, apksig.
  - `:template`: the `docs/PROMPT.md` R1.1 list plus `:core`.
  - `:uniwrap`: Compose BOM (ui, material3, material3-adaptive, material-icons-extended), activity-compose, lifecycle (viewmodel-compose, runtime-compose), navigation-compose, androidx.webkit, the Shizuku API and provider, plus `:core`.

  No other runtime dependencies without recording them in the Deviations table. Native code: none in `:template` (zero `.so`). `:uniwrap` MUST NOT add native code of its own.

## R5 Signing key

- R5.1 On first use, UniWrap generates one RSA 3072 signing key and a self-signed certificate (CN=UniWrap web apps) in software.
  - It stores them as a PKCS12 file in app-private storage.
  - The file's password is random and encrypted by an AES key in the Android Keystore.
- R5.2 Backup and restore.
  - **Export:** writes the PKCS12 through the Storage Access Framework, protected by a password the user chooses (minimum 8 characters, entered twice).
  - **Import:** replaces the key after confirmation, and only when no web apps exist or the imported certificate matches the current one, unless the user confirms that existing web apps will no longer be updatable.
- R5.3 The first time a web app is created without a backup, UniWrap MUST show a one-time prompt explaining that losing the key means web apps can no longer be updated, with "Back up now" and "Later".
- R5.4 The certificate's SHA-256 fingerprint is shown in settings.

## R6 Install methods

- R6.1 One `Installer` interface: `install(apk)`, `uninstall(package)`, `isAvailable()`. Three implementations:
  - **Session**: `PackageInstaller` sessions, as in the proof of concept. Needs REQUEST_INSTALL_PACKAGES; the user confirms each install.
  - **Shizuku**: when the Shizuku service is running and has granted UniWrap permission, create and commit the install session as the shell user via the Shizuku API. No per-install prompt.
  - **Root**: when `su` is available and grants access, stream the APK to `pm install-create`, `pm install-write` and `pm install-commit` (and `pm uninstall` for uninstalls).
- R6.2 Settings has one Method choice: System (the default; every install asks for confirmation), Shizuku (silent, through ADB) or Root (silent, through superuser). Choosing Shizuku or Root asks for access first (Shizuku's permission dialog, or the root manager's prompt); if access is not granted, the previous method stays and an error notification with Retry appears below the Installation items.
  - If the chosen method fails at install time because it became unavailable (Shizuku stopped, root denied), UniWrap MUST say so and offer Session for this install.
- R6.3 The Session method needs the "Install unknown apps" permission. If it's missing, the install sheet explains it and opens the setting, then continues when the user returns.
- R6.4 Uninstall uses the selected method (Session: the system uninstall dialog).

## R7 Template (the web app)

The template keeps every behavior of `docs/PROMPT.md` R2 to R8 (window, insets, theme, input, WebView, routing, cookies, downloads, permissions, user agent, notifications, power), with these changes:

- R7.1 Configuration at launch.
  - The template MUST read its profile from the APK asset `uniwrap/profile.json` once, at application start, and fail loudly (a crash with a clear message) if it is missing or invalid.
  - Every former `AppConfig` constant comes from the profile. Feature flags become runtime checks, as most already are.
- R7.2 Labels. The application label comes from the patched manifest (R8.2). The task description label, notification channel name and keyboard shortcut group title use the profile name, not a string resource.
- R7.3 Capabilities.
  - The template manifest declares the superset: RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, CAMERA, POST_NOTIFICATIONS and INTERNET, with the matching optional `<uses-feature>`s. The patcher removes what the profile does not enable (R8.2).
  - Code paths for disabled capabilities MUST NOT request the permission.
- R7.4 Icons.
  - The launcher icon is an adaptive icon whose background, foreground and monochrome layers, and the notification small icon, are PNG files at fixed paths in the APK. The template ships neutral placeholders.
  - The template build MUST keep these resource paths stable: disable resource path shortening, and verify it in a test (R8.5).
- R7.5 Window and launch mode. The `<layout>` default and minimum sizes and the activity launch mode are manifest values the patcher rewrites (R8.2). The multi-instance property is present and its value patched.
- R7.6 The template's package name is the placeholder `com.valghora.uniwrap.template`. Its namespace is `com.valghora.wrapper` (unchanged), so its classes keep their names in every web app.
- R7.7 Header modes (`headerMode`). System: the window, caption bar and system bars use the system surface color for the current theme. Custom: they use `headerColor`. Dynamic: in windows without a caption bar (phones, full-screen tablets), a document-start script on the app's own domains samples the page background along its top and bottom edges, ignoring overlays (dialogs, fixed panels narrower than the window such as drawers, and see-through scrims), after page loads, in-app navigation, resizing and theme changes; the status bar takes the top color and the navigation bar the bottom color, cross-fading over 250 ms, each with readable icons. Desktop windows, and pages that haven't reported yet, use the system color. Profiles saved before header modes are upgraded on read (`"headerColor": "system"` becomes System, any other color Custom).

## R8 Building a web app (on the device)

- R8.1 Template asset. `:uniwrap` bundles the release APK of `:template` (built in the same Gradle invocation) as `template/template.apk`, plus a `template/version` file holding the template version (R11.4).
- R8.2 Manifest patching (extends the proof of concept's `ManifestPatcher`):
  - **Package:** set to `<PACKAGE_PREFIX>.<APP_ID>` (default prefix `com.valghora.uniwrap.app`), renaming every string derived from the template package.
  - **Label:** a literal string, the profile name.
  - **versionCode and versionName:** the build number, and the template version plus build number (for example `3.12`).
  - **Permissions:** remove each permission and feature the profile disables, removing elements from the tree and keeping the string pool consistent.
  - **Window:** `<layout>` attributes, the activity launch mode, and the multi-instance property value.
- R8.3 APK assembly:
  - replace `assets/uniwrap/profile.json` and the icon PNGs (R10.3);
  - keep every other entry and its compression (`resources.arsc` stored);
  - drop old signatures;
  - sign with the UniWrap key (R5) using apksig, signature schemes v2 and v3 as apksig decides for minSdk 37.
- R8.4 Before installing, verify:
  - the signature, with apksig's `ApkVerifier`;
  - that the package and label read back from the patched manifest equal the intended values.

  Fail with a clear message otherwise.
- R8.5 Host tests (in `:core`, against the real `:template` release APK) cover:
  - package rename (including derived permission and provider authorities);
  - label;
  - versions;
  - permission stripping;
  - window values;
  - profile and icon replacement;
  - signature verification;
  - stable icon paths;
  - `aapt2 dump badging` output when the SDK is present.
- R8.6 Building runs off the main thread, is cancelable before install, and reports its stages to the UI (R3.4).

## R9 Discovery (on the device)

- R9.1 Input: a URL. Output: a draft profile where every field carries a source and a confidence (high, medium or low), plus notes (warnings). Kotlin only, except R9.3.
- R9.2 Static pass (in `:core`, given fetched bytes; the network layer is in `:uniwrap`):
  - Fetch the page following redirects, then parse:
    - the HTML head: title, `application-name`, `apple-mobile-web-app-title`, `og:site_name`, `theme-color` with media, the manifest link, `mask-icon`, icon links, `apple-touch-icon`;
    - the web manifest: name, short_name, start_url, theme_color, icons with purposes and sizes.
  - Apply the rules of the retired `scripts/discover_site.py`:
    - name priority;
    - start URL rules (manifest start URL on the same site; keep the requested URL across auth redirects);
    - registrable domain via a bundled Public Suffix List;
    - simple-icons by name, not bundled: the slugs of the names the site gives itself (manifest short_name and name, application-name, apple-mobile-web-app-title, og:site_name), then the domain word, are looked up at `https://cdn.simpleicons.org/<slug>` (at most 4 requests), which returns the glyph with its brand color;
    - icon priority: simple-icons glyph, mask-icon, maskable manifest icon, large icon, letter;
    - icon background = the site's brand color: the simple-icons brand color, else the first colorful value (not white, black or gray) among the mask-icon color, manifest theme_color, theme-color meta, the page's CSS brand variables (R9.3), its button colors and the manifest background_color, else any non-white theme color, else blue gray;
    - no header color: web apps follow the system color unless the user sets one.
  - Also apply the WebView-blocking and Play Store redirect notes.
- R9.3 Rendered pass (SHOULD), in a hidden WebView in `:uniwrap` with a desktop and an Android WebView user agent:
  - brand color candidates: CSS custom properties named brand, primary or accent (ranked in `:core` by colorfulness, plain names and middle shades such as `-500`, and repeated use), and the colors of visible buttons by area;
  - observed domains;
  - use of getUserMedia, Notification, Push and WebRTC, giving the microphone, camera and notification suggestions.

  It is limited to 20 seconds; on timeout, the static result stands.
- R9.4 Network: HTTPS only, a 15 second timeout per request, a total size cap of 5 MB, and no cookies kept from discovery.

## R10 Icons

- R10.1 Icon sources the user can pick:
  - the discovered site icon;
  - a simple-icons glyph, looked up by brand name (prefilled with the app name);
  - a letter;
  - an image from the device (Photo Picker), with crop;
  - an icon pack (R10.2).

  For glyphs and letters, the background and foreground colors are editable.
- R10.2 Icon packs. Query installed packs (the common `org.adw.launcher.THEMES` / `com.novalauncher.THEME` intent filters). Show a searchable grid of the pack's icons from its `appfilter.xml` and drawables, with a suggested match for the site's name first.
- R10.3 Rendering. UniWrap renders the chosen icon into:
  - **Adaptive background and foreground layers:** 432x432 px, with the foreground kept inside the 66 percent safe zone, or the 80 percent safe zone for maskable images.
  - **A monochrome layer:** the glyph or letter, or for images and packs a letter.
  - **A 96x96 px white notification icon.**

  These replace the template's placeholder PNGs (R7.4).
- R10.4 The preview shows the icon in the circle, squircle and rounded-square masks, and themed (monochrome).

## R11 Data and versions

- R11.1 Each web app record holds:
  - the profile;
  - the icon choice and its source data;
  - the package name;
  - the build number;
  - the template version it was built with;
  - created and updated times.

  Records are JSON files in app-private storage, one per web app, written atomically.
- R11.2 APP_ID is derived from the name as in the retired site config (lowercase letters and digits, starting with a letter), deduplicated with a numeric suffix. It is fixed at creation.
- R11.3 Rebuilds increment the build number by 1. A failed install does not consume a build number.
- R11.4 The template version is a single integer in `:template`, incremented whenever template code or resources change. On start, UniWrap compares each record's template version with the bundled one and marks outdated apps; "Update" rebuilds with the new template.
- R11.5 UniWrap MUST NOT read or modify web apps' private data.

## R12 Repository and CI

- R12.1 Remove the retired computer-side flow (done during Phase 5 at the user's request: only the template and UniWrap are built):
  - `wrap`, `sites/`;
  - `scripts/site_config.py`, `discover_site.py`, `enrich_site.py`, `generate_icons.py` and `preview_icons.py`, and their tests and data;
  - `scripts/create_sideload_key.sh`, `scripts/ci_verify.sh`;
  - `.github/workflows/wrap.yml`;
  - the `cloner` proof-of-concept module.

  Rewrite the README for UniWrap. Mark `docs/PROMPT.md` as superseded by this spec (keep it for its R1 to R8 baseline).
- R12.2 CI (`.github/workflows/android.yml`) on every push and pull request runs:
  - `:core` tests with coverage verification;
  - lint for `:template` and `:uniwrap` (0 errors);
  - the release build of `:template`;
  - the debug and release builds of `:uniwrap`;
  - a check that the bundled template has zero `.so` files.

  It uploads the UniWrap APKs.
- R12.3 UniWrap release builds use R8. They are signed with the debug key unless a release key is configured (environment or Gradle properties, as with today's sideload key).

</requirements>

<forbidden>

Do not use lint baselines, disabled lint checks, `@SuppressLint` (except SetJavaScriptEnabled in the template's applyWebSettings), Kotlin `@Suppress`, lowered coverage thresholds, deleted or weakened tests, or hardcoded site values in template code. If a gate still fails after 3 fix attempts, stop and report it with the log.

</forbidden>

<execution_plan>

Phase 0: Preflight
  - Confirm current stable versions (AGP, Kotlin, Compose BOM, Material 3 and material3-adaptive, kotlinx-serialization, apksig, Shizuku API) and record them in docs/BUILD_NOTES.md.
  - Gate: `./gradlew help` succeeds.
Phase 1: Modules and core (R4)
  - Create `:core`; move DesktopSessionController and its tests; add the profile model, codec and validator (ported from site_config.py, with its tests ported).
  - Gate: `:core:test` and coverage pass.
Phase 2: Template (R7)
  - Convert the app module into `:template`: profile from assets, superset manifest, fixed icon paths, profile-driven labels. Keep the existing per-site build working until Phase 6, using a default Discord profile for the template's own debug build.
  - Gate: the template release build passes lint with 0 errors and has 0 `.so` files; the template debug build with the Discord profile behaves like today's Discord app.
Phase 3: Builder (R8, R5)
  - Extend the patcher (versions, permission stripping, window values, entry replacement) and the builder, with host tests against the real template.
  - Gate: R8.5 tests pass.
Phase 4: Discovery (R9) and icons (R10)
  - Static discovery in `:core` with fixture tests (the github.com, squoosh.app, example.com and discord.com evidence from the Python version as fixtures), then the rendered pass and the icon renderer in `:uniwrap`.
  - Gate: fixture tests reproduce the Python version's drafts for the fixtures.
Phase 5: UniWrap app (R1 to R3, R6, R11)
  - Home, configuration, edit and settings pages; the Session installer first, then Shizuku and Root.
  - Gate: on a device, enter a URL, review, create, install and open a web app; edit it and see it update in place with build 2.
Phase 6: Retirement and CI (R12)
  - Gate: CI green; README rewritten.
Phase 7: Self-review
  - Re-read every requirement and the code that implements it; fill in the report.

</execution_plan>

<output_contract>

Finish each phase with a short report: what changed, the gate result with trimmed output, any deviations. At the end, a full report:
1. Modules and changed files.
2. Requirement checklist: each R-ID -> status (Done / Deviated / Skipped) -> file:line.
3. Deviations table: R-ID | what changed | why.
4. Verification: each gate's result; CI run link.
5. Known limitations. Always include:
   - Shizuku and Root installs are verified only on the product owner's device.
   - Google's developer verification for sideloaded apps may block or warn on installs on certified devices.
   - WebView limitations inherited from `docs/PROMPT.md` (sign-in blocks, no background push).
   - Losing the signing key without a backup makes web apps non-updatable.

</output_contract>
