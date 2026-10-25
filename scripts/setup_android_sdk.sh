#!/usr/bin/env bash
# Idempotent Android SDK bootstrap + Phase 0 tool check.
# Used by the Claude cloud SessionStart hook, GitHub Actions, and local setup.
#
# Env:
#   ANDROID_HOME   SDK location (default /opt/android-sdk)
#   API_LEVEL      SDK major to install (default: 37, the modules' compileSdk)
set -euo pipefail

CMDLINE_TOOLS_BUILD="16111833"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Canonicalize: the android CLI's unzip guard rejects paths containing ".." ("Illegal entry path escaping destination").
export ANDROID_HOME="$(realpath -m "${ANDROID_HOME:-/opt/android-sdk}")"

API_LEVEL="${API_LEVEL:-37}"

log() { echo "[setup_android_sdk] $*"; }

ANDROID_CLI="$ANDROID_HOME/cmdline-tools/latest/bin/android"
if [[ ! -x "$ANDROID_CLI" ]]; then
  log "installing cmdline-tools $CMDLINE_TOOLS_BUILD into $ANDROID_HOME"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL --retry 4 -o "$tmp/clt.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
  unzip -q "$tmp/clt.zip" -d "$tmp/x"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/x/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

sdk() { "$ANDROID_CLI" --sdk="$ANDROID_HOME" sdk "$@" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'; }

# Newest stable (no -rc/-beta) platform and build-tools for API_LEVEL.
installed_platform="$(ls "$ANDROID_HOME/platforms" 2>/dev/null | grep -E "^android-${API_LEVEL}(\.[0-9]+)?$" | sort -V | tail -1 || true)"
installed_bt="$(ls "$ANDROID_HOME/build-tools" 2>/dev/null | grep -E "^${API_LEVEL}\.[0-9]+\.[0-9]+$" | sort -V | tail -1 || true)"

if [[ -n "$installed_platform" && -n "$installed_bt" && -d "$ANDROID_HOME/platform-tools" ]]; then
  log "already installed: platforms/$installed_platform build-tools/$installed_bt platform-tools"
else
  available="$(sdk list --all | awk '{print $1}')"
  platform="$(grep -E "^platforms/android-${API_LEVEL}(\.[0-9]+)?$" <<<"$available" | sed 's|platforms/||' | sort -V | tail -1 || true)"
  bt="$(grep -E "^build-tools/${API_LEVEL}\.[0-9]+\.[0-9]+$" <<<"$available" | sed 's|build-tools/||' | sort -V | tail -1 || true)"
  if [[ -z "$platform" || -z "$bt" ]]; then
    echo "ERROR: no stable platform/build-tools found for API $API_LEVEL" >&2
    exit 1
  fi
  log "installing platform-tools platforms/$platform build-tools/$bt"
  sdk install platform-tools "platforms/$platform" "build-tools/$bt"
  installed_platform="$platform"
  installed_bt="$bt"
fi

echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"

# ---- Phase 0 tool check ----
fail=0
check() { if eval "$2" >/dev/null 2>&1; then printf '  %-28s ok\n' "$1"; else printf '  %-28s MISSING\n' "$1"; fail=1; fi; }
java_major="$(java -XshowSettings:properties -version 2>&1 | grep -oE 'java.specification.version = [0-9]+' | grep -oE '[0-9]+$' || echo 0)"
echo "Tool check (API $API_LEVEL):"
check "JDK 17+ (found $java_major)" "[ $java_major -ge 17 ]"
check "platforms/$installed_platform" "[ -f '$ANDROID_HOME/platforms/$installed_platform/android.jar' ]"
check "build-tools/$installed_bt" "[ -d '$ANDROID_HOME/build-tools/$installed_bt' ]"
check "aapt2" "[ -x '$ANDROID_HOME/build-tools/$installed_bt/aapt2' ]"
check "platform-tools (adb)" "[ -x '$ANDROID_HOME/platform-tools/adb' ]"
check "unzip" "command -v unzip"
exit $fail
