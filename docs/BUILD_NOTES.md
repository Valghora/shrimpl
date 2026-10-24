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
