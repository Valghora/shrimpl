#!/bin/bash
# Where a requirement lives: its spec line, then the code and the tests that cite it
# (.claude/skills/code-comments/SKILL.md).
#
#   scripts/spec_map.sh R2.6 [T6.5 ...]
#
# Includes children (R2.6 also finds R2.6.1). Settings for another project are the variables below.
set -euo pipefail
cd "$(dirname "$0")/.."

# ----- Settings -----
SPECS=(docs/SHRIMPL_SPEC.md docs/TEMPLATE_SPEC.md docs/BUILD_NOTES.md docs/TESTING.md docs/DEVIATIONS.md)
MAIN=(':(glob)**/src/main/**/*.kt')
TESTS=(':(glob)**/src/test/**/*.kt' ':(glob)**/src/androidTest/**/*.kt')

[ "$#" -gt 0 ] || { sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 64; }
for id in "$@"; do
  pattern="\\b${id//./\\.}(\\.[0-9]+)*\\b"
  echo "== $id"
  echo "-- spec"
  grep -nE "^(#+ |\s*- )$pattern" "${SPECS[@]}" | cut -c1-160 || echo "(not defined)"
  echo "-- code"
  git grep -nE "$pattern" -- "${MAIN[@]}" | cut -c1-160 || echo "(none)"
  echo "-- tests"
  git grep -nE "$pattern" -- "${TESTS[@]}" | cut -c1-160 || echo "(none)"
done
