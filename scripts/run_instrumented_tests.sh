#!/usr/bin/env bash
# Runs connectedDebugAndroidTest for both modules on the booted emulator (docs/SHRIMPL_SPEC.md R12.4), in
# one Gradle invocation: a second one costs a minute of startup and configuration. --continue lets the second
# module run when the first fails, so a run shows every failure at once.
# A module that reports zero tests is run again alone, and fails the job if it still reports none: Gradle
# counts a run in which the instrumentation died at once as a pass, and a green job with no tests is worse
# than a red one (found 2026-11-08, when the template's tests silently didn't run).
set -uo pipefail

modules=(template shrimpl)

# Prints "tests failures errors" summed over a module's result files.
summarize() {
  local tests=0 failures=0 errors=0 xml head
  for xml in "$1"/build/outputs/androidTest-results/connected/debug/TEST-*.xml; do
    [[ -f "$xml" ]] || continue
    head="$(grep -o -m1 '<testsuite [^>]*>' "$xml" | head -1)"
    tests=$((tests + $(grep -o 'tests="[0-9]*"' <<<"$head" | grep -o '[0-9]*' || echo 0)))
    failures=$((failures + $(grep -o 'failures="[0-9]*"' <<<"$head" | grep -o '[0-9]*' || echo 0)))
    errors=$((errors + $(grep -o 'errors="[0-9]*"' <<<"$head" | grep -o '[0-9]*' || echo 0)))
  done
  echo "$tests $failures $errors"
}

./gradlew --console=plain --no-daemon --continue \
  :template:connectedDebugAndroidTest :shrimpl:connectedDebugAndroidTest -x :template:lintVitalRelease
first_rc=$?

status=0
any_failed=0
for module in "${modules[@]}"; do
  read -r tests failures errors <<<"$(summarize "$module")"
  echo "[run_instrumented_tests] $module: $tests tests, $failures failures, $errors errors"
  if [[ $tests -eq 0 ]]; then
    echo "::warning::$module reported 0 tests; running it again alone"
    ./gradlew --console=plain --no-daemon ":$module:connectedDebugAndroidTest" --rerun -x :template:lintVitalRelease
    read -r tests failures errors <<<"$(summarize "$module")"
    echo "[run_instrumented_tests] $module again: $tests tests, $failures failures, $errors errors"
  fi
  if [[ $tests -eq 0 ]]; then
    echo "::error::$module reported no tests"
    status=1
  fi
  if [[ $failures -gt 0 || $errors -gt 0 ]]; then
    echo "::error::$module had $failures failures and $errors errors"
    status=1
    any_failed=1
  fi
done

# Gradle failed but no module shows a failed test: a build or install problem that must not pass.
if [[ $first_rc -ne 0 && $any_failed -eq 0 && $status -eq 0 ]]; then
  echo "::error::Gradle exited $first_rc with no failed test in the results"
  status=1
fi
exit $status
