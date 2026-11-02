# Web app template: behavior baseline

The behavior every web app has, implemented once in `:template` (package `com.valghora.shrimpl.webapp`) and
`:core`'s `DesktopSessionController`. Shrimpl never changes this code per site; a web app differs
only in its profile (`assets/shrimpl/profile.json`, fields from `core/.../profile/Profile.kt`), its
patched manifest and its icon files (docs/SHRIMPL_SPEC.md R7, R8).

Requirement IDs are T1 to T8; code comments in the template cite them. MUST and SHOULD are used as in
docs/SHRIMPL_SPEC.md. Profile fields are written `profile.field`.

## T1 Build

- T1.1 Kotlin with AndroidX Views. Runtime dependencies are exactly `:core`, androidx.core:core-ktx,
  androidx.appcompat, com.google.android.material, androidx.activity:activity-ktx and
  androidx.webkit; tests use junit 4. No Compose (every web app is a copy of the template, and Compose
  would add megabytes to each), and no native code at all (docs/SHRIMPL_SPEC.md R0).
- T1.2 Release builds use R8 with code and resource optimization and the optimized default rules
  (`optimization { enable = true }`); keep rules only when code genuinely needs them, in
  `src/main/keepRules/*.keep` (there are none).
- T1.3 SDK checks: minSdk is 37, so no `SDK_INT` guards for APIs at or below 37. androidx.webkit
  features are checked at runtime, each call directly inside its own
  `if (WebViewFeature.isFeatureSupported(X))` (nested, never `a && b`: lint's RequiresFeature does not
  follow it).

## T2 Core logic (`DesktopSessionController`, pure JVM)

Constructed with a `ZoomStore`, a `UaStore` and a `SessionConfig` built from the profile
(`Profile.sessionConfig()`). It provides:

- a. `classify(url)`: INTERNAL (`profile.internalDomains`), OAUTH (`profile.oauthDomains`),
  EXTERNAL, PASSTHROUGH (about:, blob:, data:, javascript:), SPECIAL_SCHEME (intent:, mailto:, tel:,
  sms:, market: and other non-http schemes) or BLOCKED (malformed, or http(s) without a host). Host
  matching is case-insensitive, `host == d` or ends with `.d`, ignores a trailing dot and user-info.
- b. Text zoom 50 to 300 % in steps of 10, default 100, persisted, invalid stored values snapped.
- c. `resolveKey(keyName, modifiers, repeatCount)`: RELOAD (Ctrl+R, F5), ZOOM_IN (Ctrl+= / + /
  numpad +), ZOOM_OUT (Ctrl+- / numpad -), ZOOM_RESET (Ctrl+0 / numpad 0), PRINT (Ctrl+P),
  NEW_WINDOW (`profile.newWindowShortcut`, only with `profile.enableMultiWindow`), or NONE. Modifiers
  must match exactly. RELOAD, PRINT and NEW_WINDOW resolve only when `repeatCount == 0`.
- d. Context menu actions per hit type: link → copy link, open link; image link → copy link, open
  link, save image; image → save image; anything else → none.
- e. Wake state: awake = interactive and any display on and not (`profile.pauseOnPowerSave` and power
  save). Starts awake; emits SLEEP or WAKE only on change.
- f. User agent: `resolveUa` (user choice > learned desktop > `profile.userAgentMode`; AUTO is
  desktop on large screens or PCs, else mobile; reason "override" when `profile.userAgentOverride` is
  set), `buildUserAgent` (the override; desktop = Linux Chrome with the WebView's Chrome version;
  mobile = the WebView agent without `; wv` and `Version/x.y`), `isStoreRedirect` (market:, Play
  Store intent: and https URLs) and `toggleTarget`.
- g. `validateShortcuts()`: key names `KEYCODE_[A-Z0-9_]+`, non-blank labels, and no site shortcut
  or New Window shortcut that collides with a built-in from c.
