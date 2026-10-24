# Universal APK Wrapper

<role>

You are a senior Android engineer working on your own in a terminal with file-system and network access. You will produce a complete, buildable Android Studio project that wraps one website in a WebView so it behaves like an installed PWA. It must work well on Android desktops (Googlebook OS and Android desktop windowing), tablets, and phones.

</role>

<how_to_read_this_prompt>

- Only the `<config>` block is specific to one app. Everything else is generic and refers to config values as `<<KEY>>`.
- NEVER write the characters `"<<"` or `">>"` into any generated file. Always substitute the resolved value.
- Requirements have IDs (R1.1, R6.3, ...). MUST = mandatory. SHOULD = do it unless there is a technical conflict, and if you skip it, record why in the Deviations table.
- Follow the Execution Plan in order. Do not start a phase until the gate for the previous phase passes.

</how_to_read_this_prompt>

<config>

```yaml
# ---------- Identity ----------
APP_NAME: "Discord"
PACKAGE_PREFIX: "com.valghora.pwa"
VERSION_CODE: 1
VERSION_NAME: "1.1.0"

# ---------- SDK (MIN_SDK supported range: 26–37) ----------
MIN_SDK: 37
TARGET_SDK: 37
COMPILE_SDK: 37

# ---------- Domains (hostnames only, lowercase, no scheme, no path) ----------
START_URL: "https://discord.com/app"
INTERNAL_DOMAINS: ["discord.com", "discordapp.com", "discord.gg", "discord.net", "discordapp.net"]
OAUTH_DOMAINS: ["accounts.google.com", "appleid.apple.com", "github.com", "login.microsoftonline.com", "login.live.com"]

# ---------- User agent ----------
USER_AGENT_MODE: "auto"                   # "auto" (by device class) | "desktop" | "mobile"
USER_AGENT_OVERRIDE: ""                   # non-empty = used verbatim; disables everything below
UA_STORE_REDIRECT_FALLBACK: true          # mobile UA + store redirect on first load -> switch to desktop once
UA_USER_TOGGLE: true                      # launcher shortcut "Request desktop/mobile site"

# ---------- Branding ----------
ICON_SVG: "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/discord.svg"  # URL or repo-relative file; single-color glyph, no background shape
ICON_BACKGROUND_HEX: "#5865F2"
ICON_FOREGROUND_HEX: "#FFFFFF"
HEADER_SURFACE_HEX: "#1E1F22"              # window/header color (light system theme)
HEADER_SURFACE_HEX_NIGHT: ""              # empty = same as HEADER_SURFACE_HEX

# ---------- Capabilities ----------
ALLOW_MICROPHONE: true
ALLOW_CAMERA: true
ALLOW_NOTIFICATIONS: true
ALLOW_PROTECTED_MEDIA: false               # DRM (RESOURCE_PROTECTED_MEDIA_ID)
ENABLE_MULTI_WINDOW: true                  # "New Window" in system UI
KEEP_ALIVE_WHEN_HIDDEN: true               # keep page running when minimized/backgrounded
PAUSE_ON_POWER_SAVE: false                 # true = page freezes whenever Battery Saver is on, even in the foreground
NATIVE_CONTEXT_MENU: true                  # native menu for links/images

# ---------- Build output ----------
SIGN_RELEASE_WITH_DEBUG_KEY: true          # installable optimized APK for personal sideloading; never for store distribution

# ---------- Freeform window defaults (desktop windowing only; ignored on phones) ----------
DEFAULT_WINDOW_DP: [1280, 800]
MIN_WINDOW_DP: [400, 500]

# ---------- Shortcuts ----------
# APP_SHORTCUTS are documentation only: they appear in the Keyboard Shortcuts Helper
# and MUST still reach the page unmodified. Modifiers: CTRL, SHIFT, ALT, META.
APP_SHORTCUTS: [
  {"label": "Quick Switcher", "key": "KEYCODE_K", "modifiers": ["CTRL"]},
  {"label": "Toggle Mute",    "key": "KEYCODE_M", "modifiers": ["CTRL", "SHIFT"]},
  {"label": "Toggle Deafen",  "key": "KEYCODE_D", "modifiers": ["CTRL", "SHIFT"]}
]
NEW_WINDOW_SHORTCUT: null                  # e.g. {"key": "KEYCODE_N", "modifiers": ["CTRL"]}; null = none
```

</config>

<derived_values>

Compute these in Phase 0 and print them as a table before creating any file. Use them verbatim afterward.
- APP_ID: lowercase(APP_NAME) with every character outside `[a-z0-9]` removed. If the result is empty or starts with a digit, prefix "app". (e.g. "Discord" -> "discord", "Proton Mail" -> "protonmail")
- PACKAGE_NAME: `<<PACKAGE_PREFIX>>.<<APP_ID>>`
- CORE_PACKAGE: `<<PACKAGE_NAME>>.core`
- NOTIFICATION_CHANNEL_ID: `"<<APP_ID>>_notifications"`
- HEADER_NIGHT: HEADER_SURFACE_HEX_NIGHT, or HEADER_SURFACE_HEX if empty
- USES_NIGHT_RESOURCES: true only if HEADER_NIGHT != HEADER_SURFACE_HEX
- HEADER_IS_DARK / HEADER_NIGHT_IS_DARK: true if the color's WCAG contrast ratio against white is >= its contrast ratio against black
- ALLOWED_ORIGIN_RULES: for each domain d in INTERNAL_DOMAINS: `"https://d"` and `"https://*.d"`
- UA_FEATURES_ACTIVE: true only if USER_AGENT_OVERRIDE is empty (otherwise R6.10 and R6.11 are not generated)
- JDK_TOOLCHAIN: the major version of the installed JDK (must be >= 17)
- DEBUG_APK_NAME: `"<<APP_ID>>-debug.apk"`
- RELEASE_APK_NAME: `"<<APP_ID>>-release.apk"` if SIGN_RELEASE_WITH_DEBUG_KEY, otherwise `"<<APP_ID>>-release-unsigned.apk"`
- RELEASE_APK_PATH: `app/build/outputs/apk/release/app-release.apk` if SIGN_RELEASE_WITH_DEBUG_KEY, otherwise `app/build/outputs/apk/release/app-release-unsigned.apk`
- GRADLE_FLAGS: `--project-cache-dir="$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | sha1sum | cut -c1-8)/gradle"`   (if sha1sum is missing, use `"shasum -a 1"`)

</derived_values>

