#!/usr/bin/env python3
"""Checks the project's own documents against the specs and the repository, so they can't drift apart
unnoticed (the code-side twin is scripts/check_comments.sh).

  scripts/check_docs.py                 fail on undefined requirement IDs and missing paths; report UI text
                                        the specs quote that no string resource has
  scripts/check_docs.py --diff <base>   also warn about narration and dates in document lines added since <base>

Exits 1 when a check fails.
"""
import html
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ----- Settings -----
# The project's own documents. Vendored skills and references are upstream's and aren't checked.
DOCS = ["CLAUDE.md", "docs/*.md", ".claude/skills/code-comments/SKILL.md", ".claude/skills/accessibility/SKILL.md"]
# Checked, but only reported: the public README is reworked separately.
REPORT_ONLY = ["README.md"]
SPECS = ["docs/SHRIMPL_SPEC.md", "docs/TEMPLATE_SPEC.md"]
DEFINITION = re.compile(r"^(?:#+\s+|\s*-\s+)([RT]\d+(?:\.\d+)*)\b")
# Only dotted IDs: a bare "R8" is as often the R8 shrinker as the requirement group.
DOTTED = re.compile(r"\b([RT]\d+(?:\.\d+)+)\b")
# Text the specs quote that is Android's, not ours, so no resource of ours has it.
PLATFORM_TEXT = {"Install unknown apps", "Don't allow"}
STRING_RESOURCES = ["shrimpl/src/main/res/values/strings.xml", "template/src/main/res/values/strings.xml"]
# Dates belong in these documents (when a finding was made, a decision taken, a figure researched).
DATED_DOCS = {"docs/BUILD_NOTES.md", "docs/TODO.md", "docs/GOOGLEBOOKS.md", "docs/COMMENT_EVAL.md"}
NARRATION = re.compile(
    r"\b(previously|used to be|no longer|was (changed|added|removed|renamed|replaced|moved)|"
    r"were (changed|added|removed|renamed|replaced|moved)|now uses|has been (changed|replaced|renamed)|"
    r"instead of the old|as requested)\b", re.I)
DATE = re.compile(r"\b20\d\d-\d\d-\d\d\b")
# Paths the documents name on purpose although they aren't in the repository, each with why.
NOT_IN_REPOSITORY = {
    "docs/testing.md": "the testing-setup skill's suggested file, which this project doesn't create (docs/DEVIATIONS.md)",
    "docs/comment-eval/": "scripts/comment_eval.py's output, written when it runs",
}

fail = False


def problem(msg):
    global fail
    print(f"check_docs: {msg}")
    fail = True


def expand(patterns):
    return sorted({p.relative_to(ROOT).as_posix() for pat in patterns for p in ROOT.glob(pat) if p.is_file()})


def strip_code_blocks(text):
    """Blanks fenced code blocks, keeping line numbers, so shell examples aren't read as prose."""
    out, fenced = [], False
    for line in text.split("\n"):
        if line.lstrip().startswith("```"):
            fenced = not fenced
            out.append("")
        else:
            out.append("" if fenced else line)
    return out


tracked = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
tracked_set = set(tracked)
tracked_dirs = {str(Path(t).parent) for t in tracked} | {d for t in tracked for d in
                                                        (str(p) for p in Path(t).parents)}


def path_exists(path):
    path = path.rstrip("/")
    return path in tracked_set or path in tracked_dirs or (ROOT / path).exists()


def suffix_exists(path):
    """A partial path such as ui/common/Notice.kt: some tracked file ends with it."""
    return any(t == path or t.endswith("/" + path) for t in tracked)


defined = set()
for spec in SPECS:
    for line in (ROOT / spec).read_text(encoding="utf-8").split("\n"):
        m = DEFINITION.match(line)
        if m:
            defined.add(m.group(1))

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
CODE = re.compile(r"`([^`]+)`")
ROOTED = ("docs/", "scripts/", ".github/", ".claude/", "shrimpl/", "template/", "core/", "gradle/")
# A partial path or file name of the repository's own sources. Not .md, .json or .txt: documents name
# upstream files, files inside a built APK and CI artifacts by those.
FILE_LIKE = re.compile(r"^(\.\./)*[\w.-]+(/[\w.-]+)*\.(kt|kts|sh|py|yml|yaml|xml|toml|properties|keep)$")


