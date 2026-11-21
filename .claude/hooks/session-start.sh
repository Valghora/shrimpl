#!/bin/bash
# Claude Code's SessionStart hook: the vendored skills' update note, then, in cloud sessions, the
# machine setup every agent shares (scripts/setup_agent_env.sh).
set -euo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

# Android skills (docs/SKILLS.md): when a vendored skill has an upstream update, or upstream has a
# skill docs/SKILLS.md doesn't mention, print the note so it reaches the session's context.
# Best-effort: an unreachable upstream stays quiet.
if ! skills_report="$(timeout 30 scripts/android_skills.sh check 2>/dev/null)"; then
  [ -z "$skills_report" ] || printf '%s\n' "$skills_report"
fi

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

scripts/setup_agent_env.sh "${CLAUDE_ENV_FILE:-/dev/null}"
