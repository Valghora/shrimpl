# Web app template: specification

The behavior every web app has, implemented once in `:template` (package `com.valghora.shrimpl.webapp`) and
`:core`'s `DesktopSessionController`. Shrimpl never changes this code per site; a web app differs
only in its profile (`assets/shrimpl/profile.json`, fields from `core/.../profile/Profile.kt`), its
patched manifest and its icon files (docs/SHRIMPL_SPEC.md R7, R8).

Requirement IDs are T1 to T8; code comments in the template cite them. T3 to T6 are how every web app
meets Google's adaptive and desktop app quality guidelines (multi-window, the header, keyboard,
mouse and zoom, context menus, drag and drop, full screen, printing); departures are in
docs/DEVIATIONS.md. MUST and SHOULD are used as in
docs/SHRIMPL_SPEC.md. Profile fields are written `profile.field`.

## T1 Build

- T1.1 Kotlin with AndroidX Views. Runtime dependencies are exactly `:core`, androidx.core:core-ktx,
  androidx.appcompat, com.google.android.material, androidx.activity:activity-ktx and
  androidx.webkit; tests use junit 4. No Compose (every web app is a copy of the template, and Compose
  would add megabytes to each), and no native code at all (docs/SHRIMPL_SPEC.md R0).
  *Outside code:* `template/build.gradle.kts` (dependencies).
- T1.2 Release builds use R8 with code and resource optimization and the optimized default rules
  (`optimization { enable = true }`); keep rules only when code genuinely needs them, in
  `src/main/keepRules/*.keep` (there are none).
  *Outside code:* `template/build.gradle.kts` (release optimization).
- T1.3 SDK checks: minSdk is 37, so no `SDK_INT` guards for APIs at or below 37. androidx.webkit
  features are checked at runtime, each call directly inside its own
  `if (WebViewFeature.isFeatureSupported(X))` (nested, never `a && b`: lint's RequiresFeature does not
  follow it).
  *Outside code:* lint's RequiresFeature check and AGENTS.md.

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
- d. Context menu actions per hit type, App options first wherever there are any: link → App options,
  copy link, open link; image link → App options, copy link, open link, save image; image → App
  options, save image; anything else → none.
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
  action bar, toolbar or XML layout. The theme is `Theme.Wrapper` (Material3 DayNight NoActionBar)
  with the user's dynamic colors (`DynamicColors`), so the template's menus, dialogs, snackbars and
  App options panel follow the user's Material theme, as Shrimpl does.
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
  *Outside code:* the template's resources; lint's UnusedResources at 0.

## T4 Manifest and windowing

- T4.1 No `screenOrientation`, `minAspectRatio`, `maxAspectRatio`, `resizeableActivity="false"` or
  `setRequestedOrientation`.
  *Outside code:* `template/src/main/AndroidManifest.xml`.
- T4.2 Features, all `required="false"`: type.pc, touchscreen, microphone, camera,
  camera.autofocus, location, location.gps. Permissions: INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO,
  MODIFY_AUDIO_SETTINGS, CAMERA, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION. Shrimpl's patcher
  removes those the profile doesn't enable (docs/SHRIMPL_SPEC.md R8.2).
  *Outside code:* `template/src/main/AndroidManifest.xml`.
- T4.3 `<application>`: label, icon, round icon, theme, hardware acceleration, `allowBackup="false"`,
  data extraction rules that exclude everything from cloud backup and device transfer (sign-ins never
  leave the device), `enableOnBackInvokedCallback="true"`, no cleartext traffic.
  *Outside code:* `template/src/main/AndroidManifest.xml` and `res/xml/data_extraction_rules.xml`.
