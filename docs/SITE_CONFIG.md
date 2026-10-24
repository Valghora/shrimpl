# Site configuration reference

A site is one file, `sites/<id>/site.toml`, plus its icon file in the same directory. Values are
merged in this order, later winning:

1. `sites/defaults.toml` (committed, shared by every site)
2. `sites/defaults.local.toml` (gitignored; personal overrides such as your own `PACKAGE_PREFIX`)
3. `sites/<id>/site.toml`

`python3 scripts/site_config.py validate --site <id>` checks a site; `show --site <id>` prints
every resolved and derived value. Unknown keys are rejected with a "did you mean" hint.

The directory name must equal the site's APP_ID. Build a site with `./wrap --site <id>` or
`./gradlew -Psite=<id> assembleDebug`.

## Identity

| Key | Type | Default | Meaning |
|---|---|---|---|
| `APP_NAME` | string | required | Launcher label and task name. |
| `APP_ID` | string | from APP_NAME | Lowercase `[a-z0-9]`, starting with a letter. Set it to keep the id when renaming the app. |
| `PACKAGE_PREFIX` | string | `com.valghora.pwa` | The applicationId is `<PACKAGE_PREFIX>.<APP_ID>`. Change it in `defaults.local.toml` if you share builds. |
| `VERSION_CODE` | integer or `"auto"` | `"auto"` | `"auto"` is the minutes since 2024-01-01 UTC, so every rebuild installs as an update. |
| `VERSION_NAME` | string | `1.0.0` | Shown in app info. |
| `MIN_SDK`, `TARGET_SDK`, `COMPILE_SDK` | integer | 37 | Fixed by the template; any other value is rejected. |

## Domains

| Key | Type | Default | Meaning |
|---|---|---|---|
| `START_URL` | https URL | required | Loaded on launch. Its host must be covered by `INTERNAL_DOMAINS`. |
| `INTERNAL_DOMAINS` | list of hostnames | required | Load inside the app. Each entry also matches its subdomains. Bare lowercase hostnames only. |
| `OAUTH_DOMAINS` | list of hostnames | Google, Apple, GitHub, Microsoft | Sign-in pages that also load inside the app. Everything else opens in the browser. |

## User agent

| Key | Type | Default | Meaning |
|---|---|---|---|
| `USER_AGENT_MODE` | `"auto"`, `"desktop"`, `"mobile"` | `"auto"` | `auto` = desktop on PCs and screens 600dp or wider, mobile otherwise. |
| `USER_AGENT_OVERRIDE` | string | `""` | Used verbatim when set; turns off the two features below. |
| `UA_STORE_REDIRECT_FALLBACK` | boolean | true | If the mobile site redirects to the Play Store right after launch, switch to desktop once and remember it. |
| `UA_USER_TOGGLE` | boolean | true | Long-press launcher shortcut "Request desktop/mobile site". |

## Branding

Set exactly one icon source. Icon files live in the site directory (never a URL; discovery
downloads them).

| Key | Type | Default | Meaning |
|---|---|---|---|
| `ICON_SVG` | file name | - | Single-color glyph made only of `<path>` elements, no background shape (simple-icons.org has one for most brands). Its own colors are ignored. Gives the best result: crisp launcher, themed and status-bar icons. |
| `ICON_RASTER` | file name | - | Square PNG or WebP, 192px or larger (432px+ recommended). Used as-is on the launcher. |
| `ICON_RASTER_MASKABLE` | boolean | false | true if the raster is a maskable icon (artwork fills the square, logo within the central 80%). |
| `ICON_LETTER` | one letter/digit or `"auto"` | - | A letter on the background color. `auto` = first letter or digit of APP_NAME. |
| `ICON_BACKGROUND_HEX` | `#RRGGBB` | required | Adaptive-icon background; also the notification accent color. |
| `ICON_FOREGROUND_HEX` | `#RRGGBB` or `"auto"` | `"auto"` | Glyph or letter color. `auto` = white or black, whichever contrasts better. Not used by raster icons. |
| `HEADER_SURFACE_HEX` | `#RRGGBB` | required | Window, caption bar and splash color, ideally the site's header color. Status-bar icon contrast follows it. |
| `HEADER_SURFACE_HEX_NIGHT` | `#RRGGBB` or `""` | `""` | Header color when the system is in dark theme; empty = same as `HEADER_SURFACE_HEX`. |

Raster and letter icons use the first letter of the app name for the themed (monochrome) icon
and the notification icon.

## Capabilities

| Key | Type | Default | Meaning |
|---|---|---|---|
| `ALLOW_MICROPHONE` | boolean | false | Adds RECORD_AUDIO; grants the page's microphone requests after the system prompt. |
| `ALLOW_CAMERA` | boolean | false | Adds CAMERA; grants camera requests after the system prompt. |
| `ALLOW_NOTIFICATIONS` | boolean | true | Web notifications become Android notifications; the permission is requested on first launch. |
| `ALLOW_PROTECTED_MEDIA` | boolean | false | Grants DRM (protected media ID) requests, for streaming sites. |
| `ENABLE_MULTI_WINDOW` | boolean | true | Several windows of the app at once (desktop windowing and split screen). |
| `KEEP_ALIVE_WHEN_HIDDEN` | boolean | true | Keep the page running while its window is hidden or minimized. |
| `PAUSE_ON_POWER_SAVE` | boolean | false | Also freeze the page whenever Battery Saver is on, even in the foreground. The page always freezes while the screen is off. |
| `NATIVE_CONTEXT_MENU` | boolean | true | Right-click/long-press menu on links and images (copy link, open link, save image). |

## Build output

| Key | Type | Default | Meaning |
|---|---|---|---|
| `RELEASE_SIGNING` | `"auto"` or `"unsigned"` | `"auto"` | `auto` signs the release APK with the sideload key if one is configured (see the README), otherwise the local debug key. |

## Windows

| Key | Type | Default | Meaning |
|---|---|---|---|
| `DEFAULT_WINDOW_DP` | `[width, height]` | `[1280, 800]` | First window size in desktop windowing. |
| `MIN_WINDOW_DP` | `[width, height]` | `[400, 500]` | Smallest window size; must not exceed the default. |

## Keyboard shortcuts

| Key | Type | Default | Meaning |
|---|---|---|---|
| `APP_SHORTCUTS` | list of `{ label, key, modifiers }` | `[]` | The site's own shortcuts, listed in the system Keyboard Shortcuts Helper. The keys still reach the page. |
| `NEW_WINDOW_SHORTCUT` | `{ key, modifiers }` | unset | Opens a new window (needs `ENABLE_MULTI_WINDOW`). |

`key` is an Android key name such as `KEYCODE_K`. `modifiers` is a list of `CTRL`, `SHIFT`, `ALT`
and `META`. Shortcuts may not collide with the built-ins: Ctrl+R / F5 reload, Ctrl+= / Ctrl+- /
Ctrl+0 zoom.

```toml
APP_SHORTCUTS = [
  { label = "Quick Switcher", key = "KEYCODE_K", modifiers = ["CTRL"] },
]
NEW_WINDOW_SHORTCUT = { key = "KEYCODE_N", modifiers = ["CTRL", "SHIFT"] }
```

## Discovery comments

`scripts/discover_site.py` writes a comment after each key with where the value came from and a
confidence: high (taken directly from the site's own metadata), medium (inferred from observed
behavior) or low (a guess or default). Review medium and low values, and every `# NOTE:` line,
before committing a site.
