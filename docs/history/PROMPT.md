> **Historical.** The original per-site specification, superseded by docs/UNIWRAP_SPEC.md and docs/TEMPLATE_SPEC.md. Kept for reference only; nothing here is a current requirement.

# Universal APK Wrapper: template specification

<role>

You are a senior Android engineer maintaining a template that turns any website into an Android app: a WebView wrapper that behaves like an installed PWA on Android desktops (Googlebook OS and Android desktop windowing), tablets and phones. The template code is shared by every site; each site is described only by a small configuration file. You work on your own in a terminal with file-system and network access.

</role>

<how_to_read_this_prompt>

- A site is `sites/<id>/site.toml` layered over `sites/defaults.toml` (and the gitignored `sites/defaults.local.toml`). Its keys are listed in `docs/SITE_CONFIG.md`. This prompt refers to config values as `<<KEY>>`.
- Site-specific values never appear in template code or resources. They reach the app only through files generated at build time by `scripts/site_config.py` (R1.7).
- NEVER write the characters `"<<"` or `">>"` into any generated file. Always substitute the resolved value.
- Requirements have IDs (R1.1, R6.3, ...). MUST = mandatory. SHOULD = do it unless there is a technical conflict, and if you skip it, record why in the Deviations table.
- Follow the Execution Plan in order. Do not start a phase until the gate for the previous phase passes.

</how_to_read_this_prompt>

<site_config>

Example: `sites/discord/site.toml`. Keys not set here come from `sites/defaults.toml`.

```toml
APP_NAME = "Discord"
VERSION_NAME = "1.1.0"

START_URL = "https://discord.com/app"
INTERNAL_DOMAINS = ["discord.com", "discordapp.com", "discord.gg", "discord.net", "discordapp.net"]

ICON_SVG = "icon.svg"          # simple-icons "discord", stored in sites/discord/
ICON_BACKGROUND_HEX = "#5865F2"
ICON_FOREGROUND_HEX = "#FFFFFF"
HEADER_SURFACE_HEX = "#1E1F22"

ALLOW_MICROPHONE = true
ALLOW_CAMERA = true

APP_SHORTCUTS = [
  { label = "Quick Switcher", key = "KEYCODE_K", modifiers = ["CTRL"] },
  { label = "Toggle Mute", key = "KEYCODE_M", modifiers = ["CTRL", "SHIFT"] },
  { label = "Toggle Deafen", key = "KEYCODE_D", modifiers = ["CTRL", "SHIFT"] },
]
```

Defaults that matter: `PACKAGE_PREFIX = "com.valghora.pwa"`, `VERSION_CODE = "auto"`, SDK 37 (fixed by the template), `OAUTH_DOMAINS` (Google, Apple, GitHub, Microsoft), `USER_AGENT_MODE = "auto"`, `ALLOW_MICROPHONE/CAMERA = false`, `ALLOW_NOTIFICATIONS = true`, `KEEP_ALIVE_WHEN_HIDDEN = true`, `PAUSE_ON_POWER_SAVE = false`, `RELEASE_SIGNING = "auto"`, window 1280x800 (min 400x500).

</site_config>

<derived_values>

Computed by `scripts/site_config.py` (shown by `site_config.py show --site <id>`):
- APP_ID: `APP_ID` if the site sets it, else lowercase(APP_NAME) with every character outside `[a-z0-9]` removed, prefixed "app" if empty or starting with a digit. The site directory MUST be named after it.
- PACKAGE_NAME: `<<PACKAGE_PREFIX>>.<<APP_ID>>` (the applicationId).
- TEMPLATE_NAMESPACE: `com.valghora.wrapper` (fixed). CORE_PACKAGE: `com.valghora.wrapper.core`.
- VERSION_CODE: the integer given, or for `"auto"` the minutes since 2024-01-01 UTC, so every rebuild installs as an update.
- NOTIFICATION_CHANNEL_ID: `"<<APP_ID>>_notifications"`
- HEADER_NIGHT: HEADER_SURFACE_HEX_NIGHT, or HEADER_SURFACE_HEX if empty. USES_NIGHT_RESOURCES: true only if they differ.
- HEADER_IS_DARK / HEADER_NIGHT_IS_DARK: true if the color's WCAG contrast ratio against white is >= its contrast ratio against black.
- ICON_FOREGROUND_HEX for `"auto"`: `#FFFFFF` or `#000000`, whichever contrasts better with ICON_BACKGROUND_HEX.
- ICON_MODE: `svg`, `raster` or `letter`, from whichever of ICON_SVG / ICON_RASTER / ICON_LETTER is set (exactly one). ICON_LETTER `"auto"` = first ASCII letter or digit of APP_NAME.
- ALLOWED_ORIGIN_RULES: for each domain d in INTERNAL_DOMAINS: `"https://d"` and `"https://*.d"`
- UA_FEATURES_ACTIVE: true only if USER_AGENT_OVERRIDE is empty (otherwise R6.10 and R6.11 stay inactive).
- DEBUG_APK_NAME: `"<<APP_ID>>-debug.apk"`. RELEASE_APK_NAME / RELEASE_APK_PATH: `"<<APP_ID>>-release.apk"` / `app/build/outputs/apk/release/app-release.apk`, or the `-release-unsigned` variants when RELEASE_SIGNING is `"unsigned"`.
- APK_DIR: `sites/<<APP_ID>>/apks/<<VERSION_NAME>>`. Release APKs are committed; debug APKs are gitignored. After each build, `site_config.py prune-apks` keeps only the 3 most recently built version folders (by newest file, then version number; always including the current VERSION_NAME). VERSION_NAME may contain only letters, digits and `. _ + -`.
- GRADLE_FLAGS: `--project-cache-dir="$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | sha1sum | cut -c1-8)/gradle" -Psite=<<APP_ID>>`   (if sha1sum is missing, use `"shasum -a 1"`)

</derived_values>

<precedence>

