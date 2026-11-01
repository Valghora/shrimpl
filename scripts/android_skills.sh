#!/bin/bash
# Keeps the Android skills vendored from Google's android/skills repository current (docs/SKILLS.md).
#
# Each skill is copied unchanged into .claude/skills/<name>/ with upstream's LICENSE.txt and an
# UPSTREAM.md recording the upstream path and the commit it was copied from.
#
#   scripts/android_skills.sh list                  vendored skills and their upstream dates
#   scripts/android_skills.sh check                 compare every vendored skill with upstream, and
#                                                   name upstream skills docs/SKILLS.md doesn't mention
#   scripts/android_skills.sh update <name>...|--all  replace skills with the newest upstream copy
#   scripts/android_skills.sh add <upstream path>... vendor new skills, e.g. security/android-intent-security
#
# check exits 0 when everything is current, 1 when something needs review, 2 when upstream can't be
# reached. Upstream is mirrored in $ANDROID_SKILLS_CACHE (default ~/.cache/android-skills).
set -euo pipefail

REPO_URL="https://github.com/android/skills"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKILLS="$ROOT/.claude/skills"
DOC="$ROOT/docs/SKILLS.md"
CACHE="${ANDROID_SKILLS_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/android-skills}"

up() { git -C "$CACHE" "$@"; }

fetch_upstream() {
  if [ -d "$CACHE/refs" ]; then
    up fetch -q --prune origin || return 1
  else
    mkdir -p "$(dirname "$CACHE")"
    git clone -q --mirror "$REPO_URL" "$CACHE" || return 1
  fi
}

# Field from a skill's UPSTREAM.md ("- Path: x" -> x).
field() { sed -n "s/^- $2: *//p" "$SKILLS/$1/UPSTREAM.md" | head -1; }

vendored() {
  local f
  for f in "$SKILLS"/*/UPSTREAM.md; do
    [ -e "$f" ] || continue
    grep -q 'github.com/android/skills' "$f" && basename "$(dirname "$f")"
  done
}

# "blob path" lines for the files of a skill, leaving out the files this project adds.
upstream_files() {
  up ls-tree -r "$1:$2" 2>/dev/null | awk -F'\t' '{split($1, a, " "); print a[3], $2}' |
    grep -Ev ' (UPSTREAM\.md|LICENSE\.txt)$' | sort
}
local_files() {
  (cd "$SKILLS/$1" && find . -type f ! -path ./UPSTREAM.md ! -path ./LICENSE.txt | sed 's|^\./||' |
    sort | while read -r file; do echo "$(git hash-object --no-filters "$file") $file"; done) | sort
}

upstream_date() {
  up show "$1:$2/SKILL.md" 2>/dev/null | sed -n "s/^ *last-updated: *'\{0,1\}\([0-9-]*\).*/\1/p" | head -1
}

# current | outdated | modified | removed
status_of() {
  local name="$1" path commit here
  path="$(field "$name" Path)"
  commit="$(field "$name" Commit)"
  if [ -z "$(up ls-tree HEAD "$path" 2>/dev/null)" ]; then echo removed; return; fi
  here="$(local_files "$name")"
  if [ "$here" = "$(upstream_files HEAD "$path")" ]; then echo current
  elif [ -n "$commit" ] && [ "$here" = "$(upstream_files "$commit" "$path")" ]; then echo outdated
  else echo modified
  fi
}

