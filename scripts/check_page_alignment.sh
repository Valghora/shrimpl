#!/usr/bin/env bash
# Fails if an APK's native libraries aren't aligned for 16 KB memory pages, which Android 15+ devices
# (and every Android 37 emulator image) can use: an unaligned library can't load there.
# Usage: scripts/check_page_alignment.sh <apk>...
set -euo pipefail

ANDROID_HOME="$(realpath -m "${ANDROID_HOME:-/opt/android-sdk}")"
zipalign="$(ls "$ANDROID_HOME"/build-tools/*/zipalign 2>/dev/null | sort -V | tail -1)"
[[ -x "$zipalign" ]] || { echo "ERROR: no zipalign under $ANDROID_HOME/build-tools" >&2; exit 1; }

status=0
for apk in "$@"; do
  if "$zipalign" -c -P 16 4 "$apk" >/dev/null 2>&1; then
    echo "$apk: native libraries are 16 KB aligned"
  else
    echo "ERROR: $apk has native libraries that aren't 16 KB aligned" >&2
    "$zipalign" -c -P 16 -v 4 "$apk" 2>&1 | grep -v "(OK" | head -10 >&2
    status=1
  fi
done
exit $status
