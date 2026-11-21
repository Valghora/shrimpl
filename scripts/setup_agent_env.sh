#!/usr/bin/env bash
# Prepares a cloud or local machine for building and checking Shrimpl, for any coding agent: the
# Android SDK (API 37) and its tool check, the environment the Verify commands in AGENTS.md use
# (ANDROID_HOME, PATH, GRADLE_FLAGS), and a Gradle warm-up. Idempotent.
#
#   scripts/setup_agent_env.sh              writes the exports to a marked block in ~/.bashrc
#   scripts/setup_agent_env.sh <env-file>   appends them to <env-file> instead (Claude Code's
#                                           CLAUDE_ENV_FILE, from .claude/hooks/session-start.sh)
#
# Needs a JDK 21 (Gradle's toolchain for :core) on PATH or in JAVA_HOME.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$java_bin" -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])'; then
  echo "setup_agent_env: Shrimpl needs a JDK 21 or later on PATH or in JAVA_HOME" >&2
  exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
scripts/setup_android_sdk.sh

build_tools="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
# A project cache outside the checkout, so a fresh clone of the same path reuses it.
gradle_flags="--project-cache-dir=$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | sha1sum | cut -c1-8)/gradle"
exports="export ANDROID_HOME=\"$ANDROID_HOME\"
export PATH=\"$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$build_tools:\$PATH\"
export GRADLE_FLAGS=\"$gradle_flags\""

if [ -n "${1:-}" ]; then
  printf '%s\n' "$exports" >> "$1"
else
  # Replace an earlier block, so a rerun after an SDK update leaves one current copy.
  touch "$HOME/.bashrc"
  sed -i '/^# >>> shrimpl setup_agent_env >>>$/,/^# <<< shrimpl setup_agent_env <<<$/d' "$HOME/.bashrc"
  printf '# >>> shrimpl setup_agent_env >>>\n%s\n# <<< shrimpl setup_agent_env <<<\n' "$exports" >> "$HOME/.bashrc"
fi

# Download the Gradle distribution, plugins and the template's dependencies, so they're cached
# with the machine. Best-effort: a broken work-in-progress build script must not block a session.
warm=(help)
[ -f template/build.gradle.kts ] && warm+=(:template:dependencies)
./gradlew "$gradle_flags" --console=plain -q "${warm[@]}" >/dev/null \
  || echo "setup_agent_env: Gradle warm-up failed (build script error?); continuing" >&2
