# Build notes

Baseline of 2026-10-28: the toolchain, platform findings that shape the code, and every deviation
from docs/SHRIMPLE_SPEC.md and docs/TEMPLATE_SPEC.md. Deviations from Google's adaptive guidance are
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
  (Shrimple: each library present for arm64-v8a, armeabi-v7a, x86_64 and x86, nothing else).
- Shrimple's only native library is `libandroidx.graphics.path.so` (androidx.graphics:graphics-path,
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
- **No emulator in the cloud container** (no KVM): UI changes are checked on devices.

## Deviations

| Requirement | What we do instead | Why |
|---|---|---|
| T3.3 (header color from the first frame) | The starting window uses the system surface color until the app applies the profile's header color at launch. | The window background is a compiled resource; making it per app would mean patching `resources.arsc`. |
| R9.3 | Request types come from resource timing (`xhr`, `fetch`, `script`, ...); WebSocket hosts aren't seen. | WebView has no per-request resource type. |
| R9.3 | The rendered visit's HTTP status is known only when it is an error (>= 400). | WebView reports main-document errors, not successes. |
| R10.3 | Letter icons use the system bold font. | Native rendering; no bundled font. |
| R6.1 | Package visibility uses `<queries>` (launcher apps, icon packs, Shizuku), not `QUERY_ALL_PACKAGES`. | Narrower, and enough: every web app has a launcher entry. |
| Rules (lint) | `:shrimple` lint keeps two warnings: `SetJavaScriptEnabled` (the hidden discovery WebView must run page scripts) and `DiscouragedApi` (`getIdentifier` for icon pack drawables, known only by name). | `@SuppressLint` is allowed only in the template. |

## Rename to Shrimple (2026-10-29)

UniWrap became Shrimple everywhere except `docs/history/`: the app (`com.valghora.shrimple`, module
`:shrimple`), the core package (`com.valghora.shrimple.core`), the template's namespace
(`com.valghora.shrimple.webapp`) and placeholder package (`com.valghora.shrimple.template`), the
apps' package prefix (`com.valghora.shrimple.app`), the profile asset (`assets/shrimple/`), the
signing key's alias and common name, the launcher icon (an "S") and the repository. Shrimple is a new
package, so it installs beside UniWrap rather than updating it, and can't update apps UniWrap made
(their packages start `com.valghora.uniwrap.app`). Template version 4.
