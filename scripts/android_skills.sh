#!/bin/bash
# Keeps the vendored Android skills and skill references current (docs/SKILLS.md).
#
# Each vendored folder is copied unchanged from a GitHub repository, with the repository's licence and
# an UPSTREAM.md recording where it came from:
#   .claude/skills/<name>/       a whole skill folder (Path: a directory), e.g. from android/skills
#   .claude/references/<name>/   chosen files of a larger skill (one Path: line per file)
#
#   scripts/android_skills.sh list                   vendored folders and their upstream dates
#   scripts/android_skills.sh check                  compare every vendored folder with upstream, and
#                                                    name android/skills skills docs/SKILLS.md doesn't mention
#   scripts/android_skills.sh update <name>...|--all replace folders with the newest upstream copy
#   scripts/android_skills.sh add <path>...          vendor android/skills skills, e.g. security/android-intent-security
#   scripts/android_skills.sh add --repo <github url> --into <name> <file>...
#                                                    vendor files of another repository into .claude/references/<name>
#
# check exits 0 when everything is current, 1 when something needs review, 2 when an upstream can't be
# reached. Upstreams are mirrored under $ANDROID_SKILLS_CACHE (default ~/.cache/android-skills).
set -euo pipefail

GOOGLE="https://github.com/android/skills"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLAUDE="$ROOT/.claude"
DOC="$ROOT/docs/SKILLS.md"
CACHE_ROOT="${ANDROID_SKILLS_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/android-skills}"

# The local mirror of repository URL $1 (android/skills keeps the cache root of earlier versions).
mirror() {
  if [ "$1" = "$GOOGLE" ]; then echo "$CACHE_ROOT"; else echo "$CACHE_ROOT-$(basename "$(dirname "$1")")-$(basename "$1")"; fi
}

FETCHED=" "
fetch() {
  local dir
  dir="$(mirror "$1")"
  case "$FETCHED" in *" $1 "*) return 0 ;; esac
  if [ -d "$dir/refs" ]; then
    git -C "$dir" fetch -q --prune origin || return 1
  else
    mkdir -p "$(dirname "$dir")"
    git clone -q --mirror "$1" "$dir" || return 1
  fi
  FETCHED="$FETCHED$1 "
}

# A vendored folder, relative to .claude ("skills/adaptive", "references/claude-android-ninja").
vendored() {
  local f
  for f in "$CLAUDE"/skills/*/UPSTREAM.md "$CLAUDE"/references/*/UPSTREAM.md; do
    [ -e "$f" ] && grep -q '^- Repository: https://github.com/' "$f" && echo "${f#"$CLAUDE"/}" | sed 's|/UPSTREAM.md$||'
  done
}
resolve() {
  local v
  for v in $(vendored); do [ "$v" = "$1" ] || [ "$(basename "$v")" = "$1" ] && { echo "$v"; return; }; done
  echo "$1: not a vendored folder" >&2
  return 1
}

field() { sed -n "s/^- $2: *//p" "$CLAUDE/$1/UPSTREAM.md" | head -1; }
paths() { sed -n 's/^- Path: *//p' "$CLAUDE/$1/UPSTREAM.md"; }
repo() { field "$1" Repository; }
up() { git -C "$(mirror "$(repo "$V")")" "${@}"; }

# Files this project adds next to upstream's: never compared.
OURS='^(UPSTREAM\.md|LICENSE(\.txt|\.md)?)$'

# "blob path" lines of upstream's copy of $V at commit $1: a directory's files, or each named file.
upstream_files() {
  local p
  for p in $(paths "$V"); do
    if [ "$(up cat-file -t "$1:$p" 2>/dev/null)" = tree ]; then
      up ls-tree -r "$1:$p" | awk -F'\t' '{split($1, a, " "); print a[3], $2}' | grep -Ev " (UPSTREAM\.md|LICENSE\.txt)$" || true
    else
      echo "$(up rev-parse "$1:$p" 2>/dev/null || echo missing) $(basename "$p")"
    fi
  done | sort
}
local_files() {
  (cd "$CLAUDE/$V" && find . -type f ! -path '*/__pycache__/*' | sed 's|^\./||' | grep -Ev "$OURS" |
    sort | while read -r file; do echo "$(git hash-object --no-filters "$file") $file"; done) | sort
}

