# Android skills

Google's [android/skills](https://github.com/android/skills) are agent instructions for Android best
practices. The ones that apply to this project are copied unchanged into `.claude/skills/<name>/`,
where Claude Code loads them as project skills. Each folder keeps upstream's `LICENSE.txt` (Apache
2.0) and an `UPSTREAM.md` with the upstream path, the commit it was copied from and that copy's
`last-updated` date.

This file lists the skills in use and when to use each. A skill is followed as written unless
[DEVIATIONS.md](DEVIATIONS.md) says otherwise; a new deliberate deviation gets a row there in the same
commit.

## Keeping them current

- **Check.** `scripts/android_skills.sh check` compares every vendored skill with upstream. The
  SessionStart hook runs it at the start of every session, so a session starts with a note when a
  skill has an upstream update, differs from its recorded upstream copy, or no longer exists upstream,
  and when upstream adds a skill this file doesn't mention. For an update, the owner is asked first
  whether to check it against the project; only then is it applied and reviewed (below).
- **Update.** `scripts/android_skills.sh update <name>` (or `--all`) copies the newest upstream
  version and rewrites `UPSTREAM.md`. Review `git diff .claude/skills` against DEVIATIONS.md,
  adjust it (and ADAPTIVE.md) where the guidance changed, and commit the update on its own.
- **Adopt.** `scripts/android_skills.sh add <upstream path>` vendors another android/skills skill;
  `add --repo <github url> --into <name> <file>...` vendors chosen files of another repository into
  `.claude/references/<name>/`. Add it to the tables below, and remove it from "Not adopted".
- **Never edit a vendored skill.** Record a departure in DEVIATIONS.md instead; `check` flags local edits.
- `scripts/android_skills.sh list` shows each skill's upstream date and when it was copied.

## Skills in use

| Skill | Upstream path | Use it when |
|---|---|---|
| adaptive | `jetpack-compose/adaptive` | Any Shrimpl UI change (CLAUDE.md rule 2). How it applies: [ADAPTIVE.md](ADAPTIVE.md). |
| testing-setup | `testing/testing-setup` | Adding or reviewing tests, and with every change (CLAUDE.md: look for behavior worth a Robolectric test). |
| android-intent-security | `security/android-intent-security` | Touching manifest components, intents, `PendingIntent`s, or how the template opens links and `intent:` URLs. |
| android-permissions-security | `security/android-permissions-security` | Adding or changing permissions, exported components, broadcasts, or the template's runtime permission prompts. |
| edge-to-edge | `system/edge-to-edge` | Shrimpl screens with lists, floating buttons, text fields, dialogs or sheets. |
| navigation-3 | `navigation/navigation-3` | Changing Shrimpl's navigation (`ui/ShrimplNavigation.kt`): destinations, scenes, back stack, results. |
| anr-audit-and-fix | `performance/anr-audit-and-fix` | Moving work between threads, adding file, network, signing or package manager calls, or reading an ANR trace. |
| r8-analyzer | `performance/r8-analyzer` | Changing keep rules or minify settings, or checking how much the template (copied into every web app) carries. |
| jetpack-compose-performance | `jetpack-compose/jetpack-compose-performance` | Writing or reviewing Compose code that is on a hot path: the home list, animations, custom layouts, modifiers. |

## Other sources

Community skills are weighed against Google's: they are adopted only where Google has nothing, and only
the parts that fit. Their files are vendored unchanged like Google's, and `check` and `update` keep them
current the same way; the version is the last upstream commit that touched them.

