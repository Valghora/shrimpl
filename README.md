# Universal Android Wrapper

[![Android](https://github.com/Valghora/universal-android-wrapper/actions/workflows/android.yml/badge.svg)](https://github.com/Valghora/universal-android-wrapper/actions/workflows/android.yml)

An Android project that wraps a single website in a WebView so it behaves like an installed PWA
on Android desktops, tablets and phones. The app is generated from the "Universal APK Wrapper" spec.
This branch holds only the build infrastructure; the app module is added from the spec.

## Requirements

- JDK 17+ (CI and the cloud hook use 21)
- Python 3, unzip
- Android SDK, installed by the script below

## Setup

```bash
scripts/setup_android_sdk.sh          # idempotent; ANDROID_HOME defaults to /opt/android-sdk
./gradlew help
```

`setup_android_sdk.sh` installs pinned cmdline-tools, platform-tools, and the newest stable platform
and build-tools for `compileSdk` (default 37). It writes `local.properties` and runs the tool check.

## Verify

```bash
scripts/ci_verify.sh
```

Runs the spec's Phase 6 checks: the Gradle pipeline (tests, JaCoCo, lint, debug and release
assembly), lint report counts, the native-library check, `aapt2 dump badging`, the icon verifier,
static greps, and copying the APKs to `dist/`. App values (APP_ID, package, SDK levels) are read
from the project, so the script works for any config. With no `app/` module it exits 0.

## CI

`.github/workflows/android.yml` runs both scripts on every push and pull request and uploads
`dist/*.apk` and the lint/test/coverage reports as artifacts.

## Claude Code cloud sessions

`.claude/hooks/session-start.sh` runs at session start (remote only). It installs the SDK, exports
`ANDROID_HOME`, `PATH` (cmdline-tools, platform-tools, aapt2) and `GRADLE_FLAGS`, and warms the
Gradle cache.

See `docs/BUILD_NOTES.md` for versions and toolchain findings.
