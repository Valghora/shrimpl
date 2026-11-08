#!/usr/bin/env python3
"""Comment-quality evaluation: a reproducible 0-100 score (docs/COMMENT_EVAL.md).

  scripts/comment_eval.py sample <label>   write the rubric sample for <label> to docs/comment-eval/
  scripts/comment_eval.py score  <label>   compute every metric and the combined score; the rubric
                                           part reads docs/comment-eval/<label>-grades.json

Five metrics are computed from the source; the sixth is a graded rubric on a sample chosen by a
fixed seed. Settings for another project are the constants below.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

# ----- Settings: the only project-specific part -----
ROOT = Path(__file__).resolve().parent.parent
MAIN_GLOBS = ["*/src/main/**/*.kt"]
TEST_GLOBS = ["*/src/test/**/*.kt", "*/src/androidTest/**/*.kt"]
SPECS = ["docs/SHRIMPL_SPEC.md", "docs/TEMPLATE_SPEC.md"]
# A requirement ID: R2.6, T6.5, R2.6.1. A bare group (R13) counts only in parentheses or lists: "(R13)", "(R2, R13)".
ID_DOTTED = re.compile(r"\b([RT]\d+(?:\.\d+)+)\b")
ID_BARE = re.compile(r"(?<=[(,] )([RT]\d+)(?=[),])|(?<=\()([RT]\d+)(?=[),])")
SPEC_DEFINITION = re.compile(r"^(?:#+\s+|\s*-\s+)([RT]\d+(?:\.\d+)*)\b")
OUTSIDE_CODE = "*Outside code:*"  # a line of a spec entry: the requirement is met outside Kotlin, and where
OUT = ROOT / "docs/comment-eval"
SEED = "comment-eval-v1"
VERSION = 2  # 2: source packages named "build" are measured (v1 skipped them with Gradle output)
SAMPLE_SIZE = 40
WEIGHTS = {
    "reference_integrity": 15,
    "requirement_traceability": 10,
    "kdoc_coverage": 15,
    "noise": 10,
    "anchoring": 15,
    "rubric": 35,
}
NARRATION = re.compile(
    r"\b(previously|used to be|as requested|we (?:added|changed|removed)|(?:was|were) (?:changed|added|removed|renamed)"
    r"|now uses|updated to|instead of the old)\b|\b(?:new|old):",
    re.IGNORECASE,
)
EMPHASIS = re.compile(r"\b(IMPORTANT|CRITICAL|WARNING|HACK|XXX)\b|!!!")
TODO = re.compile(r"\b(TODO|FIXME)\b(?!\s*\((?:[RT]\d|#\d|[A-Z]+-\d))")
CODE_LINE = re.compile(r"^\s*(?:val |var |fun |import |return\b|if \(|for \(|while \(|[A-Za-z_.]+\(.*\)\s*$|.*[{}]\s*$)")
DECLARATION = re.compile(
    r"^(?P<indent> {0}| {4})(?P<mods>(?:(?:public|internal|private|protected|override|abstract|open|final|data|sealed|enum|inline|"
    r"value|const|lateinit|suspend|operator|infix|tailrec|external|annotation|inner|fun(?= interface)|expect|actual)\s+)*)"
    r"(?P<kind>class|object|interface|fun|val|var|typealias)\b"
)


def files(globs):
    seen = []
    for g in globs:
        # Skip Gradle output (<module>/build/...), not source packages named "build".
        seen += sorted(p for p in ROOT.glob(g) if p.relative_to(ROOT).parts[1:2] != ("build",))
    return seen


def comments(path):
    """The comment blocks of a Kotlin file: consecutive // lines form one block; /* */ and /** */ are blocks."""
    text = path.read_text(encoding="utf-8")
    lines = text.split("\n")
    out, i, n = [], 0, len(text)
    line_of = lambda pos: text.count("\n", 0, pos) + 1
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            while j < n and text[j] == '"':
                j += 1
            i = j
        elif c == '"':
            j = i + 1
            while j < n and text[j] not in '"\n':
                j += 2 if text[j] == "\\" else 1
            i = j + 1
        elif c == "'" and re.match(r"'(\\.|[^'\\])'", text[i:i + 4]):
            i = text.index("'", i + 1 + (text[i + 1] == "\\")) + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            ln = line_of(i)
            trailing = lines[ln - 1][: len(lines[ln - 1]) - len(lines[ln - 1].lstrip())] != lines[ln - 1][: lines[ln - 1].find("//")]
            out.append({"kind": "line", "start": ln, "end": ln, "raw": text[i + 2:j].strip(), "trailing": trailing})
            i = j
        elif text.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if text.startswith("/*", j):
                    depth, j = depth + 1, j + 2
                elif text.startswith("*/", j):
                    depth, j = depth - 1, j + 2
                else:
                    j += 1
            body = text[i + 2:j - 2]
            kind = "kdoc" if body.startswith("*") and not body.startswith("**") else "block"
            cleaned = "\n".join(re.sub(r"^\s*\*\s?", "", l) for l in body.lstrip("*").split("\n")).strip()
            out.append({"kind": kind, "start": line_of(i), "end": line_of(j - 1), "raw": cleaned, "trailing": False})
            i = j
        else:
            i += 1
    # Merge consecutive whole-line // comments with the same indentation into one block.
    merged = []
    for b in out:
        prev = merged[-1] if merged else None
        if (prev and b["kind"] == "line" and prev["kind"] == "line" and not b["trailing"] and not prev["trailing"]
                and b["start"] == prev["end"] + 1 and indent(lines[b["start"] - 1]) == indent(lines[prev["start"] - 1])):
            prev["end"] = b["start"]
            prev["raw"] += "\n" + b["raw"]
        else:
            merged.append(dict(b))
    for b in merged:
        b["path"] = str(path.relative_to(ROOT))
        b["text"] = " ".join(b["raw"].split())
        after = [l for l in lines[b["end"]:] if l.strip() and not l.strip().startswith(("//", "*", "/*"))]
        b["context"] = lines[b["end"]: b["end"] + 10]
        decl = next((l for l in after if not l.strip().startswith("@")), "")
        b["attached"] = b["kind"] == "kdoc" and bool(DECLARATION.match(decl) or re.match(r"^\s*(?:[\w ]+\s)?(?:class|object|interface|fun|val|var)\b", decl))
    return merged


