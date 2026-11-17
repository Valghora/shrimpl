<p align="center"><img src="docs/brand/shrimpl-glyph-512.png" alt="" width="128"></p>

# Shrimpl

**Any website, as a real Android app, made on your device.**

Type a web address and Shrimpl does the rest. It looks the site up, picks a name, icon, colors and
the permissions it needs, and lets you change any of them. Then it builds, signs and installs an app
for that site, right on your phone, tablet or laptop. You don't need a computer, an app store or an
account.

Each app Shrimpl makes is a separate Android app. It has its own launcher icon, windows, storage and
sign-ins, and it behaves like an installed web app (a PWA) should on Android: it opens in its own
window, takes keyboard shortcuts, prints, downloads and saves files, and sends notifications. Shrimpl
is built first for Googlebooks, Google's Android laptops, and works on every Android form factor.

### What people use it for

- **Desktop sites on an Android laptop.** Many services have a phone app but a better desktop site
  (chat, mail, editors, dashboards). Shrimpl gives that site its own window and launcher icon, and
  asks for the desktop site on a laptop or large screen (the mobile one on a phone).
- **Sites with no app at all.** Web tools such as image compressors, whiteboards and note apps become
  apps you can pin, switch to with Alt+Tab and open in several windows.
- **Keeping accounts apart.** Every app has its own cookies and storage, so a work and a personal
  account of the same service can live side by side, each in its own app.
- **A quieter browser.** A site in its own app keeps its tabs, sign-in and notifications out of your
  browser, and links to other sites open in your browser.

### Get it

Download `shrimpl-<version>.apk` from the [latest release](https://github.com/Valghora/shrimpl/releases/latest) and open it on your
device. Shrimpl needs Android 17 (API 37) or later. Once installed, it finds and installs its own
updates from this repository's releases.

## Features

**Making apps**

- Finds the site's name, address, domains, brand color, icon and permissions on the device, from the
  page, its web manifest and a hidden browser visit; every guess says where it came from.
- Icons from the brand's glyph ([simple-icons](https://simpleicons.org)), the site's own icon, your
  photos or files, or an installed icon pack, as adaptive icons with a themed (monochrome) layer.
- A settings form for every option: header color (system, the page's own color, or custom), window
  sizes, multiple windows, background running, context menu, and microphone, camera, notification and
  location access.
- Builds, signs and installs entirely on the device, in pure Kotlin and Java.
- Install methods: System (Android asks you to confirm each install), [Shevery](https://github.com/HmnDev-Tech/Shevery-API)
  or Root (no prompts).

**Keeping them up to date**

- Editing an app rebuilds it and updates it in place, keeping its sign-ins and data.
- When Shrimpl's own app template improves, Update all brings every app up to date.
- One signing key for all your apps, exportable as a password-protected backup.
- Apps stay manageable after Shrimpl is reinstalled: each app carries its own settings, so Shrimpl can
  take it back.
- Shrimpl updates itself from GitHub releases, checking on the schedule you choose, and verifies each
  download's checksum before installing.

**In every app**

- Its own task, window and launcher entry, with desktop windowing, resizing and, if you allow it,
  several windows at once.
- Header and system bars in the site's colors, light and dark.
- Downloads, including files a page makes itself (an editor's Export or Save), file and folder Open
  and Save, sharing, camera uploads and drag and drop.
- Keyboard first: the site's own shortcuts, Ctrl+P to print, Ctrl+scroll to zoom, and all of them
  listed in Android's Keyboard Shortcuts Helper.
- Full-screen video, a right-click menu (copy, open or save links and images), and back that goes
  back in the page.
- Notifications from the site while the app is running, on a channel named after the app.
- A launcher shortcut to switch between the desktop and mobile site.
- Sign-in pop-ups (OAuth) open in place; links to other sites open in your browser.

**Everywhere**

- Adaptive layout: a list and detail side by side on wide windows, rows or tiles, keyboard
  shortcuts, tooltips and right-click menus, sized for Googlebook screens first.
- Runs natively on ARM and x86 devices, 32 and 64 bit, with no translation layer; the apps Shrimpl
  makes contain no native code at all.
- No accounts, analytics or ads. Shrimpl contacts only the sites you add, simple-icons for brand
  glyphs, and GitHub to check for its own updates, without sending anything about you. It never reads
  your apps' data.

## Limitations

- **Google sign-in.** Google, and some other identity providers, refuse to sign in inside an embedded
  browser such as Android's WebView. Shrimpl doesn't try to get around this; when a site depends on
  it, Shrimpl warns you and offers to open the site in Chrome instead.
- **No background push.** Notifications arrive only while the app is running and awake. While the
  device sleeps, an app pauses: live connections drop and notifications arrive when it wakes.
- **What WebView can't do.** The Push API, WebUSB, Web Bluetooth and some passkey flows don't work. A
  desktop site may offer features that need them.
- **Sideloading.** Shrimpl and the apps it makes are installed from outside an app store. On some
  certified devices, Google's developer verification may warn about or block such installs.
- **Your signing key matters.** Apps can only be updated with the key that signed them. Back it up
  (Settings, Export certificate); without it, losing Shrimpl's data means reinstalling your apps.
- **Debug and release builds don't mix.** Android won't update one with the other because their keys
  differ, so a debug build installs as a separate app, "Shrimpl Debug".
- **Tested where it can be.** Shevery and Root installs, hinges, window snapping and TalkBack are
  checked on real devices by hand, not in automated tests.
- **Personal use.** Shrimpl makes apps for you, on your device. It isn't meant for publishing apps of
  other people's services: their names and logos belong to their owners.

## Credits and license

Shrimpl is licensed under the [LICENSE_NAME](LICENSE).

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
