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
- Level 2, a runtime toggle inside the web app, with the profile as its default: a switch in the App
  options panel (TEMPLATE_SPEC T5.8, built), stored in the template's `SessionPrefs`, the way the Desktop
  site switch works (T6.11). Each change is saved to the app's edit page in Shrimpl (SHRIMPL_SPEC
  R11.9), so a rebuild keeps it.
- Level 3, built (2026-11-22): live settings (SHRIMPL_SPEC R11.10, TEMPLATE_SPEC T5.9). Dark mode and ad
  blocking join the Behavior section, so the edit page changes them without a rebuild; the app reads
  them from Shrimpl when it comes to the front.

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

**Decided (2026-11-08):** a profile field `adBlocking` (Off, Ads, Ads and trackers; default Off) on the edit
page, and a switch in the App options panel (T5.8). **Decided (2026-11-23):** the lists are EasyList (Ads)
and EasyList + EasyPrivacy (Ads and trackers), matched as URL rules, not host names only. Since live
settings (R11.10), the field sits in the Behavior section and applies without a rebuild.

**Design.**

- Network blocking in `WebViewClient.shouldInterceptRequest` and the service worker client; a blocked
  request gets an empty 403 response. The page's own document is never blocked, nor sign-in domains
  (T2a), so logins and payments keep working.
- The engine is pure Kotlin in `:core` (R0: no native engines such as Brave's): Adblock Plus network
  syntax (`||`, `|`, `*`, `^`, `@@`), the options `$third-party`/`$first-party` and `$domain=` (a web app
  is one site, so both are exact), and `$match-case`. Rules that need the resource type (`$script`,
  `$image`, …) are skipped, since WebView doesn't report it; `$popup` rules apply to popups later. Host
  rules go in a compact sorted index (about 45k of EasyList's 54k network rules); the rest are indexed
  by a token, as Adblock Plus and uBlock Origin do, so a request checks a handful of rules.
- Shrimpl downloads the lists, not the apps: a daily WorkManager job (unmetered network not required:
  conditional GETs make an unchanged list a 304) while any app has ad blocking on, and once at once
  when one turns it on. A download must parse and hold at least half the previous rule count, or the
  last good copy stays.
- Apps get the lists from Shrimpl's provider (R11.9's caller check): the live-settings answer carries
  each list's version, and an app with a newer one reads the file through `openFile`. Nothing is in the
  APK; an app blocks nothing until its first copy arrives.
- Licenses: EasyList and EasyPrivacy are GPL-3.0 or CC BY-SA 3.0. They are downloaded on the device,
  never shipped in an APK; Shrimpl's About credits them.
- Element hiding (`##` rules, about 25k) later, through the document-start script mechanism.
- Limits to tell the user: no resource-type rules, no WebSockets, ads served like the content itself
  (YouTube's) aren't caught by network rules, and some sites show anti-adblock walls.

**Tests.** `:core` unit tests for parsing and matching, against the real syntax; a Robolectric test of the
edit-page field; an emulator test: a page at the app's origin requests a blocked URL and an allowed one,
with rules handed to the engine, no network needed.

## Later

- Level 3 was built as a pull instead (SHRIMPL_SPEC R11.10): the app asks Shrimpl's provider, which checks
  the caller, so web apps export nothing new. Shrimpl is signed with its release key and web apps with the
  user's key, so a signature permission between them wouldn't hold anyway.