If two instructions conflict, the higher item wins:
1. Platform and build correctness: it compiles, installs, and runs on API 37 for every site in `sites/`.
2. Requirements in this prompt (R-numbered).
3. Values in the site config.
4. Researched Google guidance for Googlebook OS / Android desktop / large screens / phones.
Every deviation from items 2–4 MUST appear in the final Deviations table with the reason.

</precedence>

<non_goals>

Do not implement: Jetpack Compose, NDK/JNI, analytics, crash reporting, custom offline pages, verified App Links / intent filters for web URLs, blob:/data: downloads, geolocation, foreground services, WakeLocks, Play Store signing or upload, or changing the user agent when the window is resized. Signing is limited to R1.6.

</non_goals>

<requirements>

## R1 Build and architecture
- R1.1 MUST use Kotlin with AndroidX Views. The only allowed runtime dependencies are androidx.core:core-ktx, androidx.appcompat:appcompat, com.google.android.material:material, androidx.activity:activity-ktx, and androidx.webkit:webkit. The only test dependency is junit:junit (4.x). No native code, no abiFilters, no ABI splits. The APK must contain zero .so files.
- R1.2 MUST use the latest stable AGP, Gradle, and Kotlin versions that support compileSdk 37 (confirm from maven.google.com/services.gradle.org metadata). Use a version catalog (gradle/libs.versions.toml) and `kotlin { jvmToolchain(N) }` with N = the installed JDK's major version (>= 17); never pin a JDK that is not installed. With AGP's built-in Kotlin, do not apply the standalone kotlin-android plugin. If Maven Central rate-limits resolution (HTTP 429), you MAY list Google's mirror https://maven-central.storage-download.googleapis.com/maven2/ before mavenCentral(); record it in docs/BUILD_NOTES.md.
- R1.3 MUST use namespace = TEMPLATE_NAMESPACE (`com.valghora.wrapper`) for all template code, and take applicationId = PACKAGE_NAME, versionCode, versionName and the SDK levels from the selected site. rootProject.name = `"universal-android-wrapper"`. The site is chosen with `-Psite=<id>`; `gradle.properties` holds the default (`site=discord`).
- R1.4 MUST enable R8 on release builds (isMinifyEnabled = true, isShrinkResources = true, proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")). proguard-rules.pro contains no keep rules unless the code genuinely needs one.
- R1.5 SDK-check rule: wrap an API in a Build.VERSION.SDK_INT check only when its API level is greater than MIN_SDK. Never guard APIs at or below MIN_SDK (ObsoleteSdkInt). Prefer AndroidX compat APIs when they remove the need for a check. WebView-version features (androidx.webkit) are checked at runtime with WebViewFeature.isFeatureSupported, never with SDK_INT, and each androidx.webkit call MUST sit directly inside an `if (WebViewFeature.isFeatureSupported(X))` block for its own feature X (nest the checks; lint's RequiresFeature does not follow `a && b`).
- R1.6 Release signing follows RELEASE_SIGNING. `"auto"`: a sideload key when one is configured (env `SIDELOAD_KEYSTORE`, `SIDELOAD_KEYSTORE_PASSWORD`, optional `SIDELOAD_KEY_ALIAS` (default "sideload") and `SIDELOAD_KEY_PASSWORD`, or the matching `sideload.*` Gradle properties), otherwise the local debug key. `"unsigned"`: no signing config. `scripts/create_sideload_key.sh` creates the key; it is never committed (`*.jks` and `*.keystore` are gitignored). Signed builds are for personal sideloading only.
- R1.7 Site generation. MUST read the site's build properties at configuration time with `providers.exec { python3 scripts/site_config.py properties --site <id> }` (failing the build with the validation message if the site is invalid), and register one `GenerateSiteSources` task per variant that runs `site_config.py generate`. Wire its outputs through the variant API: `variant.sources.kotlin.addGeneratedSourceDirectory` (AppConfig.kt), `variant.sources.res.addGeneratedSourceDirectory` (site resources and icons) and `variant.sources.manifests.addGeneratedManifestFile` (manifest overlay). Task inputs are the site directory, the defaults files, `scripts/site_config.py`, `scripts/generate_icons.py` and `scripts/letter_glyphs.json`. Generated files are never committed.

## R2 Pure-JVM core (testable logic)
- R2.1 MUST: no file in CORE_PACKAGE imports `android.*`, `androidx.*`, or `com.google.android.*`.
- R2.2 CORE_PACKAGE/AppConfig.kt MUST be generated by `site_config.py` as a Kotlin `object` holding every site config value and derived value as typed constants (`const val` for strings, numbers and booleans). Exceptions: build metadata (VERSION_CODE, VERSION_NAME, icon file paths) stays out of it, so rebuilds do not recompile the core. It includes ICON_MODE and RELEASE_SIGNING. Shortcuts are `data class ShortcutSpec(val label: String, val keyName: String, val modifiers: Set<Modifier>)` with `enum class Modifier { CTRL, SHIFT, ALT, META }`; NEW_WINDOW_SHORTCUT is `ShortcutSpec?` (label "New Window"). USER_AGENT_MODE is a `UaMode`. Kotlin string literals MUST escape `\`, `"` and `$`.
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
       - MOBILE: defaultUa with "; wv" and " Version/x.y" removed.
     - `isStoreRedirect(url: String): Boolean`: true for any market: URI; for intent: URIs containing "package=" or a URL-decoded S.browser_fallback_url pointing to play.google.com/store/apps; and for http(s)://play.google.com/store/apps (exactly, or followed by "/"). Everything else, including intent: URIs without package= and play.google.com/store/(books|movies|...), returns false.
     - `toggleTarget(current: UaMode): UaMode` returns MOBILE for DESKTOP, otherwise DESKTOP.
  g. Shortcut validation `validateShortcuts(): List<String>`: report errors for key names that don't match `^KEYCODE_[A-Z0-9_]+$`, for blank labels, for any APP_SHORTCUTS entry that resolves to a built-in from R2.3c (including NEW_WINDOW), and for a NEW_WINDOW_SHORTCUT that resolves to RELOAD/ZOOM_*.
  h. `isDarkColor(hex)`, using the rule from `<derived_values>` (throws IllegalArgumentException on invalid input), and `parseHex(hex): Int?` validation for #RRGGBB.
- R2.4 MUST write JUnit 4 tests that pass for every site:
  - DesktopSessionControllerTest.kt uses a fixed fixture SessionConfig (never the site's AppConfig values) and covers all branches of a–h: subdomain matching, look-alike hosts ("notdiscord.com", "discord.com.evil.example"), user-info tricks ("https://discord.com@evil.example/"), malformed URLs, zoom bounds and snapping, repeated key events, every resolveUa priority level and reason (AUTO on both device classes), buildUserAgent (override, DESKTOP with and without a Chrome version, MOBILE marker removal), isStoreRedirect positives and negatives, toggleTarget both ways.
  - AppConfigTest.kt checks the generated config: START_URL is https and INTERNAL; every hex value is valid; domains are bare lowercase hostnames; shortcut validation returns no errors; USER_AGENT_MODE is valid; ICON_MODE is known; CORE_PACKAGE is AppConfig's own package; every derived value matches its rule.
- R2.5 MUST configure JaCoCo with two tasks, `jacocoTestReport` (XML + HTML) and `jacocoTestCoverageVerification`, both depending on testDebugUnitTest. Enable unit-test coverage on the debug build type (`enableUnitTestCoverage = true`). Point classDirectories at the directory where the build actually writes Kotlin classes (for AGP 9 with built-in Kotlin: build/intermediates/built_in_kotlinc/debug/), filtered to CORE_PACKAGE, and executionData at the unit-test .exec file. LINE coverage >= 0.85. The report MUST list at least one class, and only CORE_PACKAGE classes; an empty report is a failure.

## R3 Window, insets and theme (all form factors)
- R3.1 MUST build the UI in code: a root FrameLayout containing a DesktopWebView (a subclass of WebView with (Context) and (Context, AttributeSet?) constructors) with MATCH_PARENT on both axes. No ActionBar, Toolbar, or XML layout. The root's background is the current header color (HEADER_SURFACE_HEX, or HEADER_NIGHT in night mode).
- R3.2 MUST call enableEdgeToEdge() with SystemBarStyle.dark(Color.TRANSPARENT) when the current header is dark, otherwise SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT).
- R3.3 MUST call ViewCompat.setOnApplyWindowInsetsListener on the root and set left/top/right/bottom padding from insets.getInsets(systemBars() or captionBar() or displayCutout() or ime()), then return WindowInsetsCompat.CONSUMED.
- R3.4 MUST call window.insetsController?.setSystemBarsAppearance(appearance, mask), where mask = APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND | APPEARANCE_LIGHT_CAPTION_BARS and appearance = APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND | (header is light ? APPEARANCE_LIGHT_CAPTION_BARS : 0). Set status and navigation icon contrast via WindowInsetsControllerCompat (isAppearanceLightStatusBars / isAppearanceLightNavigationBars = header is light). Re-apply R3.1–R3.4 colors and R3.6 in onConfigurationChanged when the night mode changes.
- R3.5 MUST use a site-neutral theme (`Theme.Wrapper`, parent Theme.Material3.DayNight.NoActionBar) with android:windowBackground = @color/header_surface. header_surface and app_name are generated in values/site.xml; values-night/site.xml is generated only if USES_NIGHT_RESOURCES. Call webView.setBackgroundColor(current header color).
- R3.6 MUST call setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(app_name).setPrimaryColor(header).setBackgroundColor(header).build()).
- R3.7 MUST put every user-visible string in resources, and every resource must be referenced: zero UnusedResources warnings. Template strings (res/values/strings.xml): notification_channel_name, menu_copy_link, menu_open_link, menu_save_image, shortcut_group_window, shortcut_reload, shortcut_zoom_in, shortcut_zoom_out, shortcut_zoom_reset, shortcut_new_window, shortcut_request_desktop(_long), shortcut_request_mobile(_long), toast_download_started, toast_download_unsupported, toast_no_app. They are always present because features are switched at runtime (R6.10, R6.11, R7, R8.5). app_name is generated and escaped for Android string resources. APP_SHORTCUTS labels come from AppConfig.

## R4 Manifest and adaptive windowing
The manifest is the static template manifest (application and activity attributes) merged with the overlay generated per site (features, permissions, launchMode, `<layout>`, multi-instance property).
- R4.1 MUST NOT use android:screenOrientation, android:minAspectRatio, android:maxAspectRatio, resizeableActivity="false", or setRequestedOrientation anywhere.
- R4.2 The overlay MUST declare `<uses-feature android:required="false">` for android.hardware.type.pc and android.hardware.touchscreen, plus android.hardware.microphone if ALLOW_MICROPHONE and android.hardware.camera + android.hardware.camera.autofocus if ALLOW_CAMERA.
- R4.3 The overlay MUST declare exactly: INTERNET and POST_NOTIFICATIONS always (the notification code is compiled into every site and lint requires the declaration; the permission is only requested when ALLOW_NOTIFICATIONS, R7.5); RECORD_AUDIO and MODIFY_AUDIO_SETTINGS if ALLOW_MICROPHONE; CAMERA if ALLOW_CAMERA.
- R4.4 The template MUST set on `<application>`: android:label="@string/app_name", android:icon="@mipmap/ic_launcher", android:roundIcon="@mipmap/ic_launcher_round", android:theme, android:hardwareAccelerated="true", android:allowBackup="false", android:dataExtractionRules="@xml/data_extraction_rules" (excluding root, file, database, sharedpref and external under both `<cloud-backup>` and `<device-transfer>`, so session cookies never leave the device), android:enableOnBackInvokedCallback="true". Do not set usesCleartextTraffic.
- R4.5 The template MUST set on MainActivity: android:exported="true"; the LAUNCHER intent filter; icon and roundIcon (not the label: it is inherited, and repeating it triggers lint RedundantLabel); android:resizeableActivity="true"; android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|density|fontScale|colorMode|touchscreen". The overlay sets android:launchMode ("singleInstancePerTask" if ENABLE_MULTI_WINDOW, else "singleTask") and adds `<layout android:defaultWidth="<<DEFAULT_WINDOW_DP[0]>>dp" android:defaultHeight="<<DEFAULT_WINDOW_DP[1]>>dp" android:minWidth="<<MIN_WINDOW_DP[0]>>dp" android:minHeight="<<MIN_WINDOW_DP[1]>>dp" android:gravity="center" />`.
- R4.6 If ENABLE_MULTI_WINDOW, the overlay MUST add `<property android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" android:value="true" />` inside MainActivity. When the NEW_WINDOW action fires, start MainActivity with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK; the new window loads START_URL.

## R5 Input: keyboard, pointer, touch, drag and drop
- R5.1 MUST set webView.isFocusable = true and isFocusableInTouchMode = true, and call requestFocus().
- R5.2 MUST attach a View.OnDragListener to the WebView. On DragEvent.ACTION_DROP, call requestDragAndDropPermissions(event). Always return false so the WebView's own drag handling still runs.
- R5.3 MUST override dispatchKeyEvent. On ACTION_DOWN, convert the event to (KeyEvent.keyCodeToString, modifiers, repeatCount), call controller.resolveKey, and consume the event only for a non-NONE result. Every other key, including APP_SHORTCUTS, goes to super unchanged. After a zoom action, set webView.settings.textZoom to the value the controller returns.
- R5.4 MUST override onProvideKeyboardShortcuts and add two KeyboardShortcutGroups: one titled @string/app_name containing APP_SHORTCUTS, and one titled @string/shortcut_group_window containing Reload (Ctrl+R), Zoom In (Ctrl+=), Zoom Out (Ctrl+-), Reset Zoom (Ctrl+0), and New Window when NEW_WINDOW_SHORTCUT is set and ENABLE_MULTI_WINDOW.
- R5.5 If NATIVE_CONTEXT_MENU (runtime check), MUST call registerForContextMenu(webView). In onCreateContextMenu, map webView.hitTestResult through the controller and add items only for link or image results. Add nothing for OTHER.
  - COPY_LINK: for SRC_ANCHOR_TYPE use hitTestResult.extra. For SRC_IMAGE_ANCHOR_TYPE get the anchor href via webView.requestFocusNodeHref(message) (the "url" key, delivered to a main-looper Handler), not the image URL. Copy with ClipboardManager.
  - OPEN_LINK: Intent.ACTION_VIEW with CATEGORY_BROWSABLE (same href rules as COPY_LINK).
  - SAVE_IMAGE: the download path from R6.3. http(s) URLs only; otherwise show toast_download_unsupported.
- R5.6 MUST register an OnBackPressedCallback whose action is webView.goBack(). Keep its isEnabled equal to webView.canGoBack(), updated in WebViewClient.doUpdateVisitedHistory.
- R5.7 SHOULD enable pinch zoom on touch devices: settings.setSupportZoom(true), builtInZoomControls = true, displayZoomControls = false.

## R6 WebView, cookies, downloads, media and routing
- R6.1 MUST implement a single function `applyWebSettings(webView: WebView, mode: UaMode)` and use it for every WebView, including the main WebView, popup child WebViews (R6.6), and the UA switches in R6.10/R6.11. It MUST:
  a. Set: javaScriptEnabled = true, domStorageEnabled = true, mediaPlaybackRequiresUserGesture = false, setSupportMultipleWindows(true), javaScriptCanOpenWindowsAutomatically = true, allowFileAccess = false, allowContentAccess = false, the R5.7 zoom settings, and textZoom = controller.textZoom.
  b. Set userAgentString = controller.buildUserAgent(WebSettings.getDefaultUserAgent(context), mode, USER_AGENT_OVERRIDE). This is the only assignment to userAgentString in the project.
  c. If mode == DESKTOP and USER_AGENT_OVERRIDE is empty:
     - If WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA), read WebSettingsCompat.getUserAgentMetadata(settings), save it as the original metadata if not saved yet, rebuild it with UserAgentMetadata.Builder(current) setting platform = "Linux", platformVersion = "", mobile = false, model = "", apply it with WebSettingsCompat.setUserAgentMetadata and remember that this WebView was changed. If unsupported, skip this step and log it once.
     - Set useWideViewPort and loadWithOverviewMode to true if !isLargeOrPc, otherwise to false.
  d. If mode == MOBILE, or USER_AGENT_OVERRIDE is set: if this WebView's metadata was changed, restore the saved original (inside its own feature check); set useWideViewPort = false and loadWithOverviewMode = false.
  Resolve the mode exactly once in onCreate, before the first loadUrl or restoreState, with `resolution = controller.resolveUa(USER_AGENT_MODE, isLargeOrPc, uaStore.userChoice, uaStore.learnedDesktop, USER_AGENT_OVERRIDE)` and `isLargeOrPc = packageManager.hasSystemFeature(PackageManager.FEATURE_PC) || resources.configuration.smallestScreenWidthDp >= 600`. Store resolution.mode in a field; child WebViews reuse it. MUST NOT change the mode or call applyWebSettings because of window resizes or configuration changes; only R6.10 and R6.11 change it, each followed by a reload. UaStore and ZoomStore use one private SharedPreferences file named after APP_ID.