<precedence>

If two instructions conflict, the higher item wins:
1. Platform and build correctness: it compiles, installs, and runs on MIN_SDK..TARGET_SDK.
2. Requirements in this prompt (R-numbered).
3. Values in `<config>`.
4. Researched Google guidance for Googlebook OS / Android desktop / large screens / phones.
Every deviation from items 2–4 MUST appear in the final Deviations table with the reason.

</precedence>

<non_goals>

Do not implement: Jetpack Compose, NDK/JNI, analytics, crash reporting, custom offline pages, verified App Links / intent filters for web URLs, blob:/data: downloads, geolocation, foreground services, WakeLocks, custom keystores or any signing other than R1.6, or changing the user agent when the window is resized.

</non_goals>

<requirements>

## R1 Build and architecture
- R1.1 MUST use Kotlin with AndroidX Views. The only allowed runtime dependencies are androidx.core:core-ktx, androidx.appcompat:appcompat, com.google.android.material:material, androidx.activity:activity-ktx, and androidx.webkit:webkit. The only test dependency is junit:junit (4.x). Remove all Compose and template dependencies. No native code, no abiFilters, no ABI splits. The APK must contain zero .so files.
- R1.2 MUST use the latest stable AGP, Gradle, and Kotlin versions that support COMPILE_SDK `<<COMPILE_SDK>>` (confirm in Phase 0 from maven.google.com/services.gradle.org metadata). Use a version catalog (gradle/libs.versions.toml) and `kotlin { jvmToolchain(<<JDK_TOOLCHAIN>>) }`; never pin a JDK version that is not installed. If the AGP version has built-in Kotlin support, do not also apply the standalone kotlin-android plugin. If Maven Central rate-limits dependency resolution (HTTP 429), you MAY list Google's mirror https://maven-central.storage-download.googleapis.com/maven2/ before mavenCentral(); record it in docs/BUILD_NOTES.md.
- R1.3 MUST set namespace = applicationId = `<<PACKAGE_NAME>>`, versionCode = `<<VERSION_CODE>>`, versionName = `"<<VERSION_NAME>>"`, rootProject.name = `"<<APP_ID>>"`.
- R1.4 MUST enable R8 on release builds (isMinifyEnabled = true, isShrinkResources = true, proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")). proguard-rules.pro contains no keep rules unless the code genuinely needs one. (There is no @JavascriptInterface in this design, per R7.6.)
- R1.5 SDK-check rule: wrap an API in a Build.VERSION.SDK_INT check only when its API level is greater than MIN_SDK. Never guard APIs at or below MIN_SDK (this triggers the ObsoleteSdkInt lint warning). Prefer AndroidX compat APIs when they remove the need for a check. Features that depend on the installed WebView version (androidx.webkit) are checked at runtime with WebViewFeature.isFeatureSupported, never with SDK_INT. Each androidx.webkit call MUST sit directly inside an `if (WebViewFeature.isFeatureSupported(X))` block for its own feature X (nest the checks; lint's RequiresFeature does not follow `a && b`).
- R1.6 If SIGN_RELEASE_WITH_DEBUG_KEY, MUST set `signingConfig = signingConfigs.getByName("debug")` on the release build type. Do not create keystores or signing properties.

## R2 Pure-JVM core (testable logic)
- R2.1 MUST: no file in CORE_PACKAGE imports `android.*`, `androidx.*`, or `com.google.android.*`.
- R2.2 MUST create CORE_PACKAGE/AppConfig.kt as a Kotlin `object` holding every `<config>` value and derived value as typed constants (`const val` for strings, numbers and booleans). Represent shortcuts as `data class ShortcutSpec(val label: String, val keyName: String, val modifiers: Set<Modifier>)` with `enum class Modifier { CTRL, SHIFT, ALT, META }`. The Android layer maps keyName to a key code with KeyEvent.keyCodeFromString. USER_AGENT_MODE is stored as `UaMode`. AppConfig.kt is the single source of truth: scripts/generate_icons.py reads ICON_SVG, ICON_BACKGROUND_HEX and ICON_FOREGROUND_HEX from it.
- R2.3 MUST create CORE_PACKAGE/DesktopSessionController.kt. Dependencies are passed in through the constructor: `interface ZoomStore { fun load(): Int?; fun save(value: Int) }`, `interface UaStore { var userChoice: UaMode?; var learnedDesktop: Boolean }`, and a `data class SessionConfig` (internal/OAuth domains, PAUSE_ON_POWER_SAVE, ENABLE_MULTI_WINDOW, APP_SHORTCUTS, NEW_WINDOW_SHORTCUT) whose defaults come from AppConfig, so tests can cover config-dependent branches. Small helper classes in CORE_PACKAGE are allowed. It is responsible for:
  a. URL classification `classify(url)` returning one of INTERNAL, OAUTH, EXTERNAL, PASSTHROUGH (about:, blob:, data:, javascript:), SPECIAL_SCHEME (intent:, mailto:, tel:, sms:, market:, and other non-http schemes), or BLOCKED (malformed URL, or http(s) without a host). Host matching is case-insensitive: host == d OR host ends with "." + d (a trailing dot on the host is ignored). Parse with java.net.URI, and ignore user-info.
  b. Text zoom: range 50–300 %, steps of 10, default 100. Expose `val textZoom: Int` (loaded from ZoomStore at construction) and `zoomIn()`, `zoomOut()`, `resetZoom()`, each returning the new value. Values are clamped, persisted via ZoomStore, and an invalid stored value snaps to the nearest valid step.
  c. Key resolution `resolveKey(keyName, modifiers, repeatCount)` returning RELOAD (modifiers exactly {CTRL} + KEYCODE_R, or KEYCODE_F5 with no modifiers), ZOOM_IN (exactly {CTRL} + KEYCODE_EQUALS / KEYCODE_PLUS / KEYCODE_NUMPAD_ADD), ZOOM_OUT (exactly {CTRL} + KEYCODE_MINUS / KEYCODE_NUMPAD_SUBTRACT), ZOOM_RESET (exactly {CTRL} + KEYCODE_0 / KEYCODE_NUMPAD_0), NEW_WINDOW (only if NEW_WINDOW_SHORTCUT is set, ENABLE_MULTI_WINDOW is true, and key and modifiers match exactly), or NONE. RELOAD and NEW_WINDOW resolve only when repeatCount == 0.
  d. Context-menu resolution: map a core-defined hit type (ANCHOR, IMAGE_ANCHOR, IMAGE, OTHER) to an ordered list of actions (ANCHOR: COPY_LINK, OPEN_LINK; IMAGE_ANCHOR: COPY_LINK, OPEN_LINK, SAVE_IMAGE; IMAGE: SAVE_IMAGE). OTHER maps to an empty list.
  e. Wake state machine: input (interactive, anyDisplayOn, powerSaveMode). awake = interactive && anyDisplayOn && !(PAUSE_ON_POWER_SAVE && powerSaveMode). The initial state is awake. Emit SLEEP or WAKE only when the state changes, otherwise NONE. Expose the current state as `isAwake`.
  f. User agent (`enum class UaMode { AUTO, DESKTOP, MOBILE }`):
     - `resolveUa(configMode, isLargeOrPc, userChoice, learnedDesktop, override): UaResolution(mode, reason)` where mode is always DESKTOP or MOBILE. Priority: userChoice (DESKTOP or MOBILE; AUTO is treated as null; reason "user") > learnedDesktop (DESKTOP; "learned") > configMode DESKTOP/MOBILE ("config"). AUTO resolves to DESKTOP if isLargeOrPc ("auto-large"), otherwise MOBILE ("auto-compact"). If override is non-empty, the mode is resolved the same way but the reason is "override".
     - `resolveUaMode(configMode: UaMode, isLargeOrPc: Boolean, userChoice: UaMode?, learnedDesktop: Boolean): UaMode` returns resolveUa(...).mode.
     - `buildUserAgent(defaultUa: String, mode: UaMode, override: String): String`:
       - override non-empty: return the override.
       - DESKTOP: `"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/<ver> Safari/537.36"`, where `<ver>` is taken from `"Chrome/<ver>"` in defaultUa. If no Chrome version is found, return the MOBILE result.
       - MOBILE: defaultUa with "; wv" and " Version/<x.y>" removed.
     - `isStoreRedirect(url: String): Boolean`: true for any market: URI; for intent: URIs containing "package=" or a URL-decoded S.browser_fallback_url pointing to play.google.com/store/apps; and for http(s)://play.google.com/store/apps (exactly, or followed by "/"). Everything else, including intent: URIs without package= and play.google.com/store/(books|movies|...), returns false.
     - `toggleTarget(current: UaMode): UaMode` returns MOBILE for DESKTOP, otherwise DESKTOP.
  g. Shortcut validation `validateShortcuts(): List<String>`: report errors for key names that don't match `^KEYCODE_[A-Z0-9_]+$`, for blank labels, for any APP_SHORTCUTS entry that resolves to a built-in from R2.3c (including NEW_WINDOW), and for a NEW_WINDOW_SHORTCUT that resolves to RELOAD/ZOOM_*.
  h. `isDarkColor(hex)`, using the rule from <derived_values> (throws IllegalArgumentException on invalid input), and `parseHex(hex): Int?` validation for #RRGGBB.
- R2.4 MUST write JUnit 4 tests:
  - DesktopSessionControllerTest.kt covering all branches of a–h, using SessionConfig to reach config-dependent branches. Include subdomain matching, look-alike hosts such as "notdiscord.com" and "discord.com.evil.example", user-info tricks such as "https://discord.com@evil.example/", malformed URLs, zoom bounds and snapping, and repeated key events. It also covers:
    - resolveUa/resolveUaMode: every priority level (userChoice DESKTOP/MOBILE/AUTO/null, learnedDesktop true/false, configMode DESKTOP/MOBILE/AUTO), with AUTO tested on both device classes, plus every reason value.
    - buildUserAgent: override, DESKTOP with and without a Chrome version, and MOBILE removing "; wv" and "Version/x.y".
    - isStoreRedirect: positives (market:, intent: with package=, intent: with a Play Store fallback URL, a play.google.com/store/apps URL) and negatives (intent: without package=, play.google.com/store/books, normal https, malformed URLs).
    - toggleTarget in both directions.
  - AppConfigTest.kt: START_URL is https and its host is INTERNAL; every hex value is valid; domains are lowercase with no scheme or path; shortcut validation returns no errors; USER_AGENT_MODE is a valid UaMode; ICON_SVG is https or a relative .svg path; every derived value matches its rule from <derived_values>.
- R2.5 MUST configure JaCoCo with two tasks, `jacocoTestReport` (XML + HTML) and `jacocoTestCoverageVerification`, both depending on testDebugUnitTest. Enable unit-test coverage on the debug build type (AGP: `enableUnitTestCoverage = true`). Point classDirectories at the directory where the build actually writes Kotlin classes, found by inspecting the build output (for AGP 9 with built-in Kotlin: build/intermediates/built_in_kotlinc/debug/), filtered to CORE_PACKAGE, and executionData at the unit-test .exec file. The verification rule requires LINE coverage >= 0.85. The gate also requires the XML report to list at least one class, and only classes from CORE_PACKAGE; an empty report is a failure.

## R3 Window, insets and theme (all form factors)
- R3.1 MUST build the UI in code: a root FrameLayout containing a DesktopWebView (a subclass of WebView with (Context) and (Context, AttributeSet?) constructors) with MATCH_PARENT on both axes. No ActionBar, Toolbar, or XML layout. The root's background is the current header color (HEADER_SURFACE_HEX, or HEADER_NIGHT in night mode).
- R3.2 MUST call enableEdgeToEdge() with SystemBarStyle.dark(Color.TRANSPARENT) when the current header is dark, otherwise SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT).
- R3.3 MUST call ViewCompat.setOnApplyWindowInsetsListener on the root and set left/top/right/bottom padding from insets.getInsets(systemBars() or captionBar() or displayCutout() or ime()), then return WindowInsetsCompat.CONSUMED. This covers the desktop caption bar, phone status and navigation bars, cutouts, and the soft keyboard.
- R3.4 MUST call window.insetsController?.setSystemBarsAppearance(appearance, mask), where mask = APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND | APPEARANCE_LIGHT_CAPTION_BARS and appearance = APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND | (header is light ? APPEARANCE_LIGHT_CAPTION_BARS : 0). Set status and navigation icon contrast via WindowInsetsControllerCompat (isAppearanceLightStatusBars / isAppearanceLightNavigationBars = header is light). Apply R1.5 to the caption-bar constants. Re-apply R3.1–R3.4 colors and R3.6 in onConfigurationChanged when the night mode changes.
- R3.5 MUST use Theme.Material3.DayNight.NoActionBar with android:windowBackground = @color/header_surface. Define header_surface in res/values/colors.xml. Create res/values-night/colors.xml only if USES_NIGHT_RESOURCES. Call webView.setBackgroundColor(current header color).
- R3.6 MUST call setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(app_name).setPrimaryColor(header).setBackgroundColor(header).build()), applying R1.5.
- R3.7 MUST put every user-visible string in res/values/strings.xml, and every resource must be referenced: zero UnusedResources warnings. Required strings: app_name, notification_channel_name, menu_copy_link, menu_open_link, menu_save_image, shortcut_group_window, shortcut_reload, shortcut_zoom_in, shortcut_zoom_out, shortcut_zoom_reset, toast_download_started, toast_download_unsupported, toast_no_app. Add only when the feature is enabled:
  - shortcut_new_window: NEW_WINDOW_SHORTCUT is set
  - shortcut_request_desktop, shortcut_request_desktop_long, shortcut_request_mobile, shortcut_request_mobile_long: UA_USER_TOGGLE && UA_FEATURES_ACTIVE
  Omit any string whose feature is disabled. APP_SHORTCUTS labels come from AppConfig.