write_upstream_md() {
  local name="$1" path="$2" commit="$3" applied
  if [ "$name" = adaptive ]; then applied="docs/ADAPTIVE.md"; else applied="docs/SKILLS.md"; fi
  cat > "$SKILLS/$name/UPSTREAM.md" <<EOF
# Upstream

Vendored unchanged from Google's Android skills, Apache License 2.0 (see LICENSE.txt):

- Repository: $REPO_URL
- Path: $path
- Commit: $commit
- Last updated upstream: $(upstream_date "$commit" "$path")
- Copied: $(date -u +%F)

How this project applies it, and where it deliberately deviates, is in $applied. Don't edit these
files: update them with \`scripts/android_skills.sh update $name\`, which copies the newest upstream
version and rewrites this file.
EOF
}

copy_skill() {
  local name="$1" path="$2" commit
  commit="$(up rev-parse HEAD)"
  mkdir -p "$SKILLS/$name"
  find "$SKILLS/$name" -mindepth 1 -maxdepth 1 ! -name UPSTREAM.md -exec rm -rf {} +
  up archive --format=tar "HEAD:$path" | tar -x -C "$SKILLS/$name"
  up show HEAD:LICENSE.txt > "$SKILLS/$name/LICENSE.txt"
  write_upstream_md "$name" "$path" "$commit"
  echo "$name: copied $path at ${commit:0:12} (last updated $(upstream_date HEAD "$path"))"
}

cmd_list() {
  local name
  for name in $(vendored); do
    printf '%-30s %-50s last updated %s, copied %s\n' "$name" "$(field "$name" Path)" \
      "$(field "$name" 'Last updated upstream')" "$(field "$name" Copied)"
  done
}

cmd_check() {
  fetch_upstream || { echo "android skills: can't reach $REPO_URL" >&2; return 2; }
  local name state path dir review=0
  for name in $(vendored); do
    state="$(status_of "$name")"
    path="$(field "$name" Path)"
    case "$state" in
      current) ;;
      outdated)
        echo "android skills: $name has an upstream update (last updated $(upstream_date HEAD "$path")," \
          "ours $(field "$name" 'Last updated upstream')); run scripts/android_skills.sh update $name," \
          "then review the diff against docs/SKILLS.md"
        review=1 ;;
      modified)
        echo "android skills: $name differs from upstream $path at its recorded commit; vendored skills" \
          "stay unchanged, so restore it or update it"
        review=1 ;;
      removed)
        echo "android skills: $name ($path) no longer exists upstream; check whether it moved or was retired"
        review=1 ;;
    esac
  done
  for dir in $(up ls-tree -r --name-only HEAD | sed -n 's|/SKILL\.md$||p'); do
    if [ -f "$DOC" ] && ! grep -qF "\`$dir\`" "$DOC"; then
      echo "android skills: new upstream skill $dir (last updated $(upstream_date HEAD "$dir"));" \
        "decide whether it applies and record it in docs/SKILLS.md"
      review=1
    fi
  done
  [ "$review" = 0 ] && echo "android skills: all $(vendored | wc -l) vendored skills are current"
  return "$review"
}

cmd_update() {
  fetch_upstream || { echo "can't reach $REPO_URL" >&2; return 2; }
  local names=("$@") name state
  if [ "${1:-}" = --all ]; then mapfile -t names < <(vendored); fi
  [ "${#names[@]}" -gt 0 ] || { echo "usage: $0 update <name>...|--all" >&2; return 64; }
  for name in "${names[@]}"; do
    [ -f "$SKILLS/$name/UPSTREAM.md" ] || { echo "$name: not a vendored skill" >&2; return 1; }
    state="$(status_of "$name")"
    case "$state" in
      current) echo "$name: already current" ;;
      removed) echo "$name: $(field "$name" Path) no longer exists upstream; nothing copied" >&2 ;;
      *) copy_skill "$name" "$(field "$name" Path)" ;;
    esac
  done
  echo "Review the changes (git diff .claude/skills) against docs/SKILLS.md and commit them on their own."
}

cmd_add() {
  fetch_upstream || { echo "can't reach $REPO_URL" >&2; return 2; }
  [ "$#" -gt 0 ] || { echo "usage: $0 add <upstream path>..." >&2; return 64; }
  local path name
  for path in "$@"; do
    path="${path%/}"
    up cat-file -e "HEAD:$path/SKILL.md" 2>/dev/null || { echo "$path: no SKILL.md upstream" >&2; return 1; }
    name="$(basename "$path")"
    [ ! -e "$SKILLS/$name" ] || { echo "$name: already in .claude/skills" >&2; return 1; }
    copy_skill "$name" "$path"
  done
  echo "Record each new skill, when to use it and its deliberate deviations in docs/SKILLS.md."
}

case "${1:-}" in
  list) cmd_list ;;
  check) cmd_check ;;
  update) shift; cmd_update "$@" ;;
  add) shift; cmd_add "$@" ;;
  *) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 64 ;;
esac
