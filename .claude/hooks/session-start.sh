#!/bin/bash
# SessionStart hook for Claude Code cloud sessions: Android SDK + Gradle warm-up.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
scripts/setup_android_sdk.sh

BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
GRADLE_FLAGS="--project-cache-dir=$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | sha1sum | cut -c1-8)/gradle"

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$ANDROID_HOME\""
    echo "export PATH=\"$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$BUILD_TOOLS:\$PATH\""
    echo "export GRADLE_FLAGS=\"$GRADLE_FLAGS\""
  } >> "$CLAUDE_ENV_FILE"
fi

# Download the Gradle distribution and plugin dependencies so they are cached with the container.
./gradlew "$GRADLE_FLAGS" --console=plain -q help >/dev/null