## R4 Manifest and adaptive windowing
- R4.1 MUST NOT use android:screenOrientation, android:minAspectRatio, android:maxAspectRatio, resizeableActivity="false", or setRequestedOrientation anywhere.
- R4.2 MUST declare `<uses-feature android:required="false">` for android.hardware.type.pc and android.hardware.touchscreen. Add android.hardware.microphone only if ALLOW_MICROPHONE, and android.hardware.camera plus android.hardware.camera.autofocus only if ALLOW_CAMERA.
- R4.3 MUST declare exactly these permissions: INTERNET always; RECORD_AUDIO and MODIFY_AUDIO_SETTINGS only if ALLOW_MICROPHONE; CAMERA only if ALLOW_CAMERA; POST_NOTIFICATIONS only if ALLOW_NOTIFICATIONS.
- R4.4 MUST set these on `<application>`: android:label="@string/app_name", android:icon="@mipmap/ic_launcher", android:roundIcon="@mipmap/ic_launcher_round", android:theme, android:hardwareAccelerated="true", android:allowBackup="false", android:dataExtractionRules="@xml/data_extraction_rules", android:enableOnBackInvokedCallback="true". res/xml/data_extraction_rules.xml MUST exclude the root, file, database, sharedpref and external domains under both `<cloud-backup>` and `<device-transfer>` (since Android 12, allowBackup="false" does not stop device-to-device transfer, and session cookies must never leave the device). Do not set usesCleartextTraffic.
- R4.5 MUST set these on MainActivity: android:exported="true"; the LAUNCHER intent filter; the same icon and roundIcon as the application (do not repeat the label; it is inherited, and repeating it triggers lint RedundantLabel); android:resizeableActivity="true"; android:launchMode="singleInstancePerTask" if ENABLE_MULTI_WINDOW, otherwise "singleTask"; and android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|density|fontScale|colorMode|touchscreen". Also add, inside the activity:
  `<layout android:defaultWidth="<<DEFAULT_WINDOW_DP[0]>>dp" android:defaultHeight="<<DEFAULT_WINDOW_DP[1]>>dp" android:minWidth="<<MIN_WINDOW_DP[0]>>dp" android:minHeight="<<MIN_WINDOW_DP[1]>>dp" android:gravity="center" />`
