#!/bin/bash
# Checks comments against the code-comments skill (.claude/skills/code-comments/SKILL.md).
#
#   scripts/check_comments.sh                 fail on broken references and untracked TODOs; report
#                                             requirements nothing cites
#   scripts/check_comments.sh --diff <base>   also warn about narration in comment lines added since <base>
#
# Exits 1 when a check fails. Settings for another project are the variables below.
set -euo pipefail
cd "$(dirname "$0")/.."

# ----- Settings -----
SOURCES=(':(glob)**/src/**/*.kt')
SPECS=(docs/SHRIMPL_SPEC.md docs/TEMPLATE_SPEC.md)
# A defined requirement: a heading "## R2 ..." or a list item "- R2.6 ...".
DEFINITION='^(#+[[:space:]]+|[[:space:]]*-[[:space:]]+)[RT][0-9]+(\.[0-9]+)*\b'
DOTTED='\b[RT][0-9]+(\.[0-9]+)+\b'
BARE='(\(|, )[RT][0-9]+[),]'
DOC_ONLY=docs/comment-eval/doc-only-requirements.txt
NARRATION='\b(previously|used to be|as requested|we (added|changed|removed)|(was|were) (changed|added|removed|renamed)|now uses|updated to|instead of the old)\b|\b(new|old):'

fail=0
problem() { echo "check_comments: $*"; fail=1; }

# Comment lines of the Kotlin sources, as "path:line:text".
comment_lines() { git grep -nE '//|^\s*\*|/\*' -- "${SOURCES[@]}" | grep -vE '^[^:]+:[0-9]+:.*"[^"]*//[^"]*"' || true; }

defined="$(grep -hoE "$DEFINITION" "${SPECS[@]}" | grep -oE '[RT][0-9]+(\.[0-9]+)*' | sort -u)"
comments="$(comment_lines)"

# 1. Every requirement ID cited in a comment is defined.
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  where="${hit%%:*}:$(cut -d: -f2 <<<"$hit")"
  for id in $(grep -oE "$DOTTED" <<<"${hit#*:*:}") $(grep -oE "$BARE" <<<"${hit#*:*:}" | grep -oE '[RT][0-9]+'); do
    grep -qx "$id" <<<"$defined" || problem "$where cites $id, which the specs don't define"
  done
done <<<"$(grep -E "$DOTTED|$BARE" <<<"$comments" || true)"

# 2. Every docs/ or scripts/ path in a comment exists.
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  for path in $(grep -oE '\b(docs|scripts)/[A-Za-z0-9_./-]+\.[a-z]+' <<<"${hit#*:*:}"); do
    [ -e "$path" ] || problem "${hit%%:*}:$(cut -d: -f2 <<<"$hit") names $path, which doesn't exist"
  done
done <<<"$(grep -E '\b(docs|scripts)/' <<<"$comments" || true)"

# 3. TODO and FIXME carry a requirement ID or an issue: TODO(R2.6), TODO(#12).
while IFS= read -r hit; do
  [ -n "$hit" ] && problem "${hit%%:*}:$(cut -d: -f2 <<<"$hit") has a TODO or FIXME without a requirement or issue"
done <<<"$(grep -E '\b(TODO|FIXME)\b' <<<"$comments" | grep -vE '\b(TODO|FIXME)\((R|T)[0-9]|\b(TODO|FIXME)\(#[0-9]' || true)"

# 4. Requirements that nothing cites and that aren't listed as met outside the code (a report, not a failure).
cited="$(git grep -hoE "$DOTTED" -- "${SOURCES[@]}" | sort -u)"
listed=""
[ -f "$DOC_ONLY" ] && listed="$(grep -vE '^\s*(#|$)' "$DOC_ONLY" | awk '{print $1}' | sort -u)"
untraced=()
for id in $(grep -E '\.' <<<"$defined"); do
  grep -q "^$id\." <<<"$defined" && continue  # not a leaf
  grep -qx "$id" <<<"$cited" || grep -q "^$id\." <<<"$cited" || grep -qx "$id" <<<"$listed" || untraced+=("$id")
done
for id in $listed; do
  grep -qx "$id" <<<"$defined" || problem "$DOC_ONLY lists $id, which the specs don't define"
done
[ "${#untraced[@]}" -eq 0 ] || echo "check_comments: not cited in code or tests, and not in $DOC_ONLY: ${untraced[*]}"

# 5. With --diff: narration in added comment lines (a warning; history belongs in commit messages).
if [ "${1:-}" = --diff ]; then
  base="${2:?usage: $0 --diff <base>}"
  git diff -U0 "$base" -- "${SOURCES[@]}" | grep -E '^\+' | grep -vE '^\+\+\+' | grep -E '//|^\+\s*\*' |
    grep -iE "$NARRATION" | sed 's/^/check_comments: warning: narration in an added comment: /' || true
fi

[ "$fail" = 0 ] && echo "check_comments: references and TODOs are in order"
exit "$fail"
