#!/usr/bin/env bash
# Runs connectedDebugAndroidTest for each module on the booted emulator (docs/SHRIMPL_SPEC.md R12.4).
# A module that reports zero tests is run once more, and fails the job if it still does: Gradle counts a
# run in which the instrumentation died at once as a pass, and a green job with no tests is worse than a
# red one (found 2026-11-08, when the template's tests silently didn't run).
set -uo pipefail

status=0
for module in template shrimpl; do
  tests=0
  rc=0
  for attempt in 1 2; do
    rerun=()
    [[ $attempt -gt 1 ]] && rerun=(--rerun)
    ./gradlew --console=plain --no-daemon ":$module:connectedDebugAndroidTest" "${rerun[@]}" -x :template:lintVitalRelease
    rc=$?
    tests=0
    for xml in "$module"/build/outputs/androidTest-results/connected/debug/TEST-*.xml; do
      [[ -f "$xml" ]] || continue
      count="$(grep -o -m1 'tests="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo 0)"
      tests=$((tests + ${count:-0}))
    done
    echo "[run_instrumented_tests] $module attempt $attempt: gradle exit $rc, $tests tests"
    [[ $tests -gt 0 ]] && break
    echo "::warning::$module reported 0 tests (attempt $attempt)"
  done
  if [[ $rc -ne 0 || $tests -eq 0 ]]; then
    echo "::error::$module instrumented tests failed (gradle exit $rc, $tests tests)"
    status=1
  fi
done
exit $status
