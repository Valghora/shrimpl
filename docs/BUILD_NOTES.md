# Build notes

Findings from setting up the infrastructure (2026-10-24). They apply to the upcoming spec run.

## Versions (latest stable at setup time)

| Component | Version | Source |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml |
| Gradle | 9.8.2 | https://services.gradle.org/versions/current |
| Kotlin | 2.4.21 | https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml |
| androidx.webkit | 1.18.0 | https://maven.google.com (androidx/webkit/webkit) |
| androidx.core:core-ktx | 1.19.1 | maven.google.com |
| androidx.appcompat | 1.8.0 | maven.google.com |
| androidx.activity:activity-ktx | 1.13.0 | maven.google.com |
| com.google.android.material | 1.14.0 | maven.google.com |
| junit | 4.13.2 | Maven Central |
| cmdline-tools | 16111833 | https://dl.google.com/android/repository/repository2-3.xml |
| Platform / build-tools | android-37.x / 37.0.0 | same |

The catalog in `gradle/libs.versions.toml` already contains exactly the R1.1 dependencies.
Before Phase 0 of the spec, confirm webkit 1.18.0 still provides UserAgentMetadata,
WebMessageListener and DocumentStartScript.

## Toolchain findings

- **sdkmanager is deprecated.** cmdline-tools 16111833 ships the `android` CLI
  (`android --sdk=$ANDROID_HOME sdk install|list`). `sdkmanager --licenses` is no longer needed.
  Package IDs use `/`, for example `platforms/android-37.0`.
- **API 37 platforms have minor versions** (android-37.0, 37.1, 37.2). `compileSdk = 37` builds
  against any of them; verified with both 37.0 and 37.2.
- **JDK:** only JDK 21 is installed, and no JDK 17. `jvmToolchain(17)` fails with "Cannot find a
  Java installation". Use `kotlin { jvmToolchain(21) }`, which satisfies the spec's "JDK 17+".
- **AGP 9 has built-in Kotlin.** Apply only `com.android.application`, never `kotlin-android`.
- **Maven Central rate limits.** Gradle's parallel downloads through the session proxy got
  HTTP 429 from repo.maven.apache.org. `settings.gradle.kts` puts Google's official Central mirror
  (`maven-central.storage-download.googleapis.com/maven2`) first, with `mavenCentral()` as fallback.

## JaCoCo on AGP 9 (R2.5): verified working

Kotlin classes compile to `build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`,
not to the old `tmp/kotlin-classes/debug`. Unit-test coverage also has to be enabled on the debug
build type. Snippet tested with a throwaway app (replace the package path with CORE_PACKAGE):

```kotlin
plugins {
    alias(libs.plugins.android.application)
    jacoco
}

android {
    buildTypes {
        debug { enableUnitTestCoverage = true }
    }
}

val corePath = "com/example/app/core/**"   // CORE_PACKAGE as a path
val coreClasses = fileTree(layout.buildDirectory) {
    include("intermediates/built_in_kotlinc/debug/**/$corePath")
    exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*")
}
val execData = fileTree(layout.buildDirectory) {
    include("outputs/unit_test_code_coverage/debugUnitTest/*.exec")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    sourceDirectories.setFrom("src/main/java")
    executionData.setFrom(execData)
    reports { xml.required.set(true); html.required.set(true) }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    executionData.setFrom(execData)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.85".toBigDecimal() } } }
}
```

## aapt2 37 output vs. spec Phase 6.4

- The min SDK line is `minSdkVersion:'37'`, not `sdkVersion:'37'`.
- Adaptive icons in `res/mipmap-anydpi/` are emitted as `res/mipmap-anydpi-v21/ic_launcher.xml`.
  The spec's regex `mipmap-anydpi(-v26)?` does not match that, so `ci_verify.sh` accepts any
  `-vNN` qualifier. Record this in the Deviations table for Phase 6.4.
- The `launchable-activity:` line only carries an icon if the activity itself sets
  `android:icon` (R4.5 requires that).

## Spec notes

- MIN_SDK 37 means Android 17+ only. Under R1.5 that rules out every `SDK_INT` guard, including
  for the caption-bar constants (API 35) and `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` (API 35).