- R4.6 If ENABLE_MULTI_WINDOW, MUST add `<property android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" android:value="true" />` inside MainActivity (apply R1.5 if MIN_SDK < 35; the attribute is ignored on older versions). When the NEW_WINDOW action fires, start MainActivity with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK; the new window loads START_URL.

## R5 Input: keyboard, pointer, touch, drag and drop
- R5.1 MUST set webView.isFocusable = true and isFocusableInTouchMode = true, and call requestFocus().
- R5.2 MUST attach a View.OnDragListener to the WebView. On DragEvent.ACTION_DROP, call requestDragAndDropPermissions(event). Always return false so the WebView's own drag handling still runs.
- R5.3 MUST override dispatchKeyEvent. On ACTION_DOWN, convert the event to (KeyEvent.keyCodeToString, modifiers, repeatCount), call controller.resolveKey, and consume the event only for a non-NONE result. Every other key, including APP_SHORTCUTS, goes to super unchanged. After a zoom action, set webView.settings.textZoom to the value the controller returns (the controller persists it).
- R5.4 MUST override onProvideKeyboardShortcuts and add two KeyboardShortcutGroups: one titled @string/app_name containing APP_SHORTCUTS, and one titled @string/shortcut_group_window containing Reload (Ctrl+R), Zoom In (Ctrl+=), Zoom Out (Ctrl+-), Reset Zoom (Ctrl+0), and New Window if configured.
- R5.5 If NATIVE_CONTEXT_MENU, MUST call registerForContextMenu(webView). In onCreateContextMenu, map webView.hitTestResult through the controller and add items only for link or image results. Add nothing for OTHER, so touch text selection and page-defined menus keep working.
  - COPY_LINK: for SRC_ANCHOR_TYPE use hitTestResult.extra. For SRC_IMAGE_ANCHOR_TYPE get the anchor href via webView.requestFocusNodeHref(message) (the "url" key, delivered to a main-looper Handler), not the image URL. Copy with ClipboardManager.
  - OPEN_LINK: Intent.ACTION_VIEW with CATEGORY_BROWSABLE (same href rules as COPY_LINK).
  - SAVE_IMAGE: the download path from R6.3. http(s) URLs only; otherwise show toast_download_unsupported.
- R5.6 MUST register an OnBackPressedCallback whose action is webView.goBack(). Keep its isEnabled equal to webView.canGoBack(), updated in WebViewClient.doUpdateVisitedHistory, so predictive back falls through to the system when there is no history.
- R5.7 SHOULD enable pinch zoom on touch devices: settings.setSupportZoom(true), builtInZoomControls = true, displayZoomControls = false.

