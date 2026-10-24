#!/usr/bin/env bash
# Phase 6 verification driver (config-agnostic).
# Every app-specific value is read from the project itself, so this works for any
# <config> in the Universal APK Wrapper spec.
#
# Usage: scripts/ci_verify.sh [project_dir]   (default: repo root)
set -uo pipefail

PROJECT="$(cd "${1:-$(dirname "${BASH_SOURCE[0]}")/..}" && pwd)"
cd "$PROJECT"

if [[ ! -f app/build.gradle.kts ]]; then
  echo "no app module, skipping Phase 6"
  exit 0
fi

if command -v sha1sum >/dev/null; then hash() { sha1sum; }; else hash() { shasum -a 1; }; fi
GRADLE_FLAGS=(--project-cache-dir="$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | hash | cut -c1-8)/gradle")

gradle_kts="app/build.gradle.kts"
kts_value() { grep -oE "$1\s*=\s*\"?[^\"[:space:]]+\"?" "$gradle_kts" | head -1 | sed -E 's/.*=\s*"?([^"]+)"?/\1/'; }
APP_ID="$(grep -oE 'rootProject\.name\s*=\s*"[^"]+"' settings.gradle.kts | sed -E 's/.*"([^"]+)"/\1/')"
PACKAGE_NAME="$(kts_value applicationId)"
# Accepts both `minSdk = 37` and AGP 9's `minSdk { version = release(37) }` (same line).
sdk_value() { grep -oE "$1[^0-9]*[0-9]+" "$gradle_kts" | head -1 | grep -oE '[0-9]+$'; }
MIN_SDK="$(sdk_value minSdk)"
TARGET_SDK="$(sdk_value targetSdk)"
CORE_DIR="app/src/main/java/${PACKAGE_NAME//.//}/core"
[[ -d "$CORE_DIR" ]] || CORE_DIR="app/src/main/kotlin/${PACKAGE_NAME//.//}/core"
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="app/build/outputs/apk/release/app-release-unsigned.apk"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
AAPT2="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"

echo "APP_ID=$APP_ID PACKAGE_NAME=$PACKAGE_NAME MIN_SDK=$MIN_SDK TARGET_SDK=$TARGET_SDK"

declare -a SUMMARY
status=0
record() { SUMMARY+=("$(printf '%-6s %s' "$1" "$2")"); [[ "$1" == FAIL ]] && status=1; }
section() { echo; echo "=== $* ==="; }

# 6.1 Gradle pipeline. The JaCoCo tasks are included only once they exist (Phase 2).
section "6.1 gradle"
tasks=(clean testDebugUnitTest)
if ./gradlew "${GRADLE_FLAGS[@]}" -q :app:tasks --all 2>/dev/null | grep -q '^jacocoTestCoverageVerification'; then
  tasks+=(jacocoTestReport jacocoTestCoverageVerification)
else
  echo "note: jacoco tasks not defined yet; skipping them"
fi
tasks+=(lintDebug assembleDebug assembleRelease)
if ./gradlew "${GRADLE_FLAGS[@]}" --console=plain "${tasks[@]}"; then
  record PASS "6.1 gradle ${tasks[*]}"
else
  record FAIL "6.1 gradle ${tasks[*]}"
fi

# 6.2 Lint XML
section "6.2 lint"
lint_xml="$(ls app/build/reports/lint-results-debug.xml 2>/dev/null | head -1)"
if [[ -f "$lint_xml" ]]; then
  read -r errs unused obsolete < <(python3 -I - "$lint_xml" <<'PY'
import sys, xml.etree.ElementTree as ET
issues = ET.parse(sys.argv[1]).getroot().findall("issue")
for i in issues:
    if i.get("severity") not in ("Error", "Fatal"):
        loc = i.find("location")
        where = f"{loc.get('file')}:{loc.get('line')}" if loc is not None else ""
        print(f"  warning {i.get('id')}: {i.get('message')} {where}", file=sys.stderr)
e = sum(i.get("severity") in ("Error", "Fatal") for i in issues)
u = sum(i.get("id") == "UnusedResources" for i in issues)
o = sum(i.get("id") == "ObsoleteSdkInt" for i in issues)
print(e, u, o)
PY
  )
  msg="errors=$errs UnusedResources=$unused ObsoleteSdkInt=$obsolete"
  if [[ "$errs" == 0 && "$unused" == 0 && "$obsolete" == 0 ]]; then record PASS "6.2 lint $msg"; else record FAIL "6.2 lint $msg"; fi
else
  record FAIL "6.2 lint report not found"
fi

# 6.3 No native code
section "6.3 native libs"
for apk in "$DEBUG_APK" "$RELEASE_APK"; do
  if [[ ! -f "$apk" ]]; then record FAIL "6.3 $apk missing"; continue; fi
  hits="$(unzip -l "$apk" | awk '{print $4}' | grep -E '^lib/|\.so$' || true)"
  if [[ -z "$hits" ]]; then record PASS "6.3 no lib/ or .so in $(basename "$apk")"; else echo "$hits"; record FAIL "6.3 native entries in $(basename "$apk")"; fi
