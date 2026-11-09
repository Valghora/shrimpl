# Comment-quality evaluation

A reproducible 0-100 score for the comments in the Kotlin sources, so that changes to how comments are
written can be measured. Temporary: see Results. `scripts/comment_eval.py` computes it; the method is frozen at the commit that
added it, and a comparison is only valid between runs of the same script version.

```bash
scripts/comment_eval.py sample <label>   # writes docs/comment-eval/<label>-sample.md and a grades stub
# grade every item in docs/comment-eval/<label>-grades.json (rubric below)
scripts/comment_eval.py score <label>    # writes docs/comment-eval/<label>-result.json
```

## Metrics

Five metrics come straight from the source; the sixth is a graded rubric. Each is 0-100.

| Metric | Weight | What it measures | Why it matters |
|---|---|---|---|
| Reference integrity | 15 | Requirement IDs (R2.6, T6.5, "(R13)"), `docs/`/`scripts/` paths and source paths named in comments (main and test) that exist | A dangling reference sends a reader, or an agent, to nothing |
| Requirement traceability | 10 | Leaf requirements in the specs cited somewhere in Kotlin (main or test), or whose spec entry is marked `*Outside code:*` with where it is met | A requirement nothing cites can't be found from the code, and may be unimplemented or untested |
| KDoc coverage | 15 | Non-private top-level declarations, and non-private, non-override functions of top-level classes and objects, with KDoc | Other code calls these; their contract should be readable without opening the body |
| Noise | 10 | Comment blocks with none of: history narration, commented-out code, shouting (IMPORTANT, CRITICAL, !!!), a TODO/FIXME without a requirement or issue | Each is a known way comments mislead or waste reading |
| Anchoring | 15 | Of the requirements main code cites, the share cited in a declaration's KDoc | Searching for an ID then lands on the code that implements it, not only on a line inside it |
| Rubric | 35 | 40 comment blocks graded by hand on four criteria | The qualities a script can't judge |

Combined score = the weighted sum. The noise patterns are deliberately narrow (few false positives); the
rubric catches what they miss.

## Rubric

The sample is the 40 main-source comment blocks with the lowest SHA-256 of `seed|path|text` (seed
`comment-eval-v1`). The same comments are chosen as long as they are unchanged; a rewritten comment
drops out and another takes its place, so a rerun grades the code as it is then. Each item is graded
0-2 on four criteria (8 points; the rubric score is the share of points). The grades file keeps the
key `concision` for the third criterion:

| | 2 | 1 | 0 |
|---|---|---|---|
| Purpose | Says what the code can't: why, a contract, a constraint, a platform quirk, where a rule comes from | Partly restates the code, or a section header (structure only) | Restates the code, narrates its history or the conversation, or says nothing |
| Accuracy | Everything it says is true of the code beside it and of what it cites | A minor imprecision, or slightly out of date | False or stale |
| Clarity and concision | Reads right the first time, in plain words, and every clause carries information | Needs a second read, or has some padding | Hard to follow, mostly padding, or repeats the spec at length |
| Placement and anchoring | At the code it concerns; a rule from elsewhere (spec, platform bug, test) names its source in a searchable form | Right place but missing a source it clearly needs, or a little distant | Far from what it describes (a file header for a line-level rule) |

Each grade records a one-line note for any score below 2, so a second grader can audit it.

## Version

The script is v2 (it measures the source packages named `build`, which v1 skipped with Gradle output).
Compare only runs of the same version.

## Limits

- The rubric is graded by one person (or agent); grades are recorded per item for audit, not
  averaged across graders. An independent grader on the same sample is the best check.
- The declaration and comment parsing is regex-based: Kotlin strings, nested block comments and
  templates are handled, but unusual formatting may be missed.
- The outside-code markers raise traceability; each says where it is met and is reviewed like code.

## Sources

The metrics and the code-comments skill draw on:
[AndroidX KDoc guidelines](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/docs/kdoc_guidelines.md);
the agent-facing KDoc rules in Google's AppFunctions skill (android/skills,
`device-ai/appfunctions/references/kdoc-refinement-optimization.md`);
[detekt's comments rules](https://detekt.dev/docs/next/rules/comments);
requirement traceability through code comments (OpenFastTrace, StrictDoc, Doorstop);
[stale instruction files override the code](https://www.emergentmind.com/papers/2610.30571);
[comments placed at the code they constrain](https://caiopizzol-com.pages.dev/writing/comments-matter-when-the-code-does-not);
[inaccurate generated comments](https://arxiv.org/abs/2406.14836v1).

## Results

The last run, to compare the final one against:

| Run | Commit | Integrity | Traceability | KDoc | Noise | Anchoring | Rubric | Combined |
|---|---|---|---|---|---|---|---|---|
| KDoc pass, v2 | 5dcbfaf | 100.0 | 100.0 | 100.0 | 100.0 | 89.0 | 98.4 | **97.8** |
| final, v2 | baccecf | 100.0 | 100.0 | 99.1 | 100.0 | 91.7 | 97.2 | **97.6** |

One more run follows the documentation rework; then this file, `scripts/comment_eval.py` and
`docs/comment-eval/` are deleted.