- Lint warns `DataExtractionRules` on `allowBackup="false"` (it is a warning, not an error, so
  Phase 6.2 still passes). List it among the remaining warnings.

## Phase 0 research for the Discord build (2026-10-24)

Pages read; findings that affect this build. None overrides an R-requirement.

- https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing
  - Every app in desktop windowing gets a header (caption) bar. Use
    `APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND` / `APPEARANCE_LIGHT_CAPTION_BARS` via
    `window.insetsController.setSystemBarsAppearance` and pad for `WindowInsets.Type.captionBar()`
    (R3.3, R3.4).
  - Shows `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` on `<application>`; the property is valid
    on `<activity>` too, which is where R4.6 puts it.
  - Cross-window drag and drop uses `Activity.requestDragAndDropPermissions(DragEvent)` (R5.2).
- https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
  - `<layout>` `defaultWidth`/`defaultHeight`/`minWidth`/`minHeight`/`gravity` apply to desktop
    windowing (R4.5). From API 36, `resizeableActivity` is ignored on displays with sw >= 600dp.
  - `singleInstancePerTask` can run in several tasks with `FLAG_ACTIVITY_NEW_TASK |
    FLAG_ACTIVITY_MULTIPLE_TASK` (R4.6).
- https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview
  - Recommends multi-instance, cross-window drag and drop, right-click menus and the Keyboard
    Shortcuts Helper; all are covered (R4.6, R5.2, R5.4, R5.5).
- https://developer.android.com/about/versions/17/behavior-changes-17
  - Orientation/aspect-ratio restrictions are ignored on large screens for apps targeting 37, with
    no opt-out. The app declares none (R4.1).
  - Certificate Transparency is on by default for apps targeting 37. No effect on discord.com.
  - Nothing found about WebView, notifications, DownloadManager or SharedPreferences.
- https://developer.android.com/reference/androidx/webkit/WebSettingsCompat
  - The page was too large to read. Instead, the webkit 1.18.0 AAR was inspected with `javap`: it has
    `WebViewFeature.USER_AGENT_METADATA`, `WEB_MESSAGE_LISTENER`, `DOCUMENT_START_SCRIPT`,
    `WebSettingsCompat.get/setUserAgentMetadata`, `UserAgentMetadata.Builder(UserAgentMetadata)`
    with `setPlatform`/`setPlatformVersion`/`setMobile`/`setModel`,
    `WebViewCompat.addWebMessageListener` and `addDocumentStartJavaScript`.
- Lint `DataExtractionRules`: with targetSdk 31+, `allowBackup="false"` no longer blocks
  device-to-device transfer, so `res/xml/data_extraction_rules.xml` excludes all app data from both
  cloud backup and device transfer. Session cookies stay on the device.

## Rerun with the revised prompt (docs/PROMPT.md, 2026-10-24)

- `PAUSE_ON_POWER_SAVE` is now false. `SIGN_RELEASE_WITH_DEBUG_KEY` signs the release build with the
  local debug key (R1.6), so `dist/discord-release.apk` is an installable 1.7 MB R8 build for
  personal sideloading only.
- The activity no longer repeats the application label (R4.5), so lint reports zero warnings.
- `classify(url)` no longer takes an `isPopup` flag.
- Popup teardown triggered from the popup's own callbacks (`onCloseWindow`, `onRenderProcessGone`) is
  posted to the main thread (R6.6). The main WebView's `onRenderProcessGone` still destroys it
  synchronously, as R6.7 and Google's renderer-crash guidance specify.
- `scripts/ci_verify.sh` now fails if the JaCoCo report has no core classes, checks the release
  signature with `apksigner`, and scans only `app/src`, `scripts/generate_icons.py` and the Gradle
  files for `<<`/`>>`.

## Site templates (2026-10-24)

The app became a template: one code base, one config file per site (`sites/<id>/site.toml`).

- **Generation through the AGP variant API.** `GenerateSiteSources` (one per variant) runs
  `scripts/site_config.py generate` and is wired in with `sources.kotlin`/`sources.res
  .addGeneratedSourceDirectory` and `sources.manifests.addGeneratedManifestFile`. Build properties
  (applicationId, versions, SDK, signing) are read at configuration time with `providers.exec`,
  which keeps the configuration cache valid; it is invalidated when the site files change.