done

# 6.4 aapt2 badging (debug APK)
section "6.4 badging"
if [[ -f "$DEBUG_APK" && -x "$AAPT2" ]]; then
  badging="$("$AAPT2" dump badging "$DEBUG_APK" 2>&1)"
  echo "$badging" | grep -E "^(package|(min)?[sS]dkVersion|targetSdkVersion|application|launchable-activity):"
  ok=1
  grep -q "^package: name='$PACKAGE_NAME'" <<<"$badging" || { echo "package name mismatch"; ok=0; }
  # aapt2 37 prints "minSdkVersion:"; older aapt2 printed "sdkVersion:".
  grep -qE "^(min)?[sS]dkVersion:'$MIN_SDK'" <<<"$badging" || { echo "sdkVersion mismatch"; ok=0; }
  grep -q "^targetSdkVersion:'$TARGET_SDK'" <<<"$badging" || { echo "targetSdkVersion mismatch"; ok=0; }
  # aapt2 auto-versions anydpi resources (observed: mipmap-anydpi-v21), so accept any -vNN qualifier.
  icon_re="icon='res/mipmap-anydpi(-v[0-9]+)?/ic_launcher\.xml'"
  if ls app/src/main/res/mipmap-anydpi*/ic_launcher.xml >/dev/null 2>&1; then
    grep -E "^application:" <<<"$badging" | grep -qE "$icon_re" || { echo "application icon mismatch"; ok=0; }
    grep -E "^launchable-activity:" <<<"$badging" | grep -qE "$icon_re" || { echo "launchable-activity icon mismatch"; ok=0; }
  else
    echo "note: no mipmap-anydpi/ic_launcher.xml yet; icon check skipped"
  fi
  if [[ $ok == 1 ]]; then record PASS "6.4 badging"; else record FAIL "6.4 badging"; fi
else
  record FAIL "6.4 debug APK or aapt2 missing"
fi

# 6.5 Icon verifier
section "6.5 icons"
if [[ -f scripts/generate_icons.py ]]; then
  if python3 scripts/generate_icons.py --verify; then record PASS "6.5 generate_icons.py --verify"; else record FAIL "6.5 generate_icons.py --verify"; fi
else
  record SKIP "6.5 scripts/generate_icons.py not present"
fi

# 6.6 Static checks
section "6.6 static checks"
static_none() { # label, then grep args; passes when grep finds nothing
  local label="$1"; shift
  local out; out="$(grep "$@" 2>/dev/null || true)"
  if [[ -z "$out" ]]; then record PASS "6.6 $label"; else echo "$out"; record FAIL "6.6 $label"; fi
}
if [[ -d "$CORE_DIR" ]]; then
  static_none "core has no android imports" -rnE '^import (android|androidx|com\.google\.android)\.' "$CORE_DIR"
else
  record SKIP "6.6 core package dir not present"
fi
static_none "no JavascriptInterface" -rnE 'addJavascriptInterface|@JavascriptInterface' app/src
static_none "no orientation/aspect attrs" -nE 'screenOrientation|minAspectRatio|maxAspectRatio' app/src/main/AndroidManifest.xml
# Placeholders: XML/resources/scripts/Gradle anywhere, Kotlin only inside string literals (skip shl/shr operators).
static_none "no << >> placeholders (non-Kotlin)" -rnE '<<|>>' app/src scripts settings.gradle.kts build.gradle.kts app/build.gradle.kts gradle/libs.versions.toml \
  --exclude='*.kt' --exclude='ci_verify.sh' --exclude='setup_android_sdk.sh'
static_none "no << >> placeholders (Kotlin strings)" -rnE '"[^"]*(<<|>>)[^"]*"' app/src --include='*.kt'
ua_hits="$(grep -rnE 'userAgentString\s*=' app/src/main 2>/dev/null || true)"
ua_count="$(grep -c . <<<"$ua_hits" || true)"
[[ -z "$ua_hits" ]] && ua_count=0
echo "$ua_hits"
if [[ "$ua_count" == 1 ]]; then record PASS "6.6 exactly one userAgentString ="; else record FAIL "6.6 userAgentString = count is $ua_count (expected 1, inside applyWebSettings)"; fi

# 6.7 dist/
section "6.7 dist"
mkdir -p dist
if [[ -f "$DEBUG_APK" && -f "$RELEASE_APK" ]]; then
  cp "$DEBUG_APK" "dist/$APP_ID-debug.apk"
  cp "$RELEASE_APK" "dist/$APP_ID-release-unsigned.apk"
  ls -l dist/
  record PASS "6.7 dist/$APP_ID-debug.apk dist/$APP_ID-release-unsigned.apk"
else
  record FAIL "6.7 APKs missing"
fi

section "summary"
printf '%s\n' "${SUMMARY[@]}"
exit $status