def check_file(rel, report_only):
    lines = strip_code_blocks((ROOT / rel).read_text(encoding="utf-8"))
    say = (lambda m: print(f"check_docs: report: {m}")) if report_only else problem
    for n, line in enumerate(lines, 1):
        where = f"{rel}:{n}"
        # 1. Requirement IDs are defined.
        for id_ in DOTTED.findall(line):
            if id_ not in defined:
                say(f"{where} cites {id_}, which the specs don't define")
        # 2. Links and repository paths exist.
        for target in LINK.findall(line):
            if re.match(r"^[a-z]+:", target) or target.startswith("#"):
                continue
            target = target.split("#")[0]
            resolved = (Path(rel).parent / target).as_posix()
            if not path_exists(str(Path(resolved))) and not (ROOT / rel).parent.joinpath(target).exists():
                say(f"{where} links to {target}, which doesn't exist")
        for token in CODE.findall(line):
            token = token.strip()
            if any(c in token for c in "<>*{} $=|") or "..." in token or token in NOT_IN_REPOSITORY:
                continue  # a pattern, placeholder or command, not a path
            if token.startswith("../"):
                if not (ROOT / rel).parent.joinpath(token).exists():
                    say(f"{where} names {token}, which doesn't exist")
            elif token.startswith(ROOTED) and "/" in token:
                if not path_exists(token):
                    say(f"{where} names {token}, which doesn't exist")
            elif FILE_LIKE.match(token) and not suffix_exists(token):
                say(f"{where} names {token}, which no tracked file matches")
        for path in re.findall(r"(?<![\w/`.-])((?:docs|scripts)/[\w./-]+\.\w+)", line):
            if path not in NOT_IN_REPOSITORY and not path_exists(path.rstrip(".")):
                say(f"{where} names {path}, which doesn't exist")


for rel in expand(DOCS):
    check_file(rel, report_only=False)
for rel in expand(REPORT_ONLY):
    check_file(rel, report_only=True)

# 3. UI text the specs quote that no string resource has (a report: some quoted text is a site's or the
# platform's, not ours). A resource's placeholders (%1$s) match anything, as does the spec's <name>.
resources = []
for res in STRING_RESOURCES:
    for value in re.findall(r"<string[^>]*>(.*?)</string>", (ROOT / res).read_text(encoding="utf-8"), re.S):
        value = html.unescape(value).replace("\\'", "'").replace('\\"', '"').replace("\\n", " ")
        resources.append(re.sub(r"\s+", " ", value).strip())


def as_pattern(text):
    parts = re.split(r"(%\d*\$?[sd]|<[^>]+>)", text)
    return "".join(".*" if re.fullmatch(r"%\d*\$?[sd]|<[^>]+>", p) else re.escape(p) for p in parts)


resource_patterns = [re.compile(as_pattern(r), re.I) for r in resources]
missing = []
for spec in SPECS:
    text = "\n".join(strip_code_blocks((ROOT / spec).read_text(encoding="utf-8")))
    for quoted in re.findall(r'"([A-Z][^"\n]{2,})"', text.replace("\n  ", " ")):
        quoted = re.sub(r"\s+", " ", quoted).strip()
        if quoted in PLATFORM_TEXT:
            continue
        probe = re.compile(as_pattern(quoted), re.I)
        if not any(p.fullmatch(quoted) or probe.fullmatch(r) or quoted.lower() in r.lower() for p, r in
                   zip(resource_patterns, resources)):
            missing.append(f"{spec}: \"{quoted}\"")
for m in sorted(set(missing)):
    print(f"check_docs: report: no string resource has {m}")

# 4. With --diff: narration and dates in added document lines (a warning; history belongs in commit messages).
if len(sys.argv) >= 2 and sys.argv[1] == "--diff":
    base = sys.argv[2]
    diff = subprocess.run(["git", "diff", "-U0", base, "--", *expand(DOCS)], cwd=ROOT, capture_output=True,
                          text=True, check=True).stdout
    current = None
    for line in diff.split("\n"):
        if line.startswith("+++ b/"):
            current = line[6:]
        elif line.startswith("+") and not line.startswith("+++"):
            added = line[1:]
            if NARRATION.search(added):
                print(f"check_docs: warning: narration in {current}: {added.strip()}")
            elif current not in DATED_DOCS and DATE.search(added):
                print(f"check_docs: warning: a date in {current} (history belongs in commit messages): {added.strip()}")

if not fail:
    print("check_docs: requirement IDs and paths are in order")
sys.exit(1 if fail else 0)
