# Shrimpl

Shrimpl turns websites into installable Android apps, on the device. Enter a web address; Shrimpl
looks the site up, suggests a name, icon, colors, domains and permissions, lets you change any of
them, then builds, signs and installs a small web app for it. Each web app is a real app with its
own launcher icon, task, window and sign-ins, and behaves like an installed PWA on Android desktops
(Googlebook OS and Android desktop windowing), tablets and phones.

## Using it

1. Install `shrimpl-release.apk` (from CI, or `./gradlew :shrimpl:assembleRelease`).
2. Tap **+**, enter a website (`example.com`, `squoosh.app`, ...) and tap **Search**.
3. Check the suggested settings and tap **Create**. Android asks once to allow Shrimpl to install
   apps.
4. The home list shows what you made. The edit button (or right-click) opens a web app's settings;
   saving builds the next version (Build 2, 3, ...) and updates it in place, keeping its sign-ins.
   With a keyboard, Ctrl+N starts a new web app and Ctrl+, opens Settings.

Install methods (Settings): System (asks for confirmation), Shevery, or Root (no prompts). Export
the certificate in Settings and keep it safe: web apps can only be updated with the key that signed
them.

## How it works

- `template/`: the web app (Kotlin, AndroidX Views, WebView). It reads its site from
  `assets/shrimpl/profile.json` and declares every capability any site may need. Its release APK is
  bundled inside Shrimpl.
- `core/`: pure-JVM logic, unit tested with a 95% line-coverage rule: URL routing, zoom, keys, user
  agent and wake state for the web app; the profile model and validator; discovery rules; and the
  builder that patches the template's manifest (package, label, versions, permissions, window
  sizes), replaces the profile and icons, and signs with apksig.
- `shrimpl/`: the manager app in Compose (Material 3, Navigation 3 with a list-detail layout on wide
  windows): discovery (a page fetch plus a hidden WebView), the icon renderer, the record store, the
  signing key in the Android Keystore, and the System, Shevery and Root installers.

Every APK runs natively on ARM and x86 devices, 32 and 64 bit, with no translation layer: the
template and the web apps contain no native code, and building a web app on the device is pure
Kotlin and Java. `scripts/check_native_code.sh` enforces this in CI.

## Documentation

- [docs/SHRIMPL_SPEC.md](docs/SHRIMPL_SPEC.md): the specification, starting with the architecture
  rule (R0).
- [docs/TEMPLATE_SPEC.md](docs/TEMPLATE_SPEC.md): what every web app does.
- [docs/ADAPTIVE.md](docs/ADAPTIVE.md): how Google's adaptive skill (vendored in
  `.claude/skills/adaptive/`) and app quality guidelines apply, and the deliberate deviations.
- [docs/SKILLS.md](docs/SKILLS.md): the other Android skills in use, when to use each, their
  deliberate deviations, and how `scripts/android_skills.sh` keeps them current.
- [docs/BUILD_NOTES.md](docs/BUILD_NOTES.md): toolchain versions, platform findings, deviations.
- [CLAUDE.md](CLAUDE.md): the rules for every change.

Brand icons come from [simple-icons](https://simpleicons.org), looked up by name when a web app is
made; nothing from it is bundled.

## Building

```bash
scripts/setup_android_sdk.sh          # Android SDK (API 37) and tool check
./gradlew :core:check                 # core tests, including real builds from the template
./gradlew :shrimpl:assembleDebug      # also builds the template and bundles it
```

CI (`.github/workflows/android.yml`, run by hand before a merge) runs the core, template and Shrimpl
tests, lint for both apps, the template release build and Shrimpl's debug build, and checks that every
APK is architecture-neutral. A `v<version>` tag on main runs the Release workflow
(`.github/workflows/release.yml`), which publishes the signed release APK and its checksum.

## Limitations

- Google and some other identity providers block sign-in inside embedded WebViews.
- Web features WebView lacks (Push API, WebUSB, some passkey flows) don't work. File and folder Open and
  Save, sharing links, camera uploads and page-made downloads do (docs/TEMPLATE_SPEC.md T6).
- While the screen is off the page is paused, so notifications arrive after wake; there is no
  background delivery.
- For personal sideloading, not the Play Store.

## Sharing

Share the project, not APKs of other people's services: brand names and logos belong to their
owners, and simple-icons glyphs are trademarks used under the brands' guidelines.

## Claude Code cloud sessions

`.claude/hooks/session-start.sh` installs the SDK at session start, exports `ANDROID_HOME`, `PATH`
and `GRADLE_FLAGS`, and warms the Gradle cache.
