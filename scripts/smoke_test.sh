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
WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-45}"
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
  adb logcat -d -s NetworkMonitor 2>/dev/null | grep -q "isCaptivePortal: isSuccessful()=true" && { online=1; break; }
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

# Wait for the page: the UA startup line appears at once; give the site time to render.
for _ in $(seq 1 "$WAIT_SECONDS"); do
  sleep 1
  adb logcat -d | grep -q "FATAL EXCEPTION" && break
done
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

colors="$(python3 -I - "$OUT/screen.png" <<'PY'
# Counts distinct (coarsely quantized) colors in the middle of the screen; a blank page has few.
import struct, sys, zlib
data = open(sys.argv[1], "rb").read()
assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
pos, idat, width = 8, b"", 0
while pos < len(data):
    length, kind = struct.unpack(">I4s", data[pos:pos + 8])
    body = data[pos + 8:pos + 8 + length]
    if kind == b"IHDR":
        width, height, depth, ctype = struct.unpack(">IIBB", body[:10])
        assert depth == 8 and ctype in (2, 6), f"unsupported PNG (depth {depth}, type {ctype})"
        bpp = 4 if ctype == 6 else 3
    elif kind == b"IDAT":
        idat += body
    pos += 12 + length
raw, stride = zlib.decompress(idat), width * bpp
rows, prev = [], bytearray(stride)
for y in range(height):
    line = raw[y * (stride + 1):(y + 1) * (stride + 1)]
    ftype, cur = line[0], bytearray(line[1:])
    for x in range(stride):
        a = cur[x - bpp] if x >= bpp else 0
        b, c = prev[x], prev[x - bpp] if x >= bpp else 0
        if ftype == 1: cur[x] = (cur[x] + a) % 256
        elif ftype == 2: cur[x] = (cur[x] + b) % 256
        elif ftype == 3: cur[x] = (cur[x] + (a + b) // 2) % 256
        elif ftype == 4:
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            cur[x] = (cur[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) % 256
    rows.append(cur)
    prev = cur
seen = set()
for gy in range(40):
    y = int(height * (0.15 + 0.7 * gy / 39))
    for gx in range(40):
        x = int(width * (0.05 + 0.9 * gx / 39))
        px = rows[y][x * bpp:x * bpp + 3]
        seen.add(tuple(v // 16 for v in px))
print(len(seen))
PY
)"
if [[ "${colors:-0}" -ge 6 ]]; then check pass "screen not blank ($colors colors)"; else check fail "screen looks blank (${colors:-?} colors)"; fi

echo "artifacts: $OUT/screen.png $OUT/logcat.txt"
exit $status
