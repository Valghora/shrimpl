#!/usr/bin/env bash
# Emulator/device smoke test for one site's debug APK: install, launch, wait for the page, and
# fail on a crash, a missing startup log line, or a blank screen.
#
#   scripts/smoke_test.sh SITE [APK] [OUT_DIR]
#
# Needs a booted emulator or device on adb. CI starts one in .github/workflows/android.yml.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
SITE="${1:?usage: smoke_test.sh SITE [APK] [OUT_DIR]}"
props="$(python3 scripts/site_config.py properties --site "$SITE")" || exit 1
prop() { sed -n "s/^$1=//p" <<<"$props"; }
PACKAGE="$(prop applicationId)"
ACTIVITY="$(prop namespace).MainActivity"
APP_ID="$(prop appId)"
APK="${2:-dist/$(prop debugApkName)}"
OUT="${3:-dist/smoke-$SITE}"
WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-180}"
mkdir -p "$OUT"

status=0
check() { if [[ "$1" == pass ]]; then echo "PASS   $2"; else echo "FAIL   $2"; status=1; fi; }

# Every adb call is bounded: a wedged emulator must fail the test, not hang the job.
adb() { timeout "${ADB_TIMEOUT:-60}" "$(type -P adb)" "$@"; }

adb wait-for-device || { echo "FAIL   no device"; exit 1; }
# The package manager comes up a little after sys.boot_completed.
for _ in $(seq 1 30); do adb shell pm path android >/dev/null 2>&1 && break; sleep 2; done
# Wi-Fi comes up a while after boot. Launching before Android has validated internet access makes the
# page fail with ERR_NAME_NOT_RESOLVED, which is the runner's problem, not the app's.
online=0
for _ in $(seq 1 90); do
  adb shell dumpsys connectivity 2>/dev/null \
    | grep -qE 'ni\{[A-Z_]+(\[[A-Z_]+\])? CONNECTED.*Capabilities: [A-Z_&]*&VALIDATED&' && { online=1; break; }
  sleep 2
done
if [[ $online != 1 ]]; then
  adb shell dumpsys connectivity > "$OUT/connectivity.txt" 2>&1
  adb logcat -d > "$OUT/logcat.txt"
  echo "FAIL   the emulator has no validated internet connection after 3 minutes; not an app failure"
  exit 1
fi
# A slow first boot can leave a "System UI isn't responding" dialog over the screen.
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
# -g grants runtime permissions up front so no permission dialog covers the page.
ADB_TIMEOUT=180 adb install -r -g "$APK" >/dev/null || { echo "FAIL   install $APK"; exit 1; }
adb logcat -c
adb shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null || echo "note: am start did not report back in time"

# Count distinct (coarsely quantized) colors in the middle of the screen; a blank page has few.
# Uses the raw screencap format: a 16-byte header (width, height, format, dataspace), then RGBA rows.
count_colors() {
  python3 -I -c '
import struct, sys
data = sys.stdin.buffer.read()
width, height = struct.unpack("<II", data[:8])
pixels = data[16:]
seen = set()
for gy in range(40):
    y = int(height * (0.15 + 0.7 * gy / 39))
    for gx in range(40):
        i = (y * width + int(width * (0.05 + 0.9 * gx / 39))) * 4
        seen.add(tuple(v // 16 for v in pixels[i:i + 3]))
print(len(seen))' 2>/dev/null
}

# Wait for the page. The startup line appears at once, but a large web app can take a minute or more
# to paint on an emulator with software rendering, so poll the screen until it shows content.
colors=0
waited=0
while (( waited < WAIT_SECONDS )); do
  sleep 10; waited=$((waited + 10))
  adb logcat -d | grep -q "FATAL EXCEPTION" && break
  colors="$(adb exec-out screencap | count_colors)"
  [[ "${colors:-0}" -ge 6 ]] && break
done
echo "waited ${waited}s for the page"
adb logcat -d > "$OUT/logcat.txt"
adb exec-out screencap -p > "$OUT/screen.png"

if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
  grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -25
  check fail "no crash (FATAL EXCEPTION in $PACKAGE)"
else
  check pass "no crash"
fi
if [[ -n "$(adb shell pidof "$PACKAGE" | tr -d '\r')" ]]; then check pass "process alive"; else check fail "process alive"; fi
ua_line="$(grep -E " $APP_ID *: UA mode=" "$OUT/logcat.txt" | head -1)"
if [[ -n "$ua_line" ]]; then check pass "startup log: ${ua_line#*: }"; else check fail "startup log line (tag $APP_ID) missing"; fi

if [[ "${colors:-0}" -ge 6 ]]; then check pass "screen not blank ($colors colors)"; else check fail "screen looks blank after ${waited}s (${colors:-?} colors)"; fi

echo "artifacts: $OUT/screen.png $OUT/logcat.txt"
exit $status