| What | Upstream | Use it when |
|---|---|---|
| code-comments (this project's skill, `.claude/skills/code-comments/`), written for reuse in other projects | None: drawn from the AndroidX KDoc guidelines, the AppFunctions skill's agent-facing KDoc rules and requirement-traceability practice (sources in the skill) | Writing, editing or reviewing any code. Checked by `scripts/check_comments.sh` and `scripts/check_docs.py` in CI. Google's android/skills has no comments skill. |
| accessibility (this project's skill, `.claude/skills/accessibility/`) wrapping Drjacky's accessibility reference, `.claude/references/claude-android-ninja/` | [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja) `references/android-accessibility.md` and `references/android-accessibility-quick.md` (Apache 2.0) | Every UI change; reviewing or auditing a screen; TalkBack, keyboard or contrast problems. Google's android/skills has no accessibility skill. |

### Considered and not adopted

| Source | Why not |
|---|---|
| [new-silvermoon/awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills) (17 skills, 2026-11-02) | Its accessibility checklist is 36 lines, much thinner than Drjacky's reference. `compose-navigation` pins a Navigation 3 alpha and contradicts Google's navigation-3; `compose-performance-audit` and `android-testing` overlap Google's skills; `android-architecture`, `android-data-layer`, `android-retrofit`, `android-viewmodel` and `android-coroutines` assume Hilt, Room, Retrofit, injected dispatchers or an experimental Kotlin flag; `kotlin-concurrency-expert` was declined (the code already follows its rules, except dispatcher injection, which the Robolectric tests don't need); the migration, Coil, emulator, Gradle convention plugin and build-performance skills don't apply. |
| [aldefy/compose-skill](https://github.com/aldefy/compose-skill) | A good accessibility reference (489 lines, citing androidx sources) inside a broad Compose skill that overlaps Google's; kept as a second opinion, not vendored. |
| Other community Compose skills (anhvt52, azlekov, krutikJain, hamen) | Smaller accessibility sections, or none. |

## Not adopted

Upstream skills that don't apply. `check` names any upstream skill missing from this file, so a new
one gets a decision here.

| Upstream path | Why not |
|---|---|
| `build-system/agp/agp-9-upgrade` | Already on AGP 9 with built-in Kotlin, the new DSL and no transitional flags. |
| `camera/camerax` | No camera features. |
| `cars/android-for-cars-parked-apps` | Browsers are a beta category on Android Automotive only, and not supported on Android Auto. |
| `device-ai/appfunctions` | Possible later, to let assistants create web apps. |
| `device-ai/litert/litert-lm` | No on-device models; LiteRT also ships native libraries (R0). |
| `device-ai/litert/litert-runtime` | As above. |
| `device-ai/ml-kit-genai-prompt-api` | No on-device AI features. |
| `device-ai/mlkit-agent-api` | No on-device AI features. |
| `devtools/android-cli` | Most commands need a device, and it is a binary install outside Gradle. |
| `identity/restore-credentials` | Shrimpl has no accounts; web apps' sign-in belongs to each site, and passkeys in the template's WebView would need the site to vouch for our signing key ([TEMPLATE_SPEC.md](TEMPLATE_SPEC.md)). |
| `identity/verified-email` | As above. |
| `jetpack-compose/ink/ink-app-builder` | No drawing. |
| `jetpack-compose/ink/ink-custom-brush-builder` | No drawing. |
| `jetpack-compose/migration/migrate-xml-views-to-jetpack-compose` | The template stays on Views on purpose (T1.1, ADAPTIVE.md). |
| `media/media3-cast-integration` | Media plays inside each site's page. |
| `navigation/navigation-event` | Compose-only, and it defers to Navigation 3's own back handling; the template uses `OnBackPressedDispatcher`. |
| `play/engage-sdk-integration` | Shrimpl and its web apps are sideloaded, not on Play. |
| `play/play-billing-library-version-upgrade` | No billing; not on Play. |
| `play/play-policy-insights` | Not on Play. |
| `profilers/android-profiler` | Needs a connected device. |
| `system/glance-appwidget-updates-and-memory` | No widgets. |
| `system/glance-canonical-layouts` | No widgets. |
| `tv/leanback-to-compose-tv-migration` | No TV app. |
| `wear/wear-compose-m3` | No Wear OS app. |
| `wear/wear-widgets` | No Wear OS app. |
| `xr/display-glasses-with-jetpack-compose-glimmer` | No XR app. |
| `xr/projected-hardware` | No XR app. |