# The upstream copy's date: a skill's last-updated metadata, or the last commit that touched its paths.
upstream_date() {
  local p first
  first="$(paths "$V" | head -1)"
  if [ "$(up cat-file -t "$1:$first" 2>/dev/null)" = tree ]; then
    up show "$1:$first/SKILL.md" 2>/dev/null | sed -n "s/^ *last-updated: *'\{0,1\}\([0-9-]*\).*/\1/p" | head -1 && return
  fi
  # shellcheck disable=SC2046
  up log -1 --format=%as "$1" -- $(paths "$V")
}

# current | outdated | modified | removed
status_of() {
  local p commit here
  for p in $(paths "$V"); do up cat-file -e "HEAD:$p" 2>/dev/null || { echo removed; return; }; done
  commit="$(field "$V" Commit)"
  here="$(local_files)"
  if [ "$here" = "$(upstream_files HEAD)" ]; then echo current
  elif [ -n "$commit" ] && [ "$here" = "$(upstream_files "$commit")" ]; then echo outdated
  else echo modified
  fi
}

write_upstream_md() {
  local commit="$1" applied
  case "$V" in
    skills/adaptive) applied="docs/ADAPTIVE.md" ;;
    *) applied="docs/SKILLS.md" ;;
  esac
  {
    echo "# Upstream"
    echo
    echo "Vendored unchanged from $(repo "$V") (licence in this folder):"
    echo
    echo "- Repository: $(repo "$V")"
    paths "$V" | sed 's/^/- Path: /'
    echo "- Commit: $commit"
    echo "- Last updated upstream: $(upstream_date "$commit")"
    echo "- Copied: $(date -u +%F)"
    echo
    echo "How this project applies it is in $applied, and where it deliberately deviates in docs/DEVIATIONS.md. Don't edit these"
    echo "files: update them with \`scripts/android_skills.sh update $(basename "$V")\`, which copies the newest"
    echo "upstream version and rewrites this file."
  } > "$CLAUDE/$V/UPSTREAM.md.new"
  mv "$CLAUDE/$V/UPSTREAM.md.new" "$CLAUDE/$V/UPSTREAM.md"
}

copy_upstream() {
  local commit p licence
  commit="$(up rev-parse HEAD)"
  find "$CLAUDE/$V" -mindepth 1 -maxdepth 1 ! -name UPSTREAM.md -exec rm -rf {} +
  for p in $(paths "$V"); do
    if [ "$(up cat-file -t "HEAD:$p")" = tree ]; then
      up archive --format=tar "HEAD:$p" | tar -x -C "$CLAUDE/$V"
    else
      up show "HEAD:$p" > "$CLAUDE/$V/$(basename "$p")"
    fi
  done
  for licence in LICENSE.txt LICENSE.md LICENSE; do
    up cat-file -e "HEAD:$licence" 2>/dev/null && { up show "HEAD:$licence" > "$CLAUDE/$V/$licence"; break; }
  done
  write_upstream_md "$commit"
  echo "$V: copied from $(repo "$V") at ${commit:0:12} (last updated $(upstream_date HEAD))"
}

cmd_list() {
  for V in $(vendored); do
    printf '%-40s %-45s last updated %s, copied %s\n' "$V" "$(repo "$V" | sed 's|https://github.com/||')" \
      "$(field "$V" 'Last updated upstream')" "$(field "$V" Copied)"
  done
}