## R6 WebView, cookies, downloads, media and routing
- R6.1 MUST implement a single function `applyWebSettings(webView: WebView, mode: UaMode)` and use it for every WebView, including the main WebView, the popup child WebViews (R6.6), and the UA switches in R6.10/R6.11. It MUST:
  a. Set: javaScriptEnabled = true, domStorageEnabled = true, mediaPlaybackRequiresUserGesture = false, setSupportMultipleWindows(true), javaScriptCanOpenWindowsAutomatically = true, allowFileAccess = false, allowContentAccess = false, the R5.7 zoom settings, and textZoom = controller.textZoom (so the persisted zoom applies at startup and to popups).
  b. Set userAgentString = controller.buildUserAgent(WebSettings.getDefaultUserAgent(context), mode, USER_AGENT_OVERRIDE). This is the only assignment to userAgentString in the project.
  c. If mode == DESKTOP and USER_AGENT_OVERRIDE is empty:
     - If WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA), read WebSettingsCompat.getUserAgentMetadata(settings), save it as the original metadata if it was not saved yet, rebuild it with UserAgentMetadata.Builder(current) setting platform = "Linux", platformVersion = "", mobile = false, and model = "" (the brand version list and full version stay unchanged), then apply it with WebSettingsCompat.setUserAgentMetadata and remember that this WebView was changed. If the feature is not supported, skip this step and log it once.
     - Set useWideViewPort and loadWithOverviewMode to true if !isLargeOrPc, otherwise to false.
  d. If mode == MOBILE, or USER_AGENT_OVERRIDE is set: if this WebView's metadata was changed, restore the saved original (inside its own feature check); set useWideViewPort = false and loadWithOverviewMode = false (the WebView defaults). Restoring matters when R6.11 switches back from DESKTOP.
  Resolve the mode exactly once in onCreate, before the first loadUrl or restoreState, with:
    resolution = controller.resolveUa(USER_AGENT_MODE, isLargeOrPc, uaStore.userChoice, uaStore.learnedDesktop, USER_AGENT_OVERRIDE)
    isLargeOrPc = packageManager.hasSystemFeature(PackageManager.FEATURE_PC) || resources.configuration.smallestScreenWidthDp >= 600
  Store resolution.mode in a field. Child WebViews reuse it; they never resolve it again.
  MUST NOT change the mode or call applyWebSettings because of window resizes or configuration changes. The only places that may change it are R6.10 and R6.11, and each of those changes is followed by a reload.
  UaStore and ZoomStore are implemented in the Android layer with one private SharedPreferences file.
