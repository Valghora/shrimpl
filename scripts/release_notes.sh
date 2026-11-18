#!/usr/bin/env bash
# Prints the notes of the release being published (docs/SHRIMPL_SPEC.md R12.6): one line per change
# merged into develop since the previous release, from the merge commits' summaries. No names, no
# mentions and no contributor list.
#
#   scripts/release_notes.sh        notes for HEAD, since the newest v* tag it contains
set -euo pipefail

previous="$(git tag --merged HEAD --list 'v*' --sort=-v:refname | head -n1)"
range="${previous:+$previous..}HEAD"

# Merges into develop read "Merge <branch>: <summary>[, Shrimpl x.y.z]"; merges of develop into main
# ("Merge develop into main: ...") have no branch-colon form, so they drop out here.
changes="$(git log --merges --reverse --format=%s "$range" |
  sed -nE 's/^Merge [^ ]+: (.*)$/\1/p' |
  # Requirement IDs and CI markers are for the repository, not for readers of the release.
  sed -E 's/, Shrimpl [0-9]+(\.[0-9]+)*$//; s/ \[skip ci\]//; s/ \(R[0-9.]+(, R[0-9.]+)*\)//g; s/@/@\xe2\x80\x8b/g' |
  awk 'NF { print "- " toupper(substr($0, 1, 1)) substr($0, 2) }')"

echo "## What's new"
echo
echo "${changes:-- Maintenance and fixes}"
