#!/usr/bin/env bash
# Collects what is needed to understand a failed instrumented test: logcat, a screenshot, the UI
# hierarchy and the emulator's own log (docs/TESTING.md, "The emulator's environment"). Safe to run when no
# emulator is up: it then collects nothing.
set -uo pipefail

export ANDROID_HOME="$(realpath -m "${ANDROID_HOME:-/opt/android-sdk}")"
ADB="$ANDROID_HOME/platform-tools/adb"
OUT="${EMULATOR_DIR:-$PWD/emulator-out}"
mkdir -p "$OUT"

if "$ADB" get-state >/dev/null 2>&1; then
  "$ADB" logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1
  "$ADB" exec-out screencap -p > "$OUT/screen.png" 2>/dev/null
  "$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 && "$ADB" pull /sdcard/window.xml "$OUT/window.xml" >/dev/null 2>&1
  "$ADB" shell dumpsys activity activities 2>/dev/null | head -80 > "$OUT/activities.txt"
  # Screenshots and window dumps the tests themselves saved when they failed.
  "$ADB" pull /data/local/tmp/evidence "$OUT/evidence" >/dev/null 2>&1 || true
fi
echo "[collect_emulator_artifacts] $(ls "$OUT" | wc -l) files in $OUT"
