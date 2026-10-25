#!/usr/bin/env bash
# Phase 6 verification driver for one site of the Universal Android Wrapper.
# Every site-specific value comes from scripts/site_config.py, so this works for any site.
#
# Usage: SITE=<id> scripts/ci_verify.sh [project_dir]   (default site: discord)
set -uo pipefail

PROJECT="$(cd "${1:-$(dirname "${BASH_SOURCE[0]}")/..}" && pwd)"
cd "$PROJECT"

if [[ ! -f template/build.gradle.kts ]]; then
  echo "no app module, skipping Phase 6"
  exit 0
fi

if command -v sha1sum >/dev/null; then hash() { sha1sum; }; else hash() { shasum -a 1; }; fi
SITE="${SITE:-discord}"
GRADLE_FLAGS=(--project-cache-dir="$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | hash | cut -c1-8)/gradle" "-Psite=$SITE")

if ! site_props="$(python3 scripts/site_config.py properties --site "$SITE")"; then
  echo "site '$SITE' is invalid" >&2
  exit 1
fi
prop() { sed -n "s/^$1=//p" <<<"$site_props"; }
APP_ID="$(prop appId)"
PACKAGE_NAME="$(prop applicationId)"
NAMESPACE="$(prop namespace)"
MIN_SDK="$(prop minSdk)"
TARGET_SDK="$(prop targetSdk)"
CORE_DIR="core/src/main/kotlin"
DEBUG_APK="template/build/outputs/apk/debug/template-debug.apk"
DEBUG_NAME="$(prop debugApkName)"
RELEASE_APK="$(prop releaseApkPath)"
RELEASE_NAME="$(prop releaseApkName)"
APK_DIR="$(prop apkDir)"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
AAPT2="$BUILD_TOOLS/aapt2"
APKSIGNER="$BUILD_TOOLS/apksigner"

echo "SITE=$SITE APP_ID=$APP_ID PACKAGE_NAME=$PACKAGE_NAME MIN_SDK=$MIN_SDK TARGET_SDK=$TARGET_SDK"

declare -a SUMMARY
status=0
record() { SUMMARY+=("$(printf '%-6s %s' "$1" "$2")"); [[ "$1" == FAIL ]] && status=1; }
section() { echo; echo "=== $* ==="; }

# 6.1 Gradle pipeline for this site. :core's tests and coverage need the neutral template, so they
# run without -Psite in CI's "UniWrap core and template" job.
section "6.1 gradle"
tasks=(:template:clean :template:testDebugUnitTest :template:lintDebug :template:assembleDebug :template:assembleRelease)
if ./gradlew "${GRADLE_FLAGS[@]}" --console=plain "${tasks[@]}"; then
  record PASS "6.1 gradle ${tasks[*]}"
else
  record FAIL "6.1 gradle ${tasks[*]}"
fi
# Generated site sources (profile asset, resources/icons, manifest overlay) of the debug variant.
GEN_RES="$(dirname "$(dirname "$(find template/build/generated -path '*ebug*' -path '*mipmap-anydpi*' -name ic_launcher.xml 2>/dev/null | head -1)")")"
GEN_PROFILE="$(find template/build/generated -path '*ebug*' -path '*uniwrap*' -name profile.json 2>/dev/null | head -1)"
GEN_MANIFEST="$(find template/build/generated -path '*ebug*' -name AndroidManifest.xml -path '*SiteSources*' 2>/dev/null | head -1)"
echo "generated: res=$GEN_RES profile=$GEN_PROFILE manifest=$GEN_MANIFEST"

# 6.2 Lint XML
section "6.2 lint"
lint_xml="$(ls template/build/reports/lint-results-debug.xml 2>/dev/null | head -1)"
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
  if ls "$GEN_RES"/mipmap-anydpi*/ic_launcher.xml >/dev/null 2>&1; then
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
if [[ -d "$GEN_RES" && "$GEN_RES" != "." ]]; then
  if python3 scripts/generate_icons.py --verify "$GEN_RES"; then record PASS "6.5 generate_icons.py --verify"; else record FAIL "6.5 generate_icons.py --verify"; fi
else
  record FAIL "6.5 generated resources not found"
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
static_none "no JavascriptInterface" -rnE 'addJavascriptInterface|@JavascriptInterface' template/src
static_none "no orientation/aspect attrs" -nE 'screenOrientation|minAspectRatio|maxAspectRatio' template/src/main/AndroidManifest.xml ${GEN_MANIFEST:+"$GEN_MANIFEST"}
# Placeholders: generated sources, the icon script and Gradle files; Kotlin only inside string literals.
placeholder_scope=(template/src settings.gradle.kts build.gradle.kts template/build.gradle.kts gradle/libs.versions.toml)
for generated in "$GEN_RES" "$GEN_PROFILE" "$GEN_MANIFEST"; do [[ -e "$generated" && "$generated" != "." ]] && placeholder_scope+=("$generated"); done
[[ -f scripts/generate_icons.py ]] && placeholder_scope+=(scripts/generate_icons.py)
static_none "no << >> placeholders (non-Kotlin)" -rnE '<<|>>' "${placeholder_scope[@]}" --exclude='*.kt'
static_none "no << >> placeholders (Kotlin strings)" -rnE '"[^"]*(<<|>>)[^"]*"' template/src core/src --include='*.kt'
ua_hits="$(grep -rnE 'userAgentString\s*=' template/src/main 2>/dev/null || true)"
ua_count="$(grep -c . <<<"$ua_hits" || true)"
[[ -z "$ua_hits" ]] && ua_count=0
echo "$ua_hits"
if [[ "$ua_count" == 1 ]]; then record PASS "6.6 exactly one userAgentString ="; else record FAIL "6.6 userAgentString = count is $ua_count (expected 1, inside applyWebSettings)"; fi

# 6.7 sites/<id>/apks/<version>/ (a rebuild of the same version replaces its APKs; the last 3 versions are kept)
section "6.7 APKs"
mkdir -p "$APK_DIR"
rm -f "$APK_DIR"/*.apk
if [[ -f "$DEBUG_APK" && -f "$RELEASE_APK" ]]; then
  cp "$DEBUG_APK" "$APK_DIR/$DEBUG_NAME"
  cp "$RELEASE_APK" "$APK_DIR/$RELEASE_NAME"
  ls -l "$APK_DIR"
  python3 scripts/site_config.py prune-apks --site "$SITE"
  record PASS "6.7 $APK_DIR/$DEBUG_NAME $APK_DIR/$RELEASE_NAME"
else
  record FAIL "6.7 APKs missing"
fi
if [[ "$RELEASE_NAME" == "$APP_ID-release.apk" && -x "$APKSIGNER" ]]; then
  if "$APKSIGNER" verify "$APK_DIR/$RELEASE_NAME" >/dev/null 2>&1; then
    signer="$("$APKSIGNER" verify --print-certs "$APK_DIR/$RELEASE_NAME" 2>/dev/null | sed -n 's/^Signer #1 certificate DN: //p;s/^V2 Signer: certificate DN: //p' | head -1)"
    record PASS "6.7 release APK signature verifies (${signer:-unknown signer})"
  else
    "$APKSIGNER" verify --verbose "$APK_DIR/$RELEASE_NAME" 2>&1 | tail -5
    record FAIL "6.7 release APK signature"
  fi
fi

section "summary"
printf '%s\n' "${SUMMARY[@]}"
exit $status
