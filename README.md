<p align="center"><img src="docs/brand/shrimpl-glyph-512.png" alt="" width="128"></p>

# Shrimpl

**Any website, as a real Android app, made on your device.**

Type a web address and Shrimpl does the rest. It looks the site up, picks a name, an icon, colors and
the permissions the site needs, and lets you change any of them. Then it builds the site into an APK,
signs it and installs it, right from your Android phone, tablet or laptop. You don't need a computer,
an app store or an account.

### Why not just install the site from Chrome?

Chrome can install any site as a web app (a PWA), and on a phone that's often enough. Shrimpl was made
for the friction this method has on Googlebooks, where web apps sit in windows beside everything else.
Chrome builds the APK for you, so how the app looks and how well it fits into Android are mostly up to
the site and to Chrome, not to you. For example:

- **Icons may be missing or ugly.** Chrome uses whatever icon the site's manifest offers. A site
  without a good one gets a blurry or generic icon, often shrunk onto a white circle, and you can't
  pick another.
- **Window borders often don't follow the system theme.** The title bar takes the site's one theme
  color, so a bright bar can sit on a dark desktop, or the other way round.
- **The name, icon and colors are fixed.** They come from the site, and you can't change them after
  installing.
- **It shares your browser.** The app uses Chrome's cookies and storage, so it's signed in as the same
  account as your browser, and two accounts of one service can't live side by side.

Shrimpl gives those choices back to you:

- **You pick the icon:** the brand's glyph, the site's own icon, one of your photos, or an icon pack.
  It becomes a proper adaptive icon, with the themed (monochrome) layer Android's themed icons use.
- **The header and system bars follow your choice:** your light or dark theme, the site's own colors,
  or a color you choose.
- **Change anything later.** Rename, recolor or re-icon an app at any time; it updates in place and
  stays signed in.
- **Every app stands alone.** Each one is its own Android app with its own storage, so each signs in
  on its own.

Each app Shrimpl makes is a separate Android app. It has its own launcher icon, windows, storage and
sign-ins, and it behaves like an installed web app (a PWA) should on Android: it opens in its own
window, takes keyboard shortcuts, prints, downloads and saves files, and sends notifications. Shrimpl
is built first for Googlebooks, Google's Android laptops, and works on every Android form factor.

### Get it

Download `shrimpl-<version>.apk` from the [latest release](https://github.com/Valghora/shrimpl/releases/latest) and open it on your
device. Shrimpl needs Android 17 (API 37) or later. Once installed, it finds and installs its own
updates from this repository's releases.

## Features

- **Smart setup:** finds the site's name, icon, colors, domains and permissions for you.
- **Your icon, your colors:** brand glyph, site icon, photo or icon pack, with themed icons and
  headers that follow light and dark mode.
- **Built on your device:** builds, signs and installs with no computer, using System, Shevery or Root
  installs.
- **Desktop-class apps:** own windows, keyboard shortcuts, printing, downloads, file and folder
  saving, sharing and notifications.
- **Edit anytime:** changes update the app in place and keep its sign-ins.
- **Stays current:** Update all refreshes every app, and Shrimpl updates itself from GitHub releases.
- **Runs everywhere:** adaptive on phones, tablets and laptops, native on ARM and x86.
- **Private:** no accounts, analytics or ads.

## Limitations

- **No Google sign-in:** Google blocks it inside embedded browsers; Shrimpl offers Chrome instead.
- **No background push:** notifications arrive only while the app is running and awake.
- **WebView gaps:** no Push API, WebUSB, Web Bluetooth or some passkey flows.
- **Sideloaded:** some certified devices may warn about or block installs from outside an app store.
- **Back up your key:** apps can only be updated with the key that signed them.
- **Personal use:** don't publish apps of other people's services.

## Credits and license

Copyright 2026 Valghora. Shrimpl is licensed under the [Apache License 2.0](LICENSE).

It's built on:

- [Android Jetpack](https://developer.android.com/jetpack) (AndroidX, Jetpack Compose, Material 3,
  Navigation 3, WorkManager, WebKit) and [Material Components](https://github.com/material-components/material-components-android),
  Apache License 2.0
- [apksig](https://android.googlesource.com/platform/tools/apksig/) from the Android Open Source
  Project, which signs and verifies the apps, Apache License 2.0
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), Apache License 2.0
- [Shevery-API](https://github.com/HmnDev-Tech/Shevery-API) by HmnDev-Tech, for installs without
  prompts, MIT License
- The [Public Suffix List](https://publicsuffix.org) from Mozilla, bundled to tell a site's domains
  apart, Mozilla Public License 2.0
- [simple-icons](https://simpleicons.org), whose brand glyphs Shrimpl looks up when you make an app
  (none are bundled), CC0 1.0. The brands themselves are trademarks of their owners, used under
  their own guidelines.
- Google's [Android skills](https://github.com/android/skills), vendored in `.claude/skills/` to guide
  the work on this project, Apache License 2.0
- Tests use [JUnit](https://junit.org/junit4/), Eclipse Public License 1.0, and
  [Robolectric](https://robolectric.org), MIT License

Shrimpl isn't affiliated with Google or with any of the sites people make apps for.

## For developers

### How it works

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

### Documentation

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

### Building

```bash
scripts/setup_android_sdk.sh          # Android SDK (API 37) and tool check
./gradlew :core:check                 # core tests, including real builds from the template
./gradlew :shrimpl:assembleDebug      # also builds the template and bundles it
```

CI (`.github/workflows/android.yml`, run by hand before a merge) runs the core, template and Shrimpl
tests, lint for both apps, the template release build and Shrimpl's debug build, and checks that every
APK is architecture-neutral. A merge into main runs the Release workflow
(`.github/workflows/release.yml`), which tags it `v<version>` and publishes the signed release APK and
its checksum.

### Claude Code cloud sessions

`.claude/hooks/session-start.sh` installs the SDK at session start, exports `ANDROID_HOME`, `PATH`
and `GRADLE_FLAGS`, and warms the Gradle cache.