cmd_check() {
  local review=0 url dir
  for V in $(vendored); do
    fetch "$(repo "$V")" || { echo "android skills: can't reach $(repo "$V")" >&2; return 2; }
    case "$(status_of)" in
      current) ;;
      outdated)
        echo "android skills: $V has an upstream update (last updated $(upstream_date HEAD), ours" \
          "$(field "$V" 'Last updated upstream')); ask the owner whether to check it against the project" \
          "(scripts/android_skills.sh update $(basename "$V"), then review the diff against docs/DEVIATIONS.md)"
        review=1 ;;
      modified)
        echo "android skills: $V differs from upstream at its recorded commit; vendored files stay" \
          "unchanged, so restore it or update it"
        review=1 ;;
      removed)
        echo "android skills: $V ($(paths "$V" | tr '\n' ' ')) no longer exists upstream; check whether it moved"
        review=1 ;;
    esac
  done
  # New skills in android/skills: each gets a decision in docs/SKILLS.md.
  fetch "$GOOGLE" || { echo "android skills: can't reach $GOOGLE" >&2; return 2; }
  for dir in $(git -C "$(mirror "$GOOGLE")" ls-tree -r --name-only HEAD | sed -n 's|/SKILL\.md$||p'); do
    if [ -f "$DOC" ] && ! grep -qF "\`$dir\`" "$DOC"; then
      echo "android skills: new upstream skill $dir in android/skills; decide whether it applies and" \
        "record it in docs/SKILLS.md"
      review=1
    fi
  done
  [ "$review" = 0 ] && echo "android skills: all $(vendored | wc -l) vendored folders are current"
  return "$review"
}

cmd_update() {
  local names=("$@") name
  if [ "${1:-}" = --all ]; then mapfile -t names < <(vendored); fi
  [ "${#names[@]}" -gt 0 ] || { echo "usage: $0 update <name>...|--all" >&2; return 64; }
  for name in "${names[@]}"; do
    V="$(resolve "$name")"
    fetch "$(repo "$V")" || { echo "can't reach $(repo "$V")" >&2; return 2; }
    case "$(status_of)" in
      current) echo "$V: already current" ;;
      removed) echo "$V: no longer exists upstream; nothing copied" >&2 ;;
      *) copy_upstream ;;
    esac
  done
  echo "Review the changes (git diff .claude) against docs/SKILLS.md and commit them on their own."
}

cmd_add() {
  local url="$GOOGLE" into="" p
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --repo) url="${2%/}"; shift 2 ;;
      --into) into="$2"; shift 2 ;;
      *) break ;;
    esac
  done
  [ "$#" -gt 0 ] || { echo "usage: $0 add [--repo <github url> --into <name>] <path>..." >&2; return 64; }
  fetch "$url" || { echo "can't reach $url" >&2; return 2; }
  if [ "$url" = "$GOOGLE" ]; then
    for p in "$@"; do
      p="${p%/}"
      git -C "$(mirror "$url")" cat-file -e "HEAD:$p/SKILL.md" 2>/dev/null || { echo "$p: no SKILL.md upstream" >&2; return 1; }
      add_folder "$url" "skills/$(basename "$p")" "$p"
    done
  else
    [ -n "$into" ] || { echo "--into <name> names the folder under .claude/references" >&2; return 64; }
    for p in "$@"; do git -C "$(mirror "$url")" cat-file -e "HEAD:$p" 2>/dev/null || { echo "$p: not upstream" >&2; return 1; }; done
    add_folder "$url" "references/$into" "$@"
  fi
  echo "Record what was added and when to use it in docs/SKILLS.md, and its deliberate deviations in docs/DEVIATIONS.md."
}
add_folder() {
  local url="$1" p
  V="$2"
  shift 2
  [ ! -e "$CLAUDE/$V" ] || { echo "$V: already exists" >&2; return 1; }
  mkdir -p "$CLAUDE/$V"
  { echo "- Repository: $url"; for p in "$@"; do echo "- Path: $p"; done; } > "$CLAUDE/$V/UPSTREAM.md"
  copy_upstream
}

case "${1:-}" in
  list) cmd_list ;;
  check) cmd_check ;;
  update) shift; cmd_update "$@" ;;
  add) shift; cmd_add "$@" ;;
  *) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 64 ;;
esac
