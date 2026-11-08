---
name: code-comments
description: How comments and KDoc are written in this codebase, so people can read them and agents can find and trust them - what to comment, KDoc form, requirement IDs as anchors, Invariant markers, and the edit routine that keeps comments true. Use whenever writing, editing or reviewing code, and before committing.
---

# Code comments

Comments are for two readers: a person reading the code, and an agent that arrives with no memory,
finds code by searching for text, reads excerpts rather than whole files, and tends to trust what a
comment or instruction says over what the code does. A wrong comment is worse than none, so every
comment must stay true, and the checks below catch what habits miss.

Project settings (the spec files, the requirement-ID pattern, the outside-code marker) are at the top of
`scripts/check_comments.sh`; everything else here applies to any Kotlin project.

## What to comment

Comment what the code can't say:

- **Why**: the reason for a non-obvious choice, and what goes wrong without it.
- **Contracts**: what a declaration promises, its conditions, its units, what null means.
- **Constraints and quirks**: a platform bug, a library limit, a threading rule.
- **Where a rule comes from**: a requirement ID, a test, a dated build note.

Never write:

- **What the next line does.** Rename or extract instead.
- **History or narration**: "now uses", "previously", "changed to", "new:", "fixed". History belongs in
  commit messages.
- **The conversation or task**: "as requested", "per the review", "for the owner".
- **Copies of the spec.** Cite it instead.
- **Commented-out code.** Delete it; git keeps it.
- **Shouting**: IMPORTANT, CRITICAL, !!!. A rule that matters gets an `Invariant:` (below) and a test.
- **TODO or FIXME** without a requirement ID or issue number: `TODO(R2.6): ...`.

## KDoc

Every non-private top-level declaration, and every public function of a class or object other code
uses, gets KDoc, unless its name and signature say everything (a setter that only stores a value).

- The first sentence says the outcome: a third-person verb and what it acts on ("Rebuilds the app with
  the bundled template."), or for a class, a noun phrase ("The build sheet's state."). Key information
  first; some readers stop there.
- State conditional behavior and edge cases: "Null when the app isn't installed."
- Describe what the code does, never what the reader must do, and never more than it does.
- Plain words, no filler ("This function is used to..."), Markdown not HTML, `[Name]` links to real
  symbols. AndroidX's KDoc rules (`@param`, `@sample`, `@Deprecated` with `ReplaceWith`) apply to
  public API.

## Anchors: requirement IDs

A requirement ID is the stable search key from a spec to the code. Put it where a search should land:

- In the **KDoc of the declaration that implements it**, once: `/** Fix and Fix all (R11.7): ... */`.
  A search for `R11.7` then finds the implementing declarations first.
- Inline only at a line that enforces one specific part, briefly: `// T6.8: the start URL loads only
  when nothing was restored.`
- **Tests cite the IDs they verify** in their KDoc, so a requirement leads to its test as well.
- Cite the most specific ID (`R2.6`, not `R2`). A file path to another document is written in full
  from the repository root (`docs/BUILD_NOTES.md`).
- A requirement met outside the code (build files, CI, the manifest, a "never do X" rule) ends its spec
  entry with a line `*Outside code:* <where>`, so nothing is silently untraced.

`scripts/spec_map.sh <ID>` lists every place that cites an ID: the spec line, then code, then tests.
Use it before changing behavior a requirement covers.

## Invariants

A rule that isn't obvious from the code and that a reasonable edit would break gets a searchable
marker at the code it constrains, with its source:

```kotlin
// Invariant: anchor the menu to a zero-size box at the pressed point; DropdownMenu adds its offset
// to the anchor's bottom (docs/BUILD_NOTES.md, 2026-11-02).
```

The source is a requirement ID, a dated build note, or the test that guards it. Before editing a
file, search it for `Invariant:` and keep each one true, or change it on purpose and say so in the
commit.

## Section headers

Long files may divide into sections with one style only:

```kotlin
// ----- Name (optional requirement ID) -----
```

A header names a section; it carries no other rules or contracts. If a file needs many sections,
split the file instead.

## Edit routine (agents)

1. **Before editing**: read the KDoc of what you change, search the file for `Invariant:`, and run
   `scripts/spec_map.sh` for the requirement IDs it cites.
2. **While editing**: update every comment inside the code you touched, including KDoc above it;
   delete any comment that no longer applies. Keep one comment per fact.
3. **Before committing**: run `scripts/check_comments.sh`, and reread your added comment lines for
   narration and for claims the code doesn't back.

## Checks

`scripts/check_comments.sh` runs in CI:

- **Fails** on a requirement ID cited in code that the specs don't define, a `docs/` or `scripts/`
  path in a comment that doesn't exist, a TODO or FIXME without an ID or issue, and a requirement
  nothing cites that isn't marked `*Outside code:*`.
- **Warns** (with `--diff <base>`) about added comment lines that narrate history.

`scripts/check_docs.py` is its twin for the documents (requirement IDs, links and paths, quoted UI
text). `scripts/comment_eval.py` scores comment quality 0-100 (method in `docs/COMMENT_EVAL.md`).