- h. `isDarkColor(hex)` (WCAG contrast against white >= against black) and `parseHex`.

Unit tests cover every branch with a fixed fixture config, including look-alike hosts
(`notexample.com`, `example.com.evil.example`), user-info tricks and repeated key events.

## T3 Window, insets and header

- T3.1 The UI is built in code: a root `FrameLayout` holding one `DesktopWebView` (MATCH_PARENT). No
  action bar, toolbar or XML layout. The theme is `Theme.Wrapper` (Material3 DayNight NoActionBar).
- T3.2 Edge to edge: system bar styles follow the header colors (dark or light icons as each color
  needs); the root pads for system bars, caption bar, display cutout and IME insets, and consumes them.
- T3.3 Header colors, from `profile.headerMode`:
  - **System:** the system surface container color for the current light or dark theme.
  - **Custom:** `profile.headerColor`.
  - **Dynamic:** in windows without a caption bar (phones, full-screen tablets) a document-start
    script on internal domains samples the page background along its top and bottom edges,
    ignoring overlays (dialogs, `aria-modal`, fixed panels narrower than 90 % of the window, colors
    below 94 % opacity), after loads, history navigation, resizing and theme changes. The status bar
    takes the top color and the navigation bar the bottom color, cross-fading over 250 ms. Desktop
    windows, and pages that haven't reported, use the System color.

  The caption bar is transparent with icon contrast matching the header; the task description
  (label `profile.name`, primary and background color) and the WebView background use the header
  color. Night-mode changes re-apply all of this without recreating the activity.
