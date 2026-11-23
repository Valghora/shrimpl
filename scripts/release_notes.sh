#!/usr/bin/env bash
# Prints the notes of the release being published (docs/SHRIMPL_SPEC.md R12.6): the section of
# CHANGELOG.md for that version, the user-facing changes Shrimpl's own What's new shows (R14.7).
# Fails when the change log has no such section, so a release never goes out without notes.
#
#   scripts/release_notes.sh [version]   default: shrimpl.appVersion in gradle.properties
set -euo pipefail

cd "$(dirname "$0")/.."
version="${1:-$(sed -n 's/^shrimpl\.appVersion=//p' gradle.properties)}"

# The lines after "## <version>" up to the next release's heading, without blank lines at either end.
notes="$(awk -v heading="## $version" '
  $0 == heading { inside = 1; next }
  inside && /^## / { exit }
  inside { print }
' CHANGELOG.md |
  # GitHub shows every line break in release notes, so an item wrapped over several lines in the
  # change log becomes one line: an indented line joins the one before it.
  awk 'NR > 1 && /^  +[^ ]/ { sub(/^ +/, " "); printf "%s", $0; next } NR > 1 { print "" } { printf "%s", $0 } END { print "" }' |
  sed -e '/./,$!d' | tac | sed -e '/./,$!d' | tac)"

if [[ -z "$notes" ]]; then
  echo "CHANGELOG.md has no section for $version: rename ## Unreleased to ## $version before the merge into main" >&2
  exit 1
fi
echo "$notes"