- R6.2 MUST set CookieManager to accept cookies and third-party cookies for every WebView. Call flush() in onPause, onStop, and onDestroy.
- R6.3 MUST set a DownloadListener on every WebView. For http/https URLs, enqueue a DownloadManager.Request with headers Cookie (from CookieManager.getCookie(url), if non-null) and User-Agent (the WebView's current userAgentString), the MIME type (if non-blank), a file name from URLUtil.guessFileName, setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name), VISIBILITY_VISIBLE_NOTIFY_COMPLETED, and show toast_download_started. For any other scheme, show toast_download_unsupported.
- R6.4 MUST implement onShowFileChooser using ActivityResultContracts.StartActivityForResult with fileChooserParams.createIntent(). Put EXTRA_ALLOW_MULTIPLE = true when mode == MODE_OPEN_MULTIPLE. Collect URIs from both clipData and data (deduplicated; null if none). Always call the callback exactly once (null on cancel or if no chooser activity exists), and cancel any pending callback before starting a new chooser.
- R6.5 MUST implement WebChromeClient.onPermissionRequest:
  - Deny if the request origin is not INTERNAL.
  - Filter the requested resources by config: RESOURCE_AUDIO_CAPTURE needs ALLOW_MICROPHONE, RESOURCE_VIDEO_CAPTURE needs ALLOW_CAMERA, RESOURCE_PROTECTED_MEDIA_ID needs ALLOW_PROTECTED_MEDIA. Other resources are dropped.
  - Store the pending request (denying any previous pending one) and launch RequestMultiplePermissions for any missing RECORD_AUDIO / CAMERA.
  - Grant only the resources whose runtime permissions were granted (protected media needs none). Deny if nothing remains.
  - Clear the pending request in onPermissionRequestCanceled.
- R6.6 MUST route every navigation through controller.classify:
  - Main frame (shouldOverrideUrlLoading, main-frame requests only; sub-frames load normally): first apply R6.10 if active. Then: INTERNAL, OAUTH and PASSTHROUGH load in place. EXTERNAL opens ACTION_VIEW + CATEGORY_BROWSABLE. SPECIAL_SCHEME: parse intent: URIs with Intent.parseUri(URI_INTENT_SCHEME), set component and selector to null, and add CATEGORY_BROWSABLE. If no activity handles it, use browser_fallback_url (http/https only): load it in place if INTERNAL or OAUTH, otherwise open it with ACTION_VIEW. Other schemes use ACTION_VIEW + CATEGORY_BROWSABLE. Catch ActivityNotFoundException and show toast_no_app. BLOCKED does nothing.
  - Popups (onCreateWindow): always create a child WebView, configure it with applyWebSettings(child, resolvedMode), R6.2 cookies and the R6.3 download listener, attach it via WebView.WebViewTransport, and call sendToTarget(). Decide its fate on the first navigation other than about:blank, seen in shouldOverrideUrlLoading or, failing that, in onPageStarted (which decides only before the first decision):
    - EXTERNAL: hand off to ACTION_VIEW; SPECIAL_SCHEME: hand off as in the main frame. In both cases stop loading and destroy the child without showing it (even if isDialog).
    - INTERNAL or OAUTH, or isDialog == true: show the child in a modal Dialog sized to 90% of the window.
    - Anything else (PASSTHROUGH or BLOCKED without isDialog): destroy the child.
    - After the decision, route the popup's navigations like the main frame (without R6.10).
    - WebChromeClient.onCloseWindow dismisses the dialog and destroys the child; dismissing the dialog also destroys the child. Never destroy a WebView synchronously inside its own callback; post the teardown to the main thread.
- R6.7 MUST override onRenderProcessGone: for the main WebView, remove and destroy it (guarded so onDestroy does not destroy it twice), call recreate(), and return true. For a popup, dismiss it and return true.
- R6.8 SHOULD save and restore history (webView.saveState / restoreState). Load START_URL only when there is no restored state. Apply R6.1 before restoreState.
- R6.9 MUST destroy every WebView (main and popups) in onDestroy after removing it from its parent.
- R6.10 Store-redirect fallback, active only if UA_STORE_REDIRECT_FALLBACK && UA_FEATURES_ACTIVE (runtime check), and only while the resolved mode is MOBILE, uaStore.userChoice is null, and uaStore.learnedDesktop is false.
  - The detection window opens at the first main-frame onPageStarted after the app starts (or after a reload triggered by R6.11) and closes 10 s later.
  - If, while the window is open, a main-frame navigation satisfies controller.isStoreRedirect(url), block it (return true; launch no intent), set uaStore.learnedDesktop = true, set resolvedMode = DESKTOP, call applyWebSettings(webView, DESKTOP), loadUrl(START_URL), and republish the R6.11 shortcut. At most once per install.
  - Store redirects outside the window, or in DESKTOP mode, are routed normally through R6.6.
- R6.11 Desktop/mobile toggle, active only if UA_USER_TOGGLE && UA_FEATURES_ACTIVE (runtime check; when inactive, remove all dynamic shortcuts and ignore the extra).
  - Publish exactly one dynamic launcher shortcut with ShortcutManagerCompat.setDynamicShortcuts, ID "toggle_ua", icon IconCompat.createWithResource(context, R.mipmap.ic_launcher), labels @string/shortcut_request_desktop / _long when resolvedMode is MOBILE and @string/shortcut_request_mobile / _long when DESKTOP, intent = explicit Intent(context, MainActivity::class.java) with action ACTION_VIEW and extra "toggle_ua" = true.
  - Handle the extra in both onCreate and onNewIntent: remove it and call setIntent, call ShortcutManagerCompat.reportShortcutUsed(context, "toggle_ua"), set target = controller.toggleTarget(resolvedMode), save uaStore.userChoice = target, set resolvedMode = target, call applyWebSettings(webView, target), loadUrl(START_URL) (skipping restoreState in onCreate), and republish the shortcut.
  - Other open windows keep their mode until relaunched. No keyboard shortcut for the toggle.
- R6.12 Log one line at startup (tag = APP_ID): "UA mode=<DESKTOP|MOBILE> reason=<override|user|learned|config|auto-large|auto-compact> metadata=<applied|unsupported|default>".

## R7 Notification bridge (active only if ALLOW_NOTIFICATIONS; runtime check)
- R7.1 MUST check WebViewFeature.isFeatureSupported for WEB_MESSAGE_LISTENER and DOCUMENT_START_SCRIPT (nested, per R1.5). If either is missing, skip R7.2–R7.3, log once, never crash. Install the bridge on the main WebView before its first loadUrl or restoreState.
- R7.2 MUST call WebViewCompat.addWebMessageListener(webView, "AndroidNotify", ALLOWED_ORIGIN_RULES, listener). In the listener, check again that sourceOrigin's host is INTERNAL. Parse the JSON {title, body, tag} (ignore invalid JSON), truncating title to 200 characters and body to 1000, and post a notification.
- R7.3 MUST call WebViewCompat.addDocumentStartJavaScript(webView, polyfill, ALLOWED_ORIGIN_RULES). The polyfill MUST be idempotent (guarded by a window flag) and MUST:
  - Replace window.Notification with a class extending EventTarget: static permission = "granted"; static requestPermission(cb) resolves "granted" and calls cb if it is a function; the constructor calls AndroidNotify.postMessage(JSON.stringify({title: String(title || ""), body: String(options?.body || ""), tag: String(options?.tag || "")})); close() is a no-op.
  - Patch ServiceWorkerRegistration.prototype.showNotification (if present) to post the same way and return Promise.resolve().
  - Make navigator.permissions.query({name: "notifications"}) resolve {state: "granted"}, delegating all other permission names to the original.
- R7.4 MUST create a channel with NOTIFICATION_CHANNEL_ID, name @string/notification_channel_name, and IMPORTANCE_HIGH. Notifications use small icon R.drawable.ic_stat_notify, color ICON_BACKGROUND_HEX, BigTextStyle for the body, autoCancel, and a PendingIntent (FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT) that brings MainActivity to the front. Notification ID = tag.hashCode() if a tag is present, otherwise an incrementing counter. If POST_NOTIFICATIONS is not granted, drop the notification silently (check with ContextCompat.checkSelfPermission in the posting function).
- R7.5 MUST request POST_NOTIFICATIONS on a fresh launch (savedInstanceState == null) if ALLOW_NOTIFICATIONS and not granted.
- R7.6 MUST NOT use addJavascriptInterface or @JavascriptInterface anywhere.

## R8 Power, visibility and multi-display
- R8.1 MUST implement isWorkspaceAwake(context). It gathers pm.isInteractive, displayManager.displays.any { it.state == Display.STATE_ON }, and pm.isPowerSaveMode, passes them to the controller's wake state machine (R2.3e), dispatches any SLEEP/WAKE event, and returns controller.isAwake.
- R8.2 MUST give DesktopWebView a setWorkspaceAwake(Boolean) method (which re-applies the current window visibility when the value changes) and override onWindowVisibilityChanged(visibility): if not awake, call super with View.GONE; else if KEEP_ALIVE_WHEN_HIDDEN, call super with View.VISIBLE; else call super with visibility.
- R8.3 MUST register a DisplayManager.DisplayListener (on the main looper) and a BroadcastReceiver (via ContextCompat.registerReceiver with ContextCompat.RECEIVER_NOT_EXPORTED) for ACTION_SCREEN_OFF, ACTION_SCREEN_ON, and ACTION_POWER_SAVE_MODE_CHANGED. Register in onCreate, evaluate isWorkspaceAwake once right after registering, and unregister in onDestroy.
- R8.4 On SLEEP: CookieManager flush(), setWorkspaceAwake(false), webView.onPause(), webView.pauseTimers(). On WAKE: setWorkspaceAwake(true), webView.onResume(), webView.resumeTimers(), then evaluateJavascript("window.dispatchEvent(new Event('online'));window.dispatchEvent(new Event('focus'));", null). Hold zero WakeLocks.
- R8.5 Activity lifecycle methods MUST NOT call pauseTimers()/resumeTimers() (process-wide; they would freeze other windows). If KEEP_ALIVE_WHEN_HIDDEN is false (runtime check): onStop calls webView.onPause() and onStart calls webView.onResume().

## R9 Icons (zero-dependency Python generator)
- R9.1 `scripts/generate_icons.py` writes into the generated resource directory of R1.7 and MUST first delete any `mipmap-*` and `drawable*` directories there, so regeneration (including a change of icon mode) leaves no stale files.
- R9.2 MUST use only the Python 3 standard library. It is called by `site_config.py generate` with the resolved config; icon source files live in `sites/<id>/`. Builds never fetch icons from the network (discovery downloads them, R10.2).
- R9.3 Icon sources, one per site:
  - `svg` (ICON_SVG): a single-color glyph. Extract the viewBox, concatenate every `<path d>` (a leading lowercase "m" in each path is treated as absolute), and record the fill-rule (evenodd if any path declares it via attribute or style, inherited through `<g>`, otherwise nonzero). Ignore fill colors. `<title>`, `<desc>` and `<metadata>` are ignored. Exit non-zero with "ICON_SVG must be a single-color glyph made only of `<path>` elements" on transforms, `<use>`, `<defs>`, non-path shapes, gradients or pattern fills, or stroke-only/unfilled paths.
  - `raster` (ICON_RASTER): a square PNG or WebP of at least 192px (warn below 432px), dimensions read from the file header. ICON_RASTER_MASKABLE marks a maskable icon.
  - `letter` (ICON_LETTER): one A–Z/0–9 glyph from `scripts/letter_glyphs.json`, extracted once from DejaVu Sans Bold by `scripts/dev/extract_letter_glyphs.py` (license in `scripts/letter_glyphs.NOTICE`).
  - NEVER write brand path data from memory.
- R9.4 Android PathParser normalization (minified SVG path data makes Android's PathParser miscount arc parameters and silently render a blank icon):
  - Tokenize with an arc-flag-aware tokenizer. For A/a, parameters at positions 3 and 4 (mod 7, zero-based) each consume exactly one '0' or '1' character, so "00-4.88" becomes "0", "0", "-4.88". Split chained decimals ("-1.51.074" becomes "-1.51", "0.074"). Handle exponents.
  - Re-serialize as android_safe_path: a command letter before every parameter group (implicit repeats explicit, implicit lineto after M/m as L/l), tokens separated by exactly one space, numbers with a leading zero, at most 4 decimals, trailing zeros trimmed, no exponent notation, "-0" written as "0".
  - Every android:pathData uses android_safe_path (letter glyphs too).
- R9.5 MUST support `--verify RES_DIR`, which exits non-zero unless: the required files exist and every drawable/mipmap XML is well-formed; every pathData has correct parameter counts (M2 L2 H1 V1 C6 S4 Q4 T2 A7 Z0), arc flags 0/1, no number starting with a bare "." (regex `(^|[\s-])\.\d`), no exponent notation; no `"<<"` or `">>"` (built at runtime, e.g. `"<" * 2`, and the script itself contains neither sequence, not even as a bit shift); every `<inset>` drawable points at an existing image.
- R9.6 MUST generate:
  - drawable/ic_launcher_background.xml: 108dp vector, pathData "M 0 0 H 108 V 108 H 0 Z", fill ICON_BACKGROUND_HEX.
  - drawable/ic_launcher_foreground.xml:
    - svg: 108dp vector with `<group>` scale s = 54 / max(vbW, vbH), translateX = 54 - (vbMinX + vbW / 2) * s, translateY = 54 - (vbMinY + vbH / 2) * s, wrapping the path in ICON_FOREGROUND_HEX with fillType evenOdd/nonZero (24x24 viewBox: scale 2.25, translate 27).
    - raster: `<inset>` of drawable-nodpi/ic_launcher_raster.(png|webp) with 9dp inset when maskable (maps the 80% maskable safe circle onto the 72dp visible area) or 21dp otherwise (66dp safe zone).
    - letter: 108dp vector, glyph fitted to 40 units and centered, fill ICON_FOREGROUND_HEX.
  - Monochrome (themed icon): the foreground for svg and letter; for raster, drawable/ic_launcher_monochrome.xml with the app's first letter.
  - drawable/ic_stat_notify.xml: 24dp white silhouette: the svg glyph (viewport = viewBox, translated by -vbMinX/-vbMinY when non-zero, same fillType), else the letter glyph fitted to 20 units.
  - mipmap-anydpi/ic_launcher.xml and ic_launcher_round.xml: `<adaptive-icon>` with background, foreground and monochrome. No -v26 qualifier, no density PNGs.
  - Each generated file starts with a comment saying it is generated.

## R10 Site tooling
- R10.1 `scripts/site_config.py` (stdlib, Python 3.11+ for tomllib) MUST merge defaults.toml < defaults.local.toml < site.toml, reject unknown keys (suggesting the closest valid key), and validate types and rules: required keys; exactly one icon source, as a file inside the site directory; START_URL is https and covered by INTERNAL_DOMAINS; domains are bare lowercase hostnames; hex colors; SDK levels equal the template's (37); MIN_WINDOW_DP <= DEFAULT_WINDOW_DP; shortcut fields; the site directory equals APP_ID. Subcommands: `list [--json]`, `validate (--site ID | --all)`, `show`, `properties`, `generate`.
- R10.2 `scripts/discover_site.py URL` (with `scripts/discover/collect.js`, Playwright) MUST visit the site with light, dark and Android-WebView user agents, and draft `sites/<id>/site.toml` where every key carries a `# source (confidence)` comment. It derives:
  - START_URL: web manifest start_url (same site), else the requested URL; a redirect to another host of the same registrable domain keeps the requested path.
  - INTERNAL_DOMAINS: the registrable domain (Public Suffix List) plus observed domains containing the brand token; other API domains are listed in a NOTE for review.
  - APP_NAME: manifest short_name/name, simple-icons title (domain-matched), application-name, apple-mobile-web-app-title, og:site_name, cleaned title.
  - Header colors: theme-color (light/dark media), else the computed top-edge background normalized to sRGB through a canvas, else the manifest theme_color.
  - Icon, best first: simple-icons glyph (with brand color), mask-icon glyph, maskable manifest icon, any manifest/apple-touch/page icon of 192px or more, letter.
  - Capabilities: from getUserMedia/WebRTC/Notification/Push usage in page scripts and Permissions-Policy.
  - Warnings: a NOTE when the WebView user agent is blocked, and when a mobile user agent redirects to the Play Store.
  It saves the evidence to `sites/<id>/discovery.json` (gitignored) and validates the draft.
- R10.3 `scripts/enrich_site.py --site ID` (optional; the only script with a third-party dependency, the `anthropic` SDK) MUST send the draft and a compact evidence summary to Claude (`claude-opus-5-5`, explicit effort, JSON-schema structured output, server-side refusal fallback `fallbacks: "default"`), handle refusals and API errors with clear messages, and apply only: APP_NAME (adding APP_ID to keep the directory), INTERNAL_DOMAINS additions/removals (never the START_URL's domain), ALLOW_MICROPHONE/CAMERA/NOTIFICATIONS, USER_AGENT_MODE, and APP_SHORTCUTS (only when the site has none). The result is validated and rolled back if invalid. `--dry-run` and `--response FILE` work offline.
- R10.4 `./wrap URL | --site ID | --list` MUST chain discover, optional enrich, validate, SDK setup and either the full verification (`scripts/ci_verify.sh`) or `--quick` (assemble only), copy the APKs to APK_DIR, optionally render `scripts/preview_icons.py` output (`--preview`), and print `site=<id>`.
- R10.5 CI: `.github/workflows/android.yml` validates all sites, runs `python3 -m unittest discover -s scripts/tests`, then for every site in a matrix runs `scripts/ci_verify.sh` (with the sideload key from secrets when present). `.github/workflows/wrap.yml` is a workflow_dispatch form (url, site_id, enrich, open_pr) that wraps a URL, uploads APKs, preview and site files, and optionally opens a PR adding the site.
- R10.6 Script unit tests in `scripts/tests/` MUST cover site_config validation and generation, icon normalization/modes/verify, discovery derivations (offline, with synthetic evidence), and enrich patch application including rollback.

</requirements>

<forbidden>

Do not use any of the following to pass checks: lint baselines, abortOnError = false, disabling lint checks or issues, @SuppressLint (the only exception is "SetJavaScriptEnabled" on applyWebSettings), Kotlin @Suppress, JaCoCo exclusions beyond generated classes (R, BuildConfig), lowering any threshold, deleting or weakening tests, hand-editing generated files instead of fixing the generator, or hardcoding a site's values in template code or resources.
If a gate still fails after 3 fix attempts, stop and report the failure with its log. Do not weaken the requirement.

</forbidden>

<execution_plan>

Flow A, adding a site (the normal case; no template changes):
  1. `./wrap https://example.com` (add `--enrich` if an Anthropic API key is available). Gate: the summary shows every check passing.
  2. Review the low/medium-confidence keys and NOTEs in `sites/<id>/site.toml` and `dist/<id>-icons.png` (`--preview`); fix keys and rebuild with `./wrap --site <id>`.
  3. Commit `sites/<id>/` (not discovery.json). CI builds and verifies it.

Flow B, changing the template (run every phase; gates apply to every site in `sites/`):
Phase 0: Preflight and research
  - Run `scripts/setup_android_sdk.sh` (tool check: JDK 17+, platform 37, build-tools, aapt2, Python 3.11+, unzip; node + playwright optional for discovery). Stop if a required tool is missing.
  - Read the current versions of the pages below if the network allows; record findings with URLs in docs/BUILD_NOTES.md. Findings never silently override an R-requirement.
      https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview
      https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing
      https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
      https://developer.android.com/reference/androidx/webkit/WebSettingsCompat
      https://developer.android.com/about/versions/17/behavior-changes-17
  - Confirm AGP, Gradle, Kotlin and androidx.webkit versions (R1.2), and the androidx.webkit APIs from the downloaded library itself (`javap` on classes.jar from the AAR in the Gradle cache).
  - Gate: tools present; `python3 scripts/site_config.py validate --all` passes.
Phase 1: Gradle and generation (R1, R2.2, R10.1)
  - Gate: `./gradlew GRADLE_FLAGS help` succeeds for every site.
Phase 2: Core logic and tests (R2.3–R2.5)
  - Gate: `testDebugUnitTest jacocoTestReport jacocoTestCoverageVerification` succeeds and the JaCoCo report lists only CORE_PACKAGE classes (at least one).
Phase 3: Icons (R9)
  - Gate: every icon mode generates and verifies (`python3 -m unittest discover -s scripts/tests`).
Phase 4: Manifest, themes, resources (R3.5–R3.7, R4)
  - Gate: `:app:processDebugMainManifest :app:processDebugResources` succeeds for every site.
Phase 5: Android layer (R3, R5–R8)
  - Gate: `assembleDebug` succeeds with no Kotlin compiler warnings in app sources.
Phase 5.5: Self-review
  - Re-read every R-requirement and the code that implements it, especially callback re-entrancy and WebView destruction (R6.6, R6.7, R6.9), popup decisions, lifecycle registration, runtime feature flags, and that every resource and string is used.
Phase 6: Full verification
  For every site: `SITE=<id> scripts/ci_verify.sh`, which runs and reports:
  1. `./gradlew GRADLE_FLAGS clean testDebugUnitTest jacocoTestReport jacocoTestCoverageVerification lintDebug assembleDebug assembleRelease`, plus the coverage-report check (classes > 0, all in CORE_PACKAGE).
  2. Lint XML: 0 errors, 0 UnusedResources, 0 ObsoleteSdkInt; list remaining warnings (aim for zero).
  3. `unzip -l` on the debug APK and RELEASE_APK_PATH: no `lib/` entries, no `*.so` files.
  4. `aapt2 dump badging` on the DEBUG APK: package = PACKAGE_NAME; the min SDK line (`sdkVersion:` or `minSdkVersion:`) = 37; targetSdkVersion = 37; the icon on the application: and launchable-activity: lines matches `res/mipmap-anydpi(-v\d+)?/ic_launcher.xml`.
  5. `python3 scripts/generate_icons.py --verify <generated res dir>` exits 0.
  6. Static checks (no matches unless stated): core imports of `android.`/`androidx.`/`com.google.android.`; `addJavascriptInterface|@JavascriptInterface` in app/src; `screenOrientation|minAspectRatio|maxAspectRatio` in the template and generated manifests; `"<<|>>"` in app/src, the generated sources, scripts/generate_icons.py and the Gradle files (Kotlin: string literals only); `userAgentString\s*=` in app/src/main exactly once, inside applyWebSettings.
  7. Copy the APKs to `<<APK_DIR>>/<<DEBUG_APK_NAME>>` and `<<APK_DIR>>/<<RELEASE_APK_NAME>>`, replacing any APKs already in APK_DIR, then run `site_config.py prune-apks` (the last 3 versions built are kept); verify the release signature with apksigner and report the signer.

</execution_plan>

<final_reminders>

- Zero .so files. Only the listed dependencies.
- No site-specific value in template code or resources; generated files are never committed.
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
1. Sites built, with their derived values (`site_config.py show`).
2. Changed files (template, generators, tooling, sites).
3. Requirement checklist: each R-ID -> status (Done / Deviated / Skipped / Inactive for this site) -> file:line.
4. Deviations table: R-ID | what changed | why.
5. Verification results per site: each Phase 6 step with a pass/fail and a trimmed output excerpt (≤ 10 lines each); CI run link.
6. Known limitations, always including:
   - Google and some identity providers block sign-in inside embedded WebViews ("disallowed_useragent"); do not attempt to evade this. Discovery warns when a site refuses WebViews outright.
   - A desktop UA may expose site features that depend on APIs WebView lacks (Push API, File System Access, WebUSB/Bluetooth, some passkey flows).
   - A UA toggle applies to the current window only; other open windows update on relaunch.
   - Site features that depend on unsupported APIs (blob downloads, etc.).
   - While the workspace is asleep (screen off, or Battery Saver when PAUSE_ON_POWER_SAVE is true), page timers are paused, so real-time connections drop and web notifications arrive only after wake. There is no background delivery (no foreground service, by design).
   - The notification polyfill always reports "granted" to the page; if the user denies POST_NOTIFICATIONS, notifications are dropped silently. POST_NOTIFICATIONS is declared for every site, but requested only when ALLOW_NOTIFICATIONS.
   - Discovery is heuristic: domains, capabilities and colors marked medium/low confidence need review; keyboard shortcuts are not discovered (enrich can suggest them).
   - Raster and letter icons cannot be themed with the brand glyph; their themed and status-bar icons use the app's first letter.
   - Release APKs are signed with the sideload key or the local debug key: installable for personal sideloading only, not for distribution. Brand names and logos belong to their owners; share the project, not built APKs of other people's brands.

</output_contract>
