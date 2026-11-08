# Build notes

The toolchain and the platform findings that shape the code. Testing setups and their quirks are in
docs/TESTING.md; deviations from the specs and from Google's guidance are in docs/DEVIATIONS.md.

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
- **Compose dropdown menus.** A `DropdownMenu` opens below its anchor and adds its `offset` to the
  anchor's bottom, so offsetting the menu put it far below a tile. App menus are anchored to an empty
  box at the pressed point, kept there after the menu closes so the menu doesn't jump as it fades out
  (found on a device, 2026-11-02).

## R8

Both apps use the AGP 9.3+ `optimization { enable = true }` block. Shrimpl's one keep rule (the Shizuku
shell service) is in `shrimpl/src/main/keepRules/`; every broad rule comes from a library or AGP's
defaults (r8-analyzer skill, 2026-11-01).

## CI decisions

- **Lint vital is skipped** (`-x :template:lintVitalRelease`): it checks a subset of what `lintRelease`,
  which runs for both apps, finds on the same variant.
- **One Build job, a step per stage.** Parallel jobs would shorten a run to about 6 minutes, but each pays
  about 45 s of setup and rebuilds the template, so in a private repository they cost 1.5 to 2 times the
  runner minutes. Build is skipped for a commit that already passed. `org.gradle.parallel=true` is on.
- **Shrimpl's release build** (R8, about 3.5 minutes clean) runs only in a manual run with Release build
  ticked, or when the owner asks for a release APK; a problem that only shows after shrinking is found
  then. The template's release build runs every time: debug Shrimpl bundles it, and the core tests
  build web apps from it.

## Debug APK size

AGP packages debug APKs incrementally and leaves replaced entries' space behind as gaps: after many
builds in one checkout the APK reached 62 MB for 35 MB of entries. Delete
`shrimpl/build/outputs/apk/debug/` before `:shrimpl:assembleDebug`. A clean debug APK is about 35 MB,
almost all of it unshrunk dex stored uncompressed; the release build shrinks and compresses it.