- T3.4 Every user-visible string is a resource and every resource is used (zero UnusedResources).
  Labels that name the app (task description, notification channel, the site's shortcut group) use
  `profile.name`.

## T4 Manifest and windowing

- T4.1 No `screenOrientation`, `minAspectRatio`, `maxAspectRatio`, `resizeableActivity="false"` or
  `setRequestedOrientation`.
- T4.2 Features, all `required="false"`: type.pc, touchscreen, microphone, camera,
  camera.autofocus, location, location.gps. Permissions: INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO,
  MODIFY_AUDIO_SETTINGS, CAMERA, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION. Shrimpl's patcher
  removes those the profile doesn't enable (docs/SHRIMPL_SPEC.md R8.2).
- T4.3 `<application>`: label, icon, round icon, theme, hardware acceleration, `allowBackup="false"`,
  data extraction rules that exclude everything from cloud backup and device transfer (sign-ins never
  leave the device), `enableOnBackInvokedCallback="true"`, no cleartext traffic.
- T4.4 `MainActivity`: exported launcher, icon and round icon, `resizeableActivity="true"`,
  `configChanges` for orientation, size, layout, keyboard, navigation, UI mode, density, font scale,
  color mode and touchscreen (a deliberate deviation from the adaptive guidance, see
  docs/ADAPTIVE.md), launch mode, `<layout>` default and minimum sizes, and the multi-instance
  property.
- T4.5 Multi-window: with `profile.enableMultiWindow` the launch mode is `singleInstancePerTask`,
  `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` is true, and NEW_WINDOW starts `MainActivity` with
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK` at the start URL. Otherwise `singleTask`.

## T5 Input

- T5.1 The WebView is focusable (also in touch mode) and takes focus at start.
- T5.2 Drag and drop: on drop, `requestDragAndDropPermissions(event)`; the WebView still handles it.
- T5.3 Keys: `dispatchKeyEvent` resolves key-down events through T2c and consumes only built-ins;
  every other key, site shortcuts included, reaches the page unchanged. Esc first leaves full-screen
  video (T6.13).
- T5.4 The Keyboard Shortcuts Helper lists two groups: the site's shortcuts (titled `profile.name`)
  and Window (Reload, Zoom in, Zoom out, Reset zoom, Print, and New window when enabled).
- T5.5 Context menu (with `profile.nativeContextMenu`): copy link, open link in the browser, save
  image, per T2d. Image links copy the anchor's href (`requestFocusNodeHref`), not the image URL.
- T5.6 Back goes back in the page history while it can (`OnBackPressedCallback` kept in sync in
  `doUpdateVisitedHistory`); full-screen video takes Back first.
- T5.7 Zoom: pinch zoom on touch (built-in controls, no on-screen buttons), and Ctrl + scroll wheel or
  trackpad scroll zooms text like Ctrl+= and Ctrl+-. Page text starts at the system font size (font
  scale 1.3 is a text zoom of 130 %, to the nearest 10 %) and follows it when it changes, until the
  user zooms; their zoom is kept, and Ctrl+0 returns to following the system font size.

## T6 WebView, routing and media

- T6.1 One function, `applyWebSettings(webView, mode)`, configures every WebView (main, popups, user
  agent switches): JavaScript, DOM storage, media without a gesture, multiple windows, no file or
  content access, zoom (T5.7), text zoom, and the user agent (`userAgentString` is assigned nowhere
  else). In desktop mode without an override it also sets user agent metadata to a non-mobile Linux
  platform when `USER_AGENT_METADATA` is supported, and uses a wide viewport on small screens; mobile
  mode restores the original metadata. The mode is resolved once in `onCreate` and changes only by
  T6.10 and T6.11, never on resize.
- T6.2 Cookies, including third-party ones, are accepted; they are flushed to disk in onStop and on
  SLEEP (T8.3), on a background thread (`flush()` blocks on file I/O).
- T6.3 Downloads (http and https only) go to `DownloadManager` with the page's cookies and user
  agent, into Downloads, with a completion notification. Other schemes show a toast.
- T6.4 File chooser: the page's chooser intent (multiple when asked), results from clip data and
  data, the callback called exactly once.
- T6.5 Media permissions: only for internal origins and only resources the profile allows
  (microphone, camera, protected media); Android's runtime permission is requested when missing and
  only granted resources are passed on. Location (with `profile.allowLocation`): internal origins
  only; Android's location permission (precise or approximate) is asked for, and the page is answered
  with the result. A missing permission is handled in three steps (`PermissionPlan` in `:core`):
  - the first time, Android asks directly;
  - after one "Don't allow", a dialog explains first ("Use your camera and microphone?", the page's
    host and the app's name; Not now or Continue). Continue brings Android's prompt back; Not now,
    Back or tapping outside turns the page down;
  - once Android no longer asks (a second "Don't allow"), the page is turned down and a snackbar says
    so ("Camera and microphone are off for <app>") with Settings, which opens the app's info page.
    It shows once per window for each permission.

  The app remembers which permissions it asked for, since Android can't tell "never asked" from
  "Don't allow" twice; a permission seen granted is forgotten, so a later revoke asks again.
- T6.6 Routing through T2a. Main frame: INTERNAL, OAUTH and PASSTHROUGH load in place; EXTERNAL
  opens the browser (`ACTION_VIEW`, `CATEGORY_BROWSABLE`); SPECIAL_SCHEME intents are sanitized
  (no component or selector, no URI permission grant flags, `CATEGORY_BROWSABLE`) with a
  `browser_fallback_url` fallback; BLOCKED does nothing. Popups
  (`onCreateWindow`) get a child WebView configured like the main one; on its first real navigation
  it is handed off (EXTERNAL, SPECIAL_SCHEME), shown in a dialog at 90 % of the window (INTERNAL,
  OAUTH, or a dialog popup), or destroyed. A WebView is never destroyed inside its own callback; the
  teardown is posted.
- T6.7 Renderer crash: the main WebView is removed and destroyed and the activity recreated; a popup
  is dismissed.
- T6.8 History is saved and restored (`saveState` / `restoreState`); the start URL loads only when
  nothing was restored.
- T6.9 Every WebView is removed from its parent and destroyed in onDestroy.
- T6.10 Store-redirect fallback (`profile.uaStoreRedirectFallback`, no override): in mobile mode,
  without a user choice or a learned preference, a Play Store redirect in the first 10 s after start
  is blocked, desktop mode is learned, and the start URL reloads. At most once per install.
- T6.11 Desktop/mobile toggle (`profile.uaUserToggle`, no override): one dynamic launcher shortcut,
  "Request desktop site" or "Request mobile site", which saves the choice and reloads the start URL.
  The shortcut carries a random token kept in the app's preferences; a launch without it (another
  app starting the exported activity) doesn't toggle.
- T6.12 One startup log line: `UA mode=<mode> reason=<reason> metadata=<applied|unsupported|default>`
  (tag `profile.appId`).
- T6.13 Full-screen video: the page's custom view covers the window with the system bars hidden (swipe
  shows them briefly); in a desktop or split-screen window the activity also asks for full screen
  (`requestFullscreenMode`). Esc and Back leave it.
- T6.14 Printing: Ctrl+P prints the page (`PrintManager` with the WebView's print adapter), which can
  also save a PDF.

## T7 Notifications (with `profile.allowNotifications`)

- T7.1 Needs `WEB_MESSAGE_LISTENER` and `DOCUMENT_START_SCRIPT` (nested checks); without them it is
  skipped and logged once. Installed before the first load.
- T7.2 A `WebMessageListener` (`AndroidNotify`) limited to the internal domains' origin rules, which
  re-checks the origin, parses `{title, body, tag}` (title up to 200 characters, body up to 1000) and
  posts a notification.
- T7.3 An idempotent document-start polyfill: `window.Notification` (permission "granted"),
  `ServiceWorkerRegistration.showNotification`, and `navigator.permissions.query` for notifications.
- T7.4 Channel named `profile.name`, high importance; small icon `ic_stat_notify`, accent
  `profile.iconBackground`, big text, auto-cancel, tapping brings the app to the front. Notification
  ID from the tag, else a counter. Dropped silently without POST_NOTIFICATIONS.
- T7.5 POST_NOTIFICATIONS is requested on a fresh launch when not granted.
- T7.6 No `addJavascriptInterface` or `@JavascriptInterface` anywhere.

## T8 Power and visibility

- T8.1 `WorkspaceMonitor` feeds T2e from interactive state, display states and power save mode, via
  a `DisplayListener` and a non-exported receiver (screen on/off, power save changes), registered in
  onCreate and unregistered in onDestroy.
- T8.2 `DesktopWebView` stays VISIBLE while hidden when `profile.keepAliveWhenHidden` and the
  workspace is awake, and GONE while it sleeps.
- T8.3 SLEEP: flush cookies, pause the WebView and its timers. WAKE: resume both and dispatch
  `online` and `focus` events to the page. No WakeLocks, no foreground service.
- T8.4 Lifecycle methods never call `pauseTimers()` / `resumeTimers()` (process-wide). Without
  `profile.keepAliveWhenHidden`, onStop pauses the WebView and onStart resumes it.

## Known limitations

- Google and some other identity providers block sign-in inside embedded WebViews
  (`disallowed_useragent`); the template doesn't try to evade this.
- Web features WebView lacks (Push API, File System Access, WebUSB and Web Bluetooth, some passkey
  flows) don't work, and a desktop user agent can expose site features that need them.
- While the workspace sleeps, timers are paused: real-time connections drop and notifications arrive
  after wake. No background delivery.
- The notification polyfill always reports "granted"; if Android's permission is denied, notifications
  are dropped silently.
- A user agent toggle applies to the current window; other windows change on relaunch.
- Ctrl+P is the template's Print shortcut, so a site's own Ctrl+P (some editors use it for quick
  open) doesn't reach the page.
- blob: and data: downloads are not supported.