- **Fixed namespace.** Code lives in `com.valghora.wrapper`; only the applicationId varies, so the
  Kotlin sources, tests and JaCoCo filters are identical for every site.
- **POST_NOTIFICATIONS is always declared.** With it conditional, lint fails sites with
  notifications off (MissingPermission / NotificationPermission on the always-compiled notification
  code). The permission is only requested when ALLOW_NOTIFICATIONS.
- **Discord equivalence.** The Discord APK built from the template matches the pre-template build
  (manifest, resources, icons, permissions), apart from the activity class package, the auto
  versionCode, the theme name and one new string.
- **Icons.** `generate_icons.py` gained raster (`<inset>` around a PNG/WebP, 9dp maskable / 21dp
  plain) and letter modes. Letter glyphs come from DejaVu Sans Bold, extracted once into
  `scripts/letter_glyphs.json` by `scripts/dev/extract_letter_glyphs.py` (a tiny TrueType parser).
  WebP sizes are parsed from the VP8/VP8L/VP8X headers.
- **Discovery.** Playwright 1.63.0 with Chromium. Chrome reports computed colors as `oklab()` in some
  cases, so the collector normalizes every color through a 1x1 canvas to sRGB hex. Registrable
  domains use the Public Suffix List (cached in `~/.cache/universal-android-wrapper`). simple-icons
  metadata (`data/simple-icons.json` from the simple-icons repo) is downloaded and cached for 7 days; slugs follow simple-icons' `titleToSlug`.
  An auth redirect (for example to `/login`) is not taken as START_URL.
- **Enrich.** `anthropic` SDK 2.3.0, model `claude-opus-5-5`, structured JSON output, and the
  `server-side-fallback-2026-07-01` beta with `fallbacks="default"`. Verified offline and with an
  invalid key (clean 401 message); not run with a live key here.

## UniWrap Phase 0 and 1 (2026-10-25)