def indent(line):
    return len(line) - len(line.lstrip())


def spec_ids():
    ids = set()
    for s in SPECS:
        for line in (ROOT / s).read_text(encoding="utf-8").split("\n"):
            m = SPEC_DEFINITION.match(line)
            if m:
                ids.add(m.group(1))
    return ids


def cited(text):
    out = set(ID_DOTTED.findall(text))
    for a, b in ID_BARE.findall(text):
        out.add(a or b)
    return out


def declarations(path):
    """Non-private top-level declarations, and non-private, non-override functions of top-level classes and objects."""
    lines = path.read_text(encoding="utf-8").split("\n")
    found, top_kind = [], None
    for idx, line in enumerate(lines):
        m = DECLARATION.match(line)
        if not m:
            continue
        mods, kind, ind = m.group("mods"), m.group("kind"), m.group("indent")
        if ind == "":
            top_kind = kind
        elif top_kind not in ("class", "object", "interface") or kind != "fun":
            continue
        if "private" in mods or "override" in mods or line.strip().startswith("companion object"):
            continue
        # KDoc directly above, past annotations.
        j = idx - 1
        while j >= 0 and lines[j].strip().startswith("@"):
            j -= 1
        found.append({"path": str(path.relative_to(ROOT)), "line": idx + 1, "kdoc": j >= 0 and lines[j].strip().endswith("*/")})
    return found