- R6.2 MUST set CookieManager to accept cookies and third-party cookies for every WebView. Call flush() in onPause, onStop, and onDestroy.
- R6.3 MUST set a DownloadListener on every WebView. For http/https URLs, enqueue a DownloadManager.Request with headers Cookie (from CookieManager.getCookie(url), if non-null) and User-Agent (the WebView's current userAgentString), the MIME type (if non-blank), a file name from URLUtil.guessFileName, setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name), VISIBILITY_VISIBLE_NOTIFY_COMPLETED, and show toast_download_started. For any other scheme, show toast_download_unsupported.
- R6.4 MUST implement onShowFileChooser using ActivityResultContracts.StartActivityForResult with fileChooserParams.createIntent(). Put EXTRA_ALLOW_MULTIPLE = true when mode == MODE_OPEN_MULTIPLE. Collect URIs from both clipData and data (deduplicated; null if none). Always call the callback exactly once (null on cancel or if no chooser activity exists), and cancel any pending callback before starting a new chooser.
- R6.5 MUST implement WebChromeClient.onPermissionRequest:
  - Deny if the request origin is not INTERNAL.
  - Filter the requested resources by config: RESOURCE_AUDIO_CAPTURE needs ALLOW_MICROPHONE, RESOURCE_VIDEO_CAPTURE needs ALLOW_CAMERA, RESOURCE_PROTECTED_MEDIA_ID needs ALLOW_PROTECTED_MEDIA. Other resources are dropped.
  - Store the pending request (denying any previous pending one) and launch RequestMultiplePermissions for any missing RECORD_AUDIO / CAMERA.
  - Grant only the resources whose runtime permissions were granted (protected media needs no runtime permission). Deny if nothing remains.
  - Clear the pending request in onPermissionRequestCanceled.
- R6.6 MUST route every navigation through controller.classify:
  - Main frame (shouldOverrideUrlLoading, main-frame requests only; sub-frames load normally): first apply R6.10 if its conditions hold. Then: INTERNAL, OAUTH and PASSTHROUGH load in place (OAuth redirect flows need this). EXTERNAL opens ACTION_VIEW + CATEGORY_BROWSABLE. SPECIAL_SCHEME: parse intent: URIs with Intent.parseUri(URI_INTENT_SCHEME), set component and selector to null, and add CATEGORY_BROWSABLE. If no activity handles it, use browser_fallback_url (http/https only): load it in place if it is INTERNAL or OAUTH, otherwise open it with ACTION_VIEW. Other schemes use ACTION_VIEW + CATEGORY_BROWSABLE. Catch ActivityNotFoundException and show toast_no_app. BLOCKED does nothing.
  - Popups (onCreateWindow): always create a child WebView, configure it with applyWebSettings(child, resolvedMode), R6.2 cookies and the R6.3 download listener, attach it via WebView.WebViewTransport, and call sendToTarget(). Decide the popup's fate on its first navigation other than about:blank, seen in shouldOverrideUrlLoading or, if that is not called for it, in onPageStarted (onPageStarted decides only before the first decision):
    - EXTERNAL: hand off to ACTION_VIEW; SPECIAL_SCHEME: hand off as in the main frame. In both cases stop loading and destroy the child without showing it (even if isDialog).
    - INTERNAL or OAUTH, or isDialog == true: show the child in a modal Dialog sized to 90% of the window.
    - Anything else (PASSTHROUGH or BLOCKED without isDialog): destroy the child.
    - After the decision, route the popup's navigations like the main frame (without R6.10).
    - WebChromeClient.onCloseWindow dismisses the dialog and destroys the child. Dismissing the dialog also destroys the child. Never destroy a WebView synchronously inside its own callback; post the destroy to the main thread.
- R6.7 MUST override onRenderProcessGone: for the main WebView, remove and destroy it (guarded so onDestroy does not destroy it twice), call recreate(), and return true. For a popup, dismiss it and return true.
- R6.8 SHOULD save and restore history (webView.saveState / restoreState) to survive process death. Load START_URL only when there is no restored state. Apply R6.1 before restoreState.
- R6.9 MUST destroy every WebView (main and popups) in onDestroy after removing it from its parent.
- R6.10 Store-redirect fallback. Generate only if UA_STORE_REDIRECT_FALLBACK && UA_FEATURES_ACTIVE. Active only while the resolved mode is MOBILE, uaStore.userChoice is null, and uaStore.learnedDesktop is false.
  - The detection window opens at the first main-frame onPageStarted after the app starts (or after a reload triggered by R6.11) and closes 10 s later.
  - If, while the window is open, a main-frame navigation satisfies controller.isStoreRedirect(url), you MUST block it (return true from shouldOverrideUrlLoading; do not launch any intent), set uaStore.learnedDesktop = true, set resolvedMode = DESKTOP, call applyWebSettings(webView, DESKTOP), loadUrl(START_URL), and republish the R6.11 shortcut.
  - This happens at most once per install. After learnedDesktop is true, this requirement does nothing.
  - Store redirects outside the window, or with a DESKTOP mode, are routed normally through R6.6.
- R6.11 Desktop/mobile toggle. Generate only if UA_USER_TOGGLE && UA_FEATURES_ACTIVE.
  - Publish exactly one dynamic launcher shortcut with ShortcutManagerCompat.setDynamicShortcuts (androidx.core), ID "toggle_ua", icon IconCompat.createWithResource(context, R.mipmap.ic_launcher). Its labels are @string/shortcut_request_desktop / _long when resolvedMode is MOBILE, and @string/shortcut_request_mobile / _long when DESKTOP. Its intent is an explicit Intent(context, MainActivity::class.java) with action ACTION_VIEW and extra "toggle_ua" = true.
  - Handle the extra in both onCreate and onNewIntent. Call ShortcutManagerCompat.reportShortcutUsed(context, "toggle_ua"), set target = controller.toggleTarget(resolvedMode), save uaStore.userChoice = target, set resolvedMode = target, call applyWebSettings(webView, target), loadUrl(START_URL) (skipping restoreState in onCreate), and republish the shortcut with the new labels. Remove the extra and call setIntent after handling it so recreation does not toggle again.
  - Other open windows keep their current mode until they are relaunched. Note this in Known limitations.
  - Do NOT assign a keyboard shortcut to the toggle (site shortcuts may collide).
- R6.12 Log one line at startup (tag = APP_ID): "UA mode=<DESKTOP|MOBILE> reason=<override|user|learned|config|auto-large|auto-compact> metadata=<applied|unsupported|default>", using the R2.3f resolution and the metadata outcome of the main WebView's applyWebSettings call.

## R7 Notification bridge (only if ALLOW_NOTIFICATIONS)
- R7.1 MUST check WebViewFeature.isFeatureSupported for WEB_MESSAGE_LISTENER and DOCUMENT_START_SCRIPT (nested, per R1.5). If either is missing, skip R7.2–R7.3, log it once, and never crash. Install the bridge on the main WebView before its first loadUrl or restoreState.
- R7.2 MUST call WebViewCompat.addWebMessageListener(webView, "AndroidNotify", ALLOWED_ORIGIN_RULES, listener). In the listener, check again that sourceOrigin's host is INTERNAL. Parse the JSON {title, body, tag} (ignore invalid JSON), truncating title to 200 characters and body to 1000, and post a notification.
- R7.3 MUST call WebViewCompat.addDocumentStartJavaScript(webView, polyfill, ALLOWED_ORIGIN_RULES). The polyfill MUST be idempotent (guarded by a window flag) and MUST:
  - Replace window.Notification with a class extending EventTarget: static permission = "granted"; static requestPermission(cb) resolves "granted" and calls cb if it is a function; the constructor calls AndroidNotify.postMessage(JSON.stringify({title: String(title || ""), body: String(options?.body || ""), tag: String(options?.tag || "")})); close() is a no-op.
  - Patch ServiceWorkerRegistration.prototype.showNotification (if present) to post the same way and return Promise.resolve().
  - Make navigator.permissions.query({name: "notifications"}) resolve {state: "granted"}, delegating all other permission names to the original.
- R7.4 MUST create a channel with NOTIFICATION_CHANNEL_ID, name @string/notification_channel_name, and IMPORTANCE_HIGH. Notifications use small icon R.drawable.ic_stat_notify, color ICON_BACKGROUND_HEX, BigTextStyle for the body, autoCancel, and a PendingIntent (FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT) that brings MainActivity to the front. Notification ID = tag.hashCode() if a tag is present, otherwise an incrementing counter. If POST_NOTIFICATIONS is not granted, drop the notification silently (check with ContextCompat.checkSelfPermission in the posting function).
- R7.5 MUST request POST_NOTIFICATIONS on a fresh launch (savedInstanceState == null) if it is not granted (apply R1.5).
- R7.6 MUST NOT use addJavascriptInterface or @JavascriptInterface anywhere.

## R8 Power, visibility and multi-display
- R8.1 MUST implement isWorkspaceAwake(context). It gathers pm.isInteractive, displayManager.displays.any { it.state == Display.STATE_ON }, and pm.isPowerSaveMode, passes them to the controller's wake state machine (R2.3e), dispatches any SLEEP/WAKE event, and returns controller.isAwake.
- R8.2 MUST give DesktopWebView a setWorkspaceAwake(Boolean) method (which re-applies the current window visibility when the value changes) and override onWindowVisibilityChanged(visibility): if not awake, call super with View.GONE; else if KEEP_ALIVE_WHEN_HIDDEN, call super with View.VISIBLE; else call super with visibility.
- R8.3 MUST register a DisplayManager.DisplayListener (on the main looper) and a BroadcastReceiver (via ContextCompat.registerReceiver with ContextCompat.RECEIVER_NOT_EXPORTED) for ACTION_SCREEN_OFF, ACTION_SCREEN_ON, and ACTION_POWER_SAVE_MODE_CHANGED. Register in onCreate, evaluate isWorkspaceAwake once right after registering, and unregister in onDestroy.
- R8.4 On SLEEP: call CookieManager flush(), setWorkspaceAwake(false), webView.onPause(), and webView.pauseTimers(). On WAKE: call setWorkspaceAwake(true), webView.onResume(), webView.resumeTimers(), then evaluateJavascript("window.dispatchEvent(new Event('online'));window.dispatchEvent(new Event('focus'));", null). Hold zero WakeLocks.
- R8.5 Activity lifecycle methods MUST NOT call pauseTimers()/resumeTimers(), because they are process-wide and would freeze other windows. Only if KEEP_ALIVE_WHEN_HIDDEN is false: onStop calls webView.onPause() and onStart calls webView.onResume().

## R9 Adaptive icons (zero-dependency Python generator)
- R9.1 MUST delete all template launcher assets (every `res/mipmap-*/` directory and the template `drawable/ic_launcher_*` files) before generating. The script performs this deletion itself on every run, so reruns are idempotent.
- R9.2 MUST write scripts/generate_icons.py using only the Python 3 standard library (e.g. urllib.request, ssl, re, shutil, json, argparse, pathlib, xml.etree.ElementTree). It reads ICON_SVG, ICON_BACKGROUND_HEX and ICON_FOREGROUND_HEX from CORE_PACKAGE/AppConfig.kt.
- R9.3 Source acquisition:
  - ICON_SVG is either an http(s) URL or a path relative to the project root. It MUST be a single-color glyph with no background shape. The background and glyph colors come only from ICON_BACKGROUND_HEX and ICON_FOREGROUND_HEX.
  - URL: fetch it and save the response verbatim as scripts/icon_source.svg (commit this file). If the fetch fails (for example, no network), use the existing scripts/icon_source.svg. File path: read it directly.
  - Extract the viewBox, concatenate every `<path d>` (a leading lowercase "m" in each path is treated as absolute, as SVG specifies), and record the fill-rule (evenodd if any path declares fill-rule="evenodd" via attribute or style, inherited through `<g>`, otherwise nonzero). Ignore any fill colors in the SVG. `<title>`, `<desc>` and `<metadata>` are ignored.
  - Exit non-zero with a clear message ("ICON_SVG must be a single-color glyph made only of `<path>` elements") if the SVG contains transforms, `<use>`, `<defs>`, non-path shapes (circle, rect, ellipse, polygon, polyline, line, image, text), gradients or pattern fills, or stroke-only/unfilled paths.
  - NEVER write path data from memory. If neither the source nor scripts/icon_source.svg is available, stop and ask the user.
- R9.4 Android PathParser normalization (the reason this matters: minified SVG path data makes Android's PathParser miscount arc parameters and silently render a blank icon):
  - Tokenize with an arc-flag-aware tokenizer. For A/a, parameters at positions 3 and 4 (mod 7, zero-based: rx, ry, rotation, large-arc-flag, sweep-flag, x, y) each consume exactly one '0' or '1' character, so "00-4.88" becomes "0", "0", "-4.88". Split chained decimals ("-1.51.074" becomes "-1.51", "0.074"). Handle exponents.
  - Re-serialize as android_safe_path: put the command letter before every parameter group (implicit repeats become explicit, and implicit lineto after M/m becomes an explicit L/l), separate every token with exactly one space, format numbers with a leading zero, at most 4 decimals, trailing zeros trimmed, no exponent notation, and "-0" written as "0".
  - Use android_safe_path, never the raw d string, in every android:pathData.
- R9.5 MUST support a `--verify` mode that re-parses every generated pathData and exits non-zero unless: every generated file exists and is well-formed XML; every command has the correct parameter count (M2 L2 H1 V1 C6 S4 Q4 T2 A7 Z0, case-insensitive); every arc flag is 0 or 1; no number starts with a bare "." (regex `(^|[\s-])\.\d`); there is no exponent notation; and there is no `"<<"` or ">>" (build these marker strings at runtime, e.g. `"<" * 2`, so the script itself never contains them).
- R9.6 MUST generate:
  - res/drawable/ic_launcher_background.xml: 108dp x 108dp, viewport 108 x 108, pathData "M 0 0 H 108 V 108 H 0 Z", fill ICON_BACKGROUND_HEX.
  - res/drawable/ic_launcher_foreground.xml: 108dp x 108dp, viewport 108 x 108, containing `<group>` with scale s = 54 / max(vbW, vbH), translateX = 54 - (vbMinX + vbW / 2) * s, and translateY = 54 - (vbMinY + vbH / 2) * s, wrapping `<path fillColor=ICON_FOREGROUND_HEX pathData=android_safe_path fillType=(evenOdd if the source fill-rule is evenodd, else nonZero)>`. For a 24 x 24 viewBox this gives scale 2.25 and translate 27, centered in the 66dp safe zone.
  - res/drawable/ic_stat_notify.xml: 24dp x 24dp, viewport = viewBox size (translated by -vbMinX, -vbMinY if they are non-zero), fill #FFFFFF, same fillType as the foreground.
  - res/mipmap-anydpi/ic_launcher.xml and ic_launcher_round.xml: `<adaptive-icon>` with `<background @drawable/ic_launcher_background>`, `<foreground @drawable/ic_launcher_foreground>`, and `<monochrome @drawable/ic_launcher_foreground>`. Use no -v26 qualifier (MIN_SDK >= 26) and generate no density PNG mipmaps.
  - Each generated file starts with a comment saying it is generated by scripts/generate_icons.py.

</requirements>

<forbidden>

Do not use any of the following to pass checks: lint baselines, abortOnError = false, disabling lint checks or issues, @SuppressLint (the only exception is "SetJavaScriptEnabled" on applyWebSettings), Kotlin @Suppress, JaCoCo exclusions beyond generated classes (R, BuildConfig), lowering any threshold, deleting or weakening tests, or hand-editing generated icon XML instead of fixing the script.
If a gate still fails after 3 fix attempts, stop and report the failure with its log. Do not weaken the requirement.

</forbidden>

<execution_plan>

Phase 0: Preflight and research
  - Print the <derived_values> table.
  - Check that the tools exist: JDK 17+, Android SDK platform `<<COMPILE_SDK>>` and build-tools, aapt2, python3, unzip. If scripts/setup_android_sdk.sh exists, run it: it installs missing SDK packages and prints this check. Report anything missing and stop if a required tool is absent.
  - Read the current versions of the pages below if the network allows. Record findings that affect this build (with URLs) in docs/BUILD_NOTES.md. Findings never silently override an R-requirement; conflicts go into the Deviations table. If a page is too large to read, say so and rely on the library check below.
      https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview
      https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing
      https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
      https://developer.android.com/reference/androidx/webkit/WebSettingsCompat
      https://developer.android.com/about/versions/`<<TARGET_SDK - 20>>`/behavior-changes-`<<TARGET_SDK - 20>>`   (e.g., API 37 -> Android 17)
  - Confirm the AGP, Gradle, Kotlin and androidx.webkit versions (R1.2). Confirm the androidx.webkit APIs from the downloaded library itself (e.g. `javap` on classes.jar from the AAR in the Gradle cache, once Phase 1 has resolved dependencies): WebViewFeature.USER_AGENT_METADATA / WEB_MESSAGE_LISTENER / DOCUMENT_START_SCRIPT, WebSettingsCompat.get/setUserAgentMetadata, UserAgentMetadata.Builder(UserAgentMetadata), WebViewCompat.addWebMessageListener and addDocumentStartJavaScript.
  - Gate: the derived values are printed and the tools are present.

Phase 1: Scaffold, Gradle, AppConfig
  - If an app module already exists with a different applicationId than PACKAGE_NAME, stop and ask the user before changing anything.
  - Create the project skeleton, version catalog, build files, and CORE_PACKAGE/AppConfig.kt. Reuse an existing Gradle wrapper and version catalog if present, updating versions only where Phase 0 found newer stable ones.
  - Gate: ./gradlew GRADLE_FLAGS help succeeds.

Phase 2: Core logic and tests
  - Implement R2.3, then R2.4 and R2.5.
  - Gate: ./gradlew GRADLE_FLAGS testDebugUnitTest jacocoTestReport jacocoTestCoverageVerification succeeds, and the JaCoCo XML report lists at least one class, all in CORE_PACKAGE (R2.5).

Phase 3: Icons
  - Implement R9.
  - Gate: python3 scripts/generate_icons.py && python3 scripts/generate_icons.py --verify both exit 0.

Phase 4: Manifest, themes, resources
  - Implement R3.5–R3.7 and R4.
  - Gate: ./gradlew GRADLE_FLAGS :app:processDebugMainManifest :app:processDebugResources succeeds.

Phase 5: Android layer
  - Implement MainActivity, DesktopWebView, the SharedPreferences-backed stores, and the notification/power components (R3, R5–R8).
  - Gate: ./gradlew GRADLE_FLAGS assembleDebug succeeds with no Kotlin compiler warnings in app sources.

Phase 5.5: Self-review
  - Re-read every R-requirement and the code that implements it. Check especially: callback re-entrancy and WebView destruction (R6.6, R6.7, R6.9), popup decision logic, lifecycle registration/unregistration, and that every resource and string is used. Fix discrepancies before Phase 6 and note anything non-obvious in the Deviations table.

Phase 6: Full verification (all checks must pass)
  If scripts/ci_verify.sh exists, run it; it performs steps 1–7 and prints a pass/fail summary. Still report each step below with its excerpt.
  1. ./gradlew GRADLE_FLAGS clean testDebugUnitTest jacocoTestReport jacocoTestCoverageVerification lintDebug assembleDebug assembleRelease
  2. The lint XML report shows 0 errors, 0 UnusedResources, and 0 ObsoleteSdkInt. List any remaining warnings in the report; aim for zero.
  3. `unzip -l` on both the debug APK and RELEASE_APK_PATH: no entries under `lib/` and no `*.so` files.
  4. `aapt2 dump badging` on the DEBUG APK (release resource paths may be shortened by R8): package name = `<<PACKAGE_NAME>>`, the min SDK line (`sdkVersion:` or `minSdkVersion:`, depending on the aapt2 version) = `<<MIN_SDK>>`, targetSdkVersion = `<<TARGET_SDK>>`, and the icon on both the application: and launchable-activity: lines matches res/mipmap-anydpi(-v\d+)?/ic_launcher.xml (aapt2 may add a version qualifier such as -v21).
  5. python3 scripts/generate_icons.py --verify exits 0.
  6. Static checks (each must return no matches unless stated):
     - grep -rE "^import (android|androidx|com\.google\.android)\." in CORE_PACKAGE sources
     - grep -rE "addJavascriptInterface|@JavascriptInterface" app/src
     - grep -E "screenOrientation|minAspectRatio|maxAspectRatio" app/src/main/AndroidManifest.xml
     - grep -rE `"<<|>>"` in app/src (non-Kotlin files; for Kotlin, only inside string literals), scripts/generate_icons.py, and the Gradle files
     - grep -rn "userAgentString\s*=" app/src/main: exactly one match, located inside applyWebSettings
  7. Copy the debug APK to dist/`<<DEBUG_APK_NAME>>` and RELEASE_APK_PATH to dist/`<<RELEASE_APK_NAME>>`. dist/ is not committed.
  8. Optional: if adb and a device or emulator are available, install, launch, confirm there is no FATAL EXCEPTION in logcat within 15 seconds, and include the R6.12 log line in the report.

</execution_plan>

<final_reminders>

- Zero .so files. Only the listed dependencies.
- APP_SHORTCUTS are listed in the shortcuts helper, never intercepted.
- android:pathData always uses the normalized android_safe_path, checked by --verify.
- No lint suppression, baselines, or coverage exclusions to pass checks. An empty coverage report is a failure, not a pass.
- pauseTimers() is called only on SLEEP, never in lifecycle methods.
- The notification bridge accepts messages only from ALLOWED_ORIGIN_RULES. No @JavascriptInterface.
- Every WebView is configured only through applyWebSettings. The UA mode is resolved once and changes only through R6.10/R6.11, never on resize.
- Session data never leaves the device (R4.4).
- Conflicts are resolved by `<precedence>` and recorded in the Deviations table.

</final_reminders>

<output_contract>

Finish with a report containing, in this order:
1. Derived values table.
2. Project file tree (excluding build/ and .gradle/).
3. Requirement checklist: each R-ID -> status (Done / Deviated / Skipped / Not generated (feature disabled)) -> file:line.
4. Deviations table: R-ID | what changed | why.
5. Verification results: each Phase 6 step with a pass/fail and a trimmed output excerpt (≤ 10 lines each).
6. Known limitations, always including:
   - Google and some identity providers block sign-in inside embedded WebViews ("disallowed_useragent"); do not attempt to evade this.
   - A desktop UA may expose site features that depend on APIs WebView lacks (Push API, File System Access, WebUSB/Bluetooth, some passkey flows).
   - A UA toggle applies to the current window only; other open windows update on relaunch.
   - Site features that depend on unsupported APIs (blob downloads, etc.).
   - While the workspace is asleep (screen off, or Battery Saver when PAUSE_ON_POWER_SAVE is true), page timers are paused, so real-time connections drop and web notifications arrive only after wake. There is no background delivery (no foreground service, by design).
   - The notification polyfill always reports "granted" to the page; if the user denies POST_NOTIFICATIONS, notifications are dropped silently.
   - If SIGN_RELEASE_WITH_DEBUG_KEY: the release APK is signed with the local debug key, installable only for personal sideloading and not suitable for distribution.

</output_contract>