Latest stable versions, checked against maven.google.com, Maven Central (via Google's mirror) and
services.gradle.org for docs/UNIWRAP_SPEC.md:

| Component | Version | Used from |
|---|---|---|
| AGP / apksig | 9.4.1 | now / Phase 3 |
| Kotlin (Android built-in, `kotlin.jvm`, `plugin.serialization`) | 2.4.21 | now |
| Gradle | 9.8.2 | now |
| kotlinx-serialization-json | 1.12.0 | now (`:core`) |
| Compose BOM | 2026.09.00 | Phase 5 |
| Compose Material 3 | 1.4.0 | Phase 5 |
| material3-adaptive / adaptive-navigation-suite | 1.3.0 / 1.4.0 | Phase 5 |
| activity-compose | 1.13.0 | Phase 5 |
| navigation-compose | 2.10.2 | Phase 5 |
| lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | Phase 5 |
| Shizuku api / provider | 13.1.5 | Phase 5 |

Phase 1 notes:

- `:core` is a plain Kotlin JVM module (`org.jetbrains.kotlin.jvm` next to AGP's built-in Kotlin works
  with both at 2.4.21). It compiles for Java 17 bytecode (toolchain 21) so the Android modules,
  which target Java 17, can consume it.
- `SessionConfig` no longer defaults to the generated `AppConfig` (which `:core` cannot see); the app
  builds it with `AppConfig.sessionConfig()` until the template reads profiles (Phase 2).
- kotlinx-serialization adds about 14 KB to the Discord release APK after R8.
- KDoc gotcha: `https://*.d` inside a `/** */` comment opens a nested comment in Kotlin.

## UniWrap Phase 2: the template (2026-10-25)

`app/` is now `:template` (namespace `com.valghora.wrapper` unchanged). It reads its site from
`assets/uniwrap/profile.json` at start (`WebApp.kt`) and builds in two modes:

- **Template** (default): applicationId `com.valghora.uniwrap.template`, version `template-1`, the
  superset manifest, and `src/neutral/` (placeholder name "Web App", globe icon PNGs, the default
  Discord profile, the `<layout>` window sizes). This is the APK UniWrap will patch.
- **Site** (`-Psite=<id>`, kept for `./wrap` and CI until Phase 6): `site_config.py generate` writes the
  profile asset, resources, icons and a manifest overlay that removes disabled permissions/features.
  The Discord build matches the pre-template build: identical `aapt2 dump badging`, and the only
  manifest difference is the new `android:name=".WebApp"`.

Findings:

- **Resource path shortening.** AGP release builds rename resources to short paths (`res/E4.xml`),
  so icon files had no fixed path to replace. `android.enableResourceOptimizations=false` in
  gradle.properties keeps real paths; the template's icon layers land at
  `res/drawable-nodpi-v4/ic_launcher_{background,foreground,monochrome}.png` and
  `res/drawable-nodpi-v4/ic_stat_notify.png`. No deprecation warning on AGP 9.4.1.
- **`<layout>` does not merge.** The manifest merger appends an overlay's `<layout>` instead of
  merging it into the main manifest's, and Android uses the last one, so a site's window sizes were
  silently replaced by the template defaults. `<layout>` now comes from exactly one overlay per build
  (`src/neutral/AndroidManifest.xml` or the generated one).
- `POST_NOTIFICATIONS` stays declared in site builds even when notifications are off (lint requires
  it for the compiled-in notification code); UniWrap's patcher may remove it (Phase 3).
- The template's unit tests decode the profile the build ships (`ProfileAssetTest`); coverage is
  measured on `:core` only.

Deviations to carry into the final report:

| Requirement | Change | Why |
|---|---|---|
| PROMPT R3.5 (`windowBackground` = header color) | In template builds the starting window uses a neutral `#202124` until the app applies the profile's header color at launch. Site builds are unchanged. | The header color is a compiled resource; making it per app needs `resources.arsc` patching (possible in Phase 3). |
| PROMPT R3.7 (`notification_channel_name` string) | The notification channel is named after the app (UNIWRAP_SPEC R7.2). | Labels come from the profile. |

## UniWrap Phase 3: building web apps (2026-10-25)

`:core` now builds web apps from the template (`core/.../build/`):

- `ManifestPatcher` (from the proof of concept, extended): package rename (and every string derived
  from it), literal label, versionCode/versionName, removal of `<uses-permission>` and
  `<uses-feature>` elements, launch mode, `<layout>` window sizes, and the multi-instance flag.
  Typed values as aapt2 writes them: ints `0x10`, booleans `0x12` (`-1`/`0`), dimensions `0x05`
  with integer dp encoded as `value << 8 | 1` (radix 0, unit dip; 400dp = `0x00019001`).
- `WebAppBuilder`: copies the template keeping each entry's compression, replaces
  `assets/uniwrap/profile.json` and the four icon PNGs, signs with apksig (scheme v3 at minSdk 37),
  then verifies the signature, the signer and the manifest before returning. Package
  `com.valghora.uniwrap.app.<appId>`, versionCode = build number, versionName = `<template version>.<build>`
  (the template version is the template's own versionCode). Stages and cancellation for the UI.
- `SigningKeys`: RSA 3072 with a self-signed X.509 v3 certificate DER-encoded by hand (the JDK has
  no public certificate builder; this keeps it identical on Android), valid 30 years (GeneralizedTime
  after 2049), exported/imported as password-protected PKCS12. Encrypting the stored key with an
  Android Keystore AES key is UniWrap's job (Phase 5).

The tests patch the real `:template` release APK, so `:core:test` depends on
`:template:assembleRelease` and refuses `-Psite` (that would build a site APK, not the template).
CI runs them in the new "UniWrap core and template" job; `ci_verify.sh` no longer runs `:core`.
Gradle's configuration cache can't serialize a `doFirst` that reads a build-script value; copy it
into a local `val` first.

To try built web apps on a device:
`UNIWRAP_SAMPLES_OUT=<dir> ./gradlew :core:test --tests '*SampleWebApps*'` builds Discord and GitHub
samples signed with one key.

## UniWrap Phase 4: discovery and icons (2026-10-25)

**Rules in `:core`, ported from the Python version.** `core/.../discovery/` holds the evidence model
(the shape of the retired `collect.js` output), the Public Suffix List, simple-icons matching and
`titleToSlug`, color parsing, the start URL / domains / name / colors / capabilities / notes rules,
icon choice (simple-icons glyph > mask-icon > raster by maskable then size > letter), PNG/WebP
header sizes, a static `<head>` reader, and the script-signal patterns. `core/.../icons/` holds the
glyph SVG checks and Android PathParser normalization from `generate_icons.py`, and the icon geometry.

**Gate: parity with Python.** `scripts/dev/make_discovery_fixtures.py` ran `collect.js` on
example.com, github.com, squoosh.app and discord.com/app, ran Python's `derive()` while recording
every fetch, and saved evidence, responses and drafts to `core/src/test/resources/discovery/`.
`DiscoveryParityTest` replays them: every field's value, source and confidence, the icon (mode,
color, bytes), the notes and the fetch order match for all four sites. (`data:` URLs are decoded in
Kotlin rather than passed to the fetcher; Python's `fetch()` decoded them too.)

**Android layer in `:uniwrap`** (new module, no UI until Phase 5):

- `Network`: HTTPS only (http is upgraded), 15 s per request, 5 MB per response, manual redirects so
  the chain is known, no cookies.
- `RenderedPass`: an offscreen WebView laid out at 1280x800 dp (light or dark theme context, desktop
  Linux Chrome user agent with the WebView's Chrome version) or 412x915 dp with the plain WebView user
  agent; images blocked; runs `assets/discovery/collect_page.js` (the page part of `collect.js`)
  after load plus 2.5 s; records requests, main-frame navigations (market:/intent: blocked) and the
  main document's HTTP error status; survives renderer crashes.
- `SiteDiscovery`: static fetch of the page and manifest, then the three rendered visits (12 s + 4 s +
  4 s = 20 s), script scanning, then the core rules; stages for R3.2; cancellable.
- `IconRenderer`: Canvas layers at 432 px (adaptive) and 96 px (notification) with
  `androidx.core.graphics.PathParser`; images via `ImageDecoder` (PNG, WebP, ICO, JPEG...).
- Bundled data (`scripts/dev/update_uniwrap_data.py`): the Public Suffix List and simple-icons
  16.35.0 with every glyph's path, 5 MB of JSON (2.1 MB compressed in the APK).

Differences from the Python/Playwright collector, recorded as deviations:

| Requirement | Change | Why |
|---|---|---|
| R9.4 (5 MB total) | 5 MB is per response; script scanning has its own 20 MB budget (as `collect.js` had). | Large web apps keep `getUserMedia`/`Notification` calls in multi-MB bundles; a 5 MB total would miss them. |
| R9.3 | Request types come from resource timing (`xhr`, `fetch`, `script`, ...); WebSocket hosts are not seen. | WebView has no per-request resource type; only the "other domains" note uses types. |
| R9.3 | The WebView visit's HTTP status is known only when it is an error (>= 400). | WebView reports main-document errors, not successes. |
| R10 | Letter icons use the system bold font instead of DejaVu Sans Bold outlines. | Native rendering; no bundled font needed. |
| R1.2 | `uniwrap` lint keeps one `SetJavaScriptEnabled` warning (the hidden discovery WebView). | Discovery must run the page's scripts; the spec allows suppression only in the template. |

## UniWrap Phase 5: the manager app (2026-10-25)

**What's in `:uniwrap` now.** A Compose (Material 3, dynamic color) app:

- Home: "My Web Apps" with icon, name, build number and an edit button. Rows that aren't installed
  offer Reinstall and Remove, and rows built with an older template offer Update. The FAB is extended
  at 600 dp and wider. On wide screens the edit page sits beside the list
  (`NavigableListDetailPaneScaffold`). Install state is re-read on resume.
- Configuration page: URL, then Search with discovery stages and Cancel. A failure offers Try again
  or "Continue without discovery" with defaults. Next comes the review form, which shows
  low/medium-confidence sources under each field and discovery's notes as cards, then Create, which
  opens the build sheet. After the first web app, a one-time prompt offers to back up the signing key.
- Edit page: the same form with a fixed app ID. "Save as build N" builds the next version and
  installs it over the current one. The menu has Rebuild/Reinstall, Discard changes, and Uninstall
  (confirmed, then offers to remove the record) or Remove.
- Icon editor: the website icon, simple-icons brand search, letter, photo (Photo Picker, centered
  square crop, "fills the shape" switch) and installed icon packs (ADW/Nova `appfilter.xml`). It has
  circle, squircle, rounded and themed previews, and background and glyph colors.
- Settings: install method (Automatic / System installer / Shizuku with Grant / Root with Check), the
  signing key fingerprint, backup export (password typed twice, then SAF save), restore (SAF open,
  password, confirmation when it replaces a different key while web apps exist), theme, and versions.
- Installers: `PackageInstaller` sessions (`USER_ACTION_NOT_REQUIRED` for updates; first installs
  ask, and "Install unknown apps" is requested and retried on return), Shizuku (a UserService running
  `pm install -S` with the APK piped in), and root (`su -c pm install -S`). A failed install never
  uses up a build number.
- The neutral template APK is bundled as `assets/template/template.apk` by `:uniwrap:bundleTemplate`
  (the build fails with `-Psite`).

**Checked here:** the debug and release (R8) builds, lint, and the core tests. There is no
emulator in this container (no KVM), so the Phase 5 gate (create, install, open, edit, update as
build 2) is a device test.

Deviations:

| Requirement | Change | Why |
|---|---|---|
| R10.1 | Photos are center-cropped to a square automatically; there is no interactive crop. | No crop library; can be added later. |
| R2.5 | Theme is dynamic color only, with no fixed palette. | Material You is available on every supported version (minSdk 37). |
| R1.2 | `uniwrap` lint keeps a `DiscouragedApi` warning (`getIdentifier` for icon packs). | Icon pack drawables are only known by name at runtime. |
| — | `material-icons-core` instead of `material-icons-extended`. | The extended set adds megabytes for a handful of icons. |
| R6.1 | Package visibility uses `<queries>` (launcher apps, icon packs, Shizuku) instead of `QUERY_ALL_PACKAGES`. | Narrower, and enough to see web apps, which all have a launcher entry. |

## Phase 5 device feedback (2026-10-25)

Changes after the first device test on Googlebook OS:

- **One header color, "System" by default.** `headerColorNight` is gone. `headerColor` is `system`
  (the default) or one `#RRGGBB`. With `system`, the web app uses the system's surface container color
  (`system_surface_container_light`/`_dark`) and follows light and dark theme changes. Discovery no
  longer suggests a header color; the `#161CBB` seen for discord.com was the page's top-edge color.
  Records written before this change still load (unknown keys are ignored in records only).
- **Glyph color `#FFFFFF` by default** for new web apps (`auto` still works for existing ones).
- **Icon background = brand color.** The order is: the simple-icons brand color; then the first
  colorful value among the mask-icon color, manifest `theme_color`, theme-color meta, CSS brand
  variables, button colors and manifest `background_color`; then any non-white theme color; then
  blue gray. CSS variables named brand, primary or accent are ranked in `:core`. The ranking was
  checked against discord.com, reddit.com, notion.so and slack.com in Chromium; for discord.com/app
  it picks `--brand-500` `#5865F2`.
- **simple-icons are no longer bundled** (5 MB of JSON removed). Discovery looks up the slugs of the
  site's own names, then the domain word, at `cdn.simpleicons.org/<slug>`, which returns the glyph
  filled with the brand color and a title. The lookup makes at most 4 requests. The icon editor's
  Brand tab looks up a typed name, prefilled with the app name.
- **Settings-style pages.** The web app form and UniWrap's settings are titled sections of rounded
  `surfaceContainer` items with values, switches (check/cross thumbs), menus and color swatches; edits
  open dialogs. The collapsible Advanced section is gone (now Browser, Window and Keyboard shortcuts).
- **Only the template and UniWrap are built.** Removed: the `cloner` proof of concept, `sites/`
  (Discord), `wrap`, the wrap workflow, the per-site `-Psite` build mode, and the Python site tooling
  and its tests. The template's `src/neutral/` overlay moved into `src/main/`. CI is one job. The
  discovery fixtures recorded from the Python version stay as regression tests
  (`DiscoveryFixturesTest`); their expectations now follow the new rules (example.com's icon
  background is the default blue gray instead of its gray page color).
