# Universal Android Wrapper

[![Android](https://github.com/Valghora/universal-android-wrapper/actions/workflows/android.yml/badge.svg)](https://github.com/Valghora/universal-android-wrapper/actions/workflows/android.yml)

Turn a website into an Android app. Give it a URL, get an APK that behaves like an installed PWA
on Android desktops (desktop windowing, Googlebook OS), tablets and phones: its own window and
launcher icon, keyboard shortcuts, notifications, downloads, file uploads, multi-window, and
sign-in that stays on the device.

```bash
./wrap https://discord.com/app
# ... discovers the site, writes sites/discord/site.toml, builds and verifies
# sites/discord/apks/1.1.0/discord-debug.apk
# sites/discord/apks/1.1.0/discord-release.apk
```

One template app is shared by every site. A site is a small config file (`sites/<id>/site.toml`)
plus an icon; everything site-specific is generated from it at build time.

## Requirements

- JDK 17+, Python 3.11+, unzip
- The Android SDK: `scripts/setup_android_sdk.sh` installs it (and is run by `./wrap`)
- For discovering new sites: Node.js and Playwright with Chromium
  (`npm i -g playwright@1.63.0 && npx playwright install chromium`)
- Optional: `pip install anthropic` and `ANTHROPIC_API_KEY` for `--enrich`

## Wrapping a site

```bash
./wrap https://example.com            # discover, build, verify; APKs land in sites/<id>/apks/<version>/
./wrap https://example.com --enrich   # also let Claude review the draft config
./wrap https://example.com --preview  # also render dist/<id>-icons.png
./wrap --site example                 # rebuild after editing sites/example/site.toml
./wrap --site example --quick         # build only, skip tests and lint
./wrap --list                         # configured sites
```

Discovery (`scripts/discover_site.py`) loads the site in a headless browser three times (light
theme, dark theme, Android WebView user agent) and drafts the config:

- **Name** and **start URL** from the web manifest and page metadata.
- **Internal domains** from the registrable domain and the brand's own domains seen in traffic.
- **Header colors** from `theme-color` or the rendered page.
- **Icon**, best first: the simple-icons glyph for the brand, the site's mask icon, a maskable or
  large manifest icon, a letter.
- **Microphone, camera and notifications** from the APIs the page's scripts use.
- **Warnings** for sites that block WebViews or push mobile users to the Play Store.

APKs go to `sites/<id>/apks/<VERSION_NAME>/`. A rebuild of the same version replaces that folder's
APKs; bump `VERSION_NAME` to keep the previous ones. Only the last 3 versions built are kept, and the
`apks/` folders are gitignored.

Each key gets a `# source (confidence)` comment. Review medium/low values and `# NOTE:` lines,
fix anything wrong, run `./wrap --site <id>`, and commit `sites/<id>/`. Every key is documented in
[docs/SITE_CONFIG.md](docs/SITE_CONFIG.md).

`--enrich` (`scripts/enrich_site.py`) sends the draft and a summary of the evidence to Claude,
which may correct the name, domains, capabilities and user agent mode, and suggest keyboard
shortcuts. Each change is commented with Claude's reason, and the result is validated.

## Installing updates: the sideload key

Android only installs an update over an app signed with the same key. Release APKs are signed with
the local debug key by default, which differs per machine and per CI run. Create one key and use it
everywhere:

```bash
scripts/create_sideload_key.sh    # ~/.android/sideload.jks + .password; back both up
```

It prints the lines for `~/.gradle/gradle.properties` and the `gh secret set` commands for CI
(`SIDELOAD_KEYSTORE_B64`, `SIDELOAD_KEYSTORE_PASSWORD`). Set `RELEASE_SIGNING = "unsigned"` on a
site to build an unsigned release instead.

## CI

- `.github/workflows/android.yml`, on every push and pull request:
  1. validates every site and runs the script tests;
  2. builds and verifies each site in a matrix (`scripts/ci_verify.sh`: unit tests and coverage,
     lint, debug and release builds, APK checks, icon verification, signature).

  APKs and reports are uploaded as artifacts.
- `.github/workflows/wrap.yml`, "Wrap a website" (Actions tab, Run workflow): enter a URL and get
  the APKs, the icon preview and the drafted site files as an artifact. Optionally it opens a pull
  request adding the site. That needs Settings, Actions, General, "Allow GitHub Actions to create
  and approve pull requests". With the `ANTHROPIC_API_KEY` secret, the enrich option works too.

## How it works

- `app/`: the template app (Kotlin, AndroidX Views, WebView). The namespace `com.valghora.wrapper`
  is fixed; each site gets its own applicationId (`<PACKAGE_PREFIX>.<APP_ID>`), so many wrapped
  sites install side by side.
  - `core/`: pure-JVM logic (URL routing, zoom, keys, user agent, wake state), unit tested.
  - The Android layer: `MainActivity`, `DesktopWebView`, the notification bridge and the power
    monitor.
- `scripts/site_config.py`: loads and validates a site. At build time (`-Psite=<id>`, default in
  `gradle.properties`) it generates `AppConfig.kt`, the site's resources and icons, and a manifest
  overlay. Nothing generated is committed.
- `scripts/generate_icons.py`: adaptive launcher, themed and notification icons from an SVG glyph,
  a raster image or a letter, using only the Python standard library.
- `sites/`: `defaults.toml` and one directory per site.
- [docs/PROMPT.md](docs/PROMPT.md): the specification for the template, written as a prompt for a
  coding agent. [docs/BUILD_NOTES.md](docs/BUILD_NOTES.md): toolchain findings.

Manual equivalents:

```bash
scripts/setup_android_sdk.sh
python3 scripts/site_config.py validate --all
./gradlew -Psite=discord assembleDebug
SITE=discord scripts/ci_verify.sh
python3 -m unittest discover -s scripts/tests
```

## Limitations

- Google and some other identity providers block sign-in inside embedded WebViews.
- Web features WebView lacks (Push API, File System Access, WebUSB, some passkey flows) don't work.
- While the screen is off the page is paused, so notifications arrive after wake; there is no
  background delivery.
- Builds are for personal sideloading, not the Play Store.

## Sharing

Share the project, not APKs of other people's services: brand names and logos belong to their
owners, and the simple-icons glyphs are trademarks used under the brands' guidelines. The letter
glyphs come from DejaVu Sans (see `scripts/letter_glyphs.NOTICE`).

## Claude Code cloud sessions

`.claude/hooks/session-start.sh` installs the SDK at session start, exports `ANDROID_HOME`, `PATH`
and `GRADLE_FLAGS`, and warms the Gradle cache.
