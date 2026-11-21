---
name: accessibility
description: Accessibility for Shrimpl's Compose screens and the web app template's own Android Views - screen reader labels, roles and state, headings, touch targets, contrast, focus and keyboard. Use for every UI change, when reviewing or auditing a screen, and when fixing a TalkBack, Switch Access, keyboard or contrast problem.
---

# Accessibility (Shrimpl)

This project's own skill. The guidance is Drjacky's Android accessibility reference, vendored unchanged
in `.claude/references/claude-android-ninja/` (Apache 2.0; kept current by
`scripts/android_skills.sh`). Its deliberate deviations are in `docs/DEVIATIONS.md`.

## How to use it

1. Open `../../references/claude-android-ninja/android-accessibility-quick.md` first and follow its
   section routing to the one section of `android-accessibility.md` the task needs. Links to files
   that weren't vendored (`INDEX-sections.md`, `android-i18n.md`) don't resolve; the table of contents
   at the top of `android-accessibility.md` replaces the index.
2. Apply it with this project's rules below, which win where they differ.
3. Add a Robolectric test for what can be checked from the semantics tree (labels, roles, toggle
   state, headings, touch target size); see "Testing" below.

## Project rules

- Every icon-only button is a `TooltipIconButton`: its label is the content description and the
  tooltip (docs/SHRIMPL_SPEC.md R1.1). Labels name the action and the app ("Options for Discord"), from string
  resources.
- 48 dp touch targets (R1.1), also where the visible shape is smaller.
- A row that is a switch carries its state (`SwitchItem` sets `toggleableState`); a choice carries
  `selected` (`selectable`, `Role.RadioButton`).
- Colors come from the theme and `ui/common/Notice.kt`; check contrast (4.5:1 text, 3:1 large text and
  icons) whenever a palette changes, in light and dark themes.
- The template (AndroidX Views) and its WebView: the reference is Compose-only, so use Android's View
  accessibility guidance for the template's own controls (dialogs, snackbars, shortcuts). The web
  page's own accessibility belongs to the site; the template must not get in its way (zoom, font scale,
  TalkBack in the WebView).

## Testing

- Robolectric semantics checks in `shrimpl/src/test` (`SemanticsMatcher`, `assertIsOn`,
  `assertTouchHeightIsAtLeast`, `hasContentDescription`).
- Not available here: Google's Accessibility Test Framework checks
  (`enableAccessibilityChecks()` from `ui-test-junit4-accessibility`) don't run under Robolectric; they
  flag nothing, not even an unlabelled 10 dp button (docs/TESTING.md, "Robolectric"). The reference's Espresso
  checks and Accessibility Scanner need a device; TalkBack checks happen on the owner's devices.