- T4.4 `MainActivity`: exported launcher, icon and round icon, `resizeableActivity="true"`,
  `configChanges` for orientation, size, layout, keyboard, navigation, UI mode, density, font scale,
  color mode and touchscreen (a deliberate deviation from the adaptive guidance, see
  docs/DEVIATIONS.md), launch mode, `<layout>` default and minimum sizes, and the multi-instance
  property.
  *Outside code:* `template/src/main/AndroidManifest.xml`.
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
  and Window (Reload, Zoom in, Zoom out, Reset zoom, Print, App options, Context menu (Shift+F10)
  while the app's own menu is on, and New window when enabled).
- T5.5 Context menu (with `profile.nativeContextMenu`, on by default, or the Context menu switch of
  App options, T5.8, which overrides it at once): App options, then after a divider Copy link, Open
  in browser and Save image, per T2d. Anywhere else it has no items, so the press reaches the page
  (text selection, the site's menu). Image links copy the anchor's href (`requestFocusNodeHref`), not
  the image URL.
  - Drawn as Android's own menus (`ContextMenuPopup`): the theme's dynamic colors (T3.1), a 16 dp
    container on the surface container color, 48 dp items with 12 dp rounded highlights and a
    Material Symbols icon each. Long press, right-click and the keyboard open the same popup, at the
    finger or pointer, kept inside the window; `DesktopWebView` hands the system's context menu call
    to it, and with the menu left to the site the call goes on as WebView's own.
  - The menu key and Shift+F10 open it for the link or image in focus. Arrow keys move, Enter
    selects, Esc or Back closes; accessibility services hear a pane titled "Context menu".
- T5.6 Back goes back in the page history while it can (`OnBackPressedCallback` kept in sync in
  `doUpdateVisitedHistory`); full-screen video takes Back first.
- T5.7 Zoom: pinch zoom on touch (built-in controls, no on-screen buttons), and Ctrl + scroll wheel or
  trackpad scroll zooms text like Ctrl+= and Ctrl+-. Page text starts at the system font size (font
  scale 1.3 is a text zoom of 130 %, to the nearest 10 %) and follows it when it changes, until the
  user zooms; their zoom is kept, and Ctrl+0 returns to following the system font size.
- T5.8 App options: a panel of the app's own switches, opened by Ctrl+, (once per press, T2c), by
  "App options" in the app icon's launcher shortcuts, and first in the context menu on links and
  images (T5.5). The context menu opens nowhere else, so it never takes a press the page needs.
  - Titled "App options". A bottom sheet in a window narrower than 600 dp, a dialog in a wider one.
  - Each switch has a fixed label and a description of what it does when on (docs/SHRIMPL_SPEC.md
    R1.6), applies at once and is remembered in `SessionPrefs`. The whole row toggles it, at least
    72 dp tall, and accessibility services hear one switch with its label, description and state.
    Its text wraps 24 dp before the switch, as Shrimpl's settings items do.
  - Its switches, in order: Desktop site ("Shows the site as it looks on a computer"), when the
    profile allows the toggle (T6.11); Context menu ("Shows a menu to copy, open or save
    links and images, instead of the site's own.", T5.5) and Block ads ("Blocks the ads the site
    loads", T6.19), in every app. Block ads on keeps a level Shrimpl already set, else Strict;
    off is Off.
  - Done closes it. There is no Reset: each change is also saved to the app's edit page in Shrimpl
    (docs/SHRIMPL_SPEC.md R11.9), so the edit page always matches the panel.
  - The panel's changes apply in the app at once, and are sent together when it closes: with Done,
    or Back, a tap outside or swiping the sheet down, so the edit page never misses one. They go to
    every installed Shrimpl's App options provider (found by the action
    `com.valghora.shrimpl.action.APP_OPTIONS`), off the main thread; only the one that built the app
    saves them. Without one, the choice holds in the app until its next build.
  - A new build (a new versionCode) forgets the panel's choices: Shrimpl saved them, so the build
    carries them, or what the user changed on the edit page since.
  - The launcher shortcut carries a random token kept in the app's preferences; a launch without it
    (another app starting the exported activity) doesn't open the panel.
- T5.9 Live settings (docs/SHRIMPL_SPEC.md R11.10): whenever the app comes to the front (onStart), it
  asks the Shrimpl that built it for its live settings, on the same thread that sends App options
  changes, so a change just sent is read back. An answer replaces those settings in the profile and
  the panel's own choices; a new layout reloads the start URL, and the context menu, Pause
  in battery saver and ad blocking (T6.19, with any newer list copied) apply at once. Without an
  answer the app keeps what it has.
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
- T6.3 Downloads (http and https) go to `DownloadManager` with the page's cookies and user
  agent, into Downloads, with a completion notification. Files the page made itself (`blob:`, `data:`)
  follow T6.15; other schemes show a toast. "Save image" takes the same routes.
- T6.4 File chooser.
  - The page's chooser intent (multiple when asked); results from clip data and data; the callback is
    called exactly once.
  - An input that takes images (or names no type) also offers the camera for a photo, one that takes
    video for a video (`CaptureChoice` in `:core`), beside the files. With the `capture` attribute
    the camera opens on its own.
  - The camera app writes a full-size file into the cache (`camera/`, shared through the template's
    FileProvider, `<package>.files`, which the patcher renames with the package); files a day old
    are cleared when a chooser opens.
  - A web app that declares the camera permission (camera allowed, R7.3) must hold it to start a
    camera app: a `capture` input asks through T6.5's flow first and falls back to the file picker
    if refused, and a plain input leaves the camera out until it is granted. One that doesn't
    declare it starts the camera freely.
- T6.5 Media and location permissions.
  - Media: only for internal origins and only the resources the profile allows (microphone, camera,
    protected media); Android's runtime permission is requested when missing, and only granted
    resources are passed on.
  - Location (with `profile.allowLocation`): internal origins only; Android's location permission
    (precise or approximate) is asked for, and the page is answered with the result.
  - A missing permission is handled in three steps (`PermissionPlan` in `:core`):
    1. the first time, Android asks directly;
    2. after one "Don't allow", a dialog explains first ("Use your camera and microphone?", the
       page's host and the app's name; Not now or Continue). Continue brings Android's prompt back;
       Not now, Back or tapping outside turns the page down;
    3. once Android no longer asks (a second "Don't allow"), the page is turned down and a snackbar
       says so ("Camera and microphone are off for <app>") with Settings, which opens the app's info
       page. It shows once per window for each permission.
  - The app remembers which permissions it asked for, since Android can't tell "never asked" from
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
- T6.11 Desktop/mobile toggle (`profile.uaUserToggle`, no override): the Desktop site switch of App
  options (T5.8), which saves the choice and reloads the start URL. On is the Desktop user agent, off
  is Mobile.
- T6.12 One startup log line: `UA mode=<mode> reason=<reason> metadata=<applied|unsupported|default>`
  (tag `profile.appId`).
- T6.13 Full-screen video: the page's custom view covers the window with the system bars hidden (swipe
  shows them briefly); in a desktop or split-screen window the activity also asks for full screen
  (`requestFullscreenMode`). Esc and Back leave it.
- T6.14 Printing: Ctrl+P prints the page (`PrintManager` with the WebView's print adapter), which can
  also save a PDF.
- T6.15 Page-made downloads (`blob:` and `data:`, such as an editor's Export or Save to disk).
  - A document-start script on the internal origins (nested `WEB_MESSAGE_LISTENER` and
    `DOCUMENT_START_SCRIPT` checks, as T7.1) remembers the `download` name of each link the page
    clicks and takes hold of a `blob:` file at once, since sites often revoke the link right after the
    click.
  - When WebView reports the download, the template asks the page for the file, which arrives in
    512 KiB base64 chunks over a `WebMessageListener` (`AndroidDownload`, internal origins only) and
    is written in order, off the main thread, to a pending MediaStore entry in Downloads (no storage
    permission). A transfer over 1 GiB is dropped.
  - The name is made safe: no path, no reserved characters, at most 120 characters, an extension from
    the type when it has none, "download" when the page gave none. MediaStore numbers a duplicate.
  - Feedback: the toast says "Downloading…". When the file is complete, a low-importance "Downloads"
    notification ("<name>", "Saved to Downloads") opens it, or without notification permission a
    toast says "Saved <name> to Downloads". A failure removes the partial file and says "Couldn't save
    <name>".
  - A page outside the internal origins, or a WebView without the features, gets the unsupported
    toast.
  - The protocol and naming are `PageDownloads` in `:core`.
- T6.16 Web Share API (WebView has none).
  - A document-start script on the internal origins (nested checks, as T7.1) adds `navigator.share`
    and `navigator.canShare`, replacing any the WebView has.
  - A share of a title, text or link posts to a `WebMessageListener` (`AndroidShare`, internal origins
    only), which opens Android's share sheet: the text with the link on its own line after it (not
    repeated when the text has it), or the title when there is neither; the title as subject.
  - The promise resolves once the sheet opens (Android doesn't report whether the user shared) and
    rejects with AbortError when no app can share.
  - Files aren't shared: `canShare` returns false and `share` rejects with NotAllowedError, so sites
    offer a download instead (T6.15).
  - Parsing is `PageShare` in `:core`.
- T6.17 Single-file File System Access (WebView's own pickers throw a SecurityError without a user
  gesture and open no dialog; folders are T6.18).
  - A document-start script on the internal origins (nested checks, as T7.1) adds
    `showOpenFilePicker` and `showSaveFilePicker`, replacing the WebView's.
  - Open shows Android's Open dialog for any file (sites' own types are often unknown to Android),
    several with `multiple`. Save shows its Save dialog with the suggested name made safe (as T6.15)
    and the first plain MIME type from `types`, else a generic type so Android adds no extension.
  - One picker at a time; backing out rejects with AbortError.
  - Each picked file gets a handle key for this session (`FileAccessBridge`, main thread). The site
    reads it with `getFile`, sent in 512 KiB parts with its name, type and modification time.
  - Saving back is silent: `createWritable` gives a WritableStream with `write`, `seek` and `truncate`
    (strings, buffers, Blobs and write commands; `keepExistingData` starts from the file). On close it
    sends the whole content in parts to a temporary file, then replaces the picked file (`wt`, else
    `w`), so a failed save leaves it as it was.
  - Permission queries answer "granted".
  - Handles don't survive a restart of the app: a handle the site kept elsewhere fails with
    NotFoundError, and the site asks again.
  - Parsing is `FileAccess` in `:core`.
- T6.18 Folders.
  - `showDirectoryPicker` shows Android's folder picker (read and write). Android doesn't offer the
    top of internal storage, of Downloads, or `Android/data`, and some cloud providers offer no
    folders.
  - The folder's handle and every entry found in it get keys for the session, one per document, so
    `isSameEntry` holds.
  - Folder handles list entries (`entries`, `keys`, `values`, `for await`); get or create a file or
    folder (`getFileHandle`, `getDirectoryHandle`; a TypeMismatchError when the name is the other
    kind, NotFoundError without `create`); remove one (`removeEntry`; a folder with entries only with
    `recursive`, else InvalidModificationError); and `resolve` a descendant's path (handles keep their
    path from the picked folder).
  - `move` renames an entry or moves it to another folder of the same picked folder, never over an
    existing name; a picked file or folder can't move.
  - Names follow the standard: empty, `.`, `..` and names with a separator are a TypeError.
  - Files are created with a generic type so Android keeps the name as given; reads and writes are
    T6.17's.
  - All of it goes through `DocumentsContract` on the IO thread, one call per listing, so a folder of
    thousands of entries is slow to walk. Changes other apps make aren't reported.
- T6.19 Ad blocking (`profile.adBlocking`: Off, Standard with EasyList, Strict with EasyList and
  EasyPrivacy, both with the Adblock Warning Removal List; the Block ads switch of App options, T5.8, overrides it until Shrimpl answers).
  - Every request of the main window, popups and service workers goes through
    `shouldInterceptRequest`; a blocked one gets an empty 403 with status text `Blocked`. The page's
    own document is never blocked, nor anything on a sign-in domain (T2a).
  - The filter is pure Kotlin (`core/.../adblock/FilterEngine.kt`, R0): Adblock Plus network rules
    (`||`, `|`, `*`, `^`, `/regex/`, `@@`), with `$third-party` (a request off the app's internal
    domains), `$domain=` (the main page's host), `$match-case`, `$important` and negated types.
    Rules that need a request type WebView doesn't report, `$document` and unknown options are left
    out, so a list only ever blocks less. `$popup` rules and element hiding are T6.20's.
  - The app keeps its own copy of each list in `no_backup/adblock/`, copied from Shrimpl when its
    live-settings answer (T5.9) names a newer version (docs/SHRIMPL_SPEC.md R15.3). The filter is
    built off the main thread; until it exists nothing is blocked. Without Shrimpl the copy stays.
  - Switching Block ads reloads the page, so it loads as the new level allows.
- T6.20 Ad popups and element hiding, with T6.19's lists and level.
  - A window the page opens (T6.6) whose address a `$popup` rule names never shows and never
    reaches the browser; a popup that turns into one later (a redirect, a popunder) closes then.
    Only `$popup` rules decide, as in Adblock Plus; sign-in windows (T2a) always open.
  - Element hiding (`core/.../adblock/CosmeticFilter.kt`): the `##` rules, generic ones and those of
    the app's own domains, their subdomains and parents, with `#@#` exceptions and the
    `$generichide` and `$elemhide` page exceptions. A document-start script on the main window, for
    the app's internal domains, picks the rules for the page's host and adds a stylesheet hiding
    them; installed when the filter is built, it also runs once on the page already showing.
    Selectors go in groups of 200 inside `:is()`, whose forgiving list drops a selector WebView can't
    parse without its group.
  - Left out: procedural rules (`#?#`, `:has-text()` and the like), scriptlets (`#$#`, `+js()`),
    entity rules (`example.*##`) and site rules that also exclude subdomains, so a list only ever
    hides less.
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
  *Outside code:* a rule that no JavaScript interface exists.

## T8 Power and visibility

- T8.1 `WorkspaceMonitor` feeds T2e from interactive state, display states and power save mode, via
  a `DisplayListener` and a non-exported receiver (screen on/off, power save changes), registered in
  onCreate and unregistered in onDestroy.
- T8.2 `DesktopWebView` stays VISIBLE while hidden when `profile.keepAliveWhenHidden` and the
  workspace is awake, and GONE while it sleeps.
- T8.3 SLEEP: flush cookies, pause the WebView and its timers. WAKE: resume both and dispatch
  `online` and `focus` events to the page. No WakeLocks, no foreground service.
- T8.4 Lifecycle methods never call `pauseTimers()` / `resumeTimers()` (process-wide). Without
  `profile.keepAliveWhenHidden`, onStop pauses the WebView; onStart resumes what onStop paused, since
  the setting can change while the app is hidden (T5.9).

## Known limitations

- Google and some other identity providers block sign-in inside embedded WebViews
  (`disallowed_useragent`); the template doesn't try to evade this.
- Web features WebView lacks (Push API, WebUSB and Web Bluetooth, some passkey
  flows) don't work, and a desktop user agent can expose site features that need them.
- While the workspace sleeps, timers are paused: real-time connections drop and notifications arrive
  after wake. No background delivery.
- The notification polyfill always reports "granted"; if Android's permission is denied, notifications
  are dropped silently.
- A user agent toggle applies to the current window; other windows change on relaunch.
- Ctrl+P is the template's Print shortcut, so a site's own Ctrl+P (some editors use it for quick
  open) doesn't reach the page.
- Page-made downloads (T6.15) work only on the internal origins, and only from the main frame or a
  same-origin frame.
