# Planned work

Work the owner has decided on but not started. Each item says what was decided, the design, how it is
tested, and what is still open. Move an item into docs/SHRIMPL_SPEC.md or docs/TEMPLATE_SPEC.md when it is
built, and delete it here. The owner has other things to do first; the order below is the order for these
two, not a schedule.

## 1. Forced dark mode, per web app

**Decided (2026-11-08):** control levels 1 and 2.

- Level 1, a profile field: `darkMode` (System, Always dark, Never dark; default System) in `Profile`
  (`:core`), set on the app's edit page (R3.3) and baked into the APK like the other settings, so a change
  rebuilds and updates the app.
- Level 2, a runtime toggle inside the web app, with the profile as its default: a keyboard shortcut and a
  launcher shortcut, stored in the template's `SessionPrefs`, the way the desktop/mobile site toggle works
  (T6.11). It overrides the profile until cleared.
- Not now (level 3): changing it live from Shrimpl through a signature-protected provider, without a rebuild.

**Design.**

- The template theme is already `Theme.Material3.DayNight` and the manifest handles `uiMode`, so the app's
  own night mode can be forced without recreating the activity: `AppCompatDelegate.setLocalNightMode`.
  It is per app, so it is independent of the system and of other web apps.
- With night mode on, sites that support `prefers-color-scheme: dark` go dark themselves; for the rest,
  `WebSettingsCompat.setAlgorithmicDarkeningAllowed` (its own nested `isFeatureSupported` check, T1.3).
- The header (T3) follows the theme.
- Schema: a missing field means System, so existing records and APKs keep working. Template version bump.

**Tests.** `:core` profile codec and form tests; a Robolectric test of the edit-page control; an emulator
test (docs/TESTING.md): load a light test page at the app's origin with each setting and sample the pixels
of a screenshot, since darkening doesn't show in computed styles.

**Open.** Algorithmic darkening only, or also a stronger "invert" option (a CSS filter injected by a
document-start script, which mangles images)? Recommendation: algorithmic only at first.

## 2. Ad blocking, per web app

**Decided (2026-11-08):** the same control levels as dark mode: a profile field `adBlocking` (Off, Ads, Ads
and trackers; default Off) on the edit page, and a runtime toggle in the web app (keyboard and launcher
shortcuts) that overrides it.

**Design.**

- Network blocking in `WebViewClient.shouldInterceptRequest` (and the service worker client), which the
  template doesn't use yet; a blocked request gets an empty 403 response.
- The matcher is pure Kotlin in `:core` (R0: no native code, so no Brave-style engines): a host set plus a
  small subset of filter syntax (`||domain^`, `@@` exceptions, `$third-party`), thread-safe, O(1)-ish per
  request, with the 95 % coverage rule.
- Never block the app's internal domains or its sign-in domains (the existing routing classes, T2a), so
  logins and payments keep working. Only third-party requests count.
- Lists are an asset in the web app (`shrimpl/adblock.txt`), written by the builder like the profile and
  chosen by Shrimpl; start with one compact host list. Check its license and add attribution (About).
- Cosmetic hiding (CSS selectors) later, through the document-start script mechanism the bridges use.
- Limits to tell the user: no resource-type rules (WebView doesn't expose the type), no POST bodies, ads
  served from the site's own domain (YouTube's) aren't blocked, and some sites show anti-adblock walls.

**Tests.** `:core` unit tests for parsing and matching; a Robolectric test of the edit-page control; an
emulator test: a page at the app's origin requests a blocked host and an allowed one and the statuses are
checked, with no network needed.

**Open.** Which starter list, and its license. Whether "Ads and trackers" is worth a second list at first.

## Later

- Level 3, live control from Shrimpl: a provider in the template protected by a signature permission (every
  web app is signed with Shrimpl's key; the patcher already renames package-derived authorities), so
  Shrimpl's edit page changes an installed app's dark mode and ad blocking at once, with no rebuild and no
  Update prompt (R2.4). Apps built from an older template fall back to level 1. Worth doing if the rebuild
  friction proves annoying.