def evaluate(label):
    main_blocks = [b for p in files(MAIN_GLOBS) for b in comments(p)]
    test_blocks = [b for p in files(TEST_GLOBS) for b in comments(p)]
    defined = spec_ids()

    # 1. Reference integrity: requirement IDs, docs/ paths and source paths named in comments exist.
    refs, broken = 0, []
    all_paths = {str(p.relative_to(ROOT)) for p in ROOT.rglob("*")
                 if p.is_file() and p.relative_to(ROOT).parts[1:2] != ("build",) and p.relative_to(ROOT).parts[0] != ".git"}
    for b in main_blocks + test_blocks:
        for rid in cited(b["raw"]):
            refs += 1
            if rid not in defined:
                broken.append(f"{b['path']}:{b['start']} {rid}")
        for ref in re.findall(r"\b((?:docs|scripts|\.claude)/[\w./-]+\.\w+)", b["raw"]) + re.findall(r"\b([\w/]+/\w+\.kt)\b", b["raw"]):
            refs += 1
            if ref not in all_paths and not any(p.endswith("/" + ref) for p in all_paths):
                broken.append(f"{b['path']}:{b['start']} {ref}")
    integrity = 100 * (1 - len(broken) / refs) if refs else 100

    # 2. Requirement traceability: leaf requirements cited by code or tests, or listed as met outside Kotlin.
    leaves = {i for i in defined if "." in i and not any(o.startswith(i + ".") for o in defined)}
    citations = set()
    for b in main_blocks + test_blocks:
        citations |= cited(b["raw"])
    for p in files(MAIN_GLOBS + TEST_GLOBS):
        citations |= cited(p.read_text(encoding="utf-8"))
    doc_only = set()
    for spec in SPECS:
        current = None
        for line in (ROOT / spec).read_text(encoding="utf-8").split("\n"):
            m = SPEC_DEFINITION.match(line)
            if m:
                current = m.group(1)
            if OUTSIDE_CODE in line and current:
                doc_only.add(current)
    traced = {i for i in leaves if i in citations or any(c.startswith(i + ".") for c in citations) or i in doc_only}
    traceability = 100 * len(traced) / len(leaves) if leaves else 100

    # 3. KDoc coverage of the declarations other code uses.
    decls = [d for p in files(MAIN_GLOBS) for d in declarations(p)]
    coverage = 100 * sum(d["kdoc"] for d in decls) / len(decls) if decls else 100

    # 4. Noise: blocks that narrate history, hold commented-out code, shout, or leave an untracked TODO.
    flagged = []
    for b in main_blocks:
        reasons = []
        if NARRATION.search(b["raw"]):
            reasons.append("narration")
        if b["kind"] == "line" and any(CODE_LINE.match(l) for l in b["raw"].split("\n")):
            reasons.append("code")
        if EMPHASIS.search(b["raw"]):
            reasons.append("emphasis")
        if TODO.search(b["raw"]):
            reasons.append("todo")
        if reasons:
            flagged.append(f"{b['path']}:{b['start']} {','.join(reasons)}: {b['text'][:90]}")
    noise = 100 * (1 - len(flagged) / len(main_blocks)) if main_blocks else 100

    # 5. Anchoring: of the requirements main code cites, the share cited in a declaration's KDoc, so that
    #    searching for the ID lands on the code that implements it.
    in_main, in_kdoc = set(), set()
    for b in main_blocks:
        ids = cited(b["raw"])
        in_main |= ids
        if b["attached"]:
            in_kdoc |= ids
    anchoring = 100 * len(in_kdoc & in_main) / len(in_main) if in_main else 100

    # 6. Rubric: the graded sample.
    grades_file = OUT / f"{label}-grades.json"
    rubric, graded = None, 0
    if grades_file.exists():
        grades = json.loads(grades_file.read_text())
        items = [g for g in grades.values() if isinstance(g, dict) and "purpose" in g]
        graded = len(items)
        if items:
            rubric = 100 * sum(g["purpose"] + g["accuracy"] + g["concision"] + g["placement"] for g in items) / (8 * len(items))

    metrics = {
        "reference_integrity": integrity,
        "requirement_traceability": traceability,
        "kdoc_coverage": coverage,
        "noise": noise,
        "anchoring": anchoring,
        "rubric": rubric,
    }
    combined = None if rubric is None else sum(WEIGHTS[k] * v for k, v in metrics.items()) / 100
    return {
        "label": label,
        "seed": SEED,
        "version": VERSION,
        "counts": {
            "comment_blocks_main": len(main_blocks),
            "comment_blocks_test": len(test_blocks),
            "requirement_refs": refs,
            "leaf_requirements": len(leaves),
            "declarations": len(decls),
            "requirements_cited_in_main": len(in_main),
            "rubric_items_graded": graded,
        },
        "metrics": {k: (None if v is None else round(v, 1)) for k, v in metrics.items()},
        "combined": None if combined is None else round(combined, 1),
        "details": {
            "broken_references": broken,
            "untraced_requirements": sorted(leaves - traced),
            "undocumented_declarations": [f"{d['path']}:{d['line']}" for d in decls if not d["kdoc"]],
            "noise": flagged,
            "cited_in_main_not_in_kdoc": sorted(in_main - in_kdoc),
        },
    }


def sample(label):
    blocks = [b for p in files(MAIN_GLOBS) for b in comments(p)]
    key = lambda b: hashlib.sha256(f"{SEED}|{b['path']}|{b['text']}".encode()).hexdigest()
    chosen = sorted(blocks, key=key)[:SAMPLE_SIZE]
    OUT.mkdir(parents=True, exist_ok=True)
    md = [f"# Rubric sample: {label}", "", f"Seed `{SEED}`, {len(chosen)} of {len(blocks)} comment blocks in main sources.", ""]
    stub = {}
    for b in sorted(chosen, key=lambda b: (b["path"], b["start"])):
        bid = key(b)[:10]
        stub[bid] = {"where": f"{b['path']}:{b['start']}", "purpose": None, "accuracy": None, "concision": None, "placement": None, "note": ""}
        md += [f"## {bid} `{b['path']}:{b['start']}` ({b['kind']})", "", "```", b["raw"], "```", "", "Followed by:", "", "```kotlin", *b["context"], "```", ""]
    (OUT / f"{label}-sample.md").write_text("\n".join(md))
    grades = OUT / f"{label}-grades.json"
    if not grades.exists():
        grades.write_text(json.dumps(stub, indent=1) + "\n")
    print(f"{len(chosen)} items: {OUT / (label + '-sample.md')}")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("sample", "score"):
        print(__doc__)
        sys.exit(64)
    if sys.argv[1] == "sample":
        sample(sys.argv[2])
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        result = evaluate(sys.argv[2])
        (OUT / f"{sys.argv[2]}-result.json").write_text(json.dumps(result, indent=1) + "\n")
        print(json.dumps({k: result[k] for k in ("label", "counts", "metrics", "combined")}, indent=1))
